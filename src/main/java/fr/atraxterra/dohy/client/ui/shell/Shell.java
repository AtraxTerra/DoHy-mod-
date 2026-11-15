package fr.atraxterra.dohy.client.ui.shell;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La coque des menus : la fenetre, la barre laterale (un seul niveau de navigation),
 * le fil d'Ariane, le titre, les chiffres et la rangee d'onglets.
 *
 * <p>Les cotes sont celles des maquettes validees, divisees par trois : la maquette
 * fait 1920 x 1080 points et la fenetre 1824 x 1000, soit 608 x 334 unites. A
 * l'echelle d'interface 3 d'un ecran 1080p, une unite vaut trois pixels.
 *
 * <p>Une vue sans entree de navigation (une machine, un enclos) s'ouvre en panneau :
 * meme style, sans barre laterale.
 */
public final class Shell {

    public static final int WIDTH = 608;
    public static final int HEIGHT = 334;
    public static final float RADIUS = 7.3f;
    public static final float SIDE = 106.0f;
    private static final float PANEL_LEFT = 60.0f;
    private static final float PANEL_WIDTH = 488.0f;
    private static final float TOP = 9.0f;
    private static final float ITEM_HEIGHT = 11.33f;
    private static final float GROUP_HEIGHT = 9.6f;
    private static final float CLOSE = 17.3f;

    private final Identifier view;
    private final Nav.Item active;
    private String title;
    private String subtitle;
    private List<MenuView.Stat> stats;
    private List<MenuView.Tab> tabs;
    private boolean back;
    private final Map<String, Anim.Value> hovers = new HashMap<>();
    private final Anim.Value closeHover = new Anim.Value(0.0f, 14.0f);
    private final List<Hit> hits = new ArrayList<>();

    /** Une zone cliquable dessinee a cette image : l'action qu'elle declenche. */
    private record Hit(float x, float y, float width, float height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }

    public Shell(Identifier view) {
        this.view = view;
        this.active = Nav.of(view);
    }

    /** Le contenu de l'en-tete, a chaque nouvelle vue. */
    public void header(String title, String subtitle, List<MenuView.Stat> stats, List<MenuView.Tab> tabs,
                       boolean back) {
        this.title = title;
        this.subtitle = subtitle;
        this.stats = stats;
        this.tabs = tabs;
        this.back = back;
    }

    public boolean hasSidebar() {
        return active != null;
    }

    // -- Geometrie -------------------------------------------------------------------

    public float windowLeft() {
        return hasSidebar() ? 0.0f : PANEL_LEFT;
    }

    public float windowWidth() {
        return hasSidebar() ? WIDTH : PANEL_WIDTH;
    }

    public float mainLeft() {
        return hasSidebar() ? SIDE + 14.7f : PANEL_LEFT + 14.7f;
    }

    public float mainRight() {
        return windowLeft() + windowWidth() - 13.3f;
    }

    private float titleBottom() {
        float y = TOP + (crumbs().isEmpty() ? 0.0f : 8.3f) + 19.3f;
        if (subtitle != null && !subtitle.isEmpty()) {
            y += 8.6f;
        }
        return y;
    }

    /** Le haut de la zone de contenu, sous le titre et les onglets. */
    public float contentTop() {
        float y = titleBottom() + 7.3f;
        if (tabs != null && !tabs.isEmpty()) {
            y += 19.3f + 7.3f;
        }
        return y;
    }

    // -- Dessin ------------------------------------------------------------------------

    /**
     * La fenetre, la barre laterale et l'en-tete. {@code lift} decale tout vers le bas
     * (animation d'ouverture).
     */
    public void draw(UiGraphics g, Paint p, double mouseX, double mouseY) {
        hits.clear();
        float left = windowLeft();
        float width = windowWidth();
        p.shadow(left + 4, 8, width - 8, HEIGHT - 8, 22, Paint.alpha(Ui.Theme.SHADOW, 0.55f));
        p.framed(left, 0, width, HEIGHT, RADIUS, Ui.Theme.BG2, 0xFF2C2E33, 1);
        // Une lueur rouge tres douce en haut a gauche : la signature Berskar.
        g.scissor(Math.round(left), 0, Math.round(width), HEIGHT);
        p.glow(left + (hasSidebar() ? SIDE : 0) + 30, 0, 120, Paint.alpha(Ui.Theme.RED, 0.06f));
        g.endScissor();
        if (hasSidebar()) {
            sidebar(g, p, mouseX, mouseY);
        }
        header(g, p, mouseX, mouseY);
    }

    private void sidebar(UiGraphics g, Paint p, double mouseX, double mouseY) {
        // Le fond de la barre epouse les coins gauches de la fenetre.
        p.round(1, 1, SIDE + RADIUS, HEIGHT - 2, RADIUS - 0.33f, Ui.Theme.SIDEBAR);
        p.rect(SIDE, 1, RADIUS + 1, HEIGHT - 2, Ui.Theme.BG2);
        p.rect(SIDE - 0.34f, 1, 0.34f, HEIGHT - 2, 0xFF26282C);
        g.textShaded(Ui.TITLE, "BERSKAR", 9.3f, 8.5f, 13.3f, Ui.Theme.RED, Ui.Theme.RED_DD, 1.33f);
        float y = 28.0f;
        for (Nav.Group group : Nav.GROUPS) {
            if (!group.label().isEmpty()) {
                g.text(Ui.PIXEL, group.label(), 10.0f, y + 4.0f, 3.6f, Ui.Theme.DIM);
                y += GROUP_HEIGHT;
            }
            for (Nav.Item item : group.items()) {
                navItem(g, p, item, 5.3f, y, SIDE - 10.6f, mouseX, mouseY);
                y += ITEM_HEIGHT;
            }
        }
        footer(g, p);
    }

    private void navItem(UiGraphics g, Paint p, Nav.Item item, float x, float y, float width, double mouseX,
                         double mouseY) {
        boolean on = item == active;
        boolean over = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + ITEM_HEIGHT;
        float hover = hovers.computeIfAbsent(item.key(), key -> new Anim.Value(0.0f, 16.0f)).toward(over ? 1.0f : 0.0f);
        if (on) {
            p.round(x, y, width, ITEM_HEIGHT, 3.0f, Paint.alpha(Ui.Theme.RED, 0.17f));
            p.gradientH(x + 3.0f, y, width * 0.55f, ITEM_HEIGHT, Paint.alpha(Ui.Theme.RED, 0.14f), 0x00EF3926);
            p.glow(1.0f, y + ITEM_HEIGHT / 2.0f, 7.0f, Paint.alpha(Ui.Theme.RED, 0.55f));
            p.round(0.0f, y + 2.0f, 1.34f, ITEM_HEIGHT - 4.0f, 0.67f, Ui.Theme.RED);
        } else if (hover > 0.01f) {
            p.round(x, y, width, ITEM_HEIGHT, 3.0f, Paint.alpha(0xFFFFFFFF, 0.05f * hover));
        }
        if (item.key().equals("accueil")) {
            g.texture(Ui.HOME, Math.round(x + 3.6f), Math.round(y + 2.2f), 9, 7, 40, 32,
                    on || over ? 0xFFFFFFFF : 0xFFC9C9CC);
        } else {
            ItemStack icon = item.icon().get();
            g.item(icon, x + 4.0f, y + 2.0f, 7.33f, false);
        }
        int color = on ? Ui.Theme.TEXT : Paint.mix(Ui.Theme.TEXT2, Ui.Theme.TEXT, hover);
        g.text(Ui.BODY, item.label(), x + 15.3f, y + 3.2f, 5.33f, color);
        String badge = Nav.badge(item.key());
        if (!badge.isEmpty()) {
            pill(g, p, badge, x + width - 2.7f, y + 1.67f, Nav.badgeColor(item.key()), true);
        }
        hits.add(new Hit(x, y, width, ITEM_HEIGHT, () -> {
            if (!on) {
                UiScreen.click(1.0f);
                UiClient.send(view, "nav", item.target(), 0);
            }
        }));
    }

    private void footer(UiGraphics g, Paint p) {
        float x = 5.3f;
        float y = HEIGHT - 6.0f - 19.3f;
        float width = SIDE - 10.6f;
        p.framed(x, y, width, 19.3f, 4.0f, 0xFF18191C, 0xFF26282C, 1);
        g.item(new ItemStack(Items.PLAYER_HEAD), x + 4.0f, y + 4.0f, 11.33f, false);
        Minecraft minecraft = Minecraft.getInstance();
        String name = minecraft.player == null ? "Joueur" : minecraft.player.getName().getString();
        g.text(Ui.BOLD, g.fit(Ui.BOLD, name, 5.0f, 42.0f), x + 18.7f, y + 4.6f, 5.0f, Ui.Theme.TEXT);
        if (Nav.level() >= 0) {
            g.text(Ui.PIXEL, "NIVEAU GLOBAL " + Nav.level(), x + 18.7f, y + 11.6f, 3.2f, Ui.Theme.MUTED);
        }
        g.right(Ui.PIXEL, "ECHAP", x + width - 4.0f, y + 5.4f, 3.2f, Ui.Theme.DIM);
        g.right(Ui.PIXEL, "FERMER", x + width - 4.0f, y + 10.6f, 3.2f, Ui.Theme.DIM);
    }

    /** Le fil d'Ariane : Accueil, le groupe, l'entree. */
    private List<String> crumbs() {
        List<String> out = new ArrayList<>();
        if (active == null) {
            return out;
        }
        if (active.key().equals("accueil")) {
            out.add("Berskar");
            return out;
        }
        out.add("Accueil");
        String group = Nav.groupOf(active);
        if (!group.isEmpty() && !group.equalsIgnoreCase(active.label())) {
            out.add(group);
        }
        out.add(active.label());
        return out;
    }

    private void header(UiGraphics g, Paint p, double mouseX, double mouseY) {
        float x = mainLeft();
        float y = TOP;
        List<String> crumbs = crumbs();
        if (!crumbs.isEmpty()) {
            float cursor = x;
            for (int index = 0; index < crumbs.size(); index++) {
                String part = crumbs.get(index);
                boolean last = index == crumbs.size() - 1;
                float partWidth = g.width(Ui.BODY, part, 5.0f);
                boolean link = !last && index == 0 && part.equals("Accueil");
                boolean backLink = !last && index > 0 && back;
                boolean over = (link || backLink) && mouseX >= cursor && mouseX < cursor + partWidth
                        && mouseY >= y - 1 && mouseY < y + 6;
                g.text(Ui.BODY, part, cursor, y, 5.0f, last ? Ui.Theme.TEXT2 : over ? Ui.Theme.TEXT : Ui.Theme.MUTED);
                if (link) {
                    hits.add(new Hit(cursor, y - 1, partWidth, 7, () -> {
                        UiScreen.click(1.0f);
                        UiClient.send(view, "nav", "hub", 0);
                    }));
                } else if (backLink) {
                    hits.add(new Hit(cursor, y - 1, partWidth, 7, () -> {
                        UiScreen.click(1.0f);
                        UiClient.send(view, "back", "", 0);
                    }));
                }
                cursor += partWidth;
                if (!last) {
                    g.text(Ui.BODY, "›", cursor + 2.3f, y, 5.0f, Ui.Theme.DIM);
                    cursor += 7.0f;
                }
            }
            y += 8.3f;
        }
        // Le bouton de fermeture et les chiffres, a droite.
        float right = mainRight();
        float closeX = right - CLOSE;
        boolean overClose = mouseX >= closeX && mouseY >= TOP && mouseX < right && mouseY < TOP + CLOSE;
        float hover = closeHover.toward(overClose ? 1.0f : 0.0f);
        p.framed(closeX, TOP, CLOSE, CLOSE, 4.7f, Paint.mix(Ui.Theme.CARD, Ui.Theme.RED, hover),
                Paint.mix(0xFF2E3035, Ui.Theme.RED, hover), 1);
        if (hover > 0.05f) {
            p.glow(closeX + CLOSE / 2, TOP + CLOSE / 2, 16, Paint.alpha(Ui.Theme.RED, 0.35f * hover));
        }
        g.texture(Ui.CLOSE, Math.round(closeX + CLOSE / 2 - 3.5f), Math.round(TOP + CLOSE / 2 - 3.5f), 7, 7, 32, 32);
        hits.add(new Hit(closeX, TOP, CLOSE, CLOSE, () -> {
            UiScreen.click(1.0f);
            Minecraft.getInstance().gui.setScreen(null);
        }));
        float chipRight = closeX - 4.0f;
        if (stats != null) {
            for (int index = stats.size() - 1; index >= 0; index--) {
                MenuView.Stat stat = stats.get(index);
                float valueWidth = g.width(Ui.WIDE, stat.value(), 7.0f);
                float labelWidth = g.width(Ui.PIXEL, stat.label(), 3.3f);
                float chipWidth = Math.max(valueWidth, labelWidth) + 13.4f;
                float chipX = chipRight - chipWidth;
                p.framed(chipX, TOP - 2.0f, chipWidth, 21.3f, 4.7f, Ui.Theme.CARD, 0xFF2E3035, 1);
                g.text(Ui.PIXEL, stat.label(), chipX + 6.7f, TOP + 3.6f, 3.3f, Ui.Theme.MUTED);
                g.text(Ui.WIDE, stat.value(), chipX + 6.7f, TOP + 9.4f, 7.0f,
                        stat.color() == 0xFFFFFFFF ? Ui.Theme.TEXT : stat.color());
                chipRight = chipX - 4.0f;
            }
        }
        // Le titre, retreci s'il deborde jusqu'aux chiffres.
        String upper = title == null ? "" : title.toUpperCase(Locale.ROOT);
        float room = chipRight - x - 6.0f;
        float size = 19.3f;
        while (size > 11.0f && g.width(Ui.TITLE, upper, size) > room) {
            size -= 0.3f;
        }
        g.textShaded(Ui.TITLE, g.fit(Ui.TITLE, upper, size, room), x, y + (19.3f - size) * 0.6f, size, Ui.Theme.RED,
                Ui.Theme.RED_DD, 1.67f);
        y += 19.3f;
        if (subtitle != null && !subtitle.isEmpty()) {
            g.text(Ui.BODY, g.fit(Ui.BODY, subtitle, 5.33f, room), x, y + 3.3f, 5.33f, Ui.Theme.MUTED);
            y += 8.6f;
        }
        if (tabs != null && !tabs.isEmpty()) {
            tabs(g, p, x, y + 7.3f, mouseX, mouseY);
        }
    }

    private void tabs(UiGraphics g, Paint p, float x, float y, double mouseX, double mouseY) {
        float cursor = x + 2.0f;
        List<float[]> boxes = new ArrayList<>();
        for (MenuView.Tab tab : tabs) {
            float labelWidth = g.width(Ui.BOLD, tab.label(), 5.33f);
            float countWidth = tab.count().isEmpty() ? 0.0f : g.width(Ui.PIXEL, tab.count(), 3.6f) + 3.3f;
            float tabWidth = labelWidth + countWidth + 13.4f;
            boxes.add(new float[] {cursor, tabWidth});
            cursor += tabWidth + 2.7f;
        }
        float containerWidth = cursor - x - 0.7f;
        p.framed(x, y, containerWidth, 19.3f, 5.3f, Ui.Theme.INSET, 0xFF24262A, 1);
        for (int index = 0; index < tabs.size(); index++) {
            MenuView.Tab tab = tabs.get(index);
            float tabX = boxes.get(index)[0];
            float tabWidth = boxes.get(index)[1];
            float tabY = y + 2.0f;
            boolean over = mouseX >= tabX && mouseY >= tabY && mouseX < tabX + tabWidth && mouseY < tabY + 15.3f;
            float hover = hovers.computeIfAbsent("tab:" + index, key -> new Anim.Value(0.0f, 16.0f))
                    .toward(over ? 1.0f : 0.0f);
            if (tab.active()) {
                p.shadow(tabX + 2, tabY + 4, tabWidth - 4, 11.3f, 5.0f, Paint.alpha(Ui.Theme.RED, 0.28f));
                p.roundGradient(tabX, tabY, tabWidth, 15.3f, 3.7f, Ui.Theme.RED_LIGHT, Ui.Theme.RED_D);
                p.rect(tabX + 3.7f, tabY + 0.34f, tabWidth - 7.4f, 0.34f, 0x38FFFFFF);
            } else if (hover > 0.01f) {
                p.round(tabX, tabY, tabWidth, 15.3f, 3.7f, Paint.alpha(0xFFFFFFFF, 0.05f * hover));
            }
            int color = tab.active() ? Ui.Theme.TEXT : Paint.mix(Ui.Theme.TEXT2, Ui.Theme.TEXT, hover);
            g.text(Ui.BOLD, tab.label(), tabX + 6.7f, tabY + 5.0f, 5.33f, color);
            if (!tab.count().isEmpty()) {
                float labelWidth = g.width(Ui.BOLD, tab.label(), 5.33f);
                g.text(Ui.PIXEL, tab.count(), tabX + 6.7f + labelWidth + 3.3f, tabY + 6.0f, 3.6f,
                        tab.active() ? 0xCCFFFFFF : Ui.Theme.MUTED);
            }
            if (!tab.active()) {
                hits.add(new Hit(tabX, tabY, tabWidth, 15.3f, () -> {
                    UiScreen.click(1.0f);
                    UiClient.send(view, "card", tab.action(), 0);
                }));
            }
        }
    }

    /**
     * Une pastille : fond de couleur, texte en police pixel, halo. {@code right} est
     * son bord droit.
     */
    public static float pill(UiGraphics g, Paint p, String text, float right, float y, int color, boolean glow) {
        float textWidth = g.width(Ui.PIXEL, text, 3.3f);
        float width = Math.max(8.0f, textWidth + 5.4f);
        float x = right - width;
        if (glow) {
            p.glow(x + width / 2, y + 4.0f, width / 2 + 5.0f, Paint.alpha(color, 0.45f));
        }
        p.round(x, y, width, 8.0f, 4.0f, color);
        boolean dark = color == Ui.Theme.YELLOW;
        g.text(Ui.PIXEL, text, x + (width - textWidth) / 2.0f, y + 2.6f, 3.3f, dark ? 0xFF2A1E00 : Ui.Theme.TEXT);
        return width;
    }

    // -- Clics --------------------------------------------------------------------------

    /** Un clic sur la coque ; rend {@code true} s'il est consomme. */
    public boolean click(double mouseX, double mouseY) {
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }
}
