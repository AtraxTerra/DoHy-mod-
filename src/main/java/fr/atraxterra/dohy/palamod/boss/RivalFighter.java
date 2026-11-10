package fr.atraxterra.dohy.palamod.boss;

import fr.atraxterra.dohy.faction.event.FactionEvents;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.Level;

/**
 * Un combattant d'une faction rivale, dans une arene d'event (lot 22f).
 *
 * <p>Sur Paladium, l'arene est pleine d'autres factions qui frappent le boss et
 * se volent le classement. En solo, ces factions sont fictives — comme les
 * bases ennemies du lot 22g — et leurs combattants sont des vindicateurs a la
 * banniere de leur faction. Ils visent le boss et ses sbires ; ils ne
 * s'en prennent au joueur que s'il les frappe.
 *
 * <p>Comme le boss, il n'existe que pour un combat : sans combat en cours au nom
 * de {@link #fight}, il disparait.
 */
public class RivalFighter extends Vindicator {

    private UUID fight;
    private int rival = -1;

    public RivalFighter(EntityType<? extends RivalFighter> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0d, false));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.6d));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, RivalFighter.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, EventBoss.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, true,
                (target, level) -> target.getTags().contains(FactionEvents.MINION_TAG)));
    }

    public void bind(UUID fight, int rival) {
        this.fight = fight;
        this.rival = rival;
    }

    /** L'indice de sa faction dans le combat, ou -1. */
    public int rival() {
        return rival;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (fight == null || FactionEvents.fight(fight) == null) {
            discard();
            return;
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
}
