package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les ecrans du golem gardien, dans le kit d'interface : l'accueil, l'arbre de
 * competences et les choix. L'inventaire du golem reste un vrai conteneur.
 */
public final class GolemMenus {

    /** Le golem que chaque joueur regarde. */
    private static final Map<UUID, GuardianGolem> GOLEMS = new ConcurrentHashMap<>();

    private GolemMenus() {
    }

    public static void openMain(ServerPlayer player, GuardianGolem golem) {
        GOLEMS.put(player.getUUID(), golem);
        Main.INSTANCE.open(player);
    }

    static boolean usable(ServerPlayer player, GuardianGolem golem) {
        return golem != null && golem.isAlive() && !golem.has(GolemUpgrade.MAD_GOLEM)
                && player.distanceToSqr(golem) <= GuardianGolem.MENU_RANGE * GuardianGolem.MENU_RANGE * 4;
    }

    public static void openStorage(ServerPlayer player, GuardianGolem golem) {
        UiNet.forget(player);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new FilteredChestMenu(MenuType.GENERIC_9x2, containerId,
                        inventory, golem.storage(), 2),
                Component.literal("Inventaire du golem")));
    }

    /** Les choix ne s'ouvrent qu'une fois une competence a choix acquise. */
    static boolean choicesAvailable(GuardianGolem golem) {
        for (GolemChoice choice : GolemChoice.values()) {
            if (golem.has(choice.gate())) {
                return true;
            }
        }
        return false;
    }

    /** Une description en deux lignes au plus, coupee aux espaces. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width && lines.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    /** Les controleurs du golem verifient tous que le golem est la et a portee. */
    private abstract static class GolemScreen extends MenuController {
        GolemScreen(String path) {
            super(path);
        }

        GuardianGolem golem(ServerPlayer player) {
            GuardianGolem golem = GOLEMS.get(player.getUUID());
            return usable(player, golem) ? golem : null;
        }

        @Override
        protected void back(ServerPlayer player) {
            Main.INSTANCE.open(player);
        }
    }

    // -- Accueil ---------------------------------------------------------------

    public static final class Main extends GolemScreen {
        public static final Main INSTANCE = new Main();

        private Main() {
            super("golem_main");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            GuardianGolem golem = GOLEMS.get(player.getUUID());
            if (golem == null) {
                return new MenuView(id(), "Golem gardien", "", List.of(), List.of(), false);
            }
            int level = golem.golemLevel();
            List<String> info = new ArrayList<>();
            info.add("Proprietaire : " + golem.describeOwner());
            info.add("Degats : " + String.format(Locale.ROOT, "%.2f",
                    golem.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)));
            if (!golem.whitelist().isEmpty()) {
                info.add("Liste blanche : " + String.join(", ", golem.whitelist()));
            }
            if (golem.isLoading()) {
                info.add("En chargement");
            }
            info.add("Nourrissez-le de lingots ou de blocs (accroupi : toute la pile).");
            Card sheet = Card.of(new ItemStack(PalaGolem.GUARDIAN_STONE.get()), "Golem gardien", List.of(
                            "Niveau " + level + " / " + GuardianGolem.MAX_LEVEL,
                            "Vie " + Math.round(golem.getHealth()) + " / " + Math.round(golem.getMaxHealth())))
                    .accent(0xFFFAC400).tooltip(info);
            if (level < GuardianGolem.MAX_LEVEL) {
                sheet = sheet.progress(golem.subXp(), GuardianGolem.xpForLevel(level + 1));
            } else {
                sheet = sheet.state(MenuView.DONE);
            }
            List<Card> cards = new ArrayList<>();
            cards.add(sheet);
            cards.add(Card.of(new ItemStack(Items.BOOK), "Competences", List.of(
                    golem.points() + " point(s) de capacite", "Un point par niveau")).accent(0xFF2F5BEA)
                    .badge(golem.points() > 0 ? String.valueOf(golem.points()) : "").action("tree|ouvrir"));
            boolean choices = choicesAvailable(golem);
            cards.add(Card.of(new ItemStack(Items.COMPARATOR), "Choix", List.of("Reglages des competences",
                            choices ? "Disponibles" : "Aucune competence a choix"))
                    .accent(0xFF2F5BEA).action(choices ? "choices|ouvrir" : "")
                    .state(choices ? MenuView.NORMAL : MenuView.DISABLED));
            boolean storage = golem.has(GolemUpgrade.STOCKAGE);
            cards.add(Card.of(new ItemStack(Items.CHEST), "Inventaire", List.of("Le stockage du golem",
                            storage ? "Disponible" : "Competence Stockage requise"))
                    .accent(0xFF2F5BEA).action(storage ? "storage|ouvrir" : "")
                    .state(storage ? MenuView.NORMAL : MenuView.DISABLED));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("NIVEAU", level + " / " + GuardianGolem.MAX_LEVEL, 0xFFFAC400),
                    new MenuView.Stat("POINTS", String.valueOf(golem.points()), 0xFF22E558));
            return new MenuView(id(), "Golem gardien", golem.describeOwner(), stats,
                    List.of(new MenuView.Section("", "", MenuView.GRID, cards)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            GuardianGolem golem = golem(player);
            if (golem == null) {
                UiNet.close(player);
                return;
            }
            switch (key) {
                case "tree" -> Tree.INSTANCE.open(player);
                case "choices" -> {
                    if (choicesAvailable(golem)) {
                        Choices.INSTANCE.open(player);
                    }
                }
                case "storage" -> {
                    if (golem.has(GolemUpgrade.STOCKAGE)) {
                        openStorage(player, golem);
                    }
                }
                default -> {
                }
            }
        }
    }

    // -- Arbre -------------------------------------------------------------

    public static final class Tree extends GolemScreen {
        public static final Tree INSTANCE = new Tree();

        private Tree() {
            super("golem_tree");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            GuardianGolem golem = GOLEMS.get(player.getUUID());
            if (golem == null) {
                return new MenuView(id(), "Arbre", "", List.of(), List.of(), true);
            }
            List<MenuView.Section> sections = new ArrayList<>();
            for (GolemUpgrade.Branch branch : GolemUpgrade.Branch.values()) {
                List<Card> cards = new ArrayList<>();
                for (GolemUpgrade upgrade : GolemUpgrade.values()) {
                    if (upgrade.branch() != branch) {
                        continue;
                    }
                    boolean owned = golem.has(upgrade);
                    boolean buyable = golem.canBuy(upgrade);
                    List<String> tooltip = new ArrayList<>();
                    tooltip.add(upgrade.description());
                    if (upgrade.parent() != null) {
                        tooltip.add("Pre-requis : " + upgrade.parent().label());
                    }
                    Card card = Card.of(new ItemStack(iconItem(upgrade)), upgrade.label(), List.of(
                                    upgrade.cost() + " point" + (upgrade.cost() > 1 ? "s" : ""),
                                    owned ? "Acquise" : buyable ? "Disponible" : "Indisponible"))
                            .badge(String.valueOf(upgrade.cost())).tooltip(tooltip);
                    if (owned) {
                        card = card.state(MenuView.DONE);
                    } else if (buyable) {
                        card = card.accent(0xFFFAC400).action("buy:" + upgrade.name() + "|acheter");
                    } else {
                        card = card.state(MenuView.DISABLED);
                    }
                    cards.add(card);
                }
                if (!cards.isEmpty()) {
                    sections.add(new MenuView.Section(branchName(branch), "upgrade", MenuView.GRID, cards));
                }
            }
            List<MenuView.Stat> stats = List.of(new MenuView.Stat("POINTS", String.valueOf(golem.points()),
                    0xFF22E558));
            return new MenuView(id(), "Arbre du golem", "Un point de capacite par niveau du golem", stats,
                    sections, true);
        }

        private static String branchName(GolemUpgrade.Branch branch) {
            return switch (branch.ordinal()) {
                case 0 -> "RACINE";
                case 1 -> "BRANCHE UTILITAIRE";
                case 2 -> "BRANCHE GUERRE";
                default -> "BRANCHE FERMIER";
            };
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            GuardianGolem golem = golem(player);
            if (golem == null) {
                UiNet.close(player);
                return;
            }
            if (!key.startsWith("buy:")) {
                return;
            }
            GolemUpgrade upgrade;
            try {
                upgrade = GolemUpgrade.valueOf(key.substring(4));
            } catch (IllegalArgumentException unknown) {
                return;
            }
            if (golem.has(upgrade)) {
                return;
            }
            if (!golem.canManage(player) || !golem.buy(upgrade)) {
                failure(player);
                return;
            }
            success(player);
            if (upgrade == GolemUpgrade.MAD_GOLEM) {
                UiNet.close(player);
            }
        }
    }

    /** {@code ModGuardianUpgrades} : les icones de l'original, au plus proche. */
    static Item iconItem(GolemUpgrade upgrade) {
        return switch (upgrade) {
            case GOLEMANCY -> Items.GOLDEN_APPLE;
            case UTILS -> Items.NETHER_STAR;
            case WAR -> Items.DIAMOND_SWORD;
            case FARMER -> Items.DIAMOND_HOE;
            case INNOVATIVE -> Items.BOOK;
            case GREEDY -> Items.COOKED_BEEF;
            case MECAGOLEM -> Items.REDSTONE;
            case DISCREET -> Items.ENDER_PEARL;
            case INBOX -> PalaGolem.MINI_GOLEM.get();
            case STOCKAGE -> Items.CHEST;
            case HOUSEWORK -> Items.PAPER;
            case WITHER_HUNTER -> Items.WITHER_SKELETON_SKULL;
            case FIGHTER -> PalaEquipment.PALADIUM_SWORD.get();
            case BERSERKER -> Items.GOLDEN_CARROT;
            case FIGHTER2 -> PalaEquipment.GREEN_PALADIUM_SWORD.get();
            case ADVANCED_RESISTANCE -> PalaEquipment.PALADIUM_CHESTPLATE.get();
            case SPECIALIZED_RESISTANCE -> Items.POTION;
            case CARNAGE -> Items.FEATHER;
            case ACCELERATION -> PalaItems.SPEED_ORB.get();
            case LAST_BREATH -> Items.TOTEM_OF_UNDYING;
            case MAD_GOLEM -> Items.TNT;
            case MAGICIAN -> Items.BOW;
            case DESYNCHRONIZATION -> Items.PLAYER_HEAD;
            case PACIFICATION -> Items.WOODEN_HOE;
            case INDUSTRIAL_REGIME -> PalaGolem.CROQUETTE.get();
            case IRON_MAKER -> Items.IRON_INGOT;
            case AMETHYST_MAKER -> PalaItems.AMETHYST_INGOT.get();
            case TITANE_MAKER -> PalaItems.TITANE_INGOT.get();
            case PALADIUM_MAKER -> PalaItems.PALADIUM_INGOT.get();
            case SHIELD -> Items.SHIELD;
        };
    }

    // -- Choix ---------------------------------------------------------------

    public static final class Choices extends GolemScreen {
        public static final Choices INSTANCE = new Choices();

        private Choices() {
            super("golem_choices");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            GuardianGolem golem = GOLEMS.get(player.getUUID());
            List<MenuView.Section> sections = new ArrayList<>();
            if (golem != null) {
                for (GolemChoice choice : GolemChoice.values()) {
                    if (!golem.has(choice.gate())) {
                        continue;
                    }
                    List<Card> cards = new ArrayList<>();
                    for (int option = 1; option <= choice.options(); option++) {
                        boolean active = golem.choice(choice) == option;
                        Card card = Card.of(new ItemStack(active ? Items.EMERALD : Items.FLINT),
                                        active ? "Option " + option + " · choisie" : "Option " + option,
                                        wrap(choice.option(option), 34))
                                .tooltip(List.of(choice.option(option), active ? "Choisi" : "Clic : choisir"));
                        cards.add(active ? card.state(MenuView.DONE)
                                : card.accent(0xFF2F5BEA).action("choice:" + choice.name() + ":" + option + "|choisir"));
                    }
                    sections.add(new MenuView.Section(choice.label().toUpperCase(Locale.ROOT), "craft",
                            MenuView.GRID, cards));
                }
            }
            return new MenuView(id(), "Choix du golem", "Comment ton golem se comporte", List.of(), sections, true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            GuardianGolem golem = golem(player);
            if (golem == null) {
                UiNet.close(player);
                return;
            }
            String[] parts = key.split(":");
            if (parts.length != 3 || !parts[0].equals("choice")) {
                return;
            }
            GolemChoice choice;
            int option;
            try {
                choice = GolemChoice.valueOf(parts[1]);
                option = Integer.parseInt(parts[2]);
            } catch (IllegalArgumentException malformed) {
                return;
            }
            if (golem.has(choice.gate()) && option >= 1 && option <= choice.options() && golem.canManage(player)) {
                golem.setChoice(choice, option);
            }
        }
    }
}
