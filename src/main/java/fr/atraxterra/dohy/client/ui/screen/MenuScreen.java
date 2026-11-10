package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran des menus generiques ({@link MenuView}) : titre, cartouche de
 * chiffres, puis les sections de cartes dans une zone qui defile.
 */
public class MenuScreen extends UiScreen implements ViewScreen<MenuView> {

    private static final int CONTENT_X = 37;
    private static final int CONTENT_Y = 56;
    private static final int CONTENT_W = 410;
    private static final int CONTENT_H = 190;

    private MenuView view;
    private Content content;

    public MenuScreen(MenuView view) {
        super(Component.literal(view.title()), 457, 256);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return view.view();
    }

    @Override
    public void accept(MenuView next) {
        boolean sameTitle = next.title().equals(view.title());
        this.view = next;
        content.layout();
        if (!sameTitle) {
            content.scroll = 0;
        }
        content.clamp();
    }

    @Override
    protected void build() {
        add(new CloseButton(431, 16, this::onClose));
        if (view.back()) {
            add(new JobDetailScreen.HexButton(Ui.texture("icon_back"), "Retour",
                    () -> UiClient.send(view.view(), "back", "", 0)));
        }
        content = add(new Content());
        content.layout();
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(9, 0, 448, 256);
        String title = view.title().toUpperCase(java.util.Locale.ROOT);
        // Un titre long retrecit plutot que d'etre coupe, jusqu'au cartouche.
        float room = statsLeft(g) - CONTENT_X - 10;
        float size = Math.max(13, Math.min(22, 22 * room / Math.max(1, g.width(Ui.TITLE, title, 22))));
        g.textShaded(Ui.TITLE, g.fit(Ui.TITLE, title, size, room), CONTENT_X, 15 + (22 - size) * 0.6f, size, Ui.RED,
                Ui.RED_SHADE, 1.3f);
        if (!view.subtitle().isEmpty()) {
            g.text(Ui.BOLD, view.subtitle().toUpperCase(java.util.Locale.ROOT), CONTENT_X, 40, 4.2f, Ui.TEXT);
        }
        stats(g);
    }

    /** Le bord gauche du cartouche, ou le bord droit du titre sans cartouche. */
    private float statsLeft(UiGraphics g) {
        if (view.stats().isEmpty()) {
            return 420;
        }
        float total = 14 * (view.stats().size() - 1) + 16;
        for (MenuView.Stat stat : view.stats()) {
            total += Math.max(g.width(Ui.WIDE, stat.label(), 3.6f), g.width(Ui.WIDE, stat.value(), 6.0f));
        }
        return 423 - Math.round(total);
    }

    /** Le cartouche : les chiffres cote a cote, dans un cadre vert. */
    private void stats(UiGraphics g) {
        if (view.stats().isEmpty()) {
            return;
        }
        List<Float> widths = new ArrayList<>();
        float total = 0;
        for (MenuView.Stat stat : view.stats()) {
            float width = Math.max(g.width(Ui.WIDE, stat.label(), 3.6f), g.width(Ui.WIDE, stat.value(), 6.0f));
            widths.add(width);
            total += width;
        }
        total += 14 * (view.stats().size() - 1) + 16;
        int width = Math.round(total);
        int x = 423 - width;
        int y = 14;
        g.border(x, y, width, 26, 1, Ui.GREEN);
        g.rect(x + 1, y + 1, width - 2, 24, 0xFF131217);
        float cursor = x + 8;
        for (int index = 0; index < view.stats().size(); index++) {
            MenuView.Stat stat = view.stats().get(index);
            g.text(Ui.WIDE, stat.label(), cursor, y + 5, 3.6f, Ui.MUTED);
            g.text(Ui.WIDE, stat.value(), cursor, y + 12, 6.0f, stat.color());
            cursor += widths.get(index);
            if (index < view.stats().size() - 1) {
                g.rect(Math.round(cursor + 7), y + 6, 1, 14, 0xFF3A3940);
                cursor += 14;
            }
        }
    }

    /** Une carte placee : sa section, son indice, son rectangle en coordonnees de contenu. */
    private record Placed(MenuView.Section section, MenuView.Card card, int layout, int x, int y, int width,
                          int height) {
    }

    /** La zone de contenu : mise en page, defilement, dessin et clics. */
    private final class Content extends UiElement {
        private final List<Placed> placed = new ArrayList<>();
        private final List<int[]> headers = new ArrayList<>();
        private int total;
        int scroll;

        Content() {
            super(CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        }

        void layout() {
            placed.clear();
            headers.clear();
            int cursor = 0;
            int usable = CONTENT_W - 7;
            for (int sectionIndex = 0; sectionIndex < view.sections().size(); sectionIndex++) {
                MenuView.Section section = view.sections().get(sectionIndex);
                if (!section.title().isEmpty()) {
                    headers.add(new int[]{sectionIndex, cursor});
                    cursor += 13;
                }
                List<MenuView.Card> cards = section.cards();
                switch (section.layout()) {
                    case MenuView.ROWS -> {
                        for (MenuView.Card card : cards) {
                            placed.add(new Placed(section, card, MenuView.ROWS, 0, cursor, usable, 24));
                            cursor += 27;
                        }
                    }
                    case MenuView.TILES -> {
                        int size = 29;
                        int pitch = 31;
                        int columns = Math.max(1, (usable + 2) / pitch);
                        for (int index = 0; index < cards.size(); index++) {
                            int column = index % columns;
                            int row = index / columns;
                            placed.add(new Placed(section, cards.get(index), MenuView.TILES, column * pitch,
                                    cursor + row * (size + 5), size, size));
                        }
                        cursor += ((cards.size() + columns - 1) / columns) * (size + 5);
                    }
                    default -> {
                        int columns = 4;
                        int gap = 5;
                        int cardWidth = (usable - gap * (columns - 1)) / columns;
                        int cardHeight = 46;
                        for (int index = 0; index < cards.size(); index++) {
                            int column = index % columns;
                            int row = index / columns;
                            placed.add(new Placed(section, cards.get(index), MenuView.GRID,
                                    column * (cardWidth + gap), cursor + row * (cardHeight + gap), cardWidth,
                                    cardHeight));
                        }
                        cursor += ((cards.size() + columns - 1) / columns) * (cardHeight + gap);
                    }
                }
                cursor += 6;
            }
            total = cursor;
        }

        void clamp() {
            scroll = Math.max(0, Math.min(scroll, Math.max(0, total - CONTENT_H)));
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            g.scissor(x, y, width, height);
            for (int[] header : headers) {
                int headerY = y + header[1] - scroll;
                if (headerY > y + height || headerY + 12 < y) {
                    continue;
                }
                MenuView.Section section = view.sections().get(header[0]);
                if (section.icon().isEmpty()) {
                    g.text(Ui.PIXEL, section.title(), x, headerY + 1, 6, Ui.WHITE);
                    float end = x + g.width(Ui.PIXEL, section.title(), 6) + 5;
                    g.rect(Math.round(end), headerY + 4, Math.round(x + width - 10 - end), 1, 0xFF55545C);
                } else {
                    CrusherPanel.section(g, x, headerY + 1, "section_" + section.icon(), section.title(),
                            width - 10);
                }
            }
            Placed hovered = null;
            boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            for (Placed card : placed) {
                int cardY = y + card.y() - scroll;
                if (cardY > y + height || cardY + card.height() < y) {
                    continue;
                }
                int cardX = x + card.x();
                boolean over = inside && mouseX >= cardX && mouseX < cardX + card.width() && mouseY >= cardY
                        && mouseY < cardY + card.height();
                if (over) {
                    hovered = card;
                }
                switch (card.layout()) {
                    case MenuView.ROWS -> row(g, card.card(), cardX, cardY, card.width(), card.height(), over);
                    case MenuView.TILES -> tile(g, card.card(), cardX, cardY, card.width(), over);
                    default -> grid(g, card.card(), cardX, cardY, card.width(), card.height(), over);
                }
            }
            g.endScissor();
            // La barre.
            if (total > CONTENT_H) {
                int barX = x + width - 3;
                g.rect(barX, y, 3, height, Ui.SCROLL_TRACK);
                int thumb = Math.max(10, height * height / total);
                int thumbY = y + (height - thumb) * scroll / Math.max(1, total - height);
                g.rect(barX, thumbY, 3, thumb, Ui.SCROLL_THUMB);
            }
            if (hovered != null) {
                tooltip(g, hovered.card());
            }
        }

        private void tooltip(UiGraphics g, MenuView.Card card) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(card.title()).withColor(card.state() == MenuView.DONE ? 0x55FF55 : 0xFFFF55));
            for (String line : card.lines()) {
                lines.add(Component.literal(line).withColor(0xAAAAAA));
            }
            for (String line : card.tooltip()) {
                lines.add(line.isEmpty() ? Component.empty() : Component.literal(line).withColor(0x888888));
            }
            if (!card.action().isEmpty() || !card.rightAction().isEmpty()) {
                lines.add(Component.empty());
                if (!card.action().isEmpty()) {
                    lines.add(Component.literal("Clic gauche : " + verb(card.action())).withColor(0x55FFFF));
                }
                if (!card.rightAction().isEmpty()) {
                    lines.add(Component.literal("Clic droit : " + verb(card.rightAction())).withColor(0x55FFFF));
                }
            }
            g.tooltip(lines);
        }

        /** Le verbe a montrer : la partie avant « | » de l'action, si elle en a une. */
        private String verb(String action) {
            int bar = action.indexOf('|');
            return bar < 0 ? "ouvrir" : action.substring(bar + 1);
        }

        private int face(MenuView.Card card, boolean over) {
            if (card.state() == MenuView.DISABLED) {
                return 0xFF18171C;
            }
            boolean clickable = !card.action().isEmpty() || !card.rightAction().isEmpty();
            return over && clickable ? 0xFF2C2B33 : over ? 0xFF222128 : 0xFF1C1B21;
        }

        private void grid(UiGraphics g, MenuView.Card card, int cardX, int cardY, int cardWidth, int cardHeight,
                          boolean over) {
            g.rect(cardX, cardY, cardWidth, cardHeight, face(card, over));
            int accent = card.state() == MenuView.DONE ? Ui.GREEN : card.accent();
            g.rect(cardX, cardY, cardWidth, 2, card.state() == MenuView.DISABLED ? 0xFF3A3940 : accent);
            g.item(card.icon(), cardX + 5, cardY + 6, 16, false);
            int titleColor = card.state() == MenuView.DISABLED ? Ui.MUTED : Ui.WHITE;
            float titleMax = cardWidth - 28 - (card.badge().isEmpty() ? 0 : g.width(Ui.WIDE, card.badge(), 3.4f) + 6);
            g.text(Ui.WIDE, g.fit(Ui.WIDE, card.title().toUpperCase(java.util.Locale.ROOT), 4.4f, titleMax),
                    cardX + 25, cardY + 8, 4.4f, titleColor);
            badge(g, card, cardX + cardWidth - 3, cardY + 5);
            int lineY = cardY + 25;
            for (int index = 0; index < Math.min(3, card.lines().size()); index++) {
                g.text(Ui.BODY, g.fit(Ui.BODY, card.lines().get(index), 3.6f, cardWidth - 10), cardX + 5,
                        lineY + index * 5.5f, 3.6f, index == 0 && card.state() != MenuView.DISABLED ? Ui.TEXT
                                : Ui.MUTED);
            }
            if (card.progress() >= 0) {
                g.rect(cardX + 5, cardY + cardHeight - 4, cardWidth - 10, 2, 0xFF2E2D34);
                g.rect(cardX + 5, cardY + cardHeight - 4, (cardWidth - 10) * card.progress() / 1000, 2,
                        card.state() == MenuView.DONE ? Ui.GREEN : 0xFF39CF44);
            }
        }

        private void row(UiGraphics g, MenuView.Card card, int rowX, int rowY, int rowWidth, int rowHeight,
                         boolean over) {
            g.rect(rowX, rowY, rowWidth, rowHeight, face(card, over));
            int accent = card.state() == MenuView.DONE ? Ui.GREEN : card.accent();
            g.rect(rowX, rowY, 2, rowHeight, card.state() == MenuView.DISABLED ? 0xFF3A3940 : accent);
            g.item(card.icon(), rowX + 7, rowY + 4, 16, false);
            float right = rowX + rowWidth - 6;
            if (!card.badge().isEmpty()) {
                badge(g, card, Math.round(right), rowY + 4);
                right -= g.width(Ui.WIDE, card.badge(), 3.4f) + 10;
            }
            g.text(Ui.BOLD, g.fit(Ui.BOLD, card.title(), 4.6f, right - rowX - 34), rowX + 29, rowY + 5, 4.6f,
                    card.state() == MenuView.DISABLED ? Ui.MUTED : Ui.WHITE);
            if (!card.lines().isEmpty()) {
                g.text(Ui.BODY, g.fit(Ui.BODY, String.join("  ·  ", card.lines()), 3.8f, rowWidth - 140), rowX + 29,
                        rowY + 13.5f, 3.8f, card.state() == MenuView.DONE ? 0xFF7BE38F : Ui.MUTED);
            }
            if (card.progress() >= 0) {
                int barWidth = 80;
                int barX = rowX + rowWidth - barWidth - 8;
                g.rect(barX, rowY + rowHeight - 6, barWidth, 2, 0xFF2E2D34);
                g.rect(barX, rowY + rowHeight - 6, barWidth * card.progress() / 1000, 2,
                        card.state() == MenuView.DONE ? Ui.GREEN : 0xFF39CF44);
            }
        }

        private void tile(UiGraphics g, MenuView.Card card, int tileX, int tileY, int size, boolean over) {
            int border = card.state() == MenuView.DONE ? Ui.GREEN : card.state() == MenuView.DISABLED ? 0xFF3A3940
                    : card.accent() != 0xFF55545C ? card.accent() : Ui.SLOT_BORDER;
            g.rect(tileX, tileY, size, size, border);
            g.rect(tileX + 1, tileY + 1, size - 2, size - 2, over ? Ui.SLOT_HOVER : Ui.SLOT_FILL);
            g.item(card.icon(), tileX + (size - 16) / 2.0f, tileY + (size - 16) / 2.0f - 1, 16, false);
            if (card.state() == MenuView.DISABLED) {
                g.rect(tileX + 1, tileY + 1, size - 2, size - 2, 0x80101014);
            }
            if (card.progress() >= 0) {
                g.rect(tileX + 3, tileY + size - 4, size - 6, 2, 0xFF2E2D34);
                g.rect(tileX + 3, tileY + size - 4, (size - 6) * card.progress() / 1000, 2,
                        card.state() == MenuView.DONE ? Ui.GREEN : 0xFF39CF44);
            }
            if (!card.badge().isEmpty()) {
                g.right(Ui.WIDE, card.badge(), tileX + size - 2, tileY + 2.5f, 3.4f, Ui.WHITE);
            }
        }

        private void badge(UiGraphics g, MenuView.Card card, int right, int top) {
            if (card.badge().isEmpty()) {
                return;
            }
            float width = g.width(Ui.WIDE, card.badge(), 3.4f) + 6;
            int left = Math.round(right - width);
            g.rect(left, top, Math.round(width), 8, card.state() == MenuView.DONE ? 0xFF1F5A2E : 0xFF2B2A31);
            g.text(Ui.WIDE, card.badge(), left + 3, top + 2.3f, 3.4f, Ui.WHITE);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            for (Placed card : placed) {
                int cardX = x + card.x();
                int cardY = y + card.y() - scroll;
                if (mouseX >= cardX && mouseX < cardX + card.width() && mouseY >= cardY
                        && mouseY < cardY + card.height()) {
                    String action = button == 1 ? card.card().rightAction() : card.card().action();
                    if (!action.isEmpty()) {
                        int bar = action.indexOf('|');
                        UiScreen.click(1.0f);
                        // Maj + clic gauche arrive en bouton 2 : l'enclos s'en sert pour les soins.
                        boolean shift = (UiScreen.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
                        UiClient.send(view.view(), "card", bar < 0 ? action : action.substring(0, bar),
                                button == 0 && shift ? 2 : button);
                    }
                    return true;
                }
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            scroll -= (int) Math.round(amount * 18);
            clamp();
            return true;
        }
    }
}
