package fr.atraxterra.dohy.palamod.boss;

import fr.atraxterra.dohy.faction.event.FactionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Volcania, le dragon de l'Egg Hunt (lot 22f).
 *
 * <p>Le PalaMod en fait une copie du dragon de l'Ender de 1.7.10
 * ({@code EntityCustomDragon}, meme modele, texture {@code volcania.png}). Ici,
 * c'est le dragon vanilla de 26.2 : meme corps, memes parties a frapper, meme
 * mort spectaculaire, et la texture du PalaMod.
 *
 * <p>Son vol ordinaire suit des points de passage calcules sur le relief de
 * l'End, qui n'existe pas dans la Warzone : il y piquerait dans le vide. On le
 * pilote donc par sa phase de charge, la seule qu'on peut viser : il tourne
 * autour de l'ile, de point en point, et toutes les 8 a 14 s plonge sur un
 * joueur ou un combattant rival — le choc de sa tete blesse, ses ailes
 * repoussent. Il ne casse rien : la Warzone interdit le saccage aux monstres.
 */
public class VolcaniaDragon extends EnderDragon {

    private UUID run;
    private BlockPos home = BlockPos.ZERO;
    private int waypoint;
    private int nextDive = 240;

    public VolcaniaDragon(EntityType<? extends EnderDragon> type, Level level) {
        super(type, level);
    }

    /**
     * Le type de Volcania, et non celui du dragon vanilla.
     *
     * <p>En 26.2, le constructeur d'{@code EnderDragon} ignore le type qu'on lui
     * passe et s'inscrit comme {@code minecraft:ender_dragon} (verifie en jeu :
     * le serveur annoncait ce type). Le client recreait alors un dragon vanilla,
     * avec son rendu, et une sauvegarde l'aurait rendu vanilla pour de bon.
     */
    @Override
    public EntityType<?> getType() {
        return PalaBosses.VOLCANIA.get();
    }

    public void bind(UUID run, BlockPos home) {
        this.run = run;
        this.home = home;
        setFightOrigin(home);
    }

    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel level) {
            if (run == null || FactionEvents.run(run) == null) {
                discard();
                return;
            }
            if (isNoAi()) {
                // Sans IA (commande, photo) : il plane sur place au lieu de foncer.
                if (getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
                    getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
                }
            } else if (isAlive() && dragonDeathTime == 0) {
                steer(level);
            }
        }
        super.aiStep();
    }

    private void steer(ServerLevel level) {
        EnderDragonPhaseManager phases = getPhaseManager();
        EnderDragonPhase<?> current = phases.getCurrentPhase().getPhase();
        if (current == EnderDragonPhase.DYING) {
            return;
        }
        nextDive--;
        if (current == EnderDragonPhase.CHARGING_PLAYER) {
            return;
        }
        Vec3 target = null;
        if (nextDive <= 0) {
            LivingEntity prey = prey(level);
            if (prey != null) {
                target = prey.position().add(0.0d, 1.0d, 0.0d);
            }
            nextDive = 160 + getRandom().nextInt(120);
        }
        if (target == null) {
            waypoint = (waypoint + 1) % 8;
            double angle = waypoint * Math.PI / 4.0d;
            target = Vec3.atCenterOf(home).add(Math.cos(angle) * 28.0d, 22.0d + (waypoint % 2) * 6.0d,
                    Math.sin(angle) * 28.0d);
        }
        phases.setPhase(EnderDragonPhase.CHARGING_PLAYER);
        phases.getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target);
    }

    /** Un joueur (hors createur) ou un combattant rival, au hasard, sur l'ile. */
    private LivingEntity prey(ServerLevel level) {
        AABB island = new AABB(home).inflate(48.0d, 40.0d, 48.0d);
        java.util.List<LivingEntity> choices = level.getEntitiesOfClass(LivingEntity.class, island, entity ->
                entity.isAlive() && (entity instanceof ServerPlayer player
                        ? !player.isCreative() && !player.isSpectator() : entity instanceof RivalFighter));
        return choices.isEmpty() ? null : choices.get(getRandom().nextInt(choices.size()));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
