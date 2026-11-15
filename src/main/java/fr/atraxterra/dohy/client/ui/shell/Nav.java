package fr.atraxterra.dohy.client.ui.shell;

import fr.atraxterra.dohy.client.ui.Ui;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * La navigation de la barre laterale : un seul niveau, groupe par theme, comme sur
 * les maquettes validees. Chaque entree demande au serveur d'ouvrir son menu (verbe
 * {@code nav}) ; le serveur verifie le nom contre sa propre liste.
 *
 * <p>Les pastilles (contrats restants, pass a recuperer...) arrivent du serveur
 * avec chaque vue ({@code UiNet.Nav}).
 */
public final class Nav {

    /** Une entree : sa cle, son libelle, son icone, et le menu que le serveur ouvre. */
    public record Item(String key, String label, Supplier<ItemStack> icon, String target) {
    }

    /** Un groupe d'entrees, sous un intertitre (aucun pour l'accueil). */
    public record Group(String label, List<Item> items) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group("", List.of(
                    new Item("accueil", "Accueil", () -> ItemStack.EMPTY, "hub"))),
            new Group("ECONOMIE", List.of(
                    new Item("shop", "Shop", () -> new ItemStack(Items.EMERALD), "shop"),
                    new Item("vente", "Coffre de vente", () -> new ItemStack(Items.CHEST), "sell"),
                    new Item("boutique", "Boutique", () -> new ItemStack(Items.GOLD_BLOCK), "boutique"),
                    new Item("marche", "Marche", () -> new ItemStack(Items.MAP), "market"),
                    new Item("banque", "Banque", () -> new ItemStack(Items.CHEST_MINECART), "bank"),
                    new Item("clicker", "Clicker", () -> new ItemStack(Items.COOKIE), "clicker"))),
            new Group("PROGRESSION", List.of(
                    new Item("pass", "Pass du mois", () -> new ItemStack(Items.FIREWORK_STAR), "pass"),
                    new Item("contrats", "Contrats", () -> new ItemStack(Items.WRITABLE_BOOK), "contracts"),
                    new Item("metiers", "Metiers", () -> new ItemStack(Items.IRON_PICKAXE), "jobs"),
                    new Item("collections", "Collections", () -> new ItemStack(Items.BOOKSHELF), "collections"),
                    new Item("musee", "Musee", () -> new ItemStack(Items.ENCHANTING_TABLE), "museum"),
                    new Item("boss", "Boss de metier", () -> new ItemStack(Items.IRON_SWORD), "altar"),
                    new Item("succes", "Succes", () -> new ItemStack(Items.GOLDEN_APPLE), "achievements"))),
            new Group("PERSONNAGE", List.of(
                    new Item("ameliorations", "Ameliorations", () -> new ItemStack(Items.NETHER_STAR), "upgrades"),
                    new Item("rpg", "Profil RPG", () -> new ItemStack(Items.TOTEM_OF_UNDYING), "rpg"))),
            new Group("FACTION", List.of(
                    new Item("faction", "Ma faction", () -> new ItemStack(Items.BANNER.pick(DyeColor.RED)), "faction"),
                    new Item("trixium", "Concours Trixium",
                            () -> new ItemStack(fr.atraxterra.dohy.palamod.PalaItems.TRIXIUM.get()), "trixium"))),
            new Group("AIDE", List.of(
                    new Item("encyclopedie", "Encyclopedie", () -> new ItemStack(Items.KNOWLEDGE_BOOK), "encyclopedia"),
                    new Item("plans", "Plans", () -> new ItemStack(fr.atraxterra.dohy.guide.DoHyGuide.PLAN.get()),
                            "plans"))));

    /** Les vues rattachees a une entree ; les autres (machines, enclos) s'ouvrent en panneau. */
    private static final Map<String, String> VIEWS = new HashMap<>();

    static {
        VIEWS.put("hub", "accueil");
        VIEWS.put("shop", "shop");
        VIEWS.put("boutique", "boutique");
        VIEWS.put("crate", "boutique");
        VIEWS.put("auction", "marche");
        VIEWS.put("bank", "banque");
        VIEWS.put("clicker", "clicker");
        VIEWS.put("pass", "pass");
        VIEWS.put("contracts", "contrats");
        VIEWS.put("jobs", "metiers");
        VIEWS.put("job_detail", "metiers");
        VIEWS.put("collections", "collections");
        VIEWS.put("museum", "musee");
        VIEWS.put("autel", "boss");
        VIEWS.put("achievements", "succes");
        VIEWS.put("upgrades", "ameliorations");
        VIEWS.put("rpg", "rpg");
        VIEWS.put("trixium", "trixium");
        VIEWS.put("encyclopedie", "encyclopedie");
        VIEWS.put("plan", "plans");
        VIEWS.put("guerre", "faction");
        VIEWS.put("absence", "faction");
        VIEWS.put("chest_explorer", "faction");
    }

    private static Map<String, String> badges = Map.of();
    private static int level = -1;

    private Nav() {
    }

    /** L'entree d'une vue, ou {@code null} : la vue s'ouvre alors en panneau, sans barre laterale. */
    public static Item of(Identifier view) {
        if (view == null) {
            return null;
        }
        String path = view.getPath();
        String key = VIEWS.get(path);
        if (key == null && (path.equals("faction") || path.startsWith("faction_"))) {
            key = "faction";
        }
        return key == null ? null : find(key);
    }

    public static Item find(String key) {
        for (Group group : GROUPS) {
            for (Item item : group.items()) {
                if (item.key().equals(key)) {
                    return item;
                }
            }
        }
        return null;
    }

    /** Le groupe d'une entree, en minuscules accentuees comme dans un fil d'Ariane. */
    public static String groupOf(Item item) {
        for (Group group : GROUPS) {
            if (group.items().contains(item)) {
                return switch (group.label()) {
                    case "ECONOMIE" -> "Economie";
                    case "PROGRESSION" -> "Progression";
                    case "PERSONNAGE" -> "Personnage";
                    case "FACTION" -> "Faction";
                    case "AIDE" -> "Aide";
                    default -> "";
                };
            }
        }
        return "";
    }

    /** L'etat recu du serveur. */
    public static void accept(Map<String, String> received, int globalLevel) {
        badges = Map.copyOf(received);
        level = globalLevel;
    }

    public static String badge(String key) {
        return badges.getOrDefault(key, "");
    }

    /** La couleur d'une pastille : jaune pour « pret », vert pour l'aide, rouge sinon. */
    public static int badgeColor(String key) {
        return switch (key) {
            case "boss" -> Ui.Theme.YELLOW;
            case "encyclopedie", "plans" -> Ui.Theme.GREEN;
            default -> Ui.Theme.RED;
        };
    }

    /** Le niveau global, ou -1 s'il n'est pas encore connu. */
    public static int level() {
        return level;
    }
}
