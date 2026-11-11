package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.machine.CrusherBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen.Button;
import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen.Design;
import fr.atraxterra.dohy.palamod.hunter.SpawnerControllerBlockEntity;
import fr.atraxterra.dohy.palamod.hunter.SpawnerTier;
import fr.atraxterra.dohy.palamod.hunter.SpawnerUpgrade;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Les machines, une par une : intertitres, fleches, jauges, textes et bulles.
 *
 * <p>Les positions des cases sont celles de {@code MachineLayout} ; ce fichier
 * ne fait que les entourer. Les fleches suivent la progression du cycle, les
 * jauges le combustible ou le reservoir.
 */
public final class MachineDesigns {

    private MachineDesigns() {
    }

    // -- Aides -----------------------------------------------------------------

    private static String seconds(int ticks) {
        return String.format(Locale.FRANCE, "%.1f s", ticks / 20.0f);
    }

    private static float ratio(int value, int goal) {
        return goal <= 0 ? 0.0f : Math.min(1.0f, value / (float) goal);
    }

    private static void small(UiGraphics g, String text, float x, float y, int color) {
        g.text(Ui.BODY, text, x, y, 5.5f, color);
    }

    private static void note(UiGraphics g, String text, float centerX, float y) {
        g.centered(Ui.PIXEL, text, centerX, y, 4.5f, Ui.MUTED);
    }

    private static void tip(UiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height,
                            List<String> lines) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            List<net.minecraft.network.chat.Component> components = new java.util.ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                components.add(net.minecraft.network.chat.Component.literal(lines.get(index))
                        .withColor(index == 0 ? 0xFFFF55 : 0xAAAAAA));
            }
            g.tooltip(components);
        }
    }

    // -- Crusher ---------------------------------------------------------------

    private static final String[] CRUSHER_NAMES = {"AUBERGINES", "CERFEUIL", "KIWANOS", "ORANGEBLUES"};

    public static final Design CRUSHER = new Design(Set.of(2), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_resources", "RECETTES", 132);
        int listX = 16;
        int listY = 50;
        g.rect(listX, listY, 132, 88, Ui.INSET);
        for (int index = 0; index < CrusherBlockEntity.recipeCount(); index++) {
            int rowY = listY + 3 + index * 21;
            boolean active = index == menu.selected();
            g.rect(listX + 3, rowY, 126, 19, active ? 0xFF2A292F : 0xFF1C1B21);
            if (active) {
                g.rect(listX + 3, rowY, 2, 19, Ui.RED);
            }
            int tank = menu.extra(index);
            int goal = CrusherBlockEntity.fruitsPerBatch(index);
            int color = active ? Ui.WHITE : Ui.MUTED;
            g.item(new ItemStack(CrusherBlockEntity.fruit(index)), listX + 8, rowY + 1.5f, 16, false);
            g.text(Ui.TITLE, CRUSHER_NAMES[index % CRUSHER_NAMES.length], listX + 28, rowY + 3, 6.5f, color);
            g.text(Ui.TITLE, tank + "/" + goal, listX + 28, rowY + 11, 5, color);
            g.rect(listX + 70, rowY + 13, 34, 2, 0xFF2E2D34);
            g.rect(listX + 70, rowY + 13, Math.round(34 * ratio(tank, goal)), 2, active ? Ui.GREEN : 0xFF5C5B63);
            g.item(new ItemStack(CrusherBlockEntity.fuel(index)), listX + 110, rowY + 3.5f, 12, false);
            tip(g, mouseX, mouseY, listX + 3, rowY, 126, 19, List.of(
                    new ItemStack(CrusherBlockEntity.fruit(index)).getHoverName().getString(),
                    "Fruits broyes : " + tank + " / " + goal,
                    "Avec : " + new ItemStack(CrusherBlockEntity.fuel(index)).getHoverName().getString(),
                    "Rend : " + CrusherBlockEntity.resultCount(index) + " x "
                            + new ItemStack(CrusherBlockEntity.result(index)).getHoverName().getString()));
        }
        g.section(162, 38, "section_melt", "BROYAGE", 86);
        g.heat(179, 80, 14, menu.progress());
        g.arrow(200, 83, 20, menu.progress());
    }, Map.of(
            0, List.of("Fruit", "Aubergine, cerfeuil, kiwano ou orangeblue"),
            1, List.of("Charbon mixte", "Celui de la recette du fruit"),
            2, List.of("Resultat", "Les lingots du lot termine")));

    // -- Cobblebreaker ---------------------------------------------------------

    private static final String[] PARTICLE_CHANCES = {"4 %", "3,2 %", "2 %", "1,6 %", "1,16 %", "0,044 %"};

    public static final Design COBBLEBREAKER = new Design(Set.of(), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_resources", "PIERRE", 72);
        g.section(100, 38, "section_melt", "PARTICULES", 146);
        g.arrow(64, 66, 32, menu.progress());
        if (menu.duration() > 0) {
            note(g, seconds(menu.duration()), 48, 84);
        }
        for (int index = 0; index < PARTICLE_CHANCES.length; index++) {
            note(g, PARTICLE_CHANCES[index], 112 + index * 24, 84);
        }
        g.wrapped(Ui.BODY, "Une pierre sur huit rend une particule : du fer, le plus courant, au paladium, "
                + "le plus rare. Neuf particules font un lingot.", 16, 102, 5.5f, 230, 8, Ui.MUTED, 3);
    }, Map.of(
            0, List.of("Pierre", "Cobblestone, ou une voidstone de minage")));

    // -- Voidstone -------------------------------------------------------------

    public static final Design VOIDSTONE = new Design(Set.of(0), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_resources", "VIDE", 230);
        note(g, "CE QUI EST POSE ICI DISPARAIT", 131, 108);
    }, Map.of(0, List.of("Voidstone", "Tout objet pose ici est detruit")));

    // -- Palafurnace -----------------------------------------------------------

    public static final Design PALAFURNACE = new Design(Set.of(2), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_melt", "CUISSON", 120);
        g.section(150, 38, "section_upgrade", "AMELIORATION", 96);
        float burn = ratio(menu.tank(), menu.tankGoal());
        g.heat(41, 80, 14, burn);
        g.arrow(64, 83, 26, menu.progress());
        int duration = menu.duration();
        int speed = duration > 0 ? Math.max(1, Math.round(200.0f / duration)) : 1;
        g.text(Ui.TITLE, "X" + speed, 194, 59, 10, speed > 1 ? Ui.GREEN : Ui.MUTED);
        small(g, "Cuisson : " + (duration > 0 ? seconds(duration) : "-"), 150, 82, Ui.TEXT);
        small(g, "Une amelioration par vitesse,", 150, 94, Ui.MUTED);
        small(g, "seize au plus", 150, 102, Ui.MUTED);
        tip(g, mouseX, mouseY, 41, 80, 14, 14, List.of("Combustible", menu.tank() > 0
                ? "Encore " + seconds(menu.tank()) : "Eteint"));
    }, Map.of(
            0, List.of("A cuire", "Ce qui va au four"),
            1, List.of("Combustible", "Tout ce qui brule dans un four"),
            2, List.of("Resultat"),
            3, List.of("Amelioration", "Ameliorations de four, seize au plus")));

    // -- Palaforge -------------------------------------------------------------

    public static final Design PALAFORGE = new Design(Set.of(1), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_melt", "RECYCLAGE", 230);
        float burn = ratio(menu.tank(), menu.tankGoal());
        g.heat(41, 82, 14, burn);
        g.arrow(64, 83, 34, menu.progress());
        g.wrapped(Ui.BODY, "Pose une piece d'armure ou un outil en amethyste, titane, paladium ou endium : la forge "
                + "en rend les lingots, selon son usure.", 146, 62, 5.5f, 100, 8, Ui.MUTED, 6);
        tip(g, mouseX, mouseY, 41, 82, 14, 14, List.of("Combustible", menu.tank() > 0
                ? "Encore " + seconds(menu.tank()) : "Eteint"));
    }, Map.of(
            0, List.of("Piece a recycler", "Armure ou outil en metal de Paladium"),
            1, List.of("Lingots"),
            2, List.of("Combustible", "Tout ce qui brule dans un four")));

    // -- Grinder ---------------------------------------------------------------

    public static final Design GRINDER = new Design(Set.of(0), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_craft", "FORGE", 120);
        g.arrow(66, 60, 32, menu.progress());
        g.section(16, 90, "section_upgrade", "AMELIORATION", 120);
        g.arrow(46, 112, 34, menu.progress2());
        g.section(150, 38, "section_fuel", "PALADIUM", 96);
        g.gauge(206, 52, 10, 66, ratio(menu.tank(), menu.tankGoal()), Ui.RED);
        note(g, menu.tank() + "/" + menu.tankGoal(), 211, 122);
        small(g, "Reservoir", 152, 80, Ui.MUTED);
        small(g, "de paladium", 152, 88, Ui.MUTED);
        tip(g, mouseX, mouseY, 206, 52, 10, 66, List.of("Paladium", menu.tank() + " / " + menu.tankGoal(),
                "Chaque forge et amelioration en consomme"));
    }, Map.of(
            0, List.of("Resultat de la forge"),
            1, List.of("Forge", "Premier element de la recette"),
            2, List.of("Forge", "Second element de la recette"),
            3, List.of("Modificateur", "Le bonus a poser sur l'outil"),
            4, List.of("Outil a ameliorer", "Outil, pioche des dieux, gantelet ou armure de donjon"),
            5, List.of("Paladium", "Remplit le reservoir")));

    // -- Machine a croquettes --------------------------------------------------

    public static final Design FOOD_MACHINE = new Design(Set.of(), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_fuel", "LAVE", 48);
        g.gauge(27, 52, 10, 46, ratio(menu.tank(), menu.tankGoal()), 0xFFFF7A1A);
        g.section(72, 38, "section_resources", "INGREDIENTS", 104);
        small(g, "Fibres", 106, 61, Ui.TEXT);
        small(g, "Mineraux", 106, 85, Ui.TEXT);
        small(g, "Proteines", 106, 109, Ui.TEXT);
        g.arrow(156, 83, 32, menu.progress());
        g.section(190, 38, "section_craft", "SORTIE", 56);
        tip(g, mouseX, mouseY, 27, 52, 10, 46, List.of("Lave", menu.tank() + " / " + menu.tankGoal()
                + " mB"));
    }, Map.of(
            0, List.of("Lave", "Un seau de lave"),
            1, List.of("Fibres", "Snack : pasteque, citrouille, canne a sucre",
                    "The : verrue du Nether, pomme de terre, carotte", "Repas : pomme, ble"),
            2, List.of("Mineraux", "Snack : pierre ou cobblestone", "The : gravier",
                    "Repas : cobblestone moussue"),
            3, List.of("Proteines", "Snack : chair putrefiee", "The : boeuf, poulet, porc ou oeuf",
                    "Repas : morue ou saumon")));

    // -- Palamachine -----------------------------------------------------------

    public static final Design PALAMACHINE = new Design(Set.of(5), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_craft", "ASSEMBLAGE", 110);
        g.arrow(110, 83, 70, menu.progress());
        g.section(170, 38, "section_melt", "RESULTAT", 76);
    }, Map.of(
            0, List.of("Piece centrale", "L'objet a transformer"),
            1, List.of("Ingredient"),
            2, List.of("Ingredient"),
            3, List.of("Ingredient"),
            4, List.of("Ingredient"),
            5, List.of("Resultat")));

    // -- Boite a golem ---------------------------------------------------------

    public static final Design GOLEM_BOX = new Design(Set.of(), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_resources", "GOLEMS", 80);
        g.section(16, 86, "section_fuel", "CROQUETTES", 80);
        g.arrow(98, 80, 44, menu.progress());
        if (menu.duration() > 0) {
            int left = Math.max(0, menu.duration() - menu.work()) / 20;
            note(g, String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60), 120, 94);
        }
        g.section(152, 38, "section_craft", "RECOLTE", 94);
    }, Map.of(
            0, List.of("Mini golem", "Le tien, avec la competence Golem en boite"),
            1, List.of("Mini golem", "Le tien, avec la competence Golem en boite"),
            2, List.of("Mini golem", "Le tien, avec la competence Golem en boite"),
            3, List.of("Croquettes"),
            4, List.of("Croquettes"),
            5, List.of("Croquettes")));

    // -- Controleur de spawners -------------------------------------------------

    private static final int MODE_X = 128;
    private static final int MODE_Y = 110;
    private static final int MODE_W = 118;
    private static final int MODE_H = 18;

    private static final List<String> UPGRADE_HINT = List.of("Amelioration",
            "Vitesse, butin, slime ou capacite", "Le niveau de metier de l'objet est requis");

    public static final Design SPAWNER_CONTROLLER = new Design(Set.of(), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_upgrade", "AMELIORATIONS", 96);
        boolean more = false;
        for (int slot = 0; slot < SpawnerControllerBlockEntity.SLOT_COUNT; slot++) {
            more |= SpawnerUpgrade.of(menu.slots.get(slot).getItem()) == SpawnerUpgrade.MORE;
        }
        if (!more) {
            // Les deux cases du bas attendent l'amelioration de capacite.
            for (int slot = SpawnerControllerBlockEntity.FREE_SLOTS; slot < SpawnerControllerBlockEntity.SLOT_COUNT;
                 slot++) {
                net.minecraft.world.inventory.Slot locked = menu.slots.get(slot);
                g.rect(locked.x, locked.y, 16, 16, 0xFF1A191E);
                g.texture(Ui.texture("spawner_lock"), locked.x + 3, locked.y + 3, 10, 10, 32, 32, 0xFF8C8B93);
            }
        }
        small(g, more ? "Capacite : 4 cases" : "2 cases sur 4", 16, 108, Ui.MUTED);
        small(g, more ? "doublons permis" : "sans doublon", 16, 116, Ui.MUTED);

        g.section(128, 38, "section_resources", "STRUCTURE", 118);
        int level = menu.extra(0);
        boolean automatic = menu.extra(2) == 1;
        int waiting = (menu.selected() << 15) | menu.extra(3);
        if (level <= 0) {
            small(g, "Structure incomplete", 128, 54, Ui.RED);
        } else {
            SpawnerTier tier = SpawnerTier.ofLevel(level);
            small(g, "Palier " + tier.roman(), 128, 54, Ui.WHITE);
            small(g, "Spawners : " + menu.extra(1) + " / " + tier.maxSpawners(), 128, 64, Ui.TEXT);
            if (automatic) {
                small(g, "Coffres : " + menu.tank() + " / " + menu.tankGoal(), 128, 74,
                        menu.tank() > 0 ? Ui.TEXT : Ui.RED);
            } else {
                small(g, "En attente : " + String.format(Locale.FRANCE, "%,d", waiting), 128, 74, Ui.TEXT);
            }
            // La vague suivante du premier groupe de spawners.
            g.rect(128, 88, MODE_W, 4, 0xFF2E2D34);
            g.rect(128, 88, Math.round(MODE_W * menu.progress()), 4, Ui.GREEN);
            if (menu.duration() > 0) {
                small(g, "Vague : " + seconds(Math.max(0, menu.duration() - menu.work())), 128, 96, Ui.MUTED);
            } else {
                small(g, "Aucun spawner charge", 128, 96, Ui.MUTED);
            }
        }
        boolean hovered = mouseX >= MODE_X && mouseX < MODE_X + MODE_W && mouseY >= MODE_Y
                && mouseY < MODE_Y + MODE_H;
        g.button(MODE_X, MODE_Y, MODE_W, MODE_H, automatic ? 0xFF2F7DE0 : 0xFFE08A1E,
                automatic ? 0xFF1B4F94 : 0xFF9A5C10, hovered);
        g.centered(Ui.PIXEL, automatic ? "MODE AUTOMATIQUE" : "MODE MANUEL", MODE_X + MODE_W / 2.0f, MODE_Y + 5,
                5.5f, Ui.WHITE);
        tip(g, mouseX, mouseY, MODE_X, MODE_Y, MODE_W, MODE_H, automatic
                ? List.of("Mode automatique", "Le butin va dans les coffres, sans XP",
                        "Coffres : cinq cases en ligne, au pied du bloc de paladium",
                        "qui porte le controleur, au niveau du sol de la structure", "Clic : passer en manuel")
                : List.of("Mode manuel", "Frappe la creature posee sur le controleur",
                        "Butin, XP et XP de metier ; clic droit : creature suivante",
                        "Clic : passer en automatique (vide les creatures en attente)"));
    }, Map.of(
            0, UPGRADE_HINT,
            1, UPGRADE_HINT,
            2, List.of("Case verrouillee", "S'ouvre avec l'amelioration de capacite"),
            3, List.of("Case verrouillee", "S'ouvre avec l'amelioration de capacite")),
            List.of(new Button(SpawnerControllerBlockEntity.BUTTON_MODE, MODE_X, MODE_Y, MODE_W, MODE_H)));
}
