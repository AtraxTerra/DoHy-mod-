package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.skill.SkillApi;
import fr.atraxterra.dohy.skill.SkillConfig;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le niveau d'un metier, et la question « ai-je le droit ».
 *
 * <p>Aucune donnee propre : un niveau de metier <b>est</b> un niveau de skill.
 * C'est tout l'interet de la fusion — il n'y a rien a sauvegarder, rien a
 * migrer, et aucun risque que les deux chiffres divergent.
 */
public final class JobApi {

    private JobApi() {
    }

    public static int level(ServerPlayer player, Job job) {
        return SkillApi.levelOf(player, job.skill());
    }

    public static int maxLevel() {
        return SkillConfig.config().curve().maxLevel();
    }

    /** Progression dans le niveau courant, entre 0 et 1. */
    public static double progress(ServerPlayer player, Job job) {
        return SkillApi.progress(player, job.skill());
    }

    public static long xp(ServerPlayer player, Job job) {
        return SkillApi.data(player).xpOf(job.skill());
    }

    public static boolean meets(ServerPlayer player, Job job, int required) {
        return level(player, job) >= required;
    }

    /**
     * Le nom a montrer pour un deblocage.
     *
     * <p>Le libelle du datapack d'abord, parce qu'il est ecrit pour etre lu ;
     * sinon le nom traduit de l'item ; en dernier recours le chemin embelli. Un
     * identifiant brut n'arrive jamais jusqu'au joueur.
     */
    public static Component name(JobConfig.Unlock unlock) {
        if (unlock.label().isPresent()) {
            return Component.literal(unlock.label().get());
        }
        if (unlock.isTag()) {
            return Component.literal(Gui.prettify(unlock.plainId()));
        }
        Identifier id = JobConfig.parse(unlock.plainId());
        return id == null
                ? Component.literal(Gui.prettify(unlock.plainId()))
                : Gui.displayName(id);
    }

    /** « Mineur niveau 25 ». */
    public static Component requirementText(JobConfig.Unlock unlock) {
        Job job = Job.byKey(unlock.job());
        String jobName = job == null ? unlock.job() : job.display();
        return Component.literal(jobName + " niveau " + unlock.level());
    }

    /**
     * Le refus, en une ligne.
     *
     * <p>Il dit le niveau requis <i>et</i> le niveau atteint : « il te manque
     * 12 niveaux » est actionnable, « tu n'as pas le niveau » ne l'est pas.
     */
    public static Component refusal(ServerPlayer player, JobConfig.Unlock unlock) {
        Job job = Job.byKey(unlock.job());
        int current = job == null ? 0 : level(player, job);
        int missing = Math.max(0, unlock.level() - current);

        return Component.empty()
                .append(name(unlock).copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — il faut ").withStyle(ChatFormatting.GRAY))
                .append(requirementText(unlock).copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(", tu es " + current + " (" + missing + " a monter)")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }
}
