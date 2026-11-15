package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Popup;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L'ecran des menus generiques ({@link MenuView}), dans la coque de la v2 : barre
 * laterale, en-tete, puis les sections de cartes dans une zone qui defile.
 *
 * <p>Les cartes entrent en cascade a l'ouverture, se soulevent au survol, et leurs
 * barres se remplissent ; une carte qui pose une question ouvre d'abord une
 * fenetre de confirmation.
 */
public class MenuScreen extends UiScreen implements ViewScreen<MenuView> {

    private static final float GAP = 4.7f;
    private static final float ROW = 23.3f;
    private static final float AGENDA_ROW = 25.3f;
    private static final float ROW_GAP = 2.7f;
    private static final float HEADER = 14.0f;
    private static final float SECTION_GAP = 8.7f;
    private static final float SCROLLBAR = 8.7f;
    /** Le panneau de gauche d'une fiche (metier) : sa largeur, et l'espace avant le contenu. */
    private static final float HERO_WIDTH = 126.7f;
    private static final float HERO_GAP = 8.0f;
    /** La rangee « Aller a », au-dessus du contenu. */
    private static final float ANCHOR_ROW = 15.3f;
    private static final float ANCHOR_HEIGHT = 11.3f;

    private MenuView view;
    private final Shell shell;
    private Content content;
    private final double opened = Anim.now();

    public MenuScreen(MenuView view) {
        super(Component.literal(view.title()), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        this.shell = new Shell(view.view());
        header();
    }

    private void header() {
        shell.header(view.title(), view.subtitle(), view.stats(), view.tabs(), view.back());
    }

    @Override
    public Identifier viewId() {
        return view.view();
    }

    @Override
    public void accept(MenuView next) {
        // Une autre page (autre titre, autre onglet) repart du haut ; la meme page mise a
        // jour garde sa position.
        boolean samePage = next.title().equals(view.title()) && activeTab(next).equals(activeTab(view));
        this.view = next;
        header();
        content.reshape();
        content.layout();
        if (!samePage) {
            content.resetScroll();
        }
        content.clamp();
    }

    private static String activeTab(MenuView view) {
        for (MenuView.Tab tab : view.tabs()) {
            if (tab.active()) {
                return tab.action();
            }
        }
        return "";
    }

    @Override
    protected void build() {
        content = add(new Content());
        content.reshape();
        content.layout();
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        shell.draw(g, g.paint(), mouseX, mouseY);
        if (view.hero() != null) {
            hero(g, g.paint(), view.hero());
        }
        if (anchored()) {
            anchors(g, g.paint(), mouseX, mouseY);
        }
    }

    @Override
    protected boolean backgroundClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && anchored()) {
            for (AnchorHit hit : anchorHits) {
                if (mouseX >= hit.x() && mouseX < hit.x() + hit.width() && mouseY >= hit.y()
                        && mouseY < hit.y() + ANCHOR_HEIGHT) {
                    UiScreen.click(1.1f);
                    content.scrollTo(hit.target());
                    return true;
                }
            }
        }
        return button == 0 && shell.click(mouseX, mouseY);
    }

    private boolean anchored() {
        return (view.flags() & MenuView.ANCHORS) != 0;
    }

    /** Une pastille « Aller a » dessinee a cette image, et la hauteur de contenu ou elle mene. */
    private record AnchorHit(float x, float y, float width, float target) {
    }

    private final List<AnchorHit> anchorHits = new ArrayList<>();
    private final Map<String, Anim.Value> anchorHovers = new HashMap<>();

    /**
     * La rangee « Aller a » : une pastille par section ancree, celle qu'on lit en
     * surbrillance. Le contenu defile dessous ; la rangee reste.
     */
    private void anchors(UiGraphics g, Paint p, double mouseX, double mouseY) {
        anchorHits.clear();
        float left = content.contentLeft();
        float y = shell.contentTop();
        float cursor = left;
        g.text(Ui.PIXEL, "ALLER A", cursor, y + 3.9f, 3.3f, Ui.Theme.MUTED);
        cursor += g.width(Ui.PIXEL, "ALLER A", 3.3f) + 4.7f;
        String reading = content.reading();
        for (Content.Spot spot : content.spots()) {
            String label = spot.label();
            String count = "";
            int bar = label.indexOf('|');
            if (bar >= 0) {
                count = label.substring(bar + 1);
                label = label.substring(0, bar);
            }
            float labelWidth = g.width(Ui.BOLD, label, 4.7f);
            float countWidth = count.isEmpty() ? 0.0f : g.width(Ui.PIXEL, count, 3.3f) + 3.3f;
            float width = labelWidth + countWidth + 9.4f;
            if (cursor + width > shell.mainRight()) {
                break;
            }
            boolean over = mouseX >= cursor && mouseX < cursor + width && mouseY >= y && mouseY < y + ANCHOR_HEIGHT;
            float hover = anchorHovers.computeIfAbsent(spot.label(), key -> new Anim.Value(0.0f, 16.0f))
                    .toward(over ? 1.0f : 0.0f);
            boolean on = spot.label().equals(reading);
            int fill = on ? 0xFF2E3036 : Paint.mix(0xFF1A1B1E, Ui.Theme.CARD_HOVER, hover);
            int edge = on ? 0xFF4A4D55 : Paint.mix(Ui.Theme.EDGE, 0xFF45484F, hover);
            p.framed(cursor, y, width, ANCHOR_HEIGHT, 3.3f, fill, edge, 1);
            g.text(Ui.BOLD, label, cursor + 4.7f, y + 3.4f, 4.7f, on ? Ui.Theme.TEXT : Ui.Theme.TEXT2);
            if (!count.isEmpty()) {
                g.text(Ui.PIXEL, count, cursor + 4.7f + labelWidth + 3.3f, y + 4.1f, 3.3f, Ui.Theme.MUTED);
            }
            anchorHits.add(new AnchorHit(cursor, y, width, spot.y()));
            cursor += width + 3.3f;
        }
    }

    /**
     * Le panneau de gauche d'une fiche : l'embleme, le titre, la pastille, la barre et
     * sa legende, puis les chiffres en cases et quelques lignes.
     */
    private void hero(UiGraphics g, Paint p, MenuView.Hero hero) {
        float x = shell.mainLeft();
        float y = shell.contentTop();
        float width = HERO_WIDTH;
        float height = Shell.HEIGHT - 7.0f - y;
        float center = x + width / 2.0f;
        int accent = 0xFF000000 | hero.accent();
        p.framed(x, y, width, height, 5.3f, 0xFF1C1D20, Ui.Theme.EDGE, 1);
        g.scissor(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
        p.glow(center, y + 26.0f, 72.0f, Paint.alpha(accent, 0.22f));
        g.endScissor();
        g.scissor(Math.round(x), Math.round(y), Math.round(width), 1);
        p.round(x, y, width, height, 5.3f, accent);
        g.endScissor();
        float cursor = y + 9.0f;
        if (!hero.image().isEmpty()) {
            g.texture(Ui.texture(hero.image()), Math.round(center - 31.0f), Math.round(cursor), 62, 64, 256, 266);
            cursor += 66.0f;
        } else {
            p.glow(center, cursor + 16.0f, 26.0f, Paint.alpha(accent, 0.25f));
            g.item(hero.icon(), center - 16.0f, cursor, 32.0f, false);
            cursor += 38.0f;
        }
        float titleSize = 15.3f;
        float room = width - 12.0f;
        while (titleSize > 8.0f && g.width(Ui.TITLE, hero.title(), titleSize) > room) {
            titleSize -= 0.5f;
        }
        float titleWidth = g.width(Ui.TITLE, hero.title(), titleSize);
        g.textShaded(Ui.TITLE, hero.title(), center - titleWidth / 2.0f, cursor, titleSize, Ui.Theme.RED,
                Ui.Theme.RED_DD, 1.0f);
        cursor += titleSize + 4.0f;
        if (!hero.badge().isEmpty()) {
            float badgeWidth = Math.max(8.0f, g.width(Ui.PIXEL, hero.badge(), 3.3f) + 5.4f);
            Shell.pill(g, p, hero.badge(), center + badgeWidth / 2.0f, cursor, accent, true);
            cursor += 13.0f;
        }
        if (hero.progress() >= 0) {
            float barX = x + 8.0f;
            float barWidth = width - 16.0f;
            float fill = barWidth * Math.max(0, Math.min(1000, hero.progress())) / 1000.0f
                    * Anim.easeOut(Anim.progress(opened + 0.1d, 0.7d));
            p.round(barX, cursor, barWidth, 2.7f, 1.35f, 0xFF2C2E33);
            if (fill > 0.5f) {
                p.glow(barX + fill, cursor + 1.35f, 6.0f, Paint.alpha(accent, 0.5f));
                p.round(barX, cursor, Math.max(2.7f, fill), 2.7f, 1.35f, accent);
            }
            cursor += 5.3f;
            if (!hero.legend().isEmpty()) {
                int bar = hero.legend().indexOf('|');
                String leftText = bar < 0 ? hero.legend() : hero.legend().substring(0, bar);
                String rightText = bar < 0 ? "" : hero.legend().substring(bar + 1);
                g.text(Ui.PIXEL, leftText, barX, cursor, 3.3f, Ui.Theme.MUTED);
                if (!rightText.isEmpty()) {
                    g.right(Ui.PIXEL, rightText, barX + barWidth, cursor, 3.3f, Ui.Theme.MUTED);
                }
                cursor += 6.0f;
            }
            cursor += 3.0f;
        }
        float boxWidth = (width - 16.0f - 4.0f) / 2.0f;
        for (int index = 0; index < hero.stats().size(); index++) {
            MenuView.Stat stat = hero.stats().get(index);
            float boxX = x + 8.0f + (index % 2) * (boxWidth + 4.0f);
            float boxY = cursor + (index / 2) * 22.0f;
            p.framed(boxX, boxY, boxWidth, 19.3f, 3.3f, 0xFF16171A, 0xFF2A2C31, 1);
            g.text(Ui.PIXEL, g.fit(Ui.PIXEL, stat.label(), 3.3f, boxWidth - 8.0f), boxX + 4.7f, boxY + 3.6f, 3.3f,
                    Ui.Theme.MUTED);
            int color = stat.color() == 0xFFFFFFFF ? Ui.Theme.TEXT : 0xFF000000 | stat.color();
            g.text(Ui.WIDE, g.fit(Ui.WIDE, stat.value(), 6.0f, boxWidth - 8.0f), boxX + 4.7f, boxY + 9.6f, 6.0f, color);
        }
        cursor += ((hero.stats().size() + 1) / 2) * 22.0f + 3.0f;
        for (String line : hero.lines()) {
            if (cursor > y + height - 8.0f) {
                break;
            }
            if (line.startsWith("#")) {
                g.text(Ui.PIXEL, line.substring(1), x + 8.0f, cursor + 1.0f, 3.3f, Ui.Theme.MUTED);
                cursor += 7.0f;
                continue;
            }
            p.round(x + 8.6f, cursor + 1.8f, 1.6f, 1.6f, 0.4f, accent);
            g.text(Ui.BODY, g.fit(Ui.BODY, line, 4.7f, width - 22.0f), x + 13.0f, cursor, 4.7f, Ui.Theme.TEXT2);
            cursor += 7.3f;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popup == null && event.key() == GLFW.GLFW_KEY_BACKSPACE && view.back()) {
            UiClient.send(view.view(), "back", "", 0);
            return true;
        }
        return super.keyPressed(event);
    }

    /** Une carte placee, en coordonnees de contenu. */
    private record Placed(MenuView.Card card, int layout, float x, float y, float width, float height,
                          List<TextLine> text, int index) {
    }

    /** Un intertitre place : le titre, son compteur, son losange. */
    private record Header(String title, String count, String icon, float x, float y, float width) {
    }

    // -- Les cartes de texte -----------------------------------------------------------

    private static final float TEXT_SIZE = 4.7f;
    private static final float TEXT_LEADING = 6.6f;
    private static final float TEXT_GAP = 3.0f;
    private static final float HEADING_SIZE = 6.0f;
    private static final int NORMAL_LINE = 0;
    private static final int BULLET_LINE = 1;
    private static final int COMMAND_LINE = 2;
    private static final int TIP_LINE = 3;

    /** Une ligne coupee d'un paragraphe : son texte, sa sorte, et si elle ouvre le paragraphe. */
    private record TextLine(String text, int kind, boolean first, float y) {
    }

    private static float textLeft(MenuView.Card card) {
        return card.icon().isEmpty() ? 9.0f : 30.0f;
    }

    private static List<TextLine> wrapText(MenuView.Card card, float width, float[] height) {
        Font font = Minecraft.getInstance().font;
        List<TextLine> out = new ArrayList<>();
        float left = textLeft(card);
        float y = 6.5f;
        if (!card.title().isEmpty()) {
            y += 10.0f;
        }
        for (int index = 0; index < card.lines().size(); index++) {
            String paragraph = card.lines().get(index);
            int kind = NORMAL_LINE;
            String body = paragraph;
            if (paragraph.startsWith("- ")) {
                kind = BULLET_LINE;
                body = paragraph.substring(2);
            } else if (paragraph.startsWith("/")) {
                kind = COMMAND_LINE;
            } else if (paragraph.startsWith("! ")) {
                kind = TIP_LINE;
                body = paragraph.substring(2);
            }
            float indent = kind == BULLET_LINE || kind == TIP_LINE ? 7.0f : 0.0f;
            float room = width - left - 9.0f - indent;
            List<String> lines = UiGraphics.wrap(font, kind == COMMAND_LINE ? Ui.BOLD : Ui.BODY, body, TEXT_SIZE, room);
            for (int line = 0; line < lines.size(); line++) {
                out.add(new TextLine(lines.get(line), kind, line == 0, y));
                y += TEXT_LEADING;
            }
            if (index < card.lines().size() - 1) {
                y += TEXT_GAP;
            }
        }
        height[0] = y + 5.0f;
        return out;
    }

    /** La place du nom dans une carte de grille de cette largeur, pastille deduite. */
    private static float titleRoom(MenuView.Card card, float cardWidth) {
        Font font = Minecraft.getInstance().font;
        float badge = card.badge().isEmpty() ? (card.state() == MenuView.DONE ? 11.0f : 0.0f)
                : Math.max(8.0f, UiGraphics.measure(font, Ui.PIXEL, card.badge(), 3.3f) + 5.4f) + 3.0f;
        return cardWidth - 6.0f - 19.3f - 4.7f - 5.0f - badge;
    }

    /** Un nom trop long tient sur deux lignes, si la carte n'a qu'une ligne de texte. */
    private static boolean twoLineTitle(MenuView.Card card, float cardWidth) {
        return card.lines().size() <= 1 && UiGraphics.measure(Minecraft.getInstance().font, Ui.BOLD, card.title(),
                6.3f) > titleRoom(card, cardWidth);
    }

    /** La hauteur d'une carte de grille : icone, deux lignes, barre et legende. */
    private static float gridHeight(MenuView.Card card, float cardWidth) {
        int lines = Math.min(2, card.lines().size());
        float text = 1.5f + 6.3f + (lines >= 1 ? 6.7f : 0.0f) + (lines >= 2 ? 5.7f : 0.0f)
                + (twoLineTitle(card, cardWidth) ? 7.0f : 0.0f);
        float height = 6.0f + Math.max(19.3f, text) + 5.3f;
        if (card.progress() >= 0) {
            height += 4.7f + 2.7f;
            if (card.lines().size() >= 3) {
                height += 6.0f;
            }
        } else if (card.lines().size() >= 3) {
            height += 5.7f;
        }
        return height;
    }

    /** La zone de contenu : mise en page, defilement, dessin et clics. */
    private final class Content extends UiElement {
        private final List<Placed> placed = new ArrayList<>();
        private final List<Header> headers = new ArrayList<>();
        private final Map<Integer, Anim.Value> hovers = new HashMap<>();
        private final Anim.Value scroll = new Anim.Value(0.0f, 18.0f);
        private float total;
        private float target;

        Content() {
            super(0, 0, 1, 1);
        }

        /** Une section ancree : le libelle de sa pastille et sa hauteur dans le contenu. */
        record Spot(String label, float y) {
        }

        private final List<Spot> spots = new ArrayList<>();

        List<Spot> spots() {
            return spots;
        }

        /** Le bord gauche du contenu, panneau de fiche deduit. */
        float contentLeft() {
            return shell.mainLeft() + (view.hero() != null ? HERO_WIDTH + HERO_GAP : 0.0f);
        }

        void reshape() {
            float left = contentLeft();
            float top = shell.contentTop() + (anchored() ? ANCHOR_ROW : 0.0f);
            x = Math.round(left);
            y = Math.round(top);
            width = Math.round(shell.mainRight() - left);
            height = Math.round(Shell.HEIGHT - 7.0f - top);
        }

        void resetScroll() {
            target = 0;
            scroll.set(0);
        }

        /** Fait defiler jusqu'a une hauteur de contenu (une section ancree). */
        void scrollTo(float contentY) {
            target = contentY;
            clamp();
        }

        /** La section ancree qu'on lit : la derniere dont le haut est passe sous le bord. */
        String reading() {
            String current = spots.isEmpty() ? "" : spots.getFirst().label();
            float offset = scroll.get();
            for (Spot spot : spots) {
                if (spot.y() <= offset + 6.0f) {
                    current = spot.label();
                }
            }
            return current;
        }

        void layout() {
            placed.clear();
            headers.clear();
            spots.clear();
            float usable = width - SCROLLBAR;
            float cursor = 0;
            List<MenuView.Section> sections = view.sections();
            int index = 0;
            while (index < sections.size()) {
                MenuView.Section section = sections.get(index);
                boolean half = (section.layout() & MenuView.HALF) != 0;
                if (half && index + 1 < sections.size() && (sections.get(index + 1).layout() & MenuView.HALF) != 0) {
                    float leftWidth = (usable - 8.0f) * 1.35f / 2.35f;
                    float rightWidth = usable - 8.0f - leftWidth;
                    float leftHeight = place(section, 0, cursor, leftWidth);
                    float rightHeight = place(sections.get(index + 1), leftWidth + 8.0f, cursor, rightWidth);
                    cursor += Math.max(leftHeight, rightHeight) + SECTION_GAP;
                    index += 2;
                } else {
                    cursor += place(section, 0, cursor, usable) + SECTION_GAP;
                    index++;
                }
            }
            total = cursor - SECTION_GAP + 4.0f;
        }

        /** Place une section dans la colonne (x, largeur) ; rend la hauteur utilisee. */
        private float place(MenuView.Section section, float left, float top, float columnWidth) {
            float cursor = top;
            if (!section.anchor().isEmpty()) {
                spots.add(new Spot(section.anchor(), top));
            }
            if (!section.title().isEmpty()) {
                String title = section.title();
                String count = "";
                int bar = title.indexOf('|');
                if (bar >= 0) {
                    count = title.substring(bar + 1);
                    title = title.substring(0, bar);
                }
                headers.add(new Header(title, count, section.icon(), left, cursor, columnWidth));
                cursor += HEADER;
            }
            List<MenuView.Card> cards = section.cards();
            int layout = section.layout() & 0x0F;
            switch (layout) {
                case MenuView.ROWS, MenuView.AGENDA -> {
                    float rowHeight = layout == MenuView.AGENDA ? AGENDA_ROW : ROW;
                    for (MenuView.Card card : cards) {
                        placed.add(new Placed(card, layout, left, cursor, columnWidth, rowHeight, List.of(),
                                placed.size()));
                        cursor += rowHeight + ROW_GAP;
                    }
                    if (!cards.isEmpty()) {
                        cursor -= ROW_GAP;
                    }
                }
                case MenuView.TEXT -> {
                    for (MenuView.Card card : cards) {
                        float[] cardHeight = new float[1];
                        List<TextLine> lines = wrapText(card, columnWidth, cardHeight);
                        float h = Math.max(20.0f, cardHeight[0]);
                        placed.add(new Placed(card, layout, left, cursor, columnWidth, h, lines, placed.size()));
                        cursor += h + ROW_GAP;
                    }
                    if (!cards.isEmpty()) {
                        cursor -= ROW_GAP;
                    }
                }
                case MenuView.TILES -> {
                    float size = 21.3f;
                    float pitch = size + 2.7f;
                    int columns = Math.max(1, (int) ((columnWidth + 2.7f) / pitch));
                    for (int index = 0; index < cards.size(); index++) {
                        int column = index % columns;
                        int row = index / columns;
                        placed.add(new Placed(cards.get(index), layout, left + column * pitch, cursor + row * pitch,
                                size, size, List.of(), placed.size()));
                    }
                    int rows = (cards.size() + columns - 1) / columns;
                    cursor += rows * pitch - (rows > 0 ? 2.7f : 0.0f);
                }
                default -> {
                    int columns = columnWidth > 330 ? 4 : columnWidth > 220 ? 3 : 2;
                    // Trois cartes : trois colonnes, sans case vide au bout de la rangee. Et
                    // des noms trop longs pour quatre colonnes passent a trois plutot que
                    // d'etre coupes.
                    if (columns == 4 && (cards.size() == 3
                            || !namesFit(cards, (columnWidth - GAP * 3) / 4))) {
                        columns = 3;
                    }
                    float cardWidth = (columnWidth - GAP * (columns - 1)) / columns;
                    for (int start = 0; start < cards.size(); start += columns) {
                        float rowHeight = 0;
                        for (int index = start; index < Math.min(cards.size(), start + columns); index++) {
                            rowHeight = Math.max(rowHeight, gridHeight(cards.get(index), cardWidth));
                        }
                        for (int index = start; index < Math.min(cards.size(), start + columns); index++) {
                            int column = index - start;
                            placed.add(new Placed(cards.get(index), MenuView.GRID, left + column * (cardWidth + GAP),
                                    cursor, cardWidth, rowHeight, List.of(), placed.size()));
                        }
                        cursor += rowHeight + GAP;
                    }
                    if (!cards.isEmpty()) {
                        cursor -= GAP;
                    }
                }
            }
            return cursor - top;
        }

        /** Le nom de chaque carte tient-il, pastille comprise, dans cette largeur ? */
        private boolean namesFit(List<MenuView.Card> cards, float cardWidth) {
            for (MenuView.Card card : cards) {
                float badge = card.badge().isEmpty() ? (card.state() == MenuView.DONE ? 11.0f : 0.0f)
                        : Math.max(8.0f, UiGraphics.measure(font, Ui.PIXEL, card.badge(), 3.3f) + 5.4f) + 3.0f;
                float needed = 6.0f + 19.3f + 4.7f + UiGraphics.measure(font, Ui.BOLD, card.title(), 6.3f) + badge
                        + 5.0f;
                if (needed > cardWidth) {
                    return false;
                }
            }
            return true;
        }

        void clamp() {
            target = Math.max(0, Math.min(target, Math.max(0, total - height)));
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            float offset = scroll.toward(target);
            g.scissor(x - 6, y - 1, width + 12, height + 1);
            for (Header header : headers) {
                float headerY = y + header.y() - offset;
                if (headerY > y + height || headerY + HEADER < y - 2) {
                    continue;
                }
                header(g, p, header, x + header.x(), headerY);
            }
            Placed hovered = null;
            boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            float bars = Anim.easeOut(Anim.progress(opened + 0.12d, 0.7d));
            for (Placed card : placed) {
                float appear = Anim.easeOut(Anim.progress(opened + Math.min(0.3d, 0.025d * card.index()), 0.28d));
                float cardY = y + card.y() - offset + 4.0f * (1.0f - appear);
                if (cardY > y + height + 4 || cardY + card.height() < y - 4) {
                    continue;
                }
                float cardX = x + card.x();
                boolean over = inside && mouseX >= cardX && mouseX < cardX + card.width() && mouseY >= cardY
                        && mouseY < cardY + card.height();
                if (over) {
                    hovered = card;
                }
                boolean clickable = !card.card().action().isEmpty() || !card.card().rightAction().isEmpty();
                float hover = hovers.computeIfAbsent(card.index(), key -> new Anim.Value(0.0f, 16.0f))
                        .toward(over && clickable ? 1.0f : over ? 0.4f : 0.0f);
                switch (card.layout()) {
                    case MenuView.TEXT -> text(g, p, card, cardX, cardY, hover);
                    case MenuView.ROWS -> row(g, p, card.card(), cardX, cardY, card.width(), card.height(), hover,
                            bars);
                    case MenuView.AGENDA -> agenda(g, p, card.card(), cardX, cardY, card.width(), card.height(),
                            hover);
                    case MenuView.TILES -> tile(g, p, card.card(), cardX, cardY, card.width(), hover, bars);
                    default -> grid(g, p, card.card(), cardX, cardY, card.width(), card.height(), hover, bars);
                }
            }
            g.endScissor();
            if (total > height) {
                float barX = x + width - 2.0f;
                p.round(barX, y, 2.0f, height - 8.0f, 1.0f, 0xFF24262A);
                float track = height - 8.0f;
                float thumb = Math.max(12.0f, track * height / total);
                float thumbY = y + (track - thumb) * offset / Math.max(1.0f, total - height);
                p.round(barX, thumbY, 2.0f, thumb, 1.0f, 0xFF5A5D64);
            }
            if (hovered != null && (hovered.layout() != MenuView.TEXT || !hovered.card().action().isEmpty())) {
                tooltip(g, hovered.card());
            }
        }

        private void header(UiGraphics g, Paint p, Header header, float headerX, float headerY) {
            float cursor = headerX;
            if (!header.icon().isEmpty()) {
                g.texture(Ui.texture("section_" + header.icon()), Math.round(cursor), Math.round(headerY), 9, 9, 56,
                        56);
                cursor += 12.7f;
            }
            g.text(Ui.WIDE, header.title(), cursor, headerY + 1.7f, 6.3f, Ui.Theme.TEXT);
            cursor += g.width(Ui.WIDE, header.title(), 6.3f) + 4.0f;
            if (!header.count().isEmpty()) {
                g.text(Ui.PIXEL, header.count(), cursor, headerY + 3.0f, 3.7f, Ui.Theme.MUTED);
                cursor += g.width(Ui.PIXEL, header.count(), 3.7f) + 4.0f;
            }
            float lineWidth = headerX + header.width() - cursor;
            if (lineWidth > 4) {
                p.gradientH(cursor, headerY + 4.2f, lineWidth, 0.34f, 0xFF34363C, 0x0034363C);
            }
        }

        private void tooltip(UiGraphics g, MenuView.Card card) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(card.title()).withColor(card.state() == MenuView.DONE ? 0x55FF55 : 0xFFFFFF));
            for (String line : card.lines()) {
                lines.add(Component.literal(line.replace('|', ' ')).withColor(0xC1C1C1));
            }
            for (String line : card.tooltip()) {
                lines.add(line.isEmpty() ? Component.empty() : Component.literal(line).withColor(0x8B8E95));
            }
            if (!card.action().isEmpty() || !card.rightAction().isEmpty()) {
                lines.add(Component.empty());
                if (!card.action().isEmpty()) {
                    lines.add(Component.literal("Clic gauche : " + verb(card.action())).withColor(0x2BC4C4));
                }
                if (!card.rightAction().isEmpty()) {
                    lines.add(Component.literal("Clic droit : " + verb(card.rightAction())).withColor(0x2BC4C4));
                }
            }
            g.tooltip(lines);
        }

        private String verb(String action) {
            int bar = action.indexOf('|');
            return bar < 0 ? "ouvrir" : action.substring(bar + 1);
        }

        private int accentOf(MenuView.Card card) {
            if (card.state() == MenuView.DONE) {
                return Ui.Theme.GREEN;
            }
            if (card.state() == MenuView.DISABLED) {
                return 0xFF55575D;
            }
            return card.accent() == 0xFF55545C ? 0xFF55575D : card.accent();
        }

        // -- Cartes de grille ----------------------------------------------------------

        private void grid(UiGraphics g, Paint p, MenuView.Card card, float cardX, float cardY, float cardWidth,
                          float cardHeight, float hover, float bars) {
            boolean off = card.state() == MenuView.DISABLED;
            int accent = accentOf(card);
            float lift = -0.7f * hover;
            float top = cardY + lift;
            if (hover > 0.01f) {
                p.shadow(cardX + 3, top + 5, cardWidth - 6, cardHeight - 5, 8, Paint.alpha(0xFF000000, 0.35f * hover));
            }
            int fill = off ? 0xFF1D1F22 : Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover);
            int edge = Paint.mix(Ui.Theme.EDGE, Paint.mix(Ui.Theme.EDGE, accent, 0.6f), hover);
            p.framed(cardX, top, cardWidth, cardHeight, 5.3f, fill, edge, 1);
            // Le lisere du haut suit les coins arrondis ; le halo reste dans la carte.
            g.scissor(Math.round(cardX), Math.round(top), Math.round(cardWidth), Math.round(cardHeight));
            if (!off) {
                p.glow(cardX + cardWidth - 10.0f, top + 10.0f, 22.0f, Paint.alpha(accent, 0.14f + 0.08f * hover));
            }
            g.endScissor();
            g.scissor(Math.round(cardX), Math.round(top), Math.round(cardWidth), 1);
            p.round(cardX, top, cardWidth, cardHeight, 5.3f, Paint.alpha(accent, off ? 0.5f : 0.95f));
            g.endScissor();
            // L'icone dans sa tuile teintee.
            float tile = 19.3f;
            float tileX = cardX + 6.0f;
            float tileY = top + 6.0f;
            int tileFill = Paint.mix(0xFF1A1B1E, accent, off ? 0.06f : 0.16f);
            p.framed(tileX, tileY, tile, tile, 4.7f, tileFill, Paint.mix(tileFill, accent, off ? 0.15f : 0.35f), 1);
            g.item(card.icon(), tileX + (tile - 12.0f) / 2.0f, tileY + (tile - 12.0f) / 2.0f, 12.0f, false);
            float textX = tileX + tile + 4.7f;
            float badgeWidth = 0;
            if (card.state() == MenuView.DONE && card.badge().isEmpty()) {
                // Acquis : une coche verte a la place de la pastille.
                float checkX = cardX + cardWidth - 9.0f;
                float checkY = top + 9.3f;
                p.glow(checkX, checkY, 8.0f, Paint.alpha(Ui.Theme.GREEN, 0.35f));
                p.dot(checkX, checkY, 4.0f, Ui.Theme.GREEN);
                p.check(checkX, checkY, 5.3f, 0xFFFFFFFF);
                badgeWidth = 11.0f;
            }
            if (!card.badge().isEmpty()) {
                int badgeColor = card.state() == MenuView.DONE ? Ui.Theme.GREEN
                        : card.badge().startsWith("PRET") ? Ui.Theme.YELLOW : off ? 0xFF3A3C42 : Ui.Theme.RED;
                float pulse = card.badge().startsWith("PRET") ? Anim.pulse(1.6d) : 0.0f;
                badgeWidth = Shell.pill(g, p, card.badge(), cardX + cardWidth - 4.7f, top + 5.3f,
                        Paint.mix(badgeColor, 0xFFFFFFFF, 0.12f * pulse), !off) + 3.0f;
            }
            float room = cardX + cardWidth - 5.0f - badgeWidth - textX;
            List<String> lines = card.lines();
            boolean twoLines = twoLineTitle(card, cardWidth);
            float textY = top + 7.5f;
            if (lines.isEmpty()) {
                textY = top + 6.0f + (tile - (twoLines ? 13.3f : 6.3f)) / 2.0f;
            } else if (lines.size() == 1 && !twoLines) {
                textY = top + 9.0f;
            }
            int titleColor = off ? Ui.Theme.MUTED : Ui.Theme.TEXT;
            if (twoLines) {
                List<String> wrapped = UiGraphics.wrap(font, Ui.BOLD, card.title(), 6.3f, room);
                g.text(Ui.BOLD, g.fit(Ui.BOLD, wrapped.getFirst(), 6.3f, room), textX, textY, 6.3f, titleColor);
                String rest = String.join(" ", wrapped.subList(1, wrapped.size()));
                g.text(Ui.BOLD, g.fit(Ui.BOLD, rest, 6.3f, cardX + cardWidth - 5.0f - textX), textX, textY + 7.0f, 6.3f,
                        titleColor);
                textY += 7.0f;
            } else {
                g.text(Ui.BOLD, g.fit(Ui.BOLD, card.title(), 6.3f, room), textX, textY, 6.3f, titleColor);
            }
            float lineRoom = cardX + cardWidth - 5.0f - textX;
            if (!lines.isEmpty()) {
                g.text(Ui.BODY, g.fit(Ui.BODY, lines.get(0), 5.0f, lineRoom), textX, textY + 8.0f, 5.0f,
                        off ? Ui.Theme.DIM : Ui.Theme.TEXT2);
            }
            if (lines.size() >= 2) {
                g.text(Ui.BODY, g.fit(Ui.BODY, lines.get(1), 4.7f, lineRoom), textX, textY + 14.0f, 4.7f,
                        Ui.Theme.MUTED);
            }
            float below = top + 6.0f + Math.max(tile, 1.5f + 6.3f + (lines.isEmpty() ? 0 : 6.7f)
                    + (lines.size() >= 2 ? 5.7f : 0.0f) + (twoLines ? 7.0f : 0.0f));
            if (card.progress() >= 0) {
                float barY = below + 4.7f;
                bar(p, cardX + 6.0f, barY, cardWidth - 12.0f, 2.7f, card.progress() / 1000.0f * bars, accent, off);
                if (lines.size() >= 3) {
                    String legend = lines.get(2);
                    int bar = legend.indexOf('|');
                    String leftText = bar < 0 ? legend : legend.substring(0, bar);
                    String rightText = bar < 0 ? "" : legend.substring(bar + 1);
                    g.text(Ui.PIXEL, leftText, cardX + 6.0f, barY + 5.3f, 3.3f, Ui.Theme.MUTED);
                    if (!rightText.isEmpty()) {
                        g.right(Ui.PIXEL, rightText, cardX + cardWidth - 6.0f, barY + 5.3f, 3.3f, Ui.Theme.MUTED);
                    }
                }
            } else if (lines.size() >= 3) {
                g.text(Ui.BODY, g.fit(Ui.BODY, lines.get(2), 4.7f, cardWidth - 12.0f), cardX + 6.0f, below + 1.5f,
                        4.7f, Ui.Theme.MUTED);
            }
        }

        /** Une barre arrondie : le creux, et le plein en degrade avec son halo. */
        private void bar(Paint p, float barX, float barY, float barWidth, float barHeight, float progress, int accent,
                         boolean off) {
            p.framed(barX, barY, barWidth, barHeight, barHeight / 2.0f, 0xFF17181B, 0xFF2C2E33, 1);
            float filled = Math.max(0.0f, Math.min(1.0f, progress)) * barWidth;
            if (filled < 0.5f) {
                return;
            }
            if (!off) {
                p.glow(barX + filled, barY + barHeight / 2.0f, 5.0f, Paint.alpha(accent, 0.45f));
            }
            p.round(barX, barY, filled, barHeight, barHeight / 2.0f, Paint.mix(accent, 0xFF000000, 0.3f));
            p.gradientH(barX + barHeight / 2.0f, barY + 0.34f, Math.max(0, filled - barHeight), barHeight - 0.68f,
                    0x00000000 | (accent & 0xFFFFFF), accent);
            p.round(barX + filled - barHeight, barY, barHeight, barHeight, barHeight / 2.0f, accent);
        }

        // -- Lignes ------------------------------------------------------------------------

        private void row(UiGraphics g, Paint p, MenuView.Card card, float rowX, float rowY, float rowWidth,
                         float rowHeight, float hover, float bars) {
            boolean off = card.state() == MenuView.DISABLED;
            boolean done = card.state() == MenuView.DONE;
            int accent = accentOf(card);
            int fill = off ? 0xFF1D1F22 : Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover);
            p.framed(rowX, rowY, rowWidth, rowHeight, 4.7f, fill,
                    Paint.mix(Ui.Theme.EDGE, Paint.mix(Ui.Theme.EDGE, accent, 0.5f), hover), 1);
            p.round(rowX, rowY + 4.0f, 1.34f, rowHeight - 8.0f, 0.67f, accent);
            g.item(card.icon(), rowX + 6.7f, rowY + (rowHeight - 13.3f) / 2.0f, 13.3f, false);
            float textX = rowX + 25.3f;
            float right = rowX + rowWidth - 6.0f;
            if (done) {
                p.dot(right - 5.0f, rowY + rowHeight / 2.0f, 5.0f, Ui.Theme.GREEN);
                p.dot(right - 5.0f, rowY + rowHeight / 2.0f, 4.3f, 0xFF1F3A1C);
                p.check(right - 5.0f, rowY + rowHeight / 2.0f, 6.0f, Ui.Theme.GREEN_L);
                right -= 14.0f;
            } else if (off && !card.badge().isEmpty()) {
                // Le cadenas dit « pas encore » : une ligne grisee sans pastille (une liste
                // vide, un rappel) n'en a pas.
                p.dot(right - 5.0f, rowY + rowHeight / 2.0f, 5.0f, 0xFF2C2E33);
                g.texture(Ui.texture("spawner_lock"), Math.round(right - 8.0f), Math.round(rowY + rowHeight / 2 - 3),
                        6, 6, 32, 32);
                right -= 14.0f;
            }
            if (!card.badge().isEmpty()) {
                if (card.badge().startsWith("NIV") || card.badge().length() > 6) {
                    g.right(Ui.WIDE, card.badge(), right, rowY + rowHeight / 2.0f - 2.6f, 5.3f, Ui.Theme.TEXT2);
                    right -= g.width(Ui.WIDE, card.badge(), 5.3f) + 6.0f;
                } else {
                    int color = done ? Ui.Theme.GREEN : off ? 0xFF3A3C42 : accent;
                    right -= Shell.pill(g, p, card.badge(), right, rowY + rowHeight / 2.0f - 4.0f, color, !off) + 4.0f;
                }
            }
            float textRoom = right - textX;
            if (card.progress() >= 0 && rowWidth > 180) {
                float barWidth = Math.min(110.0f, rowWidth * 0.36f);
                float barX = right - barWidth;
                bar(p, barX, rowY + rowHeight / 2.0f - 1.35f, barWidth, 2.7f, card.progress() / 1000.0f * bars, accent,
                        off);
                textRoom = barX - 6.0f - textX;
            }
            boolean twoLines = !card.lines().isEmpty();
            float nameY = twoLines ? rowY + rowHeight / 2.0f - 6.3f : rowY + rowHeight / 2.0f - 3.0f;
            g.text(Ui.BOLD, g.fit(Ui.BOLD, card.title(), 6.0f, textRoom), textX, nameY, 6.0f,
                    off ? Ui.Theme.MUTED : Ui.Theme.TEXT);
            if (twoLines) {
                g.text(Ui.BODY, g.fit(Ui.BODY, String.join("  ·  ", card.lines()), 4.7f, textRoom), textX,
                        nameY + 7.6f, 4.7f, done ? Ui.Theme.GREEN_L : Ui.Theme.MUTED);
            }
            if (card.progress() >= 0 && rowWidth <= 180) {
                bar(p, textX, rowY + rowHeight - 4.7f, rowWidth - 31.0f, 2.0f, card.progress() / 1000.0f * bars,
                        accent, off);
            }
        }

        private void agenda(UiGraphics g, Paint p, MenuView.Card card, float rowX, float rowY, float rowWidth,
                            float rowHeight, float hover) {
            int accent = accentOf(card);
            p.framed(rowX, rowY, rowWidth, rowHeight, 4.7f, Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover),
                    Ui.Theme.EDGE, 1);
            p.round(rowX, rowY + 4.0f, 1.34f, rowHeight - 8.0f, 0.67f, accent);
            g.text(Ui.WIDE, g.fit(Ui.WIDE, card.badge(), 5.7f, 34.0f), rowX + 6.7f, rowY + rowHeight / 2.0f - 2.8f,
                    5.7f, accent);
            float textX = rowX + 43.3f;
            float room = rowX + rowWidth - 6.0f - textX;
            boolean twoLines = !card.lines().isEmpty();
            float nameY = twoLines ? rowY + rowHeight / 2.0f - 6.3f : rowY + rowHeight / 2.0f - 3.0f;
            g.text(Ui.BOLD, g.fit(Ui.BOLD, card.title(), 6.0f, room), textX, nameY, 6.0f, Ui.Theme.TEXT);
            if (twoLines) {
                g.text(Ui.BODY, g.fit(Ui.BODY, card.lines().get(0), 4.7f, room), textX, nameY + 7.6f, 4.7f,
                        Ui.Theme.MUTED);
            }
        }

        private void tile(UiGraphics g, Paint p, MenuView.Card card, float tileX, float tileY, float size, float hover,
                          float bars) {
            boolean off = card.state() == MenuView.DISABLED;
            boolean done = card.state() == MenuView.DONE;
            int border = done ? Ui.Theme.GREEN : hover > 0.5f ? Ui.Theme.RED
                    : card.accent() != 0xFF55545C ? Paint.mix(0xFF2A2C31, card.accent(), 0.6f) : 0xFF2A2C31;
            if (hover > 0.01f || done) {
                p.glow(tileX + size / 2, tileY + size / 2, size * 0.75f,
                        Paint.alpha(done ? Ui.Theme.GREEN : Ui.Theme.RED, 0.3f * Math.max(hover, done ? 0.6f : 0.0f)));
            }
            p.framed(tileX, tileY, size, size, 3.0f, Paint.mix(0xFF101113, 0xFF1B1C20, hover), border,
                    done || hover > 0.5f ? 2 : 1);
            g.item(card.icon(), tileX + (size - 14.7f) / 2.0f, tileY + (size - 14.7f) / 2.0f - 0.5f, 14.7f, false);
            if (off) {
                p.round(tileX + 1, tileY + 1, size - 2, size - 2, 2.7f, 0x99101014);
            }
            if (card.progress() >= 0) {
                bar(p, tileX + 3.0f, tileY + size - 4.0f, size - 6.0f, 1.6f, card.progress() / 1000.0f * bars,
                        done ? Ui.Theme.GREEN : 0xFF39CF44, off);
            }
            if (!card.badge().isEmpty()) {
                g.right(Ui.WIDE, card.badge(), tileX + size - 1.7f, tileY + 1.7f, 3.6f, Ui.Theme.TEXT);
            }
        }

        private void text(UiGraphics g, Paint p, Placed placed, float cardX, float cardY, float hover) {
            MenuView.Card card = placed.card();
            boolean clickable = !card.action().isEmpty();
            int accent = accentOf(card);
            p.framed(cardX, cardY, placed.width(), placed.height(), 4.7f,
                    clickable ? Paint.mix(0xFF1D1F22, Ui.Theme.CARD_HOVER, hover) : 0xFF1D1F22, Ui.Theme.EDGE, 1);
            p.round(cardX, cardY + 4.0f, 1.34f, placed.height() - 8.0f, 0.67f, accent);
            if (!card.icon().isEmpty()) {
                g.item(card.icon(), cardX + 8.0f, cardY + 6.0f, 16.0f, false);
            }
            float left = cardX + textLeft(card);
            if (!card.title().isEmpty()) {
                g.text(Ui.BOLD, card.title(), left, cardY + 6.5f, HEADING_SIZE, Ui.Theme.TEXT);
            }
            for (TextLine line : placed.text()) {
                float lineY = cardY + line.y();
                switch (line.kind()) {
                    case BULLET_LINE -> {
                        if (line.first()) {
                            p.dot(left + 2.0f, lineY + 2.2f, 1.2f, accent);
                        }
                        g.text(Ui.BODY, line.text(), left + 7.0f, lineY, TEXT_SIZE, Ui.Theme.TEXT2);
                    }
                    case COMMAND_LINE -> g.text(Ui.BOLD, line.text(), left, lineY, TEXT_SIZE, 0xFF55E5FF);
                    case TIP_LINE -> {
                        if (line.first()) {
                            g.text(Ui.BOLD, "!", left + 1.0f, lineY, TEXT_SIZE, Ui.Theme.YELLOW);
                        }
                        g.text(Ui.BODY, line.text(), left + 7.0f, lineY, TEXT_SIZE, 0xFFF3D27A);
                    }
                    default -> g.text(Ui.BODY, line.text(), left, lineY, TEXT_SIZE, Ui.Theme.TEXT2);
                }
            }
        }

        // -- Entrees -------------------------------------------------------------------------

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            float offset = scroll.get();
            for (Placed card : placed) {
                float cardX = x + card.x();
                float cardY = y + card.y() - offset;
                if (mouseX >= cardX && mouseX < cardX + card.width() && mouseY >= cardY
                        && mouseY < cardY + card.height()) {
                    MenuView.Card data = card.card();
                    String action = button == 1 ? data.rightAction() : data.action();
                    if (!action.isEmpty()) {
                        int bar = action.indexOf('|');
                        String key = bar < 0 ? action : action.substring(0, bar);
                        // Maj + clic gauche arrive en bouton 2 : l'enclos s'en sert pour les soins.
                        boolean shift = (UiScreen.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                        int amount = button == 0 && shift ? 2 : button;
                        Runnable send = () -> UiClient.send(view.view(), "card", key, amount);
                        if (!data.confirm().isEmpty() && button == 0) {
                            UiScreen.click(1.0f);
                            openPopup(Popup.question(data.icon(), data.confirm(), send));
                        } else {
                            UiScreen.click(1.0f);
                            send.run();
                        }
                    }
                    return true;
                }
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            target -= (float) (amount * 22.0d);
            clamp();
            return true;
        }
    }
}
