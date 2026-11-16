package fr.atraxterra.dohy.client.ui.vanilla;

import fr.atraxterra.dohy.client.guide.GuideClient;
import fr.atraxterra.dohy.client.season.ClientSeasons;
import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.ui.InventoryNav;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le menu pause de Berskar : reprendre, les menus du serveur (menu principal,
 * encyclopedie, succes), les ecrans du jeu (statistiques, options), puis quitter ;
 * en bas, l'heure du serveur, la saison et la prochaine remise a neuf de la Mine.
 *
 * <p>Il remplace le menu pause du jeu a son ouverture ({@code InventoryClient}), sauf
 * la pause sans menu (F3 + Echap), qui reste celle du jeu.
 */
public final class KitPauseScreen extends Screen {

    private static final float PANEL_W = 150.0f;
    private static final float BUTTON_W = 128.0f;
    private static final float BUTTON_H = 14.0f;
    private static final float GAP = 3.0f;

    private final List<Entry> entries = new ArrayList<>();
    private float[] hover = new float[0];
    private boolean quitting;

    public KitPauseScreen() {
        super(Component.translatable("menu.game"));
    }

    @Override
    protected void init() {
        entries.clear();
        float half = (BUTTON_W - GAP) / 2.0f;
        int row = 0;
        entries.add(new Entry("REPRENDRE", "ECHAP", MenuStyle.PRIMARY, 0, row++, BUTTON_W, this::onClose));
        entries.add(new Entry("MENU", "", MenuStyle.SOLID, 0, row, half, () -> open("hub")));
        entries.add(new Entry("ENCYCLOPEDIE", keyName(GuideClient.key()), MenuStyle.SOLID, half + GAP, row++, half,
                () -> open("encyclopedia")));
        entries.add(new Entry("SUCCES", "", MenuStyle.SOLID, 0, row, half, () -> open("achievements")));
        entries.add(new Entry("STATISTIQUES", "", MenuStyle.SOLID, half + GAP, row++, half,
                () -> minecraft.gui.setScreen(new StatsScreen(this, minecraft.player.getStats()))));
        entries.add(new Entry("OPTIONS", "", MenuStyle.SOLID, 0, row++, BUTTON_W,
                () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, true))));
        if (minecraft.hasSingleplayerServer()) {
            Screen options = new MultiplayerOptionsScreen(this);
            entries.add(new Entry(label(options.getTitle()), "", MenuStyle.SOLID, 0, row++, BUTTON_W,
                    () -> minecraft.gui.setScreen(new MultiplayerOptionsScreen(this))));
        }
        entries.add(new Entry(label(CommonComponents.disconnectButtonLabel(minecraft.isLocalServer())), "",
                MenuStyle.GHOST, 0, row, BUTTON_W, this::quit));
        hover = new float[entries.size()];
    }

    /** Un libelle du jeu, en capitales et sans accents. */
    private static String label(Component component) {
        return ContainerStyle.plain(component.getString()).toUpperCase(Locale.ROOT);
    }

    private static String keyName(KeyMapping key) {
        if (key == null || key.isUnbound()) {
            return "";
        }
        String name = ContainerStyle.plain(key.getTranslatedKeyMessage().getString()).toUpperCase(Locale.ROOT);
        return name.length() <= 6 ? name : "";
    }

    /** Un menu du serveur : la pause se ferme, le serveur ouvre le menu. */
    private void open(String target) {
        onClose();
        ClientPacketDistributor.sendToServer(new InventoryNav.Open(target));
    }

    /** Sauvegarder et quitter, comme le jeu (un signalement en cours est d'abord propose). */
    private void quit() {
        if (quitting) {
            return;
        }
        quitting = true;
        minecraft.getReportingContext().draftReportHandled(minecraft, this,
                () -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
    }

    // -- Mise en page ------------------------------------------------------------------

    private int rows() {
        int rows = 0;
        for (Entry entry : entries) {
            rows = Math.max(rows, entry.row() + 1);
        }
        return rows;
    }

    private float panelHeight() {
        return 44.0f + rows() * (BUTTON_H + GAP) + 6.0f + 15.0f + 11.0f;
    }

    private float panelX() {
        return Math.round((width - PANEL_W) / 2.0f);
    }

    private float panelY() {
        return Math.max(4.0f, Math.round((height - panelHeight()) / 2.0f));
    }

    private float buttonsX() {
        return panelX() + (PANEL_W - BUTTON_W) / 2.0f;
    }

    private float buttonsY() {
        return panelY() + 44.0f;
    }

    private int hovered(double mouseX, double mouseY) {
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            float x = buttonsX() + entry.x();
            float y = buttonsY() + entry.row() * (BUTTON_H + GAP);
            if (mouseX >= x && mouseX < x + entry.width() && mouseY >= y && mouseY < y + BUTTON_H) {
                return index;
            }
        }
        return -1;
    }

    // -- Dessin ------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
        Paint p = g.paint();
        float x = panelX();
        float y = panelY();
        float height = panelHeight();
        ContainerStyle.window(p, x, y, PANEL_W, height);

        float logo = 20.0f;
        MenuStyle.logo(g, x + (PANEL_W - MenuStyle.logoWidth(g, logo)) / 2.0f, y + 10.0f, logo);
        g.centered(Ui.PIXEL, "PAUSE", x + PANEL_W / 2.0f, y + 32.5f, 3.3f, Ui.Theme.MUTED);

        int over = quitting ? -1 : hovered(mouseX, mouseY);
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            hover[index] += ((index == over ? 1.0f : 0.0f) - hover[index]) * 0.35f;
            MenuStyle.button(g, p, buttonsX() + entry.x(), buttonsY() + entry.row() * (BUTTON_H + GAP),
                    entry.width(), BUTTON_H, entry.label(), entry.hint(), entry.kind(), hover[index], true, 4.6f);
        }

        // L'heure du serveur, la saison, la Mine.
        float chipY = buttonsY() + rows() * (BUTTON_H + GAP) + 6.0f;
        float chipW = (BUTTON_W - 2 * GAP) / 3.0f;
        float chipX = buttonsX();
        MenuStyle.chip(g, p, chipX, chipY, chipW, 15.0f, MenuStyle.city(), MenuStyle.clock(), Ui.Theme.TEXT);
        ClientSeasons.Now season = minecraft.player == null ? null : ClientSeasons.now(minecraft.player.blockPosition());
        if (season != null) {
            String label = season.day() > 0 ? "SAISON  \u00B7  J" + season.day() : "SAISON";
            MenuStyle.chip(g, p, chipX + chipW + GAP, chipY, chipW, 15.0f, label,
                    ContainerStyle.plain(season.name()).toUpperCase(Locale.ROOT), season.color());
        } else {
            long day = minecraft.level == null ? 1 : minecraft.level.getGameTime() / 24000L + 1;
            MenuStyle.chip(g, p, chipX + chipW + GAP, chipY, chipW, 15.0f, "MONDE", "JOUR " + day, Ui.Theme.YELLOW);
        }
        MenuStyle.chip(g, p, chipX + 2 * (chipW + GAP), chipY, chipW, 15.0f, "MINE", MenuStyle.mineReset(),
                Ui.Theme.CYAN);
    }

    // -- Entrees -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && !quitting) {
            int index = hovered(event.x(), event.y());
            if (index >= 0) {
                UiScreen.click(1.0f);
                entries.get(index).action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Un bouton : sa colonne (0 ou la moitie), sa rangee, sa largeur. */
    private record Entry(String label, String hint, int kind, float x, int row, float width, Runnable action) {
    }
}
