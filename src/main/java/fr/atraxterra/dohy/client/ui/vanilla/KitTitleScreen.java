package fr.atraxterra.dohy.client.ui.vanilla;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.widget.ModsButton;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran titre de Berskar, sur le panorama du jeu : le logo, cinq boutons a gauche,
 * les nouveautes a droite, la version et le joueur en bas.
 *
 * <p>Il remplace l'ecran titre du jeu a son ouverture ({@code InventoryClient}). Les
 * boutons ouvrent les memes ecrans que ceux du jeu (mondes, serveurs, options, liste
 * des mods de NeoForge).
 */
public final class KitTitleScreen extends Screen {

    /** Les nouveautes : la date, le titre, la couleur de la pastille. A tenir a jour a chaque version. */
    private static final List<News> NEWS = List.of(
            new News("16 NOV.", "Nouvelles interfaces : menus, coffres, four", Ui.Theme.RED),
            new News("15 NOV.", "La Mine se remet a neuf toutes les 5 h", Ui.Theme.CYAN),
            new News("15 NOV.", "Metiers au bareme de Paladium Bedrock", Ui.Theme.PINK));

    private static final float BUTTON_W = 105.0f;
    private static final float BUTTON_H = 15.5f;
    private static final float BUTTON_PITCH = 18.5f;
    private static final float NEWS_W = 140.0f;

    private final List<Entry> entries = new ArrayList<>();
    /**
     * La tete du joueur, creee a la premiere image et pas a la construction : l'ecran
     * s'ouvre a la fin du chargement des ressources, avant que les composants des items
     * soient lies ({@code Components not bound yet}). Tant qu'ils ne le sont pas, on
     * reessaie une fois par seconde et la pastille s'affiche sans la tete.
     */
    private ItemStack head;
    private long headRetry;
    private float[] hover = new float[0];
    private String version = "";

    public KitTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
        Minecraft.getInstance().gameRenderer.panorama().startSpin();
    }

    @Override
    protected void init() {
        entries.clear();
        entries.add(new Entry("SOLO", MenuStyle.PRIMARY,
                () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        entries.add(new Entry("MULTIJOUEUR", MenuStyle.SOLID, () -> minecraft.gui.setScreen(
                minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
        entries.add(new Entry("OPTIONS", MenuStyle.SOLID,
                () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false))));
        entries.add(new Entry("MODS", MenuStyle.SOLID,
                () -> ModsButton.create(this).onPress(new MouseButtonEvent(0, 0, new MouseButtonInfo(0, 0)))));
        entries.add(new Entry("QUITTER", MenuStyle.GHOST, minecraft::stop));
        hover = new float[entries.size()];
        String mod = ModList.get().getModContainerById("dohy")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        int plus = mod.indexOf('+');
        String neo = ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        String[] parts = neo.split("\\.");
        String loader = parts.length >= 2 ? parts[0] + "." + parts[1] : neo;
        version = "BERSKAR " + (plus > 0 ? mod.substring(0, plus) : mod) + "  \u00B7  "
                + (neo.isEmpty() ? "MINECRAFT " + SharedConstants.getCurrentVersion().name() : "NEOFORGE " + loader);
    }

    // -- Mise en page ------------------------------------------------------------------

    private float left() {
        return width < 360 ? 14.0f : 30.0f;
    }

    /** Le haut du bloc logo et boutons, un peu au-dessus du centre. */
    private float top() {
        return Math.max(10.0f, (height - 137.5f) / 2.0f - 25.0f);
    }

    private float logoSize(UiGraphics g) {
        float size = 39.0f;
        float max = Math.min(width - 2 * left(), 203.0f * Math.min(1.0f, height / 270.0f));
        float width = MenuStyle.logoWidth(g, size);
        return width > max ? size * max / width : size;
    }

    private float buttonsY() {
        return top() + 48.0f;
    }

    private boolean showNews() {
        return width - 27.5f - NEWS_W > left() + BUTTON_W + 20.0f;
    }

    private int hovered(double mouseX, double mouseY) {
        float x = left();
        float y = buttonsY();
        for (int index = 0; index < entries.size(); index++) {
            float by = y + index * BUTTON_PITCH;
            if (mouseX >= x && mouseX < x + BUTTON_W && mouseY >= by && mouseY < by + BUTTON_H) {
                return index;
            }
        }
        return -1;
    }

    private boolean overCopyright(double mouseX, double mouseY) {
        return mouseX >= width - 70 && mouseY >= height - 8;
    }

    // -- Dessin ------------------------------------------------------------------------

    /** Le panorama du jeu, sans flou, assombri a gauche sous le logo et les boutons. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractPanorama(graphics, partialTick);
        Paint p = new Paint(graphics, ContainerStyle.guiScale());
        p.gradientH(0, 0, width * 0.6f, height, 0xB0000000, 0x00000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
        Paint p = g.paint();
        float left = left();

        MenuStyle.logo(g, left - 1.0f, top(), logoSize(g));

        int over = hovered(mouseX, mouseY);
        float y = buttonsY();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            hover[index] += ((index == over ? 1.0f : 0.0f) - hover[index]) * 0.35f;
            MenuStyle.button(g, p, left, y + index * BUTTON_PITCH, BUTTON_W, BUTTON_H, entry.label(), "",
                    entry.kind(), hover[index], true, 5.0f);
        }

        if (showNews()) {
            drawNews(g, p, width - 27.5f - NEWS_W, Math.max(10.0f, top() - 3.5f));
        }

        g.text(Ui.WIDE, version, left, height - 17.0f, 4.0f, 0xFFA9ABB1);
        drawPlayer(g, p, width - 27.5f - 34.0f, height - 24.5f);
        boolean copyright = overCopyright(mouseX, mouseY);
        g.right(Ui.PIXEL, "COPYRIGHT MOJANG AB", width - 4.0f, height - 6.5f, 3.3f,
                copyright ? Ui.Theme.TEXT : 0xFF6E7178);
    }

    private void drawNews(UiGraphics g, Paint p, float x, float y) {
        float height = 21.0f + NEWS.size() * 20.0f + 2.5f;
        p.shadow(x + 4, y + 8, NEWS_W - 8, height - 6, 14, Paint.alpha(Ui.Theme.SHADOW, 0.5f));
        p.framed(x, y, NEWS_W, height, 5.0f, 0xEE17181B, 0xFF2C2E33, 1);
        // Le losange de l'intertitre.
        Matrix3x2fStack pose = g.raw().pose();
        pose.pushMatrix();
        pose.translate(x + 10.0f, y + 11.0f);
        pose.rotate((float) (Math.PI / 4.0d));
        p.round(-2.6f, -2.6f, 5.2f, 5.2f, 1.0f, Ui.Theme.ORANGE);
        p.round(-0.9f, -0.9f, 1.8f, 1.8f, 0.5f, 0xFF3A1A00);
        pose.popMatrix();
        g.text(Ui.WIDE, "NOUVEAUTES", x + 16.5f, y + 8.6f, 5.0f, Ui.Theme.TEXT);
        float lineX = x + 18.5f + g.width(Ui.WIDE, "NOUVEAUTES", 5.0f);
        p.rect(lineX, y + 11.0f, x + NEWS_W - 7.0f - lineX, 0.25f, 0xFF313338);
        float cardY = y + 18.0f;
        for (News news : NEWS) {
            p.framed(x + 7.0f, cardY, NEWS_W - 14.0f, 18.0f, 3.0f, 0xFF1C1D21, 0xFF2A2C31, 1);
            p.round(x + 7.0f, cardY + 2.5f, 1.0f, 13.0f, 0.5f, news.color());
            g.text(Ui.PIXEL, news.date(), x + 12.5f, cardY + 3.2f, 3.0f, news.color());
            g.text(Ui.BOLD, g.fit(Ui.BOLD, news.text(), 4.4f, NEWS_W - 24.0f), x + 12.5f, cardY + 8.6f, 4.4f,
                    Ui.Theme.TEXT);
            cardY += 20.0f;
        }
    }

    private void drawPlayer(UiGraphics g, Paint p, float x, float y) {
        String name = minecraft.getUser().getName();
        float width = Math.max(34.0f, 17.0f + g.width(Ui.BOLD, name, 3.8f));
        x -= width - 34.0f;
        p.framed(x, y, width, 14.0f, 3.0f, 0xE81C1D21, 0xFF2A2C31, 1);
        ItemStack stack = head();
        if (stack != null) {
            g.item(stack, x + 3.5f, y + 3.5f, 7.0f, false);
        }
        g.text(Ui.BOLD, name, x + 13.0f, y + 2.6f, 3.8f, Ui.Theme.TEXT);
        g.text(Ui.PIXEL, "CONNECTE", x + 13.0f, y + 8.2f, 2.8f, Ui.Theme.MUTED);
    }

    private ItemStack head() {
        if (head == null && System.currentTimeMillis() >= headRetry) {
            try {
                head = new ItemStack(Items.PLAYER_HEAD);
            } catch (RuntimeException notBound) {
                headRetry = System.currentTimeMillis() + 1000L;
            }
        }
        return head;
    }

    // -- Entrees -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int index = hovered(event.x(), event.y());
            if (index >= 0) {
                UiScreen.click(1.0f);
                entries.get(index).action().run();
                return true;
            }
            if (overCopyright(event.x(), event.y())) {
                UiScreen.click(1.0f);
                minecraft.gui.setScreen(new CreditsAndAttributionScreen(this));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private record Entry(String label, int kind, Runnable action) {
    }

    private record News(String date, String text, int color) {
    }
}
