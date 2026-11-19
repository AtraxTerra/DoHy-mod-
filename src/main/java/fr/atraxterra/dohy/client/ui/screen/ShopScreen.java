package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Charts;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Popup;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.client.ui.widget.SearchBox;
import fr.atraxterra.dohy.shop.ShopCategory;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.PriceChart;
import fr.atraxterra.dohy.ui.view.ShopView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * L'onglet Acheter du commerce, dans la coque des menus : la recherche et les
 * categories a gauche, la grille des objets au centre, et la fiche de l'objet
 * choisi a droite — son prix, la quantite, le total, un seul bouton.
 *
 * <p>La vente a son onglet : ici, une carte ne montre que ce qui sert a acheter
 * (l'objet, son nom, son prix). Seul ce dont le prix change chaque heure (le
 * cours de l'heure) a une courbe : en miniature sur sa carte, en grand sous sa
 * fiche ; un prix fixe n'en a pas. Une recherche trouve aussi ce qui s'achete
 * ailleurs dans le commerce (boutique, hotel des ventes) et dit ou aller ; un
 * clic y mene.
 *
 * <p>Tout le catalogue arrive d'un coup : la categorie, la recherche et la
 * selection se font ici sans aller-retour ; seul l'achat passe par le serveur.
 * Un achat qui entame une bonne part du solde demande d'abord une confirmation.
 */
public class ShopScreen extends UiScreen implements ViewScreen<ShopView> {

    private static final float CATEGORY_WIDTH = 70.0f;
    private static final float DETAIL_WIDTH = 143.0f;
    private static final float GAP = 6.0f;
    private static final float CARD_GAP = 4.0f;
    private static final float CARD_HEIGHT = 50.7f;
    private static final int COLUMNS = 5;
    private static final int MAX_QUANTITY = 2304;

    private ShopView view;
    private final Shell shell = new Shell(ShopView.ID);
    private final List<ShopView.Offer> shown = new ArrayList<>();
    /** Ce qui s'achete ailleurs et correspond a la recherche ; vide sans recherche. */
    private final List<ShopView.Elsewhere> away = new ArrayList<>();
    private final double opened = Anim.now();
    /** 0 pour tout, sinon la categorie d'indice - 1. */
    private int category;
    private String search = "";
    private Identifier selectedId;
    /** Quand l'objet affiche a change : sa courbe se deroule depuis cet instant. */
    private double picked = Anim.now();
    private int quantity = 1;
    private Grid grid;

    public ShopScreen(ShopView view) {
        super(Component.literal("Commerce"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        header();
    }

    private void header() {
        shell.header("Commerce", view.subtitle(), List.of(new MenuView.Stat("TON ARGENT",
                MoneyFormat.exact(view.balance()) + " $", 0xFFFFFFFF)), view.tabs(), false);
    }

    @Override
    public Identifier viewId() {
        return ShopView.ID;
    }

    @Override
    public void accept(ShopView next) {
        this.view = next;
        header();
        refilter();
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    // -- Mise en page --------------------------------------------------------------------

    private float top() {
        return shell.contentTop();
    }

    private float bottom() {
        return Shell.HEIGHT - 7.0f;
    }

    private float gridLeft() {
        return shell.mainLeft() + CATEGORY_WIDTH + GAP;
    }

    private float detailLeft() {
        return shell.mainRight() - DETAIL_WIDTH;
    }

    @Override
    protected void build() {
        add(new SearchBox(Math.round(shell.mainLeft()), Math.round(top()), Math.round(CATEGORY_WIDTH), 13,
                "Rechercher...", 32, text -> {
                    search = text;
                    refilter();
                }));
        add(new Categories());
        grid = add(new Grid());
        add(new Detail());
        refilter();
    }

    private void refilter() {
        shown.clear();
        away.clear();
        String needle = fold(search);
        for (ShopView.Offer offer : view.offers()) {
            if (category > 0 && !ShopCategory.values()[category - 1].key().equalsIgnoreCase(offer.category())) {
                continue;
            }
            if (!needle.isEmpty() && !fold(offer.stack().getHoverName().getString()).contains(needle)) {
                continue;
            }
            shown.add(offer);
        }
        if (!needle.isEmpty()) {
            for (ShopView.Elsewhere entry : view.elsewhere()) {
                if (fold(name(entry)).contains(needle)) {
                    away.add(entry);
                }
            }
        }
        if (selected() == null && !shown.isEmpty()) {
            selectedId = idOf(shown.getFirst());
            picked = Anim.now();
        }
        if (grid != null) {
            grid.clamp();
        }
    }

    /** Les categories qui ont quelque chose a vendre : 0 pour « Tout », sinon l'indice + 1. */
    private List<Integer> categories() {
        List<Integer> out = new ArrayList<>();
        out.add(0);
        for (int index = 1; index <= ShopCategory.values().length; index++) {
            if (count(index) > 0) {
                out.add(index);
            }
        }
        return out;
    }

    private int count(int index) {
        if (index == 0) {
            return view.offers().size();
        }
        String key = ShopCategory.values()[index - 1].key();
        int count = 0;
        for (ShopView.Offer offer : view.offers()) {
            if (key.equalsIgnoreCase(offer.category())) {
                count++;
            }
        }
        return count;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String name(ShopView.Elsewhere entry) {
        return entry.label().isEmpty() ? entry.icon().getHoverName().getString() : entry.label();
    }

    private static Identifier idOf(ShopView.Offer offer) {
        return BuiltInRegistries.ITEM.getKey(offer.stack().getItem());
    }

    private ShopView.Offer selected() {
        if (selectedId == null) {
            return null;
        }
        for (ShopView.Offer offer : view.offers()) {
            if (idOf(offer).equals(selectedId)) {
                return offer;
            }
        }
        return null;
    }

    private static String money(long value) {
        return MoneyFormat.exact(value) + " $";
    }

    /** La bulle d'un objet : son nom, son prix, ce qu'on en a. */
    private static List<Component> tip(ShopView.Offer offer) {
        List<Component> lines = new ArrayList<>();
        lines.add(offer.stack().getHoverName().copy().withColor(0xFFFFFF));
        lines.add(Component.literal("Prix : " + money(offer.buy()) + " l'unite").withColor(0x22E558));
        if (moves(offer)) {
            lines.add(Component.literal("Ce prix change chaque heure : " + course(offer.course())
                    + " en ce moment").withColor(0xFAC400));
        }
        lines.add(Component.literal("Tu en as " + offer.owned()).withColor(0x8B8E95));
        return lines;
    }

    /** Le prix de cet objet change-t-il regulierement (le cours de l'heure) ? Lui seul a une courbe. */
    private static boolean moves(ShopView.Offer offer) {
        return offer.course() > 0 && offer.chart() != null && offer.chart().size() > 1;
    }

    private static List<Component> tip(ShopView.Elsewhere entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name(entry)).withColor(0xFFFFFF));
        lines.add(Component.literal("Pas au shop : " + entry.where() + ", " + entry.price()).withColor(0xFAC400));
        lines.add(Component.literal("Clic : y aller").withColor(0x2BC4C4));
        return lines;
    }

    /** Le plus grand nombre achetable avec le solde, plafonne. */
    private long affordable(ShopView.Offer offer) {
        return offer.buy() <= 0 ? 0 : Math.min(MAX_QUANTITY, view.balance() / offer.buy());
    }

    private void buy() {
        ShopView.Offer offer = selected();
        if (offer == null) {
            return;
        }
        String id = idOf(offer).toString();
        long total = offer.buy() * quantity;
        if (total >= 1_000L && total * 10L >= view.balance()) {
            // Un achat qui prend au moins un dixieme du solde : on demande.
            String name = offer.stack().getHoverName().getString();
            int amount = quantity;
            Popup confirm = new Popup(offer.stack(), "Acheter " + amount + " × " + name + " ?", "", "ACHETER",
                    false, () -> UiClient.send(ShopView.ID, "buy", id, amount));
            openPopup(confirm.sums(List.of(new String[]{"TOTAL", money(total)},
                    new String[]{"IL TE RESTERA", money(Math.max(0L, view.balance() - total))})));
            return;
        }
        UiClient.send(ShopView.ID, "buy", id, quantity);
    }

    private static String course(int percent) {
        int delta = percent - 100;
        return (delta > 0 ? "+" : "") + delta + " %";
    }

    // -- Dessin ----------------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        shell.draw(g, g.paint(), mouseX, mouseY);
    }

    @Override
    protected boolean backgroundClicked(double mouseX, double mouseY, int button) {
        return button == 0 && shell.click(mouseX, mouseY);
    }

    /** La liste des categories qui ont des objets, avec leur nombre. */
    private final class Categories extends UiElement {
        private static final float ROW = 14.0f;
        private final Map<Integer, Anim.Value> hovers = new HashMap<>();

        Categories() {
            super(0, 0, 1, 1);
            x = Math.round(shell.mainLeft());
            y = Math.round(top() + 17.0f);
            width = Math.round(CATEGORY_WIDTH);
            height = Math.round(14.0f + (ShopCategory.values().length + 1) * ROW + 5.0f);
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            List<Integer> rows = categories();
            float boxHeight = 14.0f + rows.size() * ROW + 5.0f;
            p.framed(x, y, width, boxHeight, 5.3f, 0xFF141518, 0xFF24262A, 1);
            g.text(Ui.PIXEL, "CATEGORIES", x + 5.3f, y + 5.3f, 3.3f, Ui.Theme.DIM);
            for (int row = 0; row < rows.size(); row++) {
                int index = rows.get(row);
                float rowY = y + 12.0f + row * ROW;
                boolean on = index == category;
                boolean over = mouseX >= x + 2 && mouseX < x + width - 2 && mouseY >= rowY && mouseY < rowY + ROW;
                float hover = hovers.computeIfAbsent(index, key -> new Anim.Value(0.0f, 16.0f))
                        .toward(over ? 1.0f : 0.0f);
                if (on) {
                    p.round(x + 2.7f, rowY + 1.0f, width - 5.4f, ROW - 2.0f, 3.0f, Paint.alpha(Ui.Theme.RED, 0.17f));
                    p.round(x + 1.0f, rowY + 3.0f, 1.34f, ROW - 6.0f, 0.67f, Ui.Theme.RED);
                } else if (hover > 0.01f) {
                    p.round(x + 2.7f, rowY + 1.0f, width - 5.4f, ROW - 2.0f, 3.0f, Paint.alpha(0xFFFFFFFF, 0.05f * hover));
                }
                ItemStack icon = index == 0 ? new ItemStack(Items.NETHER_STAR)
                        : new ItemStack(ShopCategory.values()[index - 1].icon());
                g.item(icon, x + 5.3f, rowY + 3.0f, 8.0f, false);
                String label = index == 0 ? "Tout" : ShopCategory.values()[index - 1].display();
                g.text(Ui.BODY, g.fit(Ui.BODY, label, 4.7f, width - 34.0f), x + 16.0f, rowY + 4.6f, 4.7f,
                        on ? Ui.Theme.TEXT : Paint.mix(Ui.Theme.TEXT2, Ui.Theme.TEXT, hover));
                g.right(Ui.PIXEL, Integer.toString(count(index)), x + width - 5.3f, rowY + 5.0f, 3.3f, Ui.Theme.DIM);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            List<Integer> rows = categories();
            int row = (int) ((mouseY - (y + 12.0f)) / ROW);
            if (row >= 0 && row < rows.size() && rows.get(row) != category) {
                category = rows.get(row);
                UiScreen.click(1.1f);
                grid.resetScroll();
                refilter();
            }
            return true;
        }
    }

    /** La grille des objets, qui defile ; apres eux, ce qui s'achete ailleurs. */
    private final class Grid extends UiElement {
        private final Anim.Value scroll = new Anim.Value(0.0f, 18.0f);
        private final Map<String, Anim.Value> hovers = new HashMap<>();
        private float target;

        Grid() {
            super(0, 0, 1, 1);
            x = Math.round(gridLeft());
            y = Math.round(top());
            width = Math.round(detailLeft() - GAP - gridLeft());
            height = Math.round(bottom() - top());
        }

        private float cardWidth() {
            return (width - 6.0f - CARD_GAP * (COLUMNS - 1)) / COLUMNS;
        }

        private int size() {
            return shown.size() + away.size();
        }

        private float total() {
            int rows = (size() + COLUMNS - 1) / COLUMNS;
            return rows * (CARD_HEIGHT + CARD_GAP) - (rows > 0 ? CARD_GAP : 0);
        }

        void resetScroll() {
            target = 0;
            scroll.set(0);
        }

        void clamp() {
            target = Math.max(0, Math.min(target, Math.max(0, total() - height)));
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            Paint p = g.paint();
            float offset = scroll.toward(target);
            if (size() == 0) {
                if (search.isEmpty()) {
                    g.centered(Ui.BODY, "Rien dans cette categorie", x + width / 2.0f, y + 40.0f, 5.3f, Ui.Theme.MUTED);
                } else {
                    g.centered(Ui.BODY, "Introuvable : ni au shop, ni a la boutique,", x + width / 2.0f, y + 40.0f,
                            5.3f, Ui.Theme.MUTED);
                    g.centered(Ui.BODY, "ni dans les annonces du jour de l'hotel des ventes.", x + width / 2.0f,
                            y + 48.0f, 5.3f, Ui.Theme.MUTED);
                }
                return;
            }
            float cardWidth = cardWidth();
            boolean inside = contains(mouseX, mouseY);
            List<Component> tooltip = null;
            g.scissor(x - 4, y - 1, width + 8, height + 1);
            for (int index = 0; index < size(); index++) {
                float cardX = x + (index % COLUMNS) * (cardWidth + CARD_GAP);
                float cardY = y + (index / COLUMNS) * (CARD_HEIGHT + CARD_GAP) - offset;
                if (cardY > y + height || cardY + CARD_HEIGHT < y) {
                    continue;
                }
                boolean over = inside && mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY
                        && mouseY < cardY + CARD_HEIGHT;
                if (index < shown.size()) {
                    ShopView.Offer offer = shown.get(index);
                    Identifier id = idOf(offer);
                    float hover = hover("o:" + id, over);
                    card(g, p, offer, cardX, cardY - 1.3f * hover, cardWidth, hover, id.equals(selectedId));
                    if (over) {
                        tooltip = tip(offer);
                    }
                } else {
                    ShopView.Elsewhere entry = away.get(index - shown.size());
                    float hover = hover("e:" + index, over);
                    elsewhere(g, p, entry, cardX, cardY - 1.3f * hover, cardWidth, hover);
                    if (over) {
                        tooltip = tip(entry);
                    }
                }
            }
            g.endScissor();
            if (total() > height) {
                float barX = x + width - 2.0f;
                p.round(barX, y, 2.0f, height, 1.0f, 0xFF24262A);
                float thumb = Math.max(12.0f, height * height / total());
                float thumbY = y + (height - thumb) * offset / Math.max(1.0f, total() - height);
                p.round(barX, thumbY, 2.0f, thumb, 1.0f, 0xFF5A5D64);
            }
            if (tooltip != null) {
                g.tooltip(tooltip);
            }
        }

        private float hover(String key, boolean over) {
            return hovers.computeIfAbsent(key, k -> new Anim.Value(0.0f, 16.0f)).toward(over ? 1.0f : 0.0f);
        }

        private void frame(Paint p, float cardX, float cardY, float cardWidth, float hover, boolean selected, int tint) {
            if (hover > 0.01f || selected) {
                p.shadow(cardX + 2, cardY + 4, cardWidth - 4, CARD_HEIGHT - 4, 8,
                        Paint.alpha(selected ? tint : 0xFF000000, selected ? 0.22f : 0.3f * hover));
            }
            int edge = selected ? tint : Paint.mix(Ui.Theme.EDGE, 0xFF45484F, hover);
            int fill = selected ? Paint.mix(Ui.Theme.CARD, tint, 0.08f) : Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER,
                    hover);
            p.framed(cardX, cardY, cardWidth, CARD_HEIGHT, 4.7f, fill, edge, selected ? 2 : 1);
        }

        private void title(UiGraphics g, String text, float cardX, float cardY, float cardWidth) {
            List<String> name = g.wrap(Ui.BOLD, text, 4.3f, cardWidth - 6.0f);
            float nameY = cardY + 26.0f - (name.size() > 1 ? 2.6f : 0.0f);
            for (int line = 0; line < Math.min(2, name.size()); line++) {
                String part = line == 1 && name.size() > 2 ? g.fit(Ui.BOLD, name.get(1) + " ...", 4.3f, cardWidth - 6.0f)
                        : name.get(line);
                g.centered(Ui.BOLD, part, cardX + cardWidth / 2.0f, nameY + line * 5.3f, 4.3f, Ui.Theme.TEXT);
            }
        }

        private void card(UiGraphics g, Paint p, ShopView.Offer offer, float cardX, float cardY, float cardWidth,
                          float hover, boolean selected) {
            frame(p, cardX, cardY, cardWidth, hover, selected, Ui.Theme.RED);
            if (moves(offer)) {
                // Un prix qui change chaque heure : l'objet a gauche, sa courbe et sa variation a droite.
                PriceChart chart = offer.chart();
                g.item(offer.stack(), cardX + 3.0f, cardY + 6.0f, 12.0f, false);
                float sparkX = cardX + 17.0f;
                float sparkWidth = cardWidth - 20.0f;
                p.round(sparkX - 1.0f, cardY + 4.0f, sparkWidth + 2.0f, 11.0f, 2.0f, 0x40000000);
                Charts.spark(g, chart, sparkX, cardY + 5.0f, sparkWidth, 9.0f, 1.0f);
                g.right(Ui.PIXEL, course(offer.course()), cardX + cardWidth - 3.0f, cardY + 17.3f, 3.0f,
                        offer.course() > 100 ? Ui.Theme.RED_LIGHT : offer.course() < 100 ? Ui.Theme.GREEN_L
                                : Ui.Theme.MUTED);
            } else {
                g.item(offer.stack(), cardX + (cardWidth - 16.0f) / 2.0f, cardY + 7.0f, 16.0f, false);
            }
            title(g, offer.stack().getHoverName().getString(), cardX, cardY, cardWidth);
            // Un seul prix, celui qu'on paie, centre et lisible.
            String price = money(offer.buy());
            float size = 4.3f;
            while (size > 3.2f && g.width(Ui.BOLD, price, size) > cardWidth - 8.0f) {
                size -= 0.2f;
            }
            g.centered(Ui.BOLD, price, cardX + cardWidth / 2.0f, cardY + CARD_HEIGHT - 9.0f + (4.3f - size) / 2.0f,
                    size, view.balance() >= offer.buy() ? Ui.Theme.GREEN_L : Ui.Theme.MUTED);
        }

        /** Une carte « ailleurs » : l'objet, son nom, l'onglet ou il s'achete. */
        private void elsewhere(UiGraphics g, Paint p, ShopView.Elsewhere entry, float cardX, float cardY,
                               float cardWidth, float hover) {
            frame(p, cardX, cardY, cardWidth, hover, false, Ui.Theme.YELLOW);
            p.round(cardX + 3.0f, cardY + 3.0f, cardWidth - 6.0f, 6.0f, 2.0f, Paint.alpha(Ui.Theme.YELLOW, 0.18f));
            String where = entry.where().toUpperCase(Locale.ROOT);
            // « HOTEL DES VENTES » tient en entier : la police se resserre plutot que de couper. La
            // police pixel ne prend que des tailles entieres de texel : on prend la grasse, qui se resserre.
            float size = 3.4f;
            while (size > 2.4f && g.width(Ui.BOLD, where, size) > cardWidth - 8.0f) {
                size -= 0.1f;
            }
            g.centered(Ui.BOLD, g.fit(Ui.BOLD, where, size, cardWidth - 8.0f), cardX + cardWidth / 2.0f,
                    cardY + 4.4f + (3.4f - size) / 2.0f, size, Ui.Theme.YELLOW);
            g.item(entry.icon(), cardX + (cardWidth - 13.0f) / 2.0f, cardY + 11.0f, 13.0f, false);
            title(g, ShopScreen.name(entry), cardX, cardY, cardWidth);
            g.centered(Ui.BOLD, g.fit(Ui.BOLD, entry.price(), 3.6f, cardWidth - 8.0f), cardX + cardWidth / 2.0f,
                    cardY + CARD_HEIGHT - 8.5f, 3.6f, Ui.Theme.TEXT2);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            float cardWidth = cardWidth();
            float offset = scroll.get();
            int column = (int) ((mouseX - x) / (cardWidth + CARD_GAP));
            int row = (int) ((mouseY - y + offset) / (CARD_HEIGHT + CARD_GAP));
            int index = row * COLUMNS + column;
            if (column < 0 || column >= COLUMNS || index < 0 || index >= size()) {
                return true;
            }
            if (index < shown.size()) {
                Identifier id = idOf(shown.get(index));
                if (!id.equals(selectedId)) {
                    selectedId = id;
                    quantity = 1;
                    picked = Anim.now();
                    UiScreen.click(1.2f);
                }
            } else {
                // Ce qui s'achete ailleurs : on passe a l'onglet qui le vend.
                UiScreen.click(1.0f);
                UiClient.send(ShopView.ID, "card", "tab:" + away.get(index - shown.size()).tab(), 0);
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            target -= (float) amount * (CARD_HEIGHT + CARD_GAP) * 0.6f;
            clamp();
            return true;
        }
    }

    /** La fiche de l'objet choisi : son prix, la quantite, le total, et le bouton d'achat. */
    private final class Detail extends UiElement {
        private static final float BUTTON = 15.3f;
        private final Map<String, Anim.Value> hovers = new HashMap<>();
        private final List<Hit> hits = new ArrayList<>();

        private record Hit(float x, float y, float width, float height, String key) {
        }

        Detail() {
            super(0, 0, 1, 1);
            x = Math.round(detailLeft());
            y = Math.round(top());
            width = Math.round(DETAIL_WIDTH);
            height = Math.round(bottom() - top());
        }

        private float hover(String key, double mouseX, double mouseY, float hx, float hy, float hw, float hh) {
            boolean over = mouseX >= hx && mouseX < hx + hw && mouseY >= hy && mouseY < hy + hh;
            hits.add(new Hit(hx, hy, hw, hh, key));
            return hovers.computeIfAbsent(key, k -> new Anim.Value(0.0f, 16.0f)).toward(over ? 1.0f : 0.0f);
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            hits.clear();
            Paint p = g.paint();
            ShopView.Offer offer = selected();
            float panelHeight = Math.min(height, 150.0f);
            p.framed(x, y, width, panelHeight, 5.3f, 0xFF1C1D20, Ui.Theme.EDGE, 1);
            g.scissor(x, y, width, Math.round(panelHeight));
            p.glow(x + width - 10.0f, y + 6.0f, 60.0f, Paint.alpha(Ui.Theme.RED, 0.14f));
            g.endScissor();
            g.scissor(x, y, width, 1);
            p.round(x, y, width, panelHeight, 5.3f, Ui.Theme.RED);
            g.endScissor();
            if (offer == null) {
                g.centered(Ui.BODY, "Choisis un objet", x + width / 2.0f, y + 60.0f, 5.3f, Ui.Theme.MUTED);
                return;
            }
            float left = x + 8.0f;
            float inner = width - 16.0f;
            // L'objet, son nom, ce qu'on en a deja.
            p.framed(left, y + 8.0f, 30.0f, 30.0f, 7.0f, Paint.mix(0xFF1A1B1E, Ui.Theme.RED, 0.16f),
                    Paint.mix(0xFF1A1B1E, Ui.Theme.RED, 0.45f), 1);
            g.item(offer.stack(), left + 5.0f, y + 13.0f, 20.0f, false);
            float textX = left + 35.0f;
            float textRoom = x + width - 6.0f - textX;
            List<String> name = g.wrap(Ui.BOLD, offer.stack().getHoverName().getString(), 6.3f, textRoom);
            float textY = y + 10.0f;
            for (int line = 0; line < Math.min(2, name.size()); line++) {
                g.text(Ui.BOLD, g.fit(Ui.BOLD, name.get(line), 6.3f, textRoom), textX, textY, 6.3f, Ui.Theme.TEXT);
                textY += 7.3f;
            }
            g.text(Ui.BODY, "Tu en as " + offer.owned(), textX, textY, 4.7f, Ui.Theme.TEXT2);

            // Le prix, et ce qui le fait bouger, en clair.
            float boxY = y + 44.0f;
            p.framed(left, boxY, inner, 19.3f, 3.3f, 0xFF16171A, 0xFF2A2C31, 1);
            g.text(Ui.PIXEL, "PRIX A L'UNITE", left + 4.7f, boxY + 3.6f, 3.3f, Ui.Theme.MUTED);
            if (offer.discount() > 0) {
                g.right(Ui.PIXEL, "REMISE -" + offer.discount() + " % COMPRISE", left + inner - 4.0f, boxY + 3.6f, 3.3f,
                        Ui.Theme.YELLOW);
            }
            g.text(Ui.WIDE, g.fit(Ui.WIDE, money(offer.buy()), 7.0f, inner - 8.0f), left + 4.7f, boxY + 9.3f, 7.0f,
                    Ui.Theme.GREEN_L);
            float infoY = boxY + 23.0f;
            if (offer.course() > 0) {
                int delta = offer.course() - 100;
                g.text(Ui.PIXEL, "PRIX DU MOMENT " + course(offer.course()) + " · CHANGE DANS " + view.courseMinutes()
                        + " MIN", left, infoY, 3.3f, delta > 0 ? Ui.Theme.RED_LIGHT : Ui.Theme.GREEN_L);
                infoY += 6.0f;
            }

            // La quantite.
            float quantityY = Math.max(infoY + 2.0f, y + 72.0f);
            g.text(Ui.PIXEL, "QUANTITE", left, quantityY, 3.3f, Ui.Theme.MUTED);
            float rowY = quantityY + 5.3f;
            float minus = hover("minus", mouseX, mouseY, left, rowY, BUTTON, BUTTON);
            Buttons.draw(g, p, left, rowY, BUTTON, BUTTON, "-", Buttons.GHOST, minus, quantity > 1, 6.0f);
            float plus = hover("plus", mouseX, mouseY, left + inner - BUTTON, rowY, BUTTON, BUTTON);
            Buttons.draw(g, p, left + inner - BUTTON, rowY, BUTTON, BUTTON, "+", Buttons.GHOST, plus,
                    quantity < MAX_QUANTITY, 6.0f);
            float valueX = left + BUTTON + 3.0f;
            float valueWidth = inner - 2 * (BUTTON + 3.0f);
            Buttons.field(p, valueX, rowY, valueWidth, BUTTON, false);
            g.centered(Ui.WIDE, Integer.toString(quantity), valueX + valueWidth / 2.0f, rowY + (BUTTON - 6.3f) / 2.0f,
                    6.3f, Ui.Theme.TEXT);
            float presetY = rowY + BUTTON + 3.0f;
            String[] presets = {"1", "16", "64", "MAX"};
            float presetWidth = (inner - 3 * 3.0f) / 4.0f;
            for (int index = 0; index < presets.length; index++) {
                float presetX = left + index * (presetWidth + 3.0f);
                float h = hover("preset" + index, mouseX, mouseY, presetX, presetY, presetWidth, 10.7f);
                boolean on = index < 3 && quantity == Integer.parseInt(presets[index]);
                Buttons.draw(g, p, presetX, presetY, presetWidth, 10.7f, presets[index],
                        on ? Buttons.GHOST_ON : Buttons.GHOST, h, true, 4.0f);
            }

            // Le total et le bouton.
            float totalY = presetY + 16.0f;
            long total = offer.buy() * quantity;
            boolean canBuy = total <= view.balance();
            g.text(Ui.PIXEL, "TOTAL", left, totalY + 2.0f, 3.3f, Ui.Theme.MUTED);
            g.right(Ui.WIDE, money(total), left + inner, totalY, 7.3f, canBuy ? Ui.Theme.TEXT : Ui.Theme.RED_LIGHT);
            float buttonY = totalY + 12.0f;
            float buy = hover("buy", mouseX, mouseY, left, buttonY, inner, BUTTON);
            Buttons.draw(g, p, left, buttonY, inner, BUTTON, canBuy ? "ACHETER" : "PAS ASSEZ D'ARGENT", Buttons.GREEN,
                    buy, canBuy, 5.3f);
            if (offer.chart() != null) {
                trend(g, p, offer, y + panelHeight + 5.0f, height - panelHeight - 5.0f, mouseX, mouseY);
            }
            if (mouseX >= left && mouseX < left + 30 && mouseY >= y + 8 && mouseY < y + 38) {
                g.tooltip(tip(offer));
            }
        }

        /**
         * Pour un objet dont le prix change chaque heure : la courbe des dernieres
         * heures, sous sa fiche, avec ses plus haut et plus bas et l'heure de chaque
         * point sous la souris. Les objets a prix fixe n'en ont pas.
         */
        private void trend(UiGraphics g, Paint p, ShopView.Offer offer, float top, float panel, double mouseX,
                           double mouseY) {
            PriceChart chart = offer.chart();
            if (panel < 48.0f || chart == null || chart.size() == 0) {
                return;
            }
            p.framed(x, top, width, panel, 5.3f, 0xFF1C1D20, Ui.Theme.EDGE, 1);
            float left = x + 7.0f;
            float right = x + width - 7.0f;
            int color = Charts.trend(chart);
            g.text(Ui.PIXEL, "PRIX " + Charts.span(chart.span()) + " · CHANGE CHAQUE HEURE", left, top + 6.0f, 3.3f,
                    Ui.Theme.MUTED);
            String change = Charts.change(chart);
            float pillWidth = g.width(Ui.PIXEL, change, 3.3f) + 6.0f;
            p.round(right - pillWidth, top + 4.4f, pillWidth, 6.3f, 2.0f, Paint.alpha(color, 0.18f));
            g.text(Ui.PIXEL, change, right - pillWidth + 3.0f, top + 6.0f, 3.3f, color);
            g.text(Ui.WIDE, Charts.value(chart, chart.last()), left, top + 13.3f, 7.0f, Ui.Theme.TEXT);
            g.right(Ui.PIXEL, "HAUT " + Charts.value(chart, chart.high()), right, top + 13.6f, 3.0f, Ui.Theme.DIM);
            g.right(Ui.PIXEL, "BAS " + Charts.value(chart, chart.low()), right, top + 18.0f, 3.0f, Ui.Theme.DIM);
            float reveal = Anim.easeOut(Anim.progress(picked, 0.6d));
            Charts.full(g, chart, left - 1.0f, top + 24.0f, width - 12.0f, panel - 29.0f, mouseX, mouseY, reveal);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY) || button != 0) {
                return false;
            }
            ShopView.Offer offer = selected();
            if (offer == null) {
                return true;
            }
            boolean shift = (UiScreen.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            for (Hit hit : hits) {
                if (mouseX < hit.x() || mouseX >= hit.x() + hit.width() || mouseY < hit.y()
                        || mouseY >= hit.y() + hit.height()) {
                    continue;
                }
                switch (hit.key()) {
                    case "minus" -> quantity = Math.max(1, quantity - (shift ? 16 : 1));
                    case "plus" -> quantity = Math.min(MAX_QUANTITY, quantity + (shift ? 16 : 1));
                    case "preset0" -> quantity = 1;
                    case "preset1" -> quantity = 16;
                    case "preset2" -> quantity = 64;
                    case "preset3" -> quantity = (int) Math.max(1L, affordable(offer));
                    case "buy" -> {
                        if (offer.buy() * quantity <= view.balance()) {
                            buy();
                        }
                        return true;
                    }
                    default -> {
                        return true;
                    }
                }
                UiScreen.click(1.1f);
                return true;
            }
            return true;
        }
    }
}
