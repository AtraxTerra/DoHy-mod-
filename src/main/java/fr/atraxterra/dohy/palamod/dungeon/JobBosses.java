package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.Job;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.CHARGE;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.POISON;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.PULL;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.QUAKE;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.ROOT;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.SLAM;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.SUMMON;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.TELEPORT;

/**
 * Les boss de metier (DoHy) : un gardien par metier, appele par une offrande
 * sur l'autel du spawn ({@code fr.atraxterra.dohy.jobboss}), qui arrive une
 * heure plus tard et attend le joueur dans son arene.
 *
 * <p>Le combat est une partie de donjon d'une seule salle : la salle de boss,
 * dans le decor du donjon du meme metier (la grange de Talikus pour le
 * Fermier, la galerie de Nimbria pour le Mineur, la forge de Vitalys pour le
 * Chasseur, le temple de Manelios pour l'Alchimiste). Tout ce qui vaut pour un
 * boss de donjon vaut ici : barre de boss, capacites, renforts, coffre au
 * centre, porte de sortie ; une mort sort le joueur sans rien lui couter, et le
 * gardien l'attend encore.
 *
 * <p><b>Niveaux.</b> Chaque victoire fait monter le gardien d'un niveau,
 * jusqu'a {@link #MAX_LEVEL} : il prend la force des monstres du palier
 * {@code 10 x niveau} (degats compris), et sa vie croit de 35 % par niveau.
 */
public final class JobBosses {

    public static final int MAX_LEVEL = 10;

    /** Un combat de boss de metier : le metier, le niveau du gardien, son dessin. */
    public record Fight(Job job, int level, BossSpec spec) {
    }

    private JobBosses() {
    }

    public static BossSpec spec(Job job) {
        return switch (job) {
            case FARMER -> BossSpec.of("Gardien des moissons", EntityTypes.RAVAGER, 1.25f, CHARGE, SLAM, ROOT, SUMMON);
            case MINER -> BossSpec.of("Colosse de fer", EntityTypes.IRON_GOLEM, 1.7f, SLAM, QUAKE, CHARGE);
            case HUNTER -> BossSpec.of("Bete sauvage", EntityTypes.ZOGLIN, 2.4f, CHARGE, SUMMON, PULL);
            case ALCHEMIST -> BossSpec.of("Sorciere des fioles", EntityTypes.WITCH, 2.0f, POISON, TELEPORT, ROOT,
                    SUMMON);
        };
    }

    public static String name(Job job) {
        return spec(job).name();
    }

    /** L'icone du gardien dans les ecrans. */
    public static Item icon(Job job) {
        return switch (job) {
            case FARMER -> Items.HAY_BLOCK;
            case MINER -> Items.IRON_BLOCK;
            case HUNTER -> Items.BONE;
            case ALCHEMIST -> Items.BREWING_STAND;
        };
    }

    /** L'arene : le decor du donjon du meme metier. */
    public static Dungeon arena(Job job) {
        return Dungeon.of(job);
    }

    /** Le palier dont le gardien prend la force. */
    public static int palier(int level) {
        return Math.max(1, Math.min(DungeonManager.MAX_PALIER, 10 * level));
    }

    /** Ses points de vie, seul, avant le plafond de 1 024. */
    public static double health(int level) {
        return 300.0d * (1.0d + 0.35d * (Math.max(1, level) - 1));
    }

    /** Lance le combat : faux si le joueur est deja en partie ou hors d'atteinte. */
    public static boolean start(ServerPlayer player, Job job, int level) {
        return DungeonManager.startJobBoss(player, job, Math.max(1, Math.min(MAX_LEVEL, level)));
    }

    /** Le joueur est-il dans une partie (palier ou boss de metier) ? */
    public static boolean busy(ServerPlayer player) {
        return DungeonManager.runOf(player) != null || DungeonManager.inDimension(player);
    }
}
