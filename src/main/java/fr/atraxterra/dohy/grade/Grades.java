package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.Toasts;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.time.LocalDate;

/**
 * Ce que valent les grades pour un joueur : son grade, son Premium, et chaque avantage, grade et
 * Premium additionnes. Tout le reste du mod passe par ici pour lire un avantage, jamais par
 * {@link PlayerGrade} directement : c'est le seul endroit ou Premium s'ajoute.
 */
public final class Grades {

    private Grades() {
    }

    public static GradeData data(ServerPlayer player) {
        return player.getData(DoHyGrades.DATA.get());
    }

    static void save(ServerPlayer player, GradeData data) {
        player.setData(DoHyGrades.DATA.get(), data);
    }

    public static PlayerGrade grade(ServerPlayer player) {
        return PlayerGrade.byId(data(player).grade());
    }

    public static boolean premium(ServerPlayer player) {
        return data(player).premiumUntil() > System.currentTimeMillis();
    }

    /** Les jours de Premium qui restent, arrondis au jour superieur ; 0 sans Premium. */
    public static int premiumDays(ServerPlayer player) {
        long left = data(player).premiumUntil() - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + Premium.DAY_MILLIS - 1) / Premium.DAY_MILLIS);
    }

    // -- Les avantages ----------------------------------------------------------------------

    public static int homes(ServerPlayer player) {
        return grade(player).homes() + (premium(player) ? Premium.HOMES : 0);
    }

    public static int rtpPerMonth(ServerPlayer player) {
        return grade(player).rtp() + (premium(player) ? Premium.RTP : 0);
    }

    /** Les {@code /rtp} qui restent ce mois-ci. */
    public static int rtpLeft(ServerPlayer player) {
        GradeData data = data(player);
        int used = data.rtpMonth() == month() ? data.rtpUsed() : 0;
        return Math.max(0, rtpPerMonth(player) - used);
    }

    /** Le mois en cours, dans le fuseau du mod : annee x 12 + mois. */
    static int month() {
        LocalDate today = LocalDate.now(RealClock.zone());
        return today.getYear() * 12 + today.getMonthValue();
    }

    /** Les rangees du coffre de l'Ender : 3 a 7. */
    public static int enderRows(ServerPlayer player) {
        return Math.min(7, grade(player).enderRows() + (premium(player) ? Premium.ENDER_ROWS : 0));
    }

    public static double jobXpBonus(ServerPlayer player) {
        return grade(player).jobXp() + (premium(player) ? Premium.JOB_XP : 0.0d);
    }

    public static double passXpBonus(ServerPlayer player) {
        return grade(player).passXp() + (premium(player) ? Premium.PASS_XP : 0.0d);
    }

    public static double vanillaXpBonus(ServerPlayer player) {
        return grade(player).vanillaXp() + (premium(player) ? Premium.VANILLA_XP : 0.0d);
    }

    /** La part de la taxe du marche international effacee, 0 a 0,3. */
    public static double marketTaxCut(ServerPlayer player) {
        return grade(player).taxCut() + (premium(player) ? Premium.TAX_CUT : 0.0d);
    }

    public static int auctionSlots(ServerPlayer player) {
        return grade(player).auctionSlots();
    }

    /** Tous les grades gardent l'XP a la mort ; Premium seul, non. */
    public static boolean keepsXp(ServerPlayer player) {
        return grade(player) != PlayerGrade.NONE;
    }

    /** Teleportation sans attendre trois secondes : a partir de Legende. */
    public static boolean instantTeleport(ServerPlayer player) {
        return grade(player).atLeast(PlayerGrade.LEGENDE);
    }

    /** Couleurs dans le chat ({@code &a}, {@code &l}...) : a partir de Heros. */
    public static boolean chatColors(ServerPlayer player) {
        return grade(player).atLeast(PlayerGrade.HEROS);
    }

    /** Emojis dans le chat ({@code :coeur:}...) : a partir de Legende. */
    public static boolean emojis(ServerPlayer player) {
        return grade(player).atLeast(PlayerGrade.LEGENDE);
    }

    public static boolean can(ServerPlayer player, GradeCommand command) {
        return command.premium() ? premium(player) : grade(player).atLeast(command.grade());
    }

    /**
     * Le refus d'une commande verrouillee, avec le lien vers la page des grades ; {@code null}
     * si le joueur y a droit.
     */
    public static Component refusal(ServerPlayer player, GradeCommand command) {
        if (can(player, command)) {
            return null;
        }
        String need = command.premium() ? "Premium" : "le grade " + command.grade().label();
        return Component.literal("/" + command.command() + " demande " + need + ". ").withStyle(ChatFormatting.RED)
                .append(link("[Voir les grades]"));
    }

    /** Un lien cliquable vers la page des grades. */
    public static MutableComponent link(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/grade"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/grade"))));
    }

    // -- L'affichage ---------------------------------------------------------------------------

    /** La couleur du pseudo : celle du grade, celle de Premium sans grade, sinon aucune (0). */
    public static int color(ServerPlayer player) {
        PlayerGrade grade = grade(player);
        if (grade != PlayerGrade.NONE) {
            return grade.color();
        }
        return premium(player) ? Premium.COLOR : 0;
    }

    /** Le libelle court de la liste des joueurs : « LEGENDE », « PREMIUM », ou vide. */
    public static String tag(ServerPlayer player) {
        PlayerGrade grade = grade(player);
        if (grade != PlayerGrade.NONE) {
            return grade.label().toUpperCase(java.util.Locale.ROOT) + (premium(player) ? "+" : "");
        }
        return premium(player) ? "PREMIUM" : "";
    }

    /** Le pseudo du chat et des messages de mort : « [Legende] Pseudo », en couleur. */
    public static Component decorate(ServerPlayer player, Component name) {
        PlayerGrade grade = grade(player);
        int color = color(player);
        if (color == 0) {
            return name;
        }
        String label = grade != PlayerGrade.NONE ? grade.label() : "Premium";
        return Component.empty()
                .append(Component.literal("[" + label + "] ").withColor(color & 0xFFFFFF))
                .append(name.copy().withColor(color & 0xFFFFFF));
    }

    /** Le pseudo et la liste des joueurs du jeu se recalculent : apres un achat, une fin de Premium. */
    public static void refreshNames(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    // -- Les achats ----------------------------------------------------------------------------

    /** Ce que coute ce grade a ce joueur : la difference avec le sien ; -1 s'il l'a deja. */
    public static long priceFor(ServerPlayer player, PlayerGrade target) {
        PlayerGrade current = grade(player);
        if (target == PlayerGrade.NONE || current.atLeast(target)) {
            return -1L;
        }
        return target.price() - current.price();
    }

    /** Achete un grade ; la raison du refus, ou {@code null}. */
    public static String buy(ServerPlayer player, PlayerGrade target) {
        long cost = priceFor(player, target);
        if (target == PlayerGrade.NONE) {
            return "Ce grade n'existe pas.";
        }
        if (cost < 0) {
            PlayerGrade current = grade(player);
            return current == target ? "Tu as deja le grade " + target.label() + "."
                    : "Ton grade " + current.label() + " contient deja tout ce que donne " + target.label() + ".";
        }
        if (!Economy.tryDebit(player, cost)) {
            return "Il te faut " + MoneyFormat.exact(cost) + " $ en liquide.";
        }
        save(player, data(player).withGrade(target.id()));
        refreshNames(player);
        celebrate(player, "Grade " + target.label(), "Tes avantages sont actifs, pour toujours.", target.color(),
                DoHyGrades.badge(target));
        return null;
    }

    /** Achete trente jours de Premium, ajoutes a ceux qui restent ; la raison du refus, ou {@code null}. */
    public static String buyPremium(ServerPlayer player) {
        long now = System.currentTimeMillis();
        GradeData data = data(player);
        long until = Math.max(now, data.premiumUntil()) + Premium.DAYS * Premium.DAY_MILLIS;
        if (until - now > Premium.MAX_DAYS * Premium.DAY_MILLIS) {
            return "Ton Premium court deja sur plus de " + (Premium.MAX_DAYS - Premium.DAYS) + " jours.";
        }
        if (!Economy.tryDebit(player, Premium.PRICE)) {
            return "Il te faut " + MoneyFormat.exact(Premium.PRICE) + " $ en liquide.";
        }
        boolean extended = data.premiumUntil() > now;
        save(player, data.withPremiumUntil(until));
        refreshNames(player);
        celebrate(player, extended ? "Premium prolonge" : "Premium", "Actif encore " + premiumDays(player) + " jours.",
                Premium.COLOR, DoHyGrades.premiumBadge());
        return null;
    }

    private static void celebrate(ServerPlayer player, String title, String line, int color,
                                  net.minecraft.world.item.ItemStack icon) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f,
                1.2f);
        Toasts.send(player, icon, "grade", title, line, color, "grade");
    }

    // -- L'administration ---------------------------------------------------------------------

    public static void set(ServerPlayer player, PlayerGrade grade) {
        save(player, data(player).withGrade(grade.id()));
        refreshNames(player);
    }

    /** Fixe le Premium a tant de jours a partir de maintenant ; 0 l'enleve. */
    public static void setPremiumDays(ServerPlayer player, int days) {
        long until = days <= 0 ? 0L : System.currentTimeMillis() + days * Premium.DAY_MILLIS;
        save(player, data(player).withPremiumUntil(until));
        GradeEvents.premiumChanged(player);
    }
}
