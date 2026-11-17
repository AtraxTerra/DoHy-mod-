package fr.atraxterra.dohy.palamod.hunter;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobProgression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * L'elephant cote serveur : ses attributs, sa regle d'apparition et le bonus
 * de l'elephant bien nourri.
 *
 * <p><b>Ou il vit.</b> PalaMod ne le faisait jamais apparaitre seul : il
 * sortait des lucky blocks et des evenements du serveur. Ici, il vit dans les
 * savanes et les jungles (tag {@code palamod:elephant_spawns}, regle vanilla de
 * {@code biome_modifier/elephant.json}), sur un sol ou un animal pourrait
 * apparaitre, et {@code Wildlife} en ramene sur une carte deja generee.
 *
 * <p><b>Le bonus du Chasseur</b> ({@code EntityElephant.onDeath},
 * {@code EntityKillSpecialObjective}, niveau 11 de PalaJobs, 55 ici) : tue par
 * un joueur, un elephant qui a mange de 128 a 1 000 bambous rapporte de 1 a 40
 * XP de PalaJobs de plus ; au-dela de 1 000, {@code bambou / 100} plus un
 * tirage jusqu'a {@code bambou / 10 + bambou / 25}. Ces XP passent au meme
 * rapport que celles de la mise a mort ({@code job_progression.json} contre les
 * 15 de PalaJobs), pour garder la proportion de l'original. Une creature
 * capturee ne meurt pas : pas de bonus.
 */
@EventBusSubscriber
public final class ElephantEvents {

    /** L'identifiant des sources d'XP de {@code job_progression.json}. */
    static final String ID = "palamod:elephant";

    /** {@code EntityKillObjective} : 15 XP de PalaJobs pour un elephant tue. */
    static final double PALAJOBS_KILL_XP = 15.0d;

    /** {@code EntityKillSpecialObjective} : niveau 11 de PalaJobs. */
    public static final int FED_LEVEL = 55;

    private ElephantEvents() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(PalaHunter.ELEPHANT.get(), Elephant.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(PalaHunter.ELEPHANT.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Elephant::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Elephant elephant)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int level = JobApi.level(player, Job.HUNTER);
        if (level < FED_LEVEL) {
            return;
        }
        int bonus = palajobsBonus(elephant.feed(), elephant.getRandom());
        long xp = Math.round(bonus * killXp(level) / PALAJOBS_KILL_XP);
        if (xp > 0) {
            JobApi.grant(player, Job.HUNTER, xp);
        }
    }

    /** Le bonus en XP de PalaJobs, d'apres le bambou mange. */
    static int palajobsBonus(int feed, RandomSource random) {
        if (feed > 1_000) {
            return feed / 100 + random.nextInt(feed / 10 + feed / 25);
        }
        return feed >= 128 ? random.nextInt(40) + 1 : 0;
    }

    /** L'XP de Chasseur d'un elephant tue, a ce niveau ({@code job_progression.json}). */
    private static double killXp(int level) {
        return JobProgression.config().def(Job.HUNTER).sources()
                .map(sources -> sources.entities().get(ID))
                .map(reward -> reward.at(level))
                .orElse(0.0d);
    }
}
