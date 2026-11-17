package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.agri.DoHyAgri;
import fr.atraxterra.dohy.agri.TractorFuel;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * La moissonneuse d'epice.
 *
 * <p>On la conduit comme le tracteur : elle tourne vers le regard, avance et
 * recule, lentement — c'est une usine sur chenilles. La touche de saut ouvre et
 * ferme sa bouche d'aspiration. Ouverte, elle avale le sable d'epice devant elle,
 * sur trois blocs de large et jusqu'a deux de profondeur, rend du sable ordinaire
 * et garde le melange dans sa tremie : deux par bloc, la ou la main n'en tire
 * qu'un, une fois sur trois.
 *
 * <p>Elle brule du carburant au chemin (deux fois plus bouche ouverte) et une
 * unite par bloc avale. Les carburants sont ceux du tracteur : charbon, bloc de
 * charbon, baton de blaze, bidons de NTM, seau de lave.
 *
 * <p>Garee a {@value #COUNTER_RANGE} blocs du comptoir d'Arrakeen, elle y vend
 * seule sa cargaison au nom de son proprietaire, s'il est la. Le melange se prend
 * aussi pile par pile, depuis son ecran.
 *
 * <p>Elle a un proprietaire, celui qui l'a posee : lui seul (et les operateurs)
 * la conduit, l'ouvre et la demonte (accroupi + coup).
 */
public class SpiceHarvester extends PathfinderMob implements PlayerRideableJumping, HasCustomInventoryScreen {

    private static final EntityDataAccessor<Integer> FUEL =
            SynchedEntityData.defineId(SpiceHarvester.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WORKING =
            SynchedEntityData.defineId(SpiceHarvester.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CARGO =
            SynchedEntityData.defineId(SpiceHarvester.class, EntityDataSerializers.INT);

    /** Le reservoir, en unites de carburant. */
    public static final int TANK = 8000;
    /** La tremie, en melange. */
    public static final int CAPACITY = 2048;
    /** Melange tire d'un bloc de sable d'epice. */
    public static final int YIELD = 2;
    /** Unites par bloc parcouru ; le double bouche ouverte. */
    private static final double MOVE_COST = 2.0d;
    private static final double WORK_FACTOR = 2.0d;
    private static final double SPEED = 0.15d;
    private static final double WORK_SPEED = 0.6d;
    /** La bouche d'aspiration, a cette distance devant le centre. */
    private static final double INTAKE = 2.3d;
    private static final float TURN = 3.5f;
    /** XP de mineur par bloc avale, pour le conducteur. */
    private static final long XP_PER_BLOCK = 2L;
    /** Le comptoir d'Arrakeen rachete la cargaison a cette distance. */
    static final double COUNTER_RANGE = 20.0d;
    /** Ce que le comptoir prend d'un coup, toutes les deux secondes. */
    private static final int COUNTER_BATCH = 512;
    private static final int DUST = 0xD89A58;

    private UUID owner;
    private String ownerName = "";
    private Vec3 last;
    private double burn;
    private int quiet;
    private int swallowed;

    public SpiceHarvester(EntityType<? extends SpiceHarvester> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0d)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.STEP_HEIGHT, 1.25d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FUEL, 0);
        builder.define(WORKING, false);
        builder.define(CARGO, 0);
    }

    @Override
    protected void registerGoals() {
    }

    // -- Etat ----------------------------------------------------------------------

    public int fuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(int fuel) {
        entityData.set(FUEL, Mth.clamp(fuel, 0, TANK));
    }

    public boolean working() {
        return entityData.get(WORKING);
    }

    public void setWorking(boolean working) {
        entityData.set(WORKING, working);
    }

    public int cargo() {
        return entityData.get(CARGO);
    }

    public void setCargo(int cargo) {
        entityData.set(CARGO, Mth.clamp(cargo, 0, CAPACITY));
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getName().getString();
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Le proprietaire, un operateur ou le creatif ; une moissonneuse sans proprietaire est a tous. */
    public boolean mayUse(ServerPlayer player) {
        return owner == null || isOwner(player) || player.isCreative()
                || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }

    /** Retire jusqu'a {@code count} melange de la tremie ; rend ce qui a ete retire. */
    public int takeCargo(int count) {
        int taken = Math.max(0, Math.min(count, cargo()));
        setCargo(cargo() - taken);
        return taken;
    }

    // -- Tick ----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    private void serverTick(ServerLevel server) {
        if (quiet > 0) {
            quiet--;
        }
        Vec3 now = position();
        double moved = last == null ? 0.0d : Math.hypot(now.x - last.x, now.z - last.z);
        last = now;
        if (moved > 2.0d) {
            // une teleportation n'est pas un trajet
            moved = 0.0d;
        }
        if (moved > 0.001d && fuel() > 0) {
            burn += moved * MOVE_COST * (working() ? WORK_FACTOR : 1.0d);
            int whole = (int) burn;
            if (whole > 0) {
                burn -= whole;
                setFuel(fuel() - whole);
            }
            if (tickCount % 5 == 0) {
                exhaust(server);
            }
        }
        if (working()) {
            if (fuel() <= 0) {
                setWorking(false);
                tell("Reservoir vide : la bouche se ferme.", ChatFormatting.RED);
            } else {
                harvest(server);
            }
        }
        if (tickCount % 40 == 0 && cargo() > 0) {
            sellAtCounter(server);
        }
    }

    /** Un message au conducteur, au-dessus de sa barre d'objets ; pas plus d'un par seconde. */
    private void tell(String text, ChatFormatting color) {
        if (quiet == 0 && getControllingPassenger() instanceof ServerPlayer driver) {
            quiet = 20;
            driver.sendSystemMessage(Component.literal(text).withStyle(color), true);
        }
    }

    private double forwardX() {
        return -Mth.sin(getYRot() * Mth.DEG_TO_RAD);
    }

    private double forwardZ() {
        return Mth.cos(getYRot() * Mth.DEG_TO_RAD);
    }

    /** La bouche : trois colonnes devant la machine, du pied des chenilles a deux blocs sous le sol. */
    private void harvest(ServerLevel server) {
        double fx = forwardX();
        double fz = forwardZ();
        double baseX = getX() + fx * INTAKE;
        double baseZ = getZ() + fz * INTAKE;
        int feet = Mth.floor(getY() + 0.01d);
        int eaten = 0;
        for (int side = -1; side <= 1; side++) {
            double x = baseX - fz * side;
            double z = baseZ + fx * side;
            for (int dy = 0; dy >= -2; dy--) {
                if (cargo() >= CAPACITY) {
                    tell("Tremie pleine : rentre au comptoir d'Arrakeen.", ChatFormatting.GOLD);
                    finish(server, eaten, baseX, baseZ);
                    return;
                }
                BlockPos pos = BlockPos.containing(x, feet + dy, z);
                BlockState state = server.getBlockState(pos);
                if (!ArrakisContent.isSpice(state)) {
                    continue;
                }
                server.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
                setCargo(cargo() + YIELD);
                eaten++;
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5d,
                        pos.getY() + 1.0d, pos.getZ() + 0.5d, 8, 0.4d, 0.3d, 0.4d, 0.05d);
            }
        }
        finish(server, eaten, baseX, baseZ);
    }

    private void finish(ServerLevel server, int eaten, double baseX, double baseZ) {
        if (eaten <= 0) {
            return;
        }
        setFuel(fuel() - eaten);
        if (getControllingPassenger() instanceof ServerPlayer driver) {
            JobApi.grant(driver, Job.MINER, XP_PER_BLOCK * eaten);
        }
        swallowed += eaten;
        if (swallowed >= 3) {
            swallowed = 0;
            server.playSound(null, BlockPos.containing(baseX, getY(), baseZ), SoundEvents.SAND_BREAK,
                    SoundSource.NEUTRAL, 0.9f, 0.6f);
        }
        // le cyclone recrache la poussiere, l'epice reste dans la tremie
        double fx = forwardX();
        double fz = forwardZ();
        double side = 0.56d;
        double cx = getX() - fx * 0.87d + fz * side;
        double cz = getZ() - fz * 0.87d - fx * side;
        server.sendParticles(new DustParticleOptions(DUST, 2.5f), cx, getY() + 3.1d, cz, 4, 0.25d, 0.2d,
                0.25d, 0.02d);
    }

    private void exhaust(ServerLevel server) {
        double fx = forwardX();
        double fz = forwardZ();
        for (int side = -1; side <= 1; side += 2) {
            double x = getX() - fx * 1.31d - fz * side * 0.94d;
            double z = getZ() - fz * 1.31d + fx * side * 0.94d;
            server.sendParticles(ParticleTypes.SMOKE, x, getY() + 3.0d, z, 2, 0.03d, 0.05d, 0.03d, 0.01d);
        }
    }

    /** Pres du comptoir d'Arrakeen, la cargaison se vend seule, au nom du proprietaire present. */
    private void sellAtCounter(ServerLevel server) {
        if (owner == null || !DoHyArrakis.onArrakis(this)) {
            return;
        }
        BlockPos counter = PortBuilder.COUNTER;
        if (distanceToSqr(counter.getX() + 0.5d, counter.getY(), counter.getZ() + 0.5d)
                > COUNTER_RANGE * COUNTER_RANGE) {
            return;
        }
        ServerPlayer seller = server.getServer().getPlayerList().getPlayer(owner);
        if (seller == null || seller.level() != server || seller.distanceToSqr(this) > 48.0d * 48.0d) {
            return;
        }
        int amount = Math.min(cargo(), COUNTER_BATCH);
        long payout = SpiceMarket.sell(seller, amount);
        setCargo(cargo() - amount);
        seller.sendSystemMessage(Component.literal("Comptoir d'Arrakeen : " + amount + " melange vendus pour "
                + MoneyFormat.exact(payout) + " $").withStyle(ChatFormatting.GOLD), false);
        server.playSound(null, counter, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    // -- Gestes ----------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!mayUse(server)) {
            say(server, "Cette moissonneuse appartient a " + ownerName + ".", false);
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            HarvesterMenu.INSTANCE.openFor(server, this);
        } else if (TractorFuel.isFuel(player, hand)) {
            int gained = TractorFuel.drain(player, hand, TANK - fuel());
            setFuel(fuel() + gained);
            say(server, gained > 0 ? "Carburant : " + fuel() + " / " + TANK + "." : "Le reservoir est plein.",
                    gained > 0);
        } else if (!isVehicle()) {
            server.startRiding(this);
            say(server, "Carburant : " + fuel() + " / " + TANK + "  \u00B7  tremie : " + cargo() + " / " + CAPACITY
                    + "  \u00B7  saut : ouvrir ou fermer la bouche", true);
        }
        return InteractionResult.SUCCESS;
    }

    private static void say(ServerPlayer player, String text, boolean good) {
        player.sendSystemMessage(Component.literal(text).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (player instanceof ServerPlayer server && mayUse(server)) {
            HarvesterMenu.INSTANCE.openFor(server, this);
        }
    }

    /**
     * Rien ne l'abime. Son proprietaire, accroupi, la demonte d'un coup : elle
     * rend la moissonneuse (avec son carburant) et tout son melange.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        if (source.getEntity() instanceof ServerPlayer player && !hasPassenger(player)) {
            if (!mayUse(player)) {
                say(player, "Cette moissonneuse appartient a " + ownerName + ".", false);
            } else if (player.isShiftKeyDown() || player.isCreative()) {
                dismantle(level);
            } else {
                say(player, "Accroupi + coup : demonter la moissonneuse.", false);
            }
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            dismantle(server);
        }
    }

    private void dismantle(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        ejectPassengers();
        ItemStack item = new ItemStack(ArrakisContent.HARVESTER_ITEM.get());
        if (fuel() > 0) {
            item.set(DoHyAgri.FUEL.get(), fuel());
        }
        spawnAtLocation(level, item);
        for (int left = cargo(); left > 0; left -= 64) {
            spawnAtLocation(level, new ItemStack(ArrakisContent.MELANGE.get(), Math.min(64, left)));
        }
        setCargo(0);
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6f, 0.6f);
        discard();
    }

    // -- Conduite --------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    /** Elle tourne vers le regard du conducteur, lentement, et seulement quand elle roule. */
    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        if (player.zza != 0.0f && fuel() > 0) {
            float turn = Mth.wrapDegrees(player.getYRot() - getYRot());
            setRot(getYRot() + Mth.clamp(turn, -TURN, TURN), 0.0f);
        }
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Avant et arriere seulement, la marche arriere au ralenti ; sans carburant, rien. */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        if (fuel() <= 0) {
            return Vec3.ZERO;
        }
        float forward = player.zza;
        return new Vec3(0.0d, 0.0d, forward < 0.0f ? forward * 0.4f : forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        return (float) (working() ? speed * WORK_SPEED : speed);
    }

    // La touche de saut ne fait pas sauter une moissonneuse : elle ouvre sa bouche.

    @Override
    public void onPlayerJump(int strength) {
    }

    @Override
    public boolean canJump() {
        return true;
    }

    @Override
    public void handleStartJump(int strength) {
        if (level().isClientSide()) {
            return;
        }
        if (fuel() <= 0) {
            quiet = 0;
            tell("Reservoir vide.", ChatFormatting.RED);
            return;
        }
        setWorking(!working());
        quiet = 0;
        tell(working() ? "Bouche ouverte : roule sur le sable d'epice." : "Bouche fermee.",
                working() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    @Override
    public void handleStopJump() {
    }

    // -- Ce qu'une moissonneuse n'est pas ----------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ArrakisContent.HARVESTER_ITEM.get());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    // -- Sauvegarde --------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Fuel", fuel());
        output.putBoolean("Working", working());
        output.putInt("Cargo", cargo());
        if (owner != null) {
            output.store("Owner", UUIDUtil.CODEC, owner);
            output.putString("OwnerName", ownerName);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setFuel(input.getIntOr("Fuel", 0));
        setWorking(input.getBooleanOr("Working", false));
        setCargo(input.getIntOr("Cargo", 0));
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("OwnerName", "");
    }
}
