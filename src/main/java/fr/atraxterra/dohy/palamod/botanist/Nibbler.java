package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;

/**
 * Le nibbler ({@code EntityBotanistNibbler} de JobsReborn) : une petite bete que reclament
 * certaines plantes. Il apparait pres du pot quand la plante en reclame ({@link Need#NIBBLER}),
 * vit le temps du besoin, et s'attrape d'un clic droit au filet ({@link NetItem}).
 *
 * <p>Repris du jar : quatre points de vie, vitesse 0,3, invulnerable (on l'attrape, on ne le tue
 * pas), jamais retire pour l'eloignement ; il nage, regarde autour de lui, se promene, et fuit le
 * joueur a moins de 8 blocs (vitesse 1,2 puis 1,6). Quand un joueur arrive a moins de 8 blocs, il
 * panique d'abord : il se fige 25 ticks en hurlant ({@code botanist.nibbler.panic} et
 * {@code scream}), et ne repanique qu'une fois le joueur reparti a plus de 12 blocs. Le moment de
 * l'attraper.
 *
 * <p>Ici, le pot est dans le monde et non dans un laboratoire clos : le nibbler reste a moins de
 * 10 blocs de son pot ({@code setHomeTo}), pour ne pas fuir au loin.
 */
public class Nibbler extends PathfinderMob {

    /** La vie d'un nibbler sans besoin (NBT {@code lifetime} de JobsReborn, 3000 ticks par defaut). */
    public static final int DEFAULT_LIFETIME = 3000;
    /** Le rayon autour du pot ou il reste. */
    public static final int HOME_RADIUS = 10;

    private static final EntityDataAccessor<Boolean> PANIC =
            SynchedEntityData.defineId(Nibbler.class, EntityDataSerializers.BOOLEAN);

    private int lifetime = DEFAULT_LIFETIME;
    /** Un joueur est deja passe tout pres : on ne repanique pas avant qu'il soit reparti. */
    private boolean alarmed;

    public Nibbler(EntityType<? extends Nibbler> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 4.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.3d);
    }

    /** Sa duree de vie, en ticks : celle du besoin qui l'a fait venir. */
    public Nibbler lifetime(int ticks) {
        this.lifetime = Math.max(1, ticks);
        return this;
    }

    /** Le pot autour duquel il reste. */
    public Nibbler home(BlockPos pot) {
        setHomeTo(pot, HOME_RADIUS);
        return this;
    }

    public boolean panicking() {
        return entityData.get(PANIC);
    }

    private void panic(boolean on) {
        entityData.set(PANIC, on);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PANIC, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0f, 1.2d, 1.6d));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && --lifetime <= 0) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2d, getZ(), 4, 0.15d, 0.1d, 0.15d, 0.01d);
            }
            discard();
        }
    }

    // -- L'attraper ---------------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof NetItem)) {
            return super.mobInteract(player, hand);
        }
        if (!(player instanceof ServerPlayer server) || !(level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (NetItem.full(held)) {
            server.sendSystemMessage(Component.literal("Ton filet est plein : relache des nibblers sur une plante.")
                    .withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }
        NetItem.setNibblers(held, NetItem.nibblers(held) + 1);
        level.playSound(null, getX(), getY(), getZ(), PalaBotanist.NET_GRAB.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, getX(), getY(), getZ(), PalaBotanist.NET_NIBBLERS.get(), SoundSource.PLAYERS, 1.0f,
                0.9f + getRandom().nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.2d, getZ(), 5, 0.2d, 0.15d, 0.2d, 0.0d);
        server.sendSystemMessage(Component.literal("Nibbler attrape (" + NetItem.nibblers(held) + "/"
                + NetItem.CAPACITY + ")").withStyle(ChatFormatting.GREEN), true);
        discard();
        return InteractionResult.SUCCESS;
    }

    // -- Invulnerable, jamais retire ------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // /kill passe ; le reste ne le blesse pas : on l'attrape au filet.
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    // -- Sons ----------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return PalaBotanist.NIBBLER_IDLE.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(PalaBotanist.NIBBLER_WALK.get(), 0.3f, 1.0f);
    }

    // -- Sauvegarde ----------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("lifetime", lifetime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        lifetime = Math.max(1, input.getIntOr("lifetime", DEFAULT_LIFETIME));
    }

    /**
     * La panique ({@code EntityBotanistNibbler.PanicTask}) : un joueur arrive a moins de 8 blocs,
     * le nibbler s'arrete net 25 ticks, crie, puis fuit ({@link AvoidEntityGoal}). Il ne repanique
     * qu'apres le depart du joueur a plus de 12 blocs.
     */
    private static final class PanicGoal extends Goal {

        private static final int TICKS = 25;
        private static final double ALARM = 8.0d;
        private static final double CALM = 12.0d;

        private final Nibbler nibbler;
        private int ticks;

        PanicGoal(Nibbler nibbler) {
            this.nibbler = nibbler;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Level level = nibbler.level();
            if (nibbler.alarmed) {
                if (level.getNearestPlayer(nibbler, CALM) == null) {
                    nibbler.alarmed = false;
                }
                return false;
            }
            Player near = level.getNearestPlayer(nibbler, ALARM);
            if (near == null || near.isSpectator()) {
                return false;
            }
            nibbler.alarmed = true;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return ticks > 0;
        }

        @Override
        public void start() {
            ticks = TICKS;
            nibbler.getNavigation().stop();
            nibbler.panic(true);
            nibbler.playSound(PalaBotanist.NIBBLER_PANIC.get(), 0.8f, 1.0f);
            nibbler.playSound(PalaBotanist.NIBBLER_SCREAM.get(), 1.0f, 0.9f + nibbler.getRandom().nextFloat() * 0.3f);
        }

        @Override
        public void tick() {
            ticks--;
        }

        @Override
        public void stop() {
            ticks = 0;
            nibbler.panic(false);
        }
    }
}
