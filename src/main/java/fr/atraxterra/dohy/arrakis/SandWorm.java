package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le ver des sables.
 *
 * <p>Il nait loin sous l'erg quand le desert a trop entendu ({@link WormNoise})
 * et file vers le bruit, a quelques blocs sous la surface : on ne le voit pas,
 * on voit le sable qui bouillonne au-dessus de lui et on l'entend creuser. Il
 * suit sa proie tant qu'elle bat le sable ; qu'elle se taise huit secondes, ou
 * gagne la roche, et il perd sa trace. Il contourne les massifs, il ne les
 * traverse pas.
 *
 * <p>Arrive sous elle, il se fige : le sable tremble un instant (le temps de fuir,
 * pour qui court), puis il jaillit la gueule ouverte et engloutit ce qui se tient
 * la : les joueurs (soixante points de degats), les betes, les objets, les
 * marteleurs ; une moissonneuse est avalee. Il se dresse de vingt-cinq blocs,
 * rugit gueule grande ouverte, la referme d'un coup en recrachant la moissonneuse,
 * vide, de tout en haut ; il y perd parfois une dent. Puis il replonge.
 *
 * <p>Rien ne le blesse, rien ne le garde : il n'est jamais sauvegarde avec le monde.
 */
public class SandWorm extends Mob {

    private static final EntityDataAccessor<Boolean> EMERGED =
            SynchedEntityData.defineId(SandWorm.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SINCE =
            SynchedEntityData.defineId(SandWorm.class, EntityDataSerializers.INT);

    /** Le sable tremble au point vise avant qu'il jaillisse. */
    public static final int TREMOR = 16;
    /** Il creve le sable gueule ouverte, et se dresse. */
    public static final int RISE = 30;
    /** Il engloutit quand sa gueule ouverte a passe le sable. */
    public static final int SWALLOW_AT = 3;
    /** Dresse, il rugit, gueule grande ouverte. */
    public static final int OPEN = 16;
    /** La gueule se referme d'un coup. */
    public static final int BITE = 6;
    /** Il replonge. */
    public static final int SINK = 44;
    public static final int END = RISE + OPEN + BITE + SINK;
    /** Il recrache, la gueule refermee au sommet. */
    private static final int SPIT_AT = RISE + OPEN + 3;
    /** La hauteur de sa gueule une fois dresse : celle du modele (158 pixels), grandi deux fois et demie. */
    public static final double HEIGHT = 24.5d;

    /** Blocs par tick, sous le sable : il rattrape une moissonneuse. */
    private static final double SPEED = 0.32d;
    private static final double DEPTH = 4.0d;
    /** Le rayon de sa gueule. */
    private static final double REACH = 5.0d;
    private static final float PLAYER_DAMAGE = 60.0f;
    private static final int MAX_TRAVEL = 75 * 20;
    private static final int MAX_BLOCKED = 10 * 20;
    private static final double HEARD = 160.0d;
    private static final double TOOTH_CHANCE = 0.3d;
    private static final int SAND = 0xC9A46A;
    /** La trainee du signe de ver : un ocre plus sombre que l'erg, et un sable plus clair. */
    private static final int TRAIL_DARK = 0x8C6A3E;
    private static final int TRAIL_LIGHT = 0xF0DCAA;
    private static final double[] DETOURS = {0.0d, 0.5d, -0.5d, 1.0d, -1.0d, 1.5d, -1.5d};

    private WormNoise.Source target;
    private BlockPos hole;
    /** Ticks de tremblement restants, ou -1 tant qu'il file. */
    private int tremor = -1;
    /** Ce qu'il recrachera : les moissonneuses avalees. */
    private final java.util.List<ItemStack> belly = new java.util.ArrayList<>();
    private int travelled;
    private int blocked;
    /** Cote client : notre propre {@code tickCount} au moment ou il a jailli. */
    private int clientSince;

    public SandWorm(EntityType<? extends SandWorm> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMERGED, false);
        builder.define(SINCE, 0);
    }

    @Override
    protected void registerGoals() {
    }

    /** Vrai pendant que le sable tremble au point vise, juste avant qu'il jaillisse (cote serveur). */
    public boolean trembling() {
        return tremor >= 0;
    }

    /** Vrai une fois sorti du sable : avant, il n'y a rien a voir. */
    public boolean emerged() {
        return entityData.get(EMERGED);
    }

    /**
     * Ticks ecoules depuis qu'il a jailli. Le serveur compte sur l'heure du monde ;
     * le client cale une fois son propre compteur sur elle (meme s'il ne voit le ver
     * qu'apres coup), puis suit ses ticks a lui : fige avec le jeu, sans a-coups.
     */
    public int emergedTicks() {
        if (level().isClientSide()) {
            return tickCount - clientSince;
        }
        return (int) level().getGameTime() - entityData.get(SINCE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide() && (key == SINCE || key == EMERGED)) {
            clientSince = tickCount - ((int) level().getGameTime() - entityData.get(SINCE));
        }
    }

    // -- Partir vers le bruit -----------------------------------------------------------

    /** Un ver part vers cette source, de loin. Faux si l'erg autour ne s'y prete pas. */
    static boolean dispatch(ServerLevel level, WormNoise.Source source) {
        BlockPos goal = source.position(level);
        if (goal == null) {
            return false;
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = 70.0d + random.nextDouble() * 40.0d;
            int x = Mth.floor(goal.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(goal.getZ() + Math.sin(angle) * distance);
            if (!WormNoise.wormGround(level, x, z)) {
                continue;
            }
            SandWorm worm = ArrakisContent.SANDWORM.get().create(level, EntitySpawnReason.EVENT);
            if (worm == null) {
                return false;
            }
            int surface = WormNoise.sandTop(level, x, z);
            worm.target = source;
            worm.snapTo(x + 0.5d, surface - DEPTH, z + 0.5d, 0.0f, 0.0f);
            level.addFreshEntity(worm);
            DoHy.LOGGER.info("Arrakis : un ver part de {} {} vers {}", x, z, goal);
            for (ServerPlayer player : level.players()) {
                if (Math.hypot(player.getX() - goal.getX(), player.getZ() - goal.getZ()) <= HEARD) {
                    player.sendSystemMessage(Component.literal("Signe de ver ! Il vient "
                            + SpiceBlows.origin(player, new BlockPos(x, surface, z)) + ", a "
                            + (int) Math.round(Math.hypot(player.getX() - x, player.getZ() - z))
                            + " blocs. Immobile, ou sur la roche !").withStyle(ChatFormatting.RED));
                }
            }
            return true;
        }
        return false;
    }

    /** Vrai si un ver rode a moins de {@code radius} blocs de ce point. */
    static boolean near(ServerLevel level, BlockPos at, double radius) {
        for (SandWorm worm : level.getEntities(ArrakisContent.SANDWORM.get(), Entity::isAlive)) {
            if (Math.hypot(worm.getX() - at.getX(), worm.getZ() - at.getZ()) < radius) {
                return true;
            }
        }
        return false;
    }

    // -- Tick ----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            if (emerged()) {
                emergedTick(server);
            } else if (tremor >= 0) {
                tremorTick(server);
            } else {
                travel(server);
            }
        }
    }

    private void travel(ServerLevel level) {
        travelled++;
        if (target == null || travelled > MAX_TRAVEL) {
            giveUp(level, null);
            return;
        }
        if (travelled % 20 == 0) {
            WormNoise.Source thumper = WormNoise.thumperNear(level, blockPosition());
            if (thumper != null && thumper != target) {
                target = thumper;
            }
        }
        BlockPos goal = target.position(level);
        if (goal == null || target.silent(level)) {
            giveUp(level, "Le ver a perdu la trace.");
            return;
        }
        double dx = goal.getX() + 0.5d - getX();
        double dz = goal.getZ() + 0.5d - getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.6d) {
            if (WormNoise.wormGround(level, goal.getX(), goal.getZ())) {
                lockOn(level, goal);
            } else {
                giveUp(level, "Le ver ne passe pas la roche.");
            }
            return;
        }
        double step = Math.min(SPEED, distance);
        double heading = Math.atan2(dz, dx);
        boolean moved = false;
        for (double detour : DETOURS) {
            double nx = getX() + Math.cos(heading + detour) * step;
            double nz = getZ() + Math.sin(heading + detour) * step;
            if (WormNoise.wormGround(level, Mth.floor(nx), Mth.floor(nz))) {
                int surface = WormNoise.sandTop(level, Mth.floor(nx), Mth.floor(nz));
                setPos(nx, surface - DEPTH, nz);
                setYRot((float) Math.toDegrees(heading + detour) - 90.0f);
                moved = true;
                break;
            }
        }
        if (moved) {
            blocked = 0;
        } else if (++blocked > MAX_BLOCKED) {
            giveUp(level, "Le ver ne passe pas la roche.");
            return;
        }
        sign(level);
        if (travelled % 40 == 0) {
            for (ServerPlayer player : level.players()) {
                double near = Math.hypot(player.getX() - getX(), player.getZ() - getZ());
                if (near <= HEARD) {
                    player.sendSystemMessage(Component.literal("Ver des sables : " + (int) Math.round(near)
                            + " blocs " + SpiceBlows.direction(player, blockPosition()))
                            .withStyle(near < 30.0d ? ChatFormatting.RED : ChatFormatting.GOLD), true);
                }
            }
        }
    }

    /**
     * Le signe du ver : le sable qui bouillonne au-dessus de lui, une trainee de poussiere
     * (plus sombre et plus claire que l'erg, pour se voir de loin) et le bruit de sa course.
     */
    private void sign(ServerLevel level) {
        double x = getX();
        double y = getY() + DEPTH + 0.1d;
        double z = getZ();
        BlockState sand = Blocks.SAND.defaultBlockState();
        particles(level, new BlockParticleOption(ParticleTypes.BLOCK, sand), x, y, z, 10, 1.4d, 0.1d, 0.25d);
        if (tickCount % 2 == 0) {
            particles(level, new DustParticleOptions(TRAIL_DARK, 3.5f), x, y + 0.7d, z, 6, 2.2d, 0.5d, 0.01d);
        } else {
            particles(level, new DustParticleOptions(TRAIL_LIGHT, 3.0f), x, y + 0.5d, z, 4, 1.8d, 0.4d, 0.01d);
        }
        if (tickCount % 4 == 0) {
            particles(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, sand), x, y + 1.2d, z, 8, 2.0d,
                    0.6d, 0.0d);
        }
        if (tickCount % 20 == 0) {
            sound(level, SoundEvents.WARDEN_DIG, x, y, z, 2.5f, 0.55f);
        }
    }

    /** Il est sous sa proie : il vise ce point, et le sable tremble avant qu'il jaillisse. */
    private void lockOn(ServerLevel level, BlockPos goal) {
        int surface = WormNoise.sandTop(level, goal.getX(), goal.getZ());
        hole = new BlockPos(goal.getX(), surface, goal.getZ());
        setPos(goal.getX() + 0.5d, surface - DEPTH, goal.getZ() + 0.5d);
        tremor = TREMOR;
        sound(level, SoundEvents.WARDEN_DIG, getX(), surface, getZ(), 3.0f, 0.4f);
        for (ServerPlayer player : level.players()) {
            if (Math.hypot(player.getX() - getX(), player.getZ() - getZ()) < REACH * 2.5d) {
                player.sendSystemMessage(Component.literal("Le sable tremble sous tes pieds : cours !")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Le sable s'affaisse vers le point vise, de plus en plus fort. */
    private void tremorTick(ServerLevel level) {
        double x = getX();
        double y = hole.getY() + 0.1d;
        double z = getZ();
        BlockState sand = Blocks.SAND.defaultBlockState();
        int ring = 10;
        double strength = 1.0d - tremor / (double) TREMOR;
        for (int k = 0; k < ring; k++) {
            double a = (k + random.nextDouble()) * Math.PI * 2.0d / ring;
            double r = REACH * (0.6d + random.nextDouble() * 0.7d);
            vortex(level, new BlockParticleOption(ParticleTypes.BLOCK, sand), x + Math.cos(a) * r, y,
                    z + Math.sin(a) * r, -Math.cos(a) * r * 0.25d, 0.15d + strength * 0.3d, -Math.sin(a) * r * 0.25d);
        }
        particles(level, new DustParticleOptions(SAND, 3.0f), x, y + 0.4d, z, 6, REACH * 0.8d, 0.3d, 0.01d);
        if (tremor % 5 == 0) {
            sound(level, SoundEvents.SAND_BREAK, x, y, z, 2.0f, 0.5f);
        }
        if (--tremor < 0) {
            emerge(level, hole);
        }
    }

    private void emerge(ServerLevel level, BlockPos at) {
        int surface = at.getY();
        setPos(at.getX() + 0.5d, surface, at.getZ() + 0.5d);
        entityData.set(SINCE, (int) level.getGameTime());
        entityData.set(EMERGED, true);
        double x = getX();
        double z = getZ();
        particles(level, ParticleTypes.EXPLOSION_EMITTER, x, surface + 1.0d, z, 1, 0.0d, 0.0d, 0.0d);
        particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), x,
                surface + 1.0d, z, 240, 3.0d, 1.5d, 0.5d);
        sound(level, SoundEvents.WARDEN_EMERGE, x, surface, z, 3.0f, 0.6f);
        sound(level, SoundEvents.RAVAGER_ROAR, x, surface, z, 3.0f, 0.5f);
        DoHy.LOGGER.info("Arrakis : un ver jaillit en {}", hole);
    }

    private void emergedTick(ServerLevel level) {
        int t = emergedTicks();
        double x = getX();
        double y = getY();
        double z = getZ();
        BlockState sand = Blocks.SAND.defaultBlockState();
        if (t < RISE || t > RISE + OPEN + BITE && t < END) {
            particles(level, new BlockParticleOption(ParticleTypes.BLOCK, sand), x, y + 0.5d, z, 28, 4.0d, 0.6d,
                    0.25d);
            if (t % 3 == 0) {
                particles(level, new DustParticleOptions(SAND, 4.0f), x, y + 1.0d, z, 12, 4.2d, 1.0d, 0.02d);
            }
        }
        if (t == SWALLOW_AT) {
            swallow(level);
        }
        if (t == RISE) {
            sound(level, SoundEvents.WARDEN_ROAR, x, y + HEIGHT, z, 3.0f, 0.55f);
        }
        if (t == RISE + OPEN) {
            sound(level, SoundEvents.EVOKER_FANGS_ATTACK, x, y + HEIGHT, z, 3.0f, 0.4f);
        }
        if (t == SPIT_AT) {
            spit(level);
        }
        if (t == RISE + OPEN + BITE) {
            sound(level, SoundEvents.WARDEN_DIG, x, y, z, 3.0f, 0.5f);
            sound(level, SoundEvents.SAND_BREAK, x, y, z, 3.0f, 0.5f);
        }
        if (t >= END) {
            discard();
        }
    }

    /** La gueule ouverte passe le sable : ce qui s'y tient, elle le prend. */
    private void swallow(ServerLevel level) {
        double x = getX();
        double y = getY();
        double z = getZ();
        sound(level, SoundEvents.GENERIC_EXPLODE.value(), x, y, z, 2.0f, 0.5f);
        DamageSource bite = level.damageSources().source(ArrakisContent.WORM_DAMAGE);
        AABB mouth = new AABB(x - REACH, y - 2.0d, z - REACH, x + REACH, y + 8.0d, z + REACH);
        for (Entity entity : level.getEntities(this, mouth, found -> found != this && found.isAlive())) {
            if (Math.hypot(entity.getX() - x, entity.getZ() - z) > REACH) {
                continue;
            }
            if (entity instanceof SpiceHarvester harvester) {
                ItemStack wreck = harvester.swallowed(level);
                if (!wreck.isEmpty()) {
                    belly.add(wreck);
                }
            } else if (entity instanceof ServerPlayer player) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.hurtServer(level, bite, PLAYER_DAMAGE);
                }
            } else if (entity instanceof LivingEntity living) {
                living.hurtServer(level, bite, 1000.0f);
            } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                entity.discard();
            }
        }
        // les marteleurs pris dans la gueule sont broyes
        BlockPos center = BlockPos.containing(x, y, z);
        int reach = Mth.ceil(REACH);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -2, -reach), center.offset(reach, 3, reach))) {
            if (level.getBlockState(pos).is(ArrakisContent.THUMPER.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** La gueule se referme au sommet : il recrache ce qu'il ne digere pas, et y perd parfois une dent. */
    private void spit(ServerLevel level) {
        double x = getX();
        double y = getY() + HEIGHT;
        double z = getZ();
        RandomSource random = level.getRandom();
        for (ItemStack wreck : belly) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            ItemEntity item = new ItemEntity(level, x + Math.cos(angle) * 2.5d, y, z + Math.sin(angle) * 2.5d, wreck,
                    Math.cos(angle) * 0.35d, 0.45d, Math.sin(angle) * 0.35d);
            item.setDefaultPickUpDelay();
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
        }
        if (!belly.isEmpty()) {
            sound(level, SoundEvents.RAVAGER_ROAR, x, y, z, 2.5f, 0.45f);
        }
        belly.clear();
        if (random.nextDouble() < TOOTH_CHANCE) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            ItemEntity tooth = new ItemEntity(level, x + Math.cos(angle) * 2.0d, y - 1.0d, z + Math.sin(angle) * 2.0d,
                    new ItemStack(ArrakisContent.WORM_TOOTH.get()), Math.cos(angle) * 0.25d, 0.3d,
                    Math.sin(angle) * 0.25d);
            tooth.setDefaultPickUpDelay();
            level.addFreshEntity(tooth);
        }
    }

    private void giveUp(ServerLevel level, String message) {
        DoHy.LOGGER.info("Arrakis : un ver renonce en {} ({})", blockPosition(),
                message == null ? "course trop longue" : message);
        if (message != null && target != null) {
            BlockPos at = target.spot;
            if (at != null) {
                WormNoise.tell(level, at, HEARD, message, ChatFormatting.GOLD);
            }
        }
        sound(level, SoundEvents.WARDEN_DIG, getX(), getY() + DEPTH, getZ(), 1.5f, 0.45f);
        discard();
    }

    // -- Pour les joueurs : de loin, dans la bonne direction ---------------------------------

    /** Une particule lancee : vers le point vise, pour le sable qui s'affaisse. */
    private static void vortex(ServerLevel level, ParticleOptions particle, double x, double y, double z, double vx,
                               double vy, double vz) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < 192.0d * 192.0d) {
                level.sendParticles(player, particle, true, true, x, y, z, 0, vx, vy, vz, 1.0d);
            }
        }
    }

    private void particles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                           double spread, double spreadY, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < 192.0d * 192.0d) {
                level.sendParticles(player, particle, true, true, x, y, z, count, spread, spreadY, spread, speed);
            }
        }
    }

    /** Un son pour chaque joueur proche : un son lointain lui arrive de la bonne direction, a douze blocs. */
    private static void sound(ServerLevel level, SoundEvent sound, double sx, double sy, double sz, float volume,
                              float pitch) {
        for (ServerPlayer player : level.players()) {
            double dx = sx - player.getX();
            double dy = sy - player.getEyeY();
            double dz = sz - player.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > HEARD * 1.25d) {
                continue;
            }
            double px = sx;
            double py = sy;
            double pz = sz;
            if (distance > 12.0d) {
                px = player.getX() + dx / distance * 12.0d;
                py = player.getEyeY() + dy / distance * 12.0d;
                pz = player.getZ() + dz / distance * 12.0d;
            }
            float faded = (float) (volume * Math.max(0.3d, 1.0d - distance / (HEARD * 1.25d)));
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.HOSTILE, px, py, pz, faded, pitch, player.getRandom().nextLong()));
        }
    }

    // -- Ce qu'un ver n'est pas ----------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320.0d * 320.0d;
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
}
