package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.ui.InventoryNav;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * L'inventaire du joueur au style du kit, avec l'acces aux menus.
 *
 * <p>Il remplace l'ecran d'inventaire du jeu en survie ({@code InventoryClient}) ;
 * le mode creatif garde le sien. En haut a droite, le bouton rouge ouvre le menu
 * principal et le point d'interrogation l'encyclopedie ; a droite, les cinq cases
 * ouvrent le shop, les contrats, la banque, la faction et les metiers ; sous le
 * craft, le bouton rouge ouvre le pass du mois et le vert le Lucky Pass. Plus besoin de connaitre les
 * commandes.
 *
 * <h2>Les cases sont celles du jeu</h2>
 *
 * <p>La position d'une case est fixee par le menu d'inventaire du jeu
 * ({@code Slot.x} et {@code Slot.y} sont finals) : le panneau se dessine donc
 * autour d'elles, et deborde au-dessus pour l'en-tete et a droite pour les
 * boutons. L'ecran est recentre sur ce panneau, et un clic dans le panneau
 * n'est jamais pris pour un lancer d'objet.
 *
 * <p>Le livre de recettes et la liste des effets de l'ecran du jeu ne sont pas
 * repris : les effets restent affiches en haut a droite de l'ecran de jeu.
 */
public final class KitInventoryScreen extends UiContainerScreen<InventoryMenu> {

    /** Le panneau, en coordonnees des cases (l'origine est celle de l'inventaire du jeu). */
    private static final int PANEL_X = -4;
    private static final int PANEL_Y = -30;
    private static final int PANEL_W = 232;
    private static final int PANEL_H = 197;

    /** La colonne de raccourcis. */
    private static final int COLUMN_X = 188;
    private static final int BUTTON = 28;
    private static final int BUTTON_GAP = 5;
    private static final int BUTTONS_Y = -2;

    /** L'en-tete : la tuile et le titre, puis l'encyclopedie et le menu principal a droite. */
    private static final int HEADER_Y = PANEL_Y + 5;
    private static final int MENU_W = 40;
    private static final int MENU_H = 13;
    private static final int MENU_X = COLUMN_X + BUTTON - MENU_W;
    private static final int MENU_Y = HEADER_Y + 2;
    private static final int HELP_X = MENU_X - 4 - MENU_H;

    /** Le bouton du pass, sous le craft, et celui du Lucky Pass a sa droite. */
    private static final int PASS_X = 97;
    private static final int PASS_Y = 58;
    private static final int PASS_W = 47;
    private static final int PASS_H = 15;
    private static final int LUCKY_X = PASS_X + PASS_W + 3;
    private static final int LUCKY_W = 172 - LUCKY_X;

    /** La zone du personnage, entre l'armure et la seconde main. */
    private static final int MODEL_X = 26;
    private static final int MODEL_Y = 8;
    private static final int MODEL_W = 49;
    private static final int MODEL_H = 70;

    private static final List<Shortcut> SHORTCUTS = List.of(
            new Shortcut("shop", "inv_shop", "Commerce", "Acheter, vendre, hotel des ventes"),
            new Shortcut("contracts", "inv_contracts", "Contrats", "Quotidiens et hebdomadaires"),
            new Shortcut("bank", "inv_bank", "Banque", "Epargne et interets"),
            new Shortcut("faction", "inv_faction", "Faction", "Ta faction, ses claims, sa ligue"),
            new Shortcut("jobs", "inv_jobs", "Metiers", "Niveaux, deblocages, primes"));

    /** La relique du mois : l'icone du bouton du pass. */
    private final ItemStack passIcon;
    private final ItemStack luckyIcon =
            fr.atraxterra.dohy.palamod.lucky.LuckyType.PALADIUM.stack(1);
    private final ItemStack headerIcon = new ItemStack(Items.BUNDLE);

    public KitInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.inventory"), 176, 166);
        this.passIcon = new ItemStack(fr.atraxterra.dohy.pass.DoHyPass.relicItem(fr.atraxterra.dohy.pass.Relic.of(
                fr.atraxterra.dohy.pass.PassSeason.month(fr.atraxterra.dohy.pass.PassSeason.current()))));
    }

    @Override
    protected void init() {
        super.init();
        // Recentre sur le panneau, qui deborde a droite et en haut des cases du
        // jeu. Sur un petit ecran, l'en-tete ne sort jamais par le haut.
        leftPos = Math.max(-PANEL_X + 2, (width - PANEL_W) / 2 - PANEL_X);
        topPos = Math.max(-PANEL_Y + 2, (height - PANEL_H) / 2 - PANEL_Y);
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        Paint p = g.paint();
        ContainerStyle.window(p, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        ContainerStyle.header(g, p, headerIcon, "Inventaire", 8, HEADER_Y, HELP_X - 12);

        // L'encyclopedie : un point d'interrogation vert.
        boolean helpHovered = inside(mouseX, mouseY, HELP_X, MENU_Y, MENU_H, MENU_H);
        if (helpHovered) {
            p.glow(HELP_X + MENU_H / 2.0f, MENU_Y + MENU_H / 2.0f, 12.0f, Paint.alpha(Ui.Theme.GREEN, 0.35f));
        }
        p.framed(HELP_X, MENU_Y, MENU_H, MENU_H, 3.5f, helpHovered ? Ui.Theme.GREEN : 0xFF222427,
                helpHovered ? Ui.Theme.GREEN_L : 0xFF2E3035, 1);
        g.centered(Ui.BOLD, "?", HELP_X + MENU_H / 2.0f + 0.2f, MENU_Y + 2.6f, 8.0f,
                helpHovered ? Ui.Theme.TEXT : Ui.Theme.GREEN_L);

        // Le menu principal : le bouton rouge, ses trois traits et son nom.
        boolean menuHovered = inside(mouseX, mouseY, MENU_X, MENU_Y, MENU_W, MENU_H);
        if (menuHovered) {
            p.glow(MENU_X + MENU_W / 2.0f, MENU_Y + MENU_H / 2.0f, 22.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
        }
        p.roundGradient(MENU_X, MENU_Y, MENU_W, MENU_H, 3.5f, menuHovered ? 0xFFFF5A43 : Ui.Theme.RED_LIGHT,
                menuHovered ? Ui.Theme.RED : Ui.Theme.RED_D);
        for (int row = 0; row < 3; row++) {
            p.round(MENU_X + 5.0f, MENU_Y + 3.8f + row * 2.2f, 6.0f, 1.2f, 0.6f, Ui.Theme.TEXT);
        }
        g.text(Ui.WIDE, "MENU", MENU_X + 14.0f, MENU_Y + 4.4f, 4.4f, Ui.Theme.TEXT);

        // Armure et personnage.
        ContainerStyle.labelAbove(g, menu.slots.get(InventoryMenu.ARMOR_SLOT_START), "ARMURE", "");
        ContainerStyle.inset(p, MODEL_X, MODEL_Y - 0.5f, MODEL_W, MODEL_H + 1);

        // Craft : la grille, la fleche et le resultat.
        ContainerStyle.labelAbove(g, menu.slots.get(InventoryMenu.CRAFT_SLOT_START), "CRAFT", "");
        boolean ready = menu.getSlot(InventoryMenu.RESULT_SLOT).hasItem();
        p.arrow(137.5f, 33.2f, 11.0f, 5.6f, ready ? Ui.Theme.TEXT2 : 0xFF3A3C42);

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (index == InventoryMenu.RESULT_SLOT) {
                ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
            } else {
                ContainerStyle.slot(p, slot);
            }
        }

        // Le pass du mois.
        boolean passHovered = inside(mouseX, mouseY, PASS_X, PASS_Y, PASS_W, PASS_H);
        if (passHovered) {
            p.glow(PASS_X + PASS_W / 2.0f, PASS_Y + PASS_H / 2.0f, 34.0f, Paint.alpha(Ui.Theme.RED, 0.3f));
        }
        p.roundGradient(PASS_X, PASS_Y, PASS_W, PASS_H, 4.0f, passHovered ? 0xFFFF5A43 : Ui.Theme.RED_LIGHT,
                passHovered ? Ui.Theme.RED : Ui.Theme.RED_D);
        g.text(Ui.WIDE, "PASS", PASS_X + 6, PASS_Y + 5.2f, 4.6f, Ui.Theme.TEXT);
        float boxX = PASS_X + PASS_W - 14.0f;
        p.round(boxX, PASS_Y + 2.0f, 11.0f, 11.0f, 2.5f, 0x40000000);
        g.item(passIcon, boxX + 1.5f, PASS_Y + 3.5f, 8, false);

        // Le Lucky Pass : le lancer gratuit du jour.
        boolean luckyHovered = inside(mouseX, mouseY, LUCKY_X, PASS_Y, LUCKY_W, PASS_H);
        if (luckyHovered) {
            p.glow(LUCKY_X + LUCKY_W / 2.0f, PASS_Y + PASS_H / 2.0f, 20.0f, Paint.alpha(Ui.Theme.GREEN, 0.3f));
        }
        p.roundGradient(LUCKY_X, PASS_Y, LUCKY_W, PASS_H, 4.0f, luckyHovered ? 0xFF5FEA4C : 0xFF3FD52C,
                luckyHovered ? 0xFF2DB51D : 0xFF24A015);
        g.item(luckyIcon, LUCKY_X + (LUCKY_W - 11) / 2.0f, PASS_Y + 2.0f, 11, false);

        // Les cinq raccourcis.
        for (int index = 0; index < SHORTCUTS.size(); index++) {
            Shortcut shortcut = SHORTCUTS.get(index);
            int y = shortcutY(index);
            boolean hovered = inside(mouseX, mouseY, COLUMN_X, y, BUTTON, BUTTON);
            if (hovered) {
                p.glow(COLUMN_X + BUTTON / 2.0f, y + BUTTON / 2.0f, 24.0f, Paint.alpha(Ui.Theme.RED, 0.28f));
            }
            p.framed(COLUMN_X, y, BUTTON, BUTTON, 5.0f, hovered ? Ui.Theme.CARD_HOVER : 0xFF1C1D21,
                    hovered ? Ui.Theme.RED : 0xFF2E3035, 1);
            g.texture(Ui.texture(shortcut.icon()), COLUMN_X + 4, y + 4, BUTTON - 8, BUTTON - 8, 64, 64);
        }
    }

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        tip(g, mouseX, mouseY, MENU_X, MENU_Y, MENU_W, MENU_H, List.of("Menu principal", "Tous les menus"));
        tip(g, mouseX, mouseY, HELP_X, MENU_Y, MENU_H, MENU_H,
                List.of("Encyclopedie", "Tout Berskar, fiche par fiche (touche J)"));
        tip(g, mouseX, mouseY, PASS_X, PASS_Y, PASS_W, PASS_H,
                List.of("Pass du mois", "Quetes du jour et du mois, 35 recompenses"));
        tip(g, mouseX, mouseY, LUCKY_X, PASS_Y, LUCKY_W, PASS_H,
                List.of("Lucky Pass", "Un lancer gratuit par jour : 1 a 3 lucky blocks"));
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
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, leftPos + MODEL_X, topPos + MODEL_Y,
                leftPos + MODEL_X + MODEL_W, topPos + MODEL_Y + MODEL_H, 30, 0.0625f, mouseX, mouseY,
                minecraft.player);
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
        if (inside(x, y, MENU_X, MENU_Y, MENU_W, MENU_H)) {
            return "hub";
        }
        if (inside(x, y, HELP_X, MENU_Y, MENU_H, MENU_H)) {
            return "encyclopedia";
        }
        if (inside(x, y, PASS_X, PASS_Y, PASS_W, PASS_H)) {
            return "pass";
        }
        if (inside(x, y, LUCKY_X, PASS_Y, LUCKY_W, PASS_H)) {
            return "lucky";
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
