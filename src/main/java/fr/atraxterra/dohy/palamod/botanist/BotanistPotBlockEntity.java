package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.agri.Climate;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Une plante en pot, de la graine a la fanaison ({@code BotanistPotBlockData} de
 * JobsReborn : graine, maitre, recoltes, stade suivant, hors climat, besoin, nombre
 * et echeance du besoin).
 *
 * <p>Tout se compte en temps passe pres du maitre : la plante ne pousse, ne
 * reclame et ne fane que s'il est en ligne, dans la meme dimension, a moins de
 * {@link #RANGE} blocs. Loin de lui, elle attend ; le chrono d'un besoin aussi.
 *
 * <p>Le client recoit l'etat a chaque changement, avec l'heure du jeu a l'envoi :
 * il deduit les chronos sans paquet par seconde ({@link #remaining(long)}).
 */
public class BotanistPotBlockEntity extends BlockEntity {

    /** Le maitre doit etre a moins de 48 blocs. */
    public static final double RANGE = 48.0d;
    /** Un passage par seconde. */
    public static final int PASS = 20;
    /** Une sauvegarde toutes les quinze secondes de pousse, et un envoi toutes les trente. */
    private static final int SAVE_EVERY = 15;
    private static final int SYNC_EVERY = 30;
    /** Deux clics accroupi, a trois secondes au plus, pour deraciner. */
    private static final int UPROOT_WINDOW = 3 * 20;

    private UUID owner;
    private String ownerName = "";
    private Mutation mutation;
    private PotStage stage = PotStage.EMPTY;
    /** Le temps passe dans le stade, et ce qu'il dure retards compris (ticks pres du maitre). */
    private int progress;
    private int length;
    private int harvestsLeft;
    private Need need;
    private String needItem = "";
    private int needCount;
    private int needLeft;
    /** Le moment du stade ou la plante reclamera, ou -1 : un besoin par stade de pousse. */
    private int needAt = -1;
    private boolean outOfClimate;
    /** Fanee hors climat : elle ne rend que sa graine. */
    private boolean barren;
    /** Le maitre est pres d'elle : les chronos avancent. */
    private boolean active;
    /** L'heure du jeu a l'envoi (lue par le client). */
    private long syncedAt;
    private long uprootArmedUntil;
    private int passes;

    public BotanistPotBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBotanist.POT_TYPE.get(), pos, state);
    }

    // -- Lu par le rendu et le bloc ------------------------------------------------------------

    public Mutation mutation() {
        return mutation;
    }

    public PotStage stage() {
        return stage;
    }

    public Need need() {
        return need;
    }

    public String needItem() {
        return needItem;
    }

    public int needCount() {
        return needCount;
    }

    public int harvestsLeft() {
        return harvestsLeft;
    }

    public boolean outOfClimate() {
        return outOfClimate;
    }

    public boolean active() {
        return active;
    }

    public String ownerName() {
        return ownerName;
    }

    /** Ce qu'il reste du stade, en ticks, a l'heure du jeu {@code now} (cote client : deduit). */
    public int remaining(long now) {
        return Math.max(0, length - progress - elapsed(now));
    }

    /** Ce qu'il reste pour remplir le besoin, en ticks. */
    public int needRemaining(long now) {
        return Math.max(0, needLeft - elapsed(now));
    }

    private int elapsed(long now) {
        return active ? (int) Mth.clamp(now - syncedAt, 0L, Integer.MAX_VALUE) : 0;
    }

    /** « 4:05 » a partir de ticks. */
    public static String clock(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds >= 3600L) {
            return seconds / 3600L + "h" + String.format("%02d", (seconds / 60L) % 60L);
        }
        return seconds / 60L + ":" + String.format("%02d", seconds % 60L);
    }

    // -- Le maitre ---------------------------------------------------------------------------------

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        sync();
    }

    /** Rend vrai si c'est le maitre (ou un pot sans maitre, qui devient le sien). */
    private boolean checkOwner(ServerPlayer player) {
        if (owner == null) {
            setOwner(player);
            return true;
        }
        if (owner.equals(player.getUUID())) {
            return true;
        }
        say(player, Component.literal("Ce pot appartient a " + ownerName + " : tu ne peux que le casser.")
                .withStyle(ChatFormatting.RED));
        return false;
    }

    private ServerPlayer ownerNear(ServerLevel server) {
        if (owner == null) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != server || player.isSpectator()) {
            return null;
        }
        return player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= RANGE * RANGE ? player : null;
    }

    private ServerPlayer ownerOnline(ServerLevel server) {
        return owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
    }

    // -- Planter -----------------------------------------------------------------------------------

    void plant(ServerPlayer player, ItemStack stack, Mutation seed) {
        if (!checkOwner(player)) {
            return;
        }
        if (stage != PotStage.EMPTY) {
            say(player, Component.literal("Ce pot est deja occupe.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        stack.consume(1, player);
        mutation = seed;
        stage = PotStage.SEED;
        progress = 0;
        length = seed.budTicks();
        harvestsLeft = Mutation.HARVESTS;
        barren = false;
        clearNeed();
        scheduleNeed(server.getRandom());
        Climate here = Climate.at(server, worldPosition);
        outOfClimate = here != seed.climate();
        active = ownerNear(server) != null;
        updateState();
        server.playSound(null, worldPosition, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (outOfClimate) {
            say(player, prefix().append(Component.literal(seed.label() + " plante, mais hors climat : il lui faut un "
                    + "climat " + Mutation.climateLabel(seed.climate()) + ", ici c'est " + Mutation.climateLabel(here)
                    + ". Elle fanera au bourgeon.").withStyle(ChatFormatting.RED)));
        } else {
            say(player, prefix().append(Component.literal(seed.label() + " plante. Elle ne pousse que si tu restes "
                    + "a moins de 48 blocs, et te reclamera de l'aide en grandissant.").withStyle(ChatFormatting.GREEN)));
        }
        sync();
    }

    /** Un besoin par stade de pousse, entre le quart et les trois quarts du stade. */
    private void scheduleNeed(RandomSource random) {
        needAt = length / 4 + random.nextInt(Math.max(1, length / 2));
    }

    private void clearNeed() {
        need = null;
        needItem = "";
        needCount = 0;
        needLeft = 0;
    }

    private void clear() {
        mutation = null;
        stage = PotStage.EMPTY;
        progress = 0;
        length = 0;
        harvestsLeft = 0;
        clearNeed();
        needAt = -1;
        outOfClimate = false;
        barren = false;
        active = false;
        updateState();
    }

    // -- Le temps ----------------------------------------------------------------------------------

    void serverTick() {
        if (!(level instanceof ServerLevel server) || stage == PotStage.EMPTY || mutation == null) {
            return;
        }
        ServerPlayer player = ownerNear(server);
        boolean near = player != null;
        if (near != active) {
            active = near;
            sync();
        }
        if (!near) {
            return;
        }
        progress += PASS;
        boolean changed = false;
        if (need != null) {
            needLeft -= PASS;
            if (needLeft <= 0) {
                failNeed(server, player);
                changed = true;
            }
        } else if (stage.growing() && needAt >= 0 && progress >= needAt) {
            openNeed(server, player);
            changed = true;
        }
        if (progress >= length) {
            advance(server, player);
            changed = true;
        }
        passes++;
        if (!changed && passes % SAVE_EVERY == 0) {
            setChanged();
        }
        if (!changed && passes % SYNC_EVERY == 0) {
            sync();
        }
    }

    private void advance(ServerLevel server, ServerPlayer player) {
        switch (stage) {
            case SEED -> {
                Climate here = Climate.at(server, worldPosition);
                outOfClimate = here != mutation.climate();
                if (outOfClimate) {
                    wilt(true);
                    say(player, prefix().append(Component.literal(mutation.label() + " a fane au bourgeon : hors "
                            + "climat (il lui faut un climat " + Mutation.climateLabel(mutation.climate())
                            + "). Un clic droit rend sa graine.").withStyle(ChatFormatting.RED)));
                } else {
                    stage = PotStage.BUD;
                    progress = 0;
                    length = mutation.bloomTicks();
                    scheduleNeed(server.getRandom());
                }
            }
            case BUD -> {
                stage = PotStage.BLOOMING;
                progress = 0;
                length = Mutation.WILT_TICKS;
                clearNeed();
                needAt = -1;
                server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f,
                        1.3f);
                say(player, prefix().append(Component.literal(mutation.label() + " est en fleur (" + where()
                        + ") : un clic droit pour la recolter, avant qu'elle ne fane (" + clock(length) + ").")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
            }
            case BLOOMING -> {
                wilt(false);
                say(player, prefix().append(Component.literal(mutation.label() + " a fane sans etre recoltee. "
                        + "Recupere ses graines avant qu'elle ne disparaisse (" + clock(length) + ").")
                        .withStyle(ChatFormatting.GOLD)));
            }
            case WILTED -> {
                say(player, prefix().append(Component.literal(mutation.label() + " a disparu : fanee trop "
                        + "longtemps.").withStyle(ChatFormatting.GRAY)));
                clear();
            }
            default -> {
            }
        }
        updateState();
        sync();
    }

    private void wilt(boolean barren) {
        stage = PotStage.WILTED;
        progress = 0;
        length = Mutation.GONE_TICKS;
        harvestsLeft = 0;
        this.barren = barren;
        clearNeed();
        needAt = -1;
    }

    // -- Les besoins -------------------------------------------------------------------------------

    private void openNeed(ServerLevel server, ServerPlayer player) {
        RandomSource random = server.getRandom();
        need = mutation.pickNeed(random);
        needLeft = Need.TIMEOUT;
        needAt = -1;
        if (need == Need.ITEM) {
            needItem = mutation.pickItem(random);
            needCount = Mth.nextInt(random, 1, 3);
        } else {
            needItem = "";
            needCount = 0;
        }
        server.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8f, 1.6f);
        say(player, prefix().append(Component.literal(mutation.label() + " (" + where() + ") reclame ")
                .withStyle(ChatFormatting.YELLOW)).append(needText().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(", " + clock(needLeft) + " pour repondre. " + need.hint())
                        .withStyle(ChatFormatting.YELLOW)));
        sync();
    }

    /** « de l'eau », ou « 2 x Charbon ». */
    public MutableComponent needText() {
        if (need == null) {
            return Component.empty();
        }
        if (need == Need.ITEM) {
            Item item = Mutation.item(needItem);
            return Component.literal(needCount + " x ").append(new ItemStack(item).getHoverName());
        }
        return Component.literal(need.label());
    }

    private void failNeed(ServerLevel server, ServerPlayer player) {
        clearNeed();
        length += Need.SLOW;
        server.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.6f);
        say(player, prefix().append(Component.literal(mutation.label() + " : besoin rate, elle ralentit d'une "
                + "minute.").withStyle(ChatFormatting.RED)));
        sync();
    }

    private void fulfil(ServerLevel server, ServerPlayer player, SoundEvent sound, float pitch,
                        ParticleOptions particle) {
        clearNeed();
        JobApi.grant(player, Job.FARMER, Mutation.NEED_XP);
        server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0f, pitch);
        burst(server, particle, 8);
        burst(server, ParticleTypes.HAPPY_VILLAGER, 6);
        say(player, Component.literal("Besoin rempli").withStyle(ChatFormatting.GREEN), true);
        sync();
    }

    /**
     * Un objet tenu en main sur le pot : graine mise a part (le bloc s'en charge),
     * l'arrosoir, la guitare ou l'objet reclame. Rend vrai si le clic a servi.
     */
    boolean answer(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        boolean tool = stack.getItem() instanceof WateringCanItem || stack.getItem() instanceof GuitarItem;
        boolean wanted = need == Need.ITEM && stack.is(Mutation.item(needItem));
        if (!tool && !wanted) {
            return false;
        }
        if (!checkOwner(player) || !(level instanceof ServerLevel server)) {
            return true;
        }
        if (need == null) {
            say(player, Component.literal(stage == PotStage.EMPTY ? "Ce pot est vide."
                    : "Ta plante ne reclame rien pour l'instant.").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (stack.getItem() instanceof WateringCanItem) {
            if (need != Need.WATER) {
                refuse(player);
                return true;
            }
            if (WateringCanItem.charges(stack) <= 0) {
                say(player, Component.literal("Arrosoir vide : remplis-le a une source d'eau.")
                        .withStyle(ChatFormatting.GOLD), true);
                return true;
            }
            WateringCanItem.setCharges(stack, WateringCanItem.charges(stack) - 1);
            stack.hurtAndBreak(1, player, hand);
            fulfil(server, player, SoundEvents.BUCKET_EMPTY, 1.2f, ParticleTypes.SPLASH);
            return true;
        }
        if (stack.getItem() instanceof GuitarItem) {
            if (need != Need.MUSIC) {
                refuse(player);
                return true;
            }
            stack.hurtAndBreak(1, player, hand);
            GuitarItem.strum(server, worldPosition, server.getRandom());
            fulfil(server, player, SoundEvents.NOTE_BLOCK_GUITAR.value(), 1.0f, ParticleTypes.NOTE);
            return true;
        }
        int given = Math.min(needCount, stack.getCount());
        stack.consume(given, player);
        needCount -= given;
        if (needCount <= 0) {
            fulfil(server, player, SoundEvents.ITEM_PICKUP, 0.8f, ParticleTypes.HAPPY_VILLAGER);
        } else {
            say(player, Component.literal("Encore ").append(needText()).withStyle(ChatFormatting.YELLOW), true);
            sync();
        }
        return true;
    }

    private void refuse(ServerPlayer player) {
        say(player, Component.literal("Elle ne reclame pas ca : elle veut ").append(needText())
                .withStyle(ChatFormatting.YELLOW), true);
    }

    // -- La main vide --------------------------------------------------------------------------

    /** Un clic droit main vide (ou avec un objet qui ne sert pas au pot). */
    void use(ServerPlayer player, boolean emptyHand) {
        if (stage == PotStage.EMPTY) {
            if (emptyHand) {
                say(player, Component.literal("Pot vide : plante-y une graine de mutation.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        if (!checkOwner(player) || !(level instanceof ServerLevel server)) {
            return;
        }
        if (emptyHand && player.isShiftKeyDown()) {
            uproot(server, player);
            return;
        }
        switch (stage) {
            case BLOOMING -> harvest(server, player);
            case WILTED -> recover(server, player);
            default -> {
                if (emptyHand && need != null && need.emptyHand()) {
                    if (need == Need.SHAKE) {
                        fulfil(server, player, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.4f,
                                ParticleTypes.FALLING_SPORE_BLOSSOM);
                    } else {
                        fulfil(server, player, SoundEvents.SLIME_SQUISH_SMALL, 1.2f, ParticleTypes.ITEM_SLIME);
                    }
                } else if (emptyHand) {
                    status(player);
                }
            }
        }
    }

    private void status(ServerPlayer player) {
        long now = level == null ? 0L : level.getGameTime();
        MutableComponent line = Component.literal(mutation.label() + " : ").withStyle(ChatFormatting.WHITE);
        if (need != null) {
            line.append(Component.literal("reclame ").withStyle(ChatFormatting.YELLOW)).append(needText())
                    .append(Component.literal(" (" + clock(needRemaining(now)) + ")").withStyle(ChatFormatting.YELLOW));
        } else {
            String when = stage == PotStage.SEED ? "bourgeon dans " : "fleur dans ";
            line.append(Component.literal(when + clock(remaining(now))).withStyle(ChatFormatting.GRAY));
        }
        if (!active) {
            line.append(Component.literal(" (en pause)").withStyle(ChatFormatting.DARK_GRAY));
        }
        say(player, line, true);
    }

    private void harvest(ServerLevel server, ServerPlayer player) {
        for (ItemStack drop : mutation.roll(server.getRandom())) {
            Block.popResource(server, worldPosition.above(), drop);
        }
        JobApi.grant(player, Job.FARMER, mutation.xp());
        server.playSound(null, worldPosition, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f,
                1.0f);
        burst(server, ParticleTypes.HAPPY_VILLAGER, 10);
        harvestsLeft--;
        if (harvestsLeft > 0) {
            stage = PotStage.BUD;
            progress = 0;
            length = mutation.rebloomTicks();
            scheduleNeed(server.getRandom());
            say(player, Component.literal("Recolte ! Encore " + harvestsLeft + ", prochaine fleur dans "
                    + clock(length) + ".").withStyle(ChatFormatting.GREEN), true);
        } else {
            wilt(false);
            say(player, prefix().append(Component.literal("Derniere recolte de " + mutation.label()
                    + " : un clic droit rend ses graines.").withStyle(ChatFormatting.GREEN)));
        }
        updateState();
        sync();
    }

    private void recover(ServerLevel server, ServerPlayer player) {
        int count = barren ? 1 : Mth.nextInt(server.getRandom(), Mutation.SEEDS_MIN, Mutation.SEEDS_MAX);
        ItemStack seeds = new ItemStack(PalaBotanist.MUTATION_SEEDS.get(mutation).get(), count);
        say(player, Component.literal("Graines recuperees : " + count + " x ").append(seeds.getHoverName())
                .withStyle(ChatFormatting.GREEN), true);
        BotanistEvents.give(player, seeds);
        server.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8f, 1.2f);
        clear();
        sync();
    }

    private void uproot(ServerLevel server, ServerPlayer player) {
        long now = server.getGameTime();
        if (now > uprootArmedUntil) {
            uprootArmedUntil = now + UPROOT_WINDOW;
            say(player, Component.literal("Accroupi + clic droit encore une fois pour deraciner : la plante sera "
                    + "perdue.").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        uprootArmedUntil = 0L;
        server.playSound(null, worldPosition, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        burst(server, ParticleTypes.POOF, 6);
        say(player, Component.literal(mutation.label() + " deracinee.").withStyle(ChatFormatting.GRAY), true);
        clear();
        sync();
    }

    /** La graine rendue quand on casse un pot ou la graine vient d'etre plantee ; vide sinon. */
    ItemStack brokenSeed() {
        if (stage != PotStage.SEED || mutation == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(PalaBotanist.MUTATION_SEEDS.get(mutation).get());
    }

    // -- Outils ------------------------------------------------------------------------------------

    private void burst(ServerLevel server, ParticleOptions particle, int count) {
        server.sendParticles(particle, worldPosition.getX() + 0.5d, worldPosition.getY() + 0.9d,
                worldPosition.getZ() + 0.5d, count, 0.3d, 0.25d, 0.3d, 0.02d);
    }

    private String where() {
        return worldPosition.getX() + " " + worldPosition.getY() + " " + worldPosition.getZ();
    }

    private static MutableComponent prefix() {
        return Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN);
    }

    private static void say(ServerPlayer player, Component message) {
        say(player, message, false);
    }

    private static void say(ServerPlayer player, Component message, boolean actionBar) {
        if (player != null) {
            player.sendSystemMessage(message, actionBar);
        }
    }

    /** Le stade et la plante, dans l'etat du bloc : c'est ce que dessine le modele. */
    private void updateState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BotanistPotBlock.STAGE)) {
            return;
        }
        BlockState next = state.setValue(BotanistPotBlock.STAGE, stage);
        if (mutation != null) {
            next = next.setValue(BotanistPotBlock.PLANT, mutation);
        }
        if (next != state) {
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        }
    }

    // -- Sauvegarde et envoi -----------------------------------------------------------------------

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

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
        mutation = Mutation.byId(input.getStringOr("mutation", ""));
        stage = mutation == null ? PotStage.EMPTY : PotStage.byId(input.getStringOr("stage", "empty"));
        progress = Math.max(0, input.getIntOr("progress", 0));
        length = Math.max(0, input.getIntOr("length", 0));
        harvestsLeft = Math.max(0, input.getIntOr("harvests", 0));
        need = Need.byId(input.getStringOr("need", ""));
        needItem = input.getStringOr("need_item", "");
        needCount = Math.max(0, input.getIntOr("need_count", 0));
        needLeft = Math.max(0, input.getIntOr("need_left", 0));
        needAt = input.getIntOr("need_at", -1);
        outOfClimate = input.getBooleanOr("out_of_climate", false);
        barren = input.getBooleanOr("barren", false);
        active = input.getBooleanOr("active", false);
        syncedAt = input.getLongOr("synced_at", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putString("mutation", mutation == null ? "" : mutation.id());
        output.putString("stage", stage.getSerializedName());
        output.putInt("progress", progress);
        output.putInt("length", length);
        output.putInt("harvests", harvestsLeft);
        output.putString("need", need == null ? "" : need.id());
        output.putString("need_item", needItem);
        output.putInt("need_count", needCount);
        output.putInt("need_left", needLeft);
        output.putInt("need_at", needAt);
        output.putBoolean("out_of_climate", outOfClimate);
        output.putBoolean("barren", barren);
        output.putBoolean("active", active);
        output.putLong("synced_at", level == null ? 0L : level.getGameTime());
    }
}
