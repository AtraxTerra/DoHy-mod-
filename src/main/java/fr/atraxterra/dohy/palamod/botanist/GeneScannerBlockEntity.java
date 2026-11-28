package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import fr.atraxterra.dohy.ui.Toasts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le scanner de Genis ({@code TileEntityBotanistScanner} de JobsReborn) : il revele la plante
 * d'une mutation inconnue, ou ajoute au codex la plante d'une graine qui n'y est pas encore.
 *
 * <p>Trois cases : la graine a analyser, le carburant (le charbon : l'animation « no_coal » du
 * jar), le resultat. « Lancer le scan » demande le scan ; il part au tick suivant si tout est pret
 * (graine scannable par son maitre, charbon, case de resultat vide) et dure 15 secondes pour une
 * plante deja dans le codex, 45 pour une decouverte. A la fin, il consomme une graine et un charbon
 * et pose le resultat :
 * <ul>
 *   <li>une graine de mutation revient telle quelle, sa plante ajoutee au codex ;</li>
 *   <li>une mutation inconnue devient une graine de la plante qu'elle portait ;</li>
 *   <li>une mutation inconnue sans plante (aucune a la portee du joueur) devient un dechet.</li>
 * </ul>
 * Une plante nouvelle au codex rapporte l'XP de decouverte de sa rarete ({@link Rarity}).
 *
 * <p>Comme dans JobsReborn, le scan s'interrompt (sans rien consommer) si la graine change, si le
 * charbon manque ou si la case du resultat se remplit ; il n'avance que son maitre en ligne, dans
 * la dimension du scanner. Seul celui qui l'a pose s'en sert (comme le pot) : le codex est le sien.
 */
public class GeneScannerBlockEntity extends MachineBlockEntity implements MachineMenu.Buttons {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SIZE = 3;
    /** Le bouton « Lancer le scan ». */
    public static final int BUTTON_SCAN = 0;
    /** La duree d'un scan : plante deja dans le codex, ou decouverte. */
    public static final int KNOWN_TICKS = 15 * 20;
    public static final int DISCOVERY_TICKS = 45 * 20;
    /** Le son du scan dure cinq secondes : il est rejoue tant que le scan dure. */
    private static final int SCAN_SOUND_EVERY = 100;

    /** Ce que dit l'ecran (la valeur libre 0 du menu), les lignes d'etat de JobsReborn. */
    public static final int STATE_EMPTY = 0;
    public static final int STATE_KNOWN = 1;
    public static final int STATE_NO_FUEL = 2;
    public static final int STATE_TAKE = 3;
    public static final int STATE_READY = 4;
    public static final int STATE_SCANNING = 5;

    /** Les animations du modele : attente, sans charbon, scan en cours, scan termine. */
    public enum Anim {
        IDLE("idle"),
        NO_COAL("no_coal"),
        SCANNING("scan_in_progress"),
        COMPLETE("scan_complete");

        private final String clip;

        Anim(String clip) {
            this.clip = clip;
        }

        public String clip() {
            return clip;
        }

        static Anim byOrdinal(int ordinal) {
            Anim[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
        }
    }

    private UUID owner;
    private String ownerName = "";
    /** La graine en cours d'analyse (une seule), vide hors scan. */
    private ItemStack scanned = ItemStack.EMPTY;
    /** La plante que le scan revele ({@code ""} : aucune, la mutation finira en dechet). */
    private String target = "";
    private boolean discovery;
    private int progress;
    private int duration;
    private boolean requested;
    /** Pour le rendu (envoye au client) : l'animation, depuis quand, et l'objet pose sur le scanner. */
    private Anim anim = Anim.IDLE;
    private long animSince;
    private long scanStart;
    private ItemStack display = ItemStack.EMPTY;

    public GeneScannerBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBotanist.GENE_SCANNER_TYPE.get(), pos, state, SIZE, Component.translatable(
                "container.palamod.gene_scanner"));
    }

    // -- Lu par le rendu et l'ecran ------------------------------------------------------------

    public Anim anim() {
        return anim;
    }

    public long animSince() {
        return animSince;
    }

    public long scanStart() {
        return scanStart;
    }

    public int scanDuration() {
        return duration;
    }

    public ItemStack display() {
        return display;
    }

    public String ownerName() {
        return ownerName;
    }

    // -- Le maitre ---------------------------------------------------------------------------------

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        setChanged();
    }

    @Override
    public boolean canOpen(ServerPlayer player) {
        if (owner == null) {
            setOwner(player);
        }
        if (owner.equals(player.getUUID())) {
            return true;
        }
        player.sendSystemMessage(Component.literal("Ce scanner appartient a " + ownerName
                + " : il revele les mutations pour son codex.").withStyle(ChatFormatting.RED), true);
        return false;
    }

    private ServerPlayer ownerHere(ServerLevel server) {
        if (owner == null) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.level() == server ? player : null;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Le codex part avec l'ecran : ses chiffres (decouvertes, prochain deblocage) sont a jour.
        if (player instanceof ServerPlayer server) {
            BotanistCodex.sync(server);
        }
        return super.createMenu(containerId, playerInventory, player);
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaBotanist.GENE_SCANNER_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.GENE_SCANNER;
    }

    // -- Les cases -----------------------------------------------------------------------------

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INPUT -> stack.getItem() instanceof UnknownMutationItem || stack.getItem() instanceof MutationSeedItem;
            case SLOT_FUEL -> fuel(stack);
            default -> false;
        };
    }

    @Override
    protected boolean hopperMayTake(int slot) {
        return slot == SLOT_OUTPUT;
    }

    /** Le carburant du scanner (le {@code fuel} de sa configuration) : le charbon. */
    public static boolean fuel(ItemStack stack) {
        return stack.is(Items.COAL);
    }

    /**
     * Ce que le scanner accepte d'analyser pour ce joueur ({@code isScannable}) : une mutation
     * inconnue, ou la graine d'une plante absente de son codex.
     */
    public static boolean scannable(ItemStack stack, ServerPlayer player) {
        if (stack.getItem() instanceof UnknownMutationItem) {
            return true;
        }
        return stack.getItem() instanceof MutationSeedItem seed && !BotanistCodex.has(player, seed.mutation());
    }

    private boolean ready(ServerPlayer player) {
        ItemStack input = items.get(SLOT_INPUT);
        return !input.isEmpty() && scannable(input, player) && fuel(items.get(SLOT_FUEL))
                && items.get(SLOT_OUTPUT).isEmpty();
    }

    // -- Le scan -------------------------------------------------------------------------------

    @Override
    public boolean pressButton(ServerPlayer player, int id) {
        if (id != BUTTON_SCAN || !(level instanceof ServerLevel server)) {
            return false;
        }
        if (!scanned.isEmpty()) {
            return false;
        }
        if (!ready(player)) {
            if (!items.get(SLOT_INPUT).isEmpty() && !fuel(items.get(SLOT_FUEL))) {
                play(server, PalaBotanist.SCANNER_NO_COAL.get(), 1.0f);
            }
            return false;
        }
        if (owner == null || !owner.equals(player.getUUID())) {
            return false;
        }
        requested = true;
        return true;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ServerPlayer player = ownerHere(server);
        if (player != null) {
            scan(server, player);
        }
        refresh(server);
    }

    private void scan(ServerLevel server, ServerPlayer player) {
        ItemStack input = items.get(SLOT_INPUT);
        if (!ready(player) || (!scanned.isEmpty() && !ItemStack.isSameItemSameComponents(single(input), scanned))) {
            reset();
            return;
        }
        if (scanned.isEmpty()) {
            if (requested) {
                requested = false;
                start(server, player, input);
            }
            return;
        }
        progress++;
        if (progress % SCAN_SOUND_EVERY == 0 && progress < duration) {
            play(server, PalaBotanist.SCANNER_SCAN.get(), 0.7f);
        }
        if (progress >= duration) {
            finish(server, player, input);
        } else if (progress % 20 == 0) {
            setChanged();
        }
    }

    private void start(ServerLevel server, ServerPlayer player, ItemStack input) {
        Mutation revealed = reveal(server, player, input);
        target = revealed == null ? "" : revealed.id();
        discovery = revealed != null && !BotanistCodex.has(player, revealed);
        progress = 0;
        duration = discovery ? DISCOVERY_TICKS : KNOWN_TICKS;
        scanned = single(input);
        scanStart = server.getGameTime();
        play(server, PalaBotanist.SCANNER_SCAN.get(), 0.7f);
        setChanged();
    }

    /**
     * La plante que revelera le scan : celle portee par la mutation inconnue, ou celle de la
     * graine ; une mutation inconnue sans plante en tire une a la portee du joueur ({@code draw}),
     * ou aucune.
     */
    private static Mutation reveal(ServerLevel server, ServerPlayer player, ItemStack input) {
        if (input.getItem() instanceof MutationSeedItem seed) {
            return seed.mutation();
        }
        if (input.getItem() instanceof UnknownMutationItem) {
            Mutation carried = UnknownMutationItem.target(input).orElse(null);
            if (carried != null) {
                return carried;
            }
            int level = JobApi.level(player, Job.FARMER);
            List<Mutation> eligible = new ArrayList<>();
            for (Mutation mutation : Mutation.values()) {
                if (mutation.level() <= level) {
                    eligible.add(mutation);
                }
            }
            return eligible.isEmpty() ? null : eligible.get(server.getRandom().nextInt(eligible.size()));
        }
        return null;
    }

    private void finish(ServerLevel server, ServerPlayer player, ItemStack input) {
        Mutation revealed = Mutation.byId(target);
        ItemStack result;
        if (input.getItem() instanceof MutationSeedItem) {
            result = single(input);
        } else if (revealed == null) {
            result = new ItemStack(PalaBotanist.MUTATION_WASTE.get());
        } else {
            result = new ItemStack(PalaBotanist.MUTATION_SEEDS.get(revealed).get());
        }
        removeItem(SLOT_FUEL, 1);
        removeItem(SLOT_INPUT, 1);
        setItem(SLOT_OUTPUT, result);
        reset();
        play(server, PalaBotanist.SCANNER_COMPLETE.get(), 1.0f);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, worldPosition.getX() + 0.5d, worldPosition.getY() + 1.1d,
                worldPosition.getZ() + 0.5d, 8, 0.3d, 0.2d, 0.3d, 0.0d);
        if (revealed == null) {
            player.sendSystemMessage(Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN)
                    .append(Component.literal("Rien a decouvrir a ton niveau : la mutation tombe en dechet.")
                            .withStyle(ChatFormatting.GRAY)));
            return;
        }
        if (BotanistCodex.discover(player, revealed)) {
            int xp = revealed.discoveryXp();
            JobApi.grant(player, Job.FARMER, xp);
            player.sendSystemMessage(Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN)
                    .append(Component.literal("Nouvelle mutation dans ton codex : " + revealed.label() + " ("
                            + revealed.rarity().label().toLowerCase(java.util.Locale.ROOT) + ", +" + xp
                            + " XP de Fermier).").withColor(revealed.rarity().color() & 0xFFFFFF)));
            Toasts.send(player, result.copy(), "Nouvelle mutation", revealed.label(), "Ajoutee a ton codex",
                    revealed.rarity().color(), "botanist_codex");
        } else {
            player.sendSystemMessage(Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN)
                    .append(Component.literal("Mutation revelee : " + revealed.label() + " (deja dans ton codex).")
                            .withStyle(ChatFormatting.GREEN)));
        }
    }

    private void reset() {
        if (scanned.isEmpty() && progress == 0 && duration == 0 && target.isEmpty() && !requested) {
            return;
        }
        progress = 0;
        duration = 0;
        target = "";
        discovery = false;
        scanned = ItemStack.EMPTY;
        requested = false;
        setChanged();
    }

    private static ItemStack single(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    private void play(ServerLevel server, SoundEvent sound, float volume) {
        server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, 1.0f);
    }

    // -- Le rendu : animation et objet pose ---------------------------------------------------

    /** L'objet pose sur le scanner ({@code resolveDisplay}) : le resultat, sinon la graine. */
    private ItemStack resolveDisplay() {
        ItemStack output = items.get(SLOT_OUTPUT);
        return single(output.isEmpty() ? items.get(SLOT_INPUT) : output);
    }

    /** L'animation ({@code resolveAnimation}) : scan, resultat a prendre, charbon absent, attente. */
    private Anim resolveAnim() {
        if (!scanned.isEmpty()) {
            return Anim.SCANNING;
        }
        if (!items.get(SLOT_OUTPUT).isEmpty()) {
            return Anim.COMPLETE;
        }
        return fuel(items.get(SLOT_FUEL)) ? Anim.IDLE : Anim.NO_COAL;
    }

    private void refresh(ServerLevel server) {
        Anim next = resolveAnim();
        ItemStack shown = resolveDisplay();
        boolean changed = false;
        if (next != anim) {
            anim = next;
            animSince = server.getGameTime();
            changed = true;
        }
        if (!ItemStack.isSameItemSameComponents(shown, display)) {
            display = shown;
            changed = true;
        }
        if (changed) {
            setChanged();
            BlockState state = getBlockState();
            server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // -- L'ecran : l'etat et l'avancement -------------------------------------------------------

    @Override
    public int work() {
        return progress;
    }

    @Override
    public int cycleDuration() {
        return duration;
    }

    /**
     * La valeur libre 0 : la ligne d'etat de l'ecran, vue par le maitre du scanner (le seul a
     * l'ouvrir) ; la valeur 1 : 1 si le scan en cours est une decouverte.
     */
    @Override
    public int extra(int index) {
        if (index == 1) {
            return discovery ? 1 : 0;
        }
        if (index != 0) {
            return 0;
        }
        if (!scanned.isEmpty()) {
            return STATE_SCANNING;
        }
        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty()) {
            return items.get(SLOT_OUTPUT).isEmpty() ? STATE_EMPTY : STATE_TAKE;
        }
        ServerPlayer player = level instanceof ServerLevel server ? ownerHere(server) : null;
        if (player != null && !scannable(input, player)) {
            return STATE_KNOWN;
        }
        if (!items.get(SLOT_OUTPUT).isEmpty()) {
            return STATE_TAKE;
        }
        return fuel(items.get(SLOT_FUEL)) ? STATE_READY : STATE_NO_FUEL;
    }

    // -- Sauvegarde et envoi -------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr("owner", "");
        try {
            owner = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            owner = null;
        }
        ownerName = input.getStringOr("owner_name", "");
        scanned = input.read("scanned", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        target = input.getStringOr("target", "");
        discovery = input.getBooleanOr("discovery", false);
        progress = Math.max(0, input.getIntOr("progress", 0));
        duration = Math.max(0, input.getIntOr("duration", 0));
        anim = Anim.byOrdinal(input.getIntOr("anim", 0));
        animSince = input.getLongOr("anim_since", 0L);
        scanStart = input.getLongOr("scan_start", 0L);
        display = input.read("display", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        if (!scanned.isEmpty()) {
            output.store("scanned", ItemStack.CODEC, scanned);
        }
        output.putString("target", target);
        output.putBoolean("discovery", discovery);
        output.putInt("progress", progress);
        output.putInt("duration", duration);
        output.putInt("anim", anim.ordinal());
        output.putLong("anim_since", animSince);
        output.putLong("scan_start", scanStart);
        if (!display.isEmpty()) {
            output.store("display", ItemStack.CODEC, display);
        }
    }
}
