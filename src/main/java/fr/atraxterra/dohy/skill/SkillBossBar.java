package fr.atraxterra.dohy.skill;

import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * La barre de progression des skills, en haut de l'ecran.
 *
 * <h2>Pourquoi une barre de boss</h2>
 *
 * <p>C'est le seul affichage riche en haut de l'ecran qui soit <b>entierement
 * cote serveur</b>. Le jeu envoie lui-meme les paquets, le client vanilla la
 * dessine : aucune couche de rendu a ecrire, donc rien a reprendre a chaque
 * refonte du rendu — et le mod n'a toujours pas une ligne de code client en
 * dehors du HUD de solde.
 *
 * <p>Une seule barre par joueur, renommee quand il change d'activite. C'est le
 * comportement de SkyBlock : miner puis couper du bois ne fait pas apparaitre
 * deux barres, la meme bascule de « Minage » a « Bucheronnage ».
 *
 * <h2>Pourquoi elle disparait</h2>
 *
 * <p>Une barre permanente mangerait le haut de l'ecran en continu et se
 * confondrait avec un vrai combat de boss. Elle s'efface donc apres
 * {@link #VISIBLE_TICKS} sans gain d'XP, et toute activite la rappelle.
 */
@EventBusSubscriber
public final class SkillBossBar {

    /** Duree d'affichage apres le dernier gain d'XP : cinq secondes. */
    private static final int VISIBLE_TICKS = 100;

    /** Affichage prolonge sur une montee de niveau, qui merite d'etre lue. */
    private static final int LEVEL_UP_TICKS = 160;

    /**
     * Etat transitoire, hors sauvegarde : une barre est de l'affichage, pas une
     * donnee de jeu. Un redemarrage la fait simplement reapparaitre au premier
     * bloc mine.
     */
    private static final Map<UUID, Bar> BARS = new HashMap<>();

    private SkillBossBar() {
    }

    private static final class Bar {
        final ServerBossEvent event;
        int ticksLeft;

        Bar(ServerBossEvent event) {
            this.event = event;
        }
    }

    /**
     * Montre ou rafraichit la barre apres un gain d'XP.
     *
     * @param levelUp vrai si ce gain a fait monter d'au moins un niveau
     */
    public static void show(ServerPlayer player, Skill skill, long gained, boolean levelUp) {
        Bar bar = BARS.computeIfAbsent(player.getUUID(), uuid -> {
            ServerBossEvent event = new ServerBossEvent(UUID.randomUUID(),
                    Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            event.addPlayer(player);
            return new Bar(event);
        });

        int level = SkillApi.levelOf(player, skill);
        int maxLevel = SkillConfig.config().curve().maxLevel();
        boolean maxed = level >= maxLevel;

        bar.event.setColor(colorOf(skill));
        bar.event.setProgress(maxed ? 1.0f : clamp(SkillApi.progress(player, skill)));
        bar.event.setName(label(player, skill, level, maxLevel, gained, maxed));
        bar.ticksLeft = levelUp ? LEVEL_UP_TICKS : VISIBLE_TICKS;
    }

    /**
     * Le texte de la barre.
     *
     * <p>Trois informations et pas plus : ou on en est, ce qu'il reste, ce qu'on
     * vient de gagner. Une barre se lit en un coup d'oeil au milieu d'une
     * action, pas comme un ecran.
     */
    private static Component label(ServerPlayer player, Skill skill, int level,
                                   int maxLevel, long gained, boolean maxed) {
        Component head = Component.literal(skill.display() + " " + level)
                .withStyle(ChatFormatting.AQUA);

        if (maxed) {
            return head.copy()
                    .append(Component.literal("  MAXIMUM").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("   +" + MoneyFormat.exact(gained) + " XP")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }

        long into = SkillApi.data(player).xpOf(skill) - SkillApi.xpToReach(level);
        long need = SkillApi.xpForLevel(level + 1);
        return head.copy()
                .append(Component.literal("   " + MoneyFormat.shortForm(into)
                                + " / " + MoneyFormat.shortForm(need))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   +" + MoneyFormat.exact(gained))
                        .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Une couleur par skill, pour reconnaitre l'activite sans lire.
     *
     * <p>Sept couleurs vanilla pour huit skills : la peche et le minage
     * partagent le bleu, ce sont les deux activites qu'on ne pratique jamais en
     * meme temps.
     */
    private static BossEvent.BossBarColor colorOf(Skill skill) {
        return switch (skill) {
            case MINING -> BossEvent.BossBarColor.BLUE;
            case COMBAT -> BossEvent.BossBarColor.RED;
            case FARMING -> BossEvent.BossBarColor.GREEN;
            case FORAGING -> BossEvent.BossBarColor.YELLOW;
            case FISHING -> BossEvent.BossBarColor.BLUE;
            case SMITHING -> BossEvent.BossBarColor.WHITE;
            case BUILDING -> BossEvent.BossBarColor.PINK;
            case NUCLEAR -> BossEvent.BossBarColor.PURPLE;
        };
    }

    /**
     * Fait expirer les barres.
     *
     * <p>Les entrees sont retrouvees par UUID et non gardees comme references de
     * joueur : un joueur deconnecte n'est plus dans la liste, son entree est
     * donc simplement jetee. Pas besoin d'ecouter la deconnexion, et aucune
     * reference a une entite morte ne traine.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BARS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Bar>> iterator = BARS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Bar> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Joueur parti : sa barre a disparu avec sa session, il n'y a
                // rien a retirer cote client. On jette juste l'entree.
                iterator.remove();
                continue;
            }
            if (--entry.getValue().ticksLeft <= 0) {
                entry.getValue().event.removePlayer(player);
                iterator.remove();
            }
        }
    }

    private static float clamp(double ratio) {
        return (float) Math.max(0.0d, Math.min(1.0d, ratio));
    }
}
