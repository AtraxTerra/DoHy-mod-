package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet « Bases ennemies » de la faction (lot 22g) : la cible en cours, la
 * demande d'une nouvelle base (trois tailles), et comment la piller.
 */
public final class EnemyBaseMenu extends MenuController {

    public static final EnemyBaseMenu INSTANCE = new EnemyBaseMenu();

    /** Le genre demande aux eclaireurs ; absent, ils prennent ce qu'ils trouvent. Le temps d'une partie. */
    private static final Map<UUID, EnemyBaseStyle> CHOICE = new ConcurrentHashMap<>();

    private EnemyBaseMenu() {
        super("faction_enemy_bases");
    }

    @Override
    public void open(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            FactionEdit.open(player);
            return;
        }
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        fr.atraxterra.dohy.faction.org.DiplomacyMenus.Hub.INSTANCE.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        EnemyBase base = EnemyBases.of(player);
        boolean pending = EnemyBases.pending(player);
        List<Card> target = new ArrayList<>();
        if (pending) {
            target.add(Card.of(new ItemStack(Items.SPYGLASS), "Reperage en cours", List.of(
                    "Tes eclaireurs cherchent", "a plus de " + MoneyFormat.exact(EnemyBases.MIN_DISTANCE) + " blocs"))
                    .accent(0xFFFAC400).badge("..."));
        } else if (base.active()) {
            EnemyBaseTier tier = base.kind();
            EnemyBaseStyle genre = base.genre();
            target.add(Card.of(new ItemStack(genre.icon()), "Les " + base.name(), List.of(
                            genre.title() + " · " + tier.label() + " · " + base.center().getX() + ", "
                                    + base.center().getZ(),
                            EnemyBases.where(player, base)))
                    .accent(0xFFEF3826).badge("CIBLE")
                    .tooltip(List.of(genre.summary(), "Les coffres : " + genre.vault(),
                            "Il faudra : " + tier.tools(), "Clic : une boussole"))
                    .action("compass|recevoir une boussole"));
            target.add(Card.of(new ItemStack(Items.BARRIER), "Abandonner", List.of(
                            "Sans recompense", "Elle s'efface en 5 minutes"))
                    .accent(0xFF55545C).action("abandon|abandonner")
                    .confirm("!Abandonner les " + base.name() + " ?|Sans recompense"));
        } else {
            target.add(Card.of(new ItemStack(Items.MAP), base.state() == EnemyBase.LOOTED
                            ? "Les " + base.name() + " : pillee" : "Aucune cible", List.of(
                            EnemyBaseRegen.regenerating(base) ? "Le terrain revient"
                                    : "Choisis-en une", "Une a la fois"))
                    .state(base.state() == EnemyBase.LOOTED ? MenuView.DONE : MenuView.DISABLED));
        }
        // Le genre : celui que les eclaireurs trouveront, ou celui qu'on leur demande.
        EnemyBaseStyle wanted = CHOICE.get(player.getUUID());
        List<Card> genres = new ArrayList<>();
        Card any = Card.of(new ItemStack(Items.SPYGLASS), "Au hasard", List.of("", ""));
        genres.add(wanted == null ? any.state(MenuView.DONE).badge("CHOISI")
                : any.accent(0xFF3FA9F5).action("genre:|choisir"));
        for (EnemyBaseStyle genre : EnemyBaseStyle.values()) {
            Card card = Card.of(new ItemStack(genre.icon()), genre.title(), wrap(genre.summary(), 40, 2))
                    .tooltip(List.of(genre.summary(), "Les coffres : " + genre.vault()));
            genres.add(wanted == genre ? card.state(MenuView.DONE).badge("CHOISI")
                    : card.accent(0xFF3FA9F5).action("genre:" + genre.key() + "|choisir"));
        }
        List<Card> tiers = new ArrayList<>();
        for (EnemyBaseTier tier : EnemyBaseTier.values()) {
            Card card = Card.of(new ItemStack(tier.icon()), tier.label(), List.of(tier.summary(),
                            "+" + MoneyFormat.exact(tier.elo()) + " ELO · +" + MoneyFormat.exact(tier.money()) + " $"))
                    .tooltip(List.of(tier.contents(), "Il faudra : " + tier.tools()));
            tiers.add(base.active() || pending ? card.state(MenuView.DISABLED)
                    : card.accent(0xFFEF3826).action("generate:" + tier.level() + "|reperer"));
        }
        List<Card> help = List.of(
                Card.of(new ItemStack(Items.COMPASS), "La trouver", List.of("Boussole et coordonnees,",
                        "puis l'Unclaim Finder")).accent(0xFF3FA9F5),
                Card.of(new ItemStack(Items.TNT), "Entrer", List.of("Dynamites, canons,",
                        "withers")).accent(0xFFEF3826),
                NtmDefenses.present()
                        ? Card.of(new ItemStack(Items.CROSSBOW), "Ses defenses", List.of("Tourelles, mines,",
                                "barbeles de NTM")).accent(0xFFEF3826)
                        .tooltip(List.of("Tourelles : 32 blocs (Maxwell : 64)"))
                        : Card.of(new ItemStack(Items.CROSSBOW), "Ses defenses", List.of("Gardes, spikes,",
                                "obsidiennes piegees")).accent(0xFFEF3826),
                Card.of(new ItemStack(Items.CHEST), "Piller", List.of("Dans la salle des coffres,",
                        "ses claims tombent")).accent(0xFFFAC400));
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("CIBLE", pending ? "REPERAGE" : base.active() ? base.name().toUpperCase() : "AUCUNE",
                        base.active() ? 0xFFEF3826 : 0xFFFFFFFF),
                new MenuView.Stat("PILLEES", String.valueOf(data.stat("raid:base")), 0xFFFAC400));
        return new MenuView(id(), "Bases ennemies", "", stats, List.of(
                new MenuView.Section("TA CIBLE", "resources", MenuView.GRID, target),
                new MenuView.Section("GENRE DE BASE", "craft", MenuView.GRID, genres),
                new MenuView.Section("REPERER UNE BASE", "upgrade", MenuView.GRID, tiers),
                new MenuView.Section("PILLAGE", "resources", MenuView.GRID, help)), true)
                .withTabs(FactionMenus.tabs(player, FactionMenus.DIPLOMACY));
    }

    /** Coupe un texte en lignes de {@code width} caracteres au plus, aux espaces ; {@code lines} lignes au plus. */
    static List<String> wrap(String text, int width, int lines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        if (out.size() > lines) {
            List<String> kept = new ArrayList<>(out.subList(0, lines));
            kept.set(lines - 1, kept.get(lines - 1) + "...");
            return kept;
        }
        return out;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("generate:")) {
            EnemyBaseTier tier = EnemyBaseTier.byLevel(Integer.parseInt(key.substring(9)));
            String refusal = EnemyBases.request(player, tier, CHOICE.get(player.getUUID()));
            if (refusal != null) {
                report(player, refusal, "");
            } else {
                success(player);
            }
        } else if (key.startsWith("genre:")) {
            EnemyBaseStyle genre = EnemyBaseStyle.byKey(key.substring(6));
            if (genre == null) {
                CHOICE.remove(player.getUUID());
            } else {
                CHOICE.put(player.getUUID(), genre);
            }
        } else if ("compass".equals(key)) {
            report(player, EnemyBases.compass(player), "Boussole recue.");
        } else if ("abandon".equals(key)) {
            // La fenetre de confirmation a deja pose la question.
            report(player, EnemyBases.abandon(player), "Base abandonnee.");
        }
    }
}
