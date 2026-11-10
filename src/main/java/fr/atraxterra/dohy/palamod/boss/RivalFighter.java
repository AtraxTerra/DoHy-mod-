package fr.atraxterra.dohy.palamod.boss;

import fr.atraxterra.dohy.faction.event.FactionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Un combattant d'une faction rivale, dans une arene d'event (lot 22f).
 *
 * <p>Sur Paladium, l'arene est pleine d'autres factions qui se disputent le
 * classement. En solo, ces factions sont fictives — comme les bases ennemies du
 * lot 22g — et leurs combattants sont des vindicateurs a la banniere de leur
 * faction.
 *
 * <ul>
 *   <li><b>Au Boss</b>, ils visent le boss et ses sbires ; ils ne s'en prennent
 *       au joueur que s'il les frappe.</li>
 *   <li><b>Au KOTH et au Totem</b> ({@link #pvp}), c'est la guerre : ils
 *       attaquent le joueur et les combattants des autres factions, et filent
 *       vers leur objectif — la zone, le totem — quand personne n'est a
 *       portee.</li>
 *   <li><b>Porteur d'un bloc du totem</b>, il ne se bat plus : il court a la zone
 *       de depot.</li>
 * </ul>
 *
 * <p>Comme le boss, il n'existe que pour un event : sans event en cours au nom de
 * {@link #run}, il disparait.
 */
public class RivalFighter extends Vindicator {

    private UUID run;
    private int rival = -1;
    private boolean pvp;
    private boolean carrying;
    private Vec3 objective;

    public RivalFighter(EntityType<? extends RivalFighter> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0d, false));
        goalSelector.addGoal(3, new ObjectiveGoal());
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.6d));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, EventBoss.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, true,
                (target, level) -> target.entityTags().contains(FactionEvents.MINION_TAG)));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, true,
                (target, level) -> pvp && !carrying));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, RivalFighter.class, true,
                (target, level) -> pvp && !carrying && ((RivalFighter) target).rival != rival));
    }

    public void bind(UUID run, int rival, boolean pvp) {
        this.run = run;
        this.rival = rival;
        this.pvp = pvp;
    }

    /** L'indice de sa faction dans l'event, ou -1. */
    public int rival() {
        return rival;
    }

    public void setObjective(Vec3 objective) {
        this.objective = objective;
    }

    public void setCarrying(boolean carrying) {
        this.carrying = carrying;
        if (carrying) {
            setTarget(null);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (run == null || FactionEvents.run(run) == null) {
            discard();
            return;
        }
        if (carrying && getTarget() != null) {
            setTarget(null);
        }
        super.customServerAiStep(level);
    }

    /** Pas de pillage en meute : ce n'est pas un raid. */
    @Override
    public boolean canJoinRaid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Rejoindre l'objectif quand personne n'est a combattre, ou toujours quand on porte un bloc. */
    private final class ObjectiveGoal extends Goal {

        ObjectiveGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return objective != null && (carrying || getTarget() == null)
                    && distanceToSqr(objective) > 2.0d;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            getNavigation().moveTo(objective.x, objective.y, objective.z, carrying ? 1.15d : 1.0d);
        }

        @Override
        public void tick() {
            if (getNavigation().isDone() || tickCount % 20 == 0) {
                start();
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}
