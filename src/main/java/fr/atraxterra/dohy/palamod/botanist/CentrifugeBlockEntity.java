package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * La centrifugeuse ({@code TileEntityBotanistCentrifuge} de JobsReborn) : elle change une graine de
 * mutation en mutation inconnue, avec une chance que donnent les objets poses autour.
 *
 * <p>Huit cases : la graine (une graine de mutation, n'importe laquelle), six objets de
 * centrifugation ({@link Catalysts}), le resultat. « Centrifuger » demande la centrifugation ; elle
 * part au tick suivant si tout est pret (graine, case de resultat vide, chance au-dessus de zero) et
 * dure dix secondes. Si la graine ou les objets changent en route, elle s'interrompt sans rien
 * consommer. A la fin, elle prend la graine et un de chaque objet, puis tire la chance (la somme de
 * celles des objets, au plus 100 %) :
 * <ul>
 *   <li>reussite : une mutation inconnue, sans plante (le scanner en tire une a la portee de celui
 *   qui la scanne) ;</li>
 *   <li>echec : la graine rendue une fois sur deux, sinon un dechet de mutation.</li>
 * </ul>
 * A 100 %, la centrifugation est « boostee » : la reussite est sure, l'animation et le son changent.
 *
 * <p>Pas de maitre : la machine ne garde rien du joueur, n'importe qui s'en sert.
 */
public class CentrifugeBlockEntity extends MachineBlockEntity implements MachineMenu.Buttons {

    public static final int SLOT_SEED = 0;
    public static final int FIRST_ITEM = 1;
    public static final int ITEMS = 6;
    public static final int SLOT_RESULT = 7;
    public static final int SIZE = 8;
    /** Le bouton « Centrifuger ». */
    public static final int BUTTON_START = 0;
    /** Dix secondes ({@code DURATION} de JobsReborn). */
    public static final int DURATION = 200;
    /** A l'echec, la graine revient une fois sur deux ({@code SEED_BACK}). */
    private static final double SEED_BACK = 0.5d;
    /** Le bourdonnement d'attente (idle.ogg, quatre secondes) est rejoue tant qu'elle attend. */
    private static final int IDLE_SOUND_EVERY = 80;

    /** Ce que dit l'ecran (la valeur libre 0 du menu). */
    public static final int STATE_EMPTY = 0;
    public static final int STATE_NO_ITEM = 1;
    public static final int STATE_TAKE = 2;
    public static final int STATE_READY = 3;
    public static final int STATE_RUNNING = 4;

    /** Les animations du modele ({@code resolveAnimation}). */
    public enum Anim {
        IDLE("idle"),
        DIRECT_MUTATION("direct_mutation"),
        BOOSTED_SEED("boosted_seed"),
        FINISHED("finished"),
        FINISHED_MUTATED("finished_mutated"),
        FINISHED_BOOSTED("finished_boosted"),
        FINISHED_BOOSTED_MUTATED("finished_boosted_mutated");

        private final String clip;

        Anim(String clip) {
            this.clip = clip;
        }

        public String clip() {
            return clip;
        }

        public boolean looping() {
            return this == IDLE;
        }

        static Anim byOrdinal(int ordinal) {
            Anim[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
        }
    }

    private int progress;
    private int duration;
    private boolean requested;
    /** La derniere centrifugation etait a 100 % : l'animation de fin le montre. */
    private boolean boosted;
    private Anim anim = Anim.IDLE;
    private long animSince;
    private long idleSoundAt;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBotanist.CENTRIFUGE_TYPE.get(), pos, state, SIZE, Component.translatable(
                "container.palamod.mutation_centrifuge"));
    }

    // -- Lu par le rendu -----------------------------------------------------------------------

    public Anim anim() {
        return anim;
    }

    public long animSince() {
        return animSince;
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaBotanist.CENTRIFUGE_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.CENTRIFUGE;
    }

    // -- Les cases -----------------------------------------------------------------------------

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_SEED) {
            return stack.getItem() instanceof MutationSeedItem;
        }
        if (slot >= FIRST_ITEM && slot < FIRST_ITEM + ITEMS) {
            return Catalysts.accepts(stack);
        }
        return false;
    }

    @Override
    protected boolean hopperMayTake(int slot) {
        return slot == SLOT_RESULT;
    }

    /** La chance de reussite : la somme de celles des objets poses, au plus 1 ({@code getChance}). */
    public double chance() {
        double chance = 0.0d;
        for (int slot = FIRST_ITEM; slot < FIRST_ITEM + ITEMS; slot++) {
            chance += Catalysts.chance(items.get(slot));
        }
        return Math.min(1.0d, chance);
    }

    /** Graine de mutation, case de resultat vide, chance au-dessus de zero ({@code isReady}). */
    private boolean ready() {
        return items.get(SLOT_SEED).getItem() instanceof MutationSeedItem && items.get(SLOT_RESULT).isEmpty()
                && chance() > 0.0d;
    }

    // -- La centrifugation ------------------------------------------------------------------

    @Override
    public boolean pressButton(ServerPlayer player, int id) {
        if (id != BUTTON_START || progress > 0 || !ready()) {
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
        process(server);
        refresh(server);
    }

    private void process(ServerLevel server) {
        if (!ready() || (progress <= 0 && !requested)) {
            if (progress != 0 || duration != 0 || requested) {
                progress = 0;
                duration = 0;
                requested = false;
                setChanged();
            }
            return;
        }
        requested = false;
        duration = DURATION;
        progress++;
        if (progress % 20 == 0) {
            setChanged();
        }
        if (progress < duration) {
            return;
        }
        progress = 0;
        duration = 0;
        craft(server);
    }

    private void craft(ServerLevel server) {
        double chance = chance();
        boosted = chance >= 1.0d;
        ItemStack seed = items.get(SLOT_SEED).copyWithCount(1);
        removeItem(SLOT_SEED, 1);
        for (int slot = FIRST_ITEM; slot < FIRST_ITEM + ITEMS; slot++) {
            if (Catalysts.accepts(items.get(slot))) {
                removeItem(slot, 1);
            }
        }
        ItemStack result;
        if (server.getRandom().nextDouble() < chance) {
            result = UnknownMutationItem.create(null);
            server.sendParticles(ParticleTypes.WITCH, worldPosition.getX() + 0.5d, worldPosition.getY() + 1.2d,
                    worldPosition.getZ() + 0.5d, 14, 0.3d, 0.3d, 0.3d, 0.02d);
        } else {
            result = server.getRandom().nextDouble() < SEED_BACK ? seed : new ItemStack(PalaBotanist.MUTATION_WASTE.get());
            server.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5d, worldPosition.getY() + 1.2d,
                    worldPosition.getZ() + 0.5d, 10, 0.25d, 0.2d, 0.25d, 0.01d);
        }
        setItem(SLOT_RESULT, result);
        setChanged();
    }

    // -- Animation et sons ------------------------------------------------------------------------

    /** L'animation ({@code resolveAnimation}) : la fin si un resultat attend, la centrifugation, l'attente. */
    private Anim resolveAnim() {
        ItemStack result = items.get(SLOT_RESULT);
        if (!result.isEmpty()) {
            boolean mutated = result.getItem() instanceof UnknownMutationItem;
            if (boosted) {
                return mutated ? Anim.FINISHED_BOOSTED_MUTATED : Anim.FINISHED_BOOSTED;
            }
            return mutated ? Anim.FINISHED_MUTATED : Anim.FINISHED;
        }
        if (progress <= 0) {
            return Anim.IDLE;
        }
        return chance() >= 1.0d ? Anim.BOOSTED_SEED : Anim.DIRECT_MUTATION;
    }

    private void refresh(ServerLevel server) {
        Anim next = resolveAnim();
        long now = server.getGameTime();
        if (next != anim) {
            anim = next;
            animSince = now;
            setChanged();
            BlockState state = getBlockState();
            server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            if (!next.looping()) {
                play(server, sound(next));
            }
            idleSoundAt = now;
        }
        // Comme dans JobsReborn, l'attente bourdonne en boucle ; le reste se joue une fois.
        if (anim.looping() && now >= idleSoundAt) {
            play(server, PalaBotanist.CENTRIFUGE_IDLE.get());
            idleSoundAt = now + IDLE_SOUND_EVERY;
        }
    }

    private static SoundEvent sound(Anim anim) {
        return switch (anim) {
            case DIRECT_MUTATION -> PalaBotanist.CENTRIFUGE_DIRECT_MUTATION.get();
            case BOOSTED_SEED -> PalaBotanist.CENTRIFUGE_BOOSTED_SEED.get();
            case FINISHED_MUTATED -> PalaBotanist.CENTRIFUGE_FINISHED_MUTATED.get();
            case FINISHED_BOOSTED -> PalaBotanist.CENTRIFUGE_FINISHED_BOOSTED.get();
            case FINISHED_BOOSTED_MUTATED -> PalaBotanist.CENTRIFUGE_FINISHED_BOOSTED_MUTATED.get();
            default -> PalaBotanist.CENTRIFUGE_FINISHED.get();
        };
    }

    private void play(ServerLevel server, SoundEvent sound) {
        server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    // -- L'ecran -------------------------------------------------------------------------------

    @Override
    public int work() {
        return progress;
    }

    @Override
    public int cycleDuration() {
        return duration;
    }

    /** La valeur libre 0 : la ligne d'etat ; la 1 : la chance, en pour mille. */
    @Override
    public int extra(int index) {
        if (index == 1) {
            return (int) Math.round(chance() * 1000.0d);
        }
        if (index != 0) {
            return 0;
        }
        if (progress > 0) {
            return STATE_RUNNING;
        }
        if (!items.get(SLOT_RESULT).isEmpty()) {
            return STATE_TAKE;
        }
        if (!(items.get(SLOT_SEED).getItem() instanceof MutationSeedItem)) {
            return STATE_EMPTY;
        }
        return chance() > 0.0d ? STATE_READY : STATE_NO_ITEM;
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
        progress = Math.max(0, input.getIntOr("progress", 0));
        duration = Math.max(0, input.getIntOr("length", 0));
        boosted = input.getBooleanOr("boosted", false);
        anim = Anim.byOrdinal(input.getIntOr("anim", 0));
        animSince = input.getLongOr("anim_since", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", progress);
        output.putInt("length", duration);
        output.putBoolean("boosted", boosted);
        output.putInt("anim", anim.ordinal());
        output.putLong("anim_since", animSince);
    }
}
