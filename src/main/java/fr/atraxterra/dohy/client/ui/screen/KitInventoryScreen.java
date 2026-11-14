package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.ui.InventoryNav;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * L'inventaire du joueur au style du kit, avec l'acces aux menus.
 *
 * <p>Il remplace l'ecran d'inventaire du jeu en survie ({@code InventoryClient}) ;
 * le mode creatif garde le sien. A droite, le bouton rouge ouvre le menu
 * principal et les cinq cases ouvrent le shop, les contrats, la banque, la
 * faction et les metiers ; sous le craft, le bouton rouge ouvre le concours
 * Trixium. Plus besoin de connaitre les commandes.
 *
 * <h2>Les cases sont celles du jeu</h2>
 *
 * <p>La position d'une case est fixee par le menu d'inventaire du jeu
 * ({@code Slot.x} et {@code Slot.y} sont finals) : le panneau se dessine donc
 * autour d'elles, et deborde au-dessus pour le titre et a droite pour les
 * boutons. L'ecran est recentre sur ce panneau, et un clic dans le panneau
 * n'est jamais pris pour un lancer d'objet.
 *
 * <p>Le livre de recettes et la liste des effets de l'ecran du jeu ne sont pas
 * repris : les effets restent affiches en haut a droite de l'ecran de jeu.
 */
public final class KitInventoryScreen extends UiContainerScreen<InventoryMenu> {

    /** Le panneau, en coordonnees des cases (l'origine est celle de l'inventaire du jeu). */
    private static final int PANEL_X = -20;
    private static final int PANEL_Y = -54;
    private static final int PANEL_W = 252;
    private static final int PANEL_H = 230;

    /** La colonne de boutons. */
    private static final int COLUMN_X = 188;
    private static final int BUTTON = 28;
    private static final int BUTTON_GAP = 5;
    private static final int BUTTONS_Y = -2;
    private static final int MENU_X = 192;
    private static final int MENU_Y = -34;
    private static final int MENU_SIZE = 21;
    /** L'encyclopedie, a gauche du menu principal. */
    private static final int HELP_X = MENU_X - 25;

    /** Le bouton du concours, sous le craft. */
    private static final int PASS_X = 97;
    private static final int PASS_Y = 58;
    private static final int PASS_W = 75;
    private static final int PASS_H = 15;

    private static final List<Shortcut> SHORTCUTS = List.of(
            new Shortcut("shop", "inv_shop", "Shop", "Acheter et vendre"),
            new Shortcut("contracts", "inv_contracts", "Contrats", "Quotidiens et hebdomadaires"),
            new Shortcut("bank", "inv_bank", "Banque", "Épargne et intérêts"),
            new Shortcut("faction", "inv_faction", "Faction", "Ta faction, ses claims, sa ligue"),
            new Shortcut("jobs", "inv_jobs", "Métiers", "Niveaux, déblocages, primes"));

    private final ItemStack trixium;

    public KitInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.inventory"), 176, 166);
        this.trixium = new ItemStack(PalaItems.TRIXIUM.get());
    }

    @Override
    protected void init() {
        super.init();
        // Recentre sur le panneau, qui deborde a gauche, a droite et en haut des
        // cases du jeu. Sur un petit ecran, le titre ne sort jamais par le haut.
        leftPos = Math.max(-PANEL_X + 2, (width - PANEL_W) / 2 - PANEL_X);
        topPos = Math.max(-PANEL_Y + 2, (height - PANEL_H) / 2 - PANEL_Y);
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panel(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        title(g, "INVENTAIRE", -4, -40, 15, 180, Ui.RED, Ui.RED_SHADE);

        // Armure : le losange, le mot, les quatre cases et le personnage.
        g.texture(Ui.texture("section_armor"), 6, -12, 11, 11, 56, 56);
        g.text(Ui.WIDE, "ARMURE", 21, -9.5f, 6, Ui.WHITE);
        g.rect(26, 8, 50, 71, Ui.INSET);

        // Craft : le losange, le mot, la grille, la fleche et le resultat.
        g.texture(Ui.texture("section_craft"), 96, -12, 11, 11, 56, 56);
        g.text(Ui.WIDE, "CRAFT", 111, -9.5f, 6, Ui.WHITE);
        drawCraftArrow(g, 137, 30);

        for (Slot slot : menu.slots) {
            frame(g, slot, false);
        }

        // Le concours Trixium, a la place du Lucky Pass.
        boolean passHovered = inside(mouseX, mouseY, PASS_X, PASS_Y, PASS_W, PASS_H);
        g.button(PASS_X, PASS_Y, PASS_W, PASS_H, Ui.RED, Ui.RED_DARK, passHovered);
        g.text(Ui.WIDE, "CONCOURS", PASS_X + 7, PASS_Y + 4.5f, 4.6f, Ui.WHITE);
        int boxX = PASS_X + PASS_W - 15;
        g.border(boxX, PASS_Y + 2, 11, 10, 1, Ui.WHITE);
        g.item(trixium, boxX + 1.5f, PASS_Y + 3, 8, false);

        // Le menu principal.
        boolean menuHovered = inside(mouseX, mouseY, MENU_X, MENU_Y, MENU_SIZE, MENU_SIZE);
        g.button(MENU_X, MENU_Y, MENU_SIZE, MENU_SIZE, Ui.RED, Ui.RED_DARK, menuHovered);
        for (int row = 0; row < 3; row++) {
            int y = MENU_Y + 5 + row * 4;
            g.rect(MENU_X + 4, y, 3, 3, Ui.WHITE);
            g.rect(MENU_X + 8, y, 9, 3, Ui.WHITE);
        }

        // L'encyclopedie : un point d'interrogation.
        boolean helpHovered = inside(mouseX, mouseY, HELP_X, MENU_Y, MENU_SIZE, MENU_SIZE);
        g.button(HELP_X, MENU_Y, MENU_SIZE, MENU_SIZE, Ui.GREEN, Ui.GREEN_DARK, helpHovered);
        g.centered(Ui.TITLE, "?", HELP_X + MENU_SIZE / 2.0f + 0.5f, MENU_Y + 4.5f, 12, Ui.WHITE);

        // Les cinq raccourcis.
        for (int index = 0; index < SHORTCUTS.size(); index++) {
            Shortcut shortcut = SHORTCUTS.get(index);
            int y = shortcutY(index);
            boolean hovered = inside(mouseX, mouseY, COLUMN_X, y, BUTTON, BUTTON);
            g.rect(COLUMN_X, y, BUTTON, BUTTON, hovered ? Ui.WHITE : Ui.SLOT_BORDER);
            g.rect(COLUMN_X + 1, y + 1, BUTTON - 2, BUTTON - 2, hovered ? Ui.SLOT_HOVER : 0xFF242428);
            g.texture(Ui.texture(shortcut.icon()), COLUMN_X + 3, y + 3, BUTTON - 6, BUTTON - 6, 64, 64);
        }
    }

    /** La petite fleche blanche qui descend vers le resultat. */
    private static void drawCraftArrow(UiGraphics g, int x, int y) {
        g.rect(x, y, 2, 5, Ui.WHITE);
        g.rect(x, y + 4, 9, 2, Ui.WHITE);
        g.rect(x + 7, y + 2, 2, 6, Ui.WHITE);
        g.rect(x + 9, y + 3, 1, 4, Ui.WHITE);
        g.rect(x + 10, y + 4, 1, 2, Ui.WHITE);
    }

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        tip(g, mouseX, mouseY, MENU_X, MENU_Y, MENU_SIZE, MENU_SIZE, List.of("Menu principal", "Tous les menus"));
        tip(g, mouseX, mouseY, HELP_X, MENU_Y, MENU_SIZE, MENU_SIZE,
                List.of("Encyclopédie", "Tout DoHy, fiche par fiche (touche J)"));
        tip(g, mouseX, mouseY, PASS_X, PASS_Y, PASS_W, PASS_H,
                List.of("Concours Trixium", "La saison du mois et ses paliers"));
        for (int index = 0; index < SHORTCUTS.size(); index++) {
            Shortcut shortcut = SHORTCUTS.get(index);
            tip(g, mouseX, mouseY, COLUMN_X, shortcutY(index), BUTTON, BUTTON,
                    List.of(shortcut.label(), shortcut.hint()));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // Le personnage, au meme cadrage que l'inventaire du jeu.
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, leftPos + 26, topPos + 8, leftPos + 75,
                topPos + 78, 30, 0.0625f, mouseX, mouseY, minecraft.player);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            String target = target(event.x() - leftPos, event.y() - topPos);
            if (target != null) {
                UiScreen.click(1.0f);
                // Fermer d'abord : le serveur rend au joueur ce qui est dans la
                // grille de craft et sous la souris, puis ouvre le menu.
                onClose();
                ClientPacketDistributor.sendToServer(new InventoryNav.Open(target));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Le menu vise par un clic, ou {@code null}. */
    private static String target(double x, double y) {
        if (inside(x, y, MENU_X, MENU_Y, MENU_SIZE, MENU_SIZE)) {
            return "hub";
        }
        if (inside(x, y, HELP_X, MENU_Y, MENU_SIZE, MENU_SIZE)) {
            return "encyclopedia";
        }
        if (inside(x, y, PASS_X, PASS_Y, PASS_W, PASS_H)) {
            return "trixium";
        }
        for (int index = 0; index < SHORTCUTS.size(); index++) {
            if (inside(x, y, COLUMN_X, shortcutY(index), BUTTON, BUTTON)) {
                return SHORTCUTS.get(index).target();
            }
        }
        return null;
    }

    /** Un clic dans le panneau n'est pas un lancer d'objet. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return !inside(mouseX - left, mouseY - top, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
    }

    private static int shortcutY(int index) {
        return BUTTONS_Y + index * (BUTTON + BUTTON_GAP);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private record Shortcut(String target, String icon, String label, String hint) {
    }
}
