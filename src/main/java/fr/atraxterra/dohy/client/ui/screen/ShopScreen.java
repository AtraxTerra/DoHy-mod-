package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
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
 * Le shop, dans la coque des menus (maquette validee) : la recherche et les categories
 * a gauche, la grille des objets au centre avec le cours de l'heure, et la fiche de
 * l'objet choisi a droite — quantite, total, acheter, vendre.
 *
 * <p>Tout le catalogue arrive d'un coup : la categorie, la recherche et la selection
 * se font ici sans aller-retour ; seuls l'achat et la vente passent par le serveur.
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
    private final double opened = Anim.now();
    /** 0 pour tout, sinon la categorie d'indice - 1. */
    private int category;
    private String search = "";
    private Identifier selectedId;
    private int quantity = 1;
    private Grid grid;

    public ShopScreen(ShopView view) {
        super(Component.literal("Shop"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        header();
    }

    private void header() {
        shell.header("Shop", "", List.of(new MenuView.Stat("TON ARGENT", MoneyFormat.exact(view.balance()) + " $",
                0xFFFFFFFF)), List.of(), false);
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
        if (selected() == null && !shown.isEmpty()) {
            selectedId = idOf(shown.getFirst());
        }
        if (grid != null) {
            grid.clamp();
        }
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
        return MoneyFormat.shortForm(value) + " $";
    }

    /** La bulle d'un objet, dans le style du kit : son nom, ses prix, ce qu'on en a. */
    private static List<Component> tip(ShopView.Offer offer) {
        List<Component> lines = new ArrayList<>();
        lines.add(offer.stack().getHoverName().copy().withColor(0xFFFFFF));
        if (offer.buy() > 0) {
            lines.add(Component.literal("Achat : " + MoneyFormat.exact(offer.buy()) + " $").withColor(0x22E558));
        }
        if (offer.sellable()) {
            lines.add(Component.literal("Vente : " + MoneyFormat.exact(offer.sell()) + " $").withColor(0xEF8A7D));
        }
        lines.add(Component.literal("Tu en as " + offer.owned()).withColor(0x8B8E95));
        return lines;
    }

    /** Le plus grand nombre achetable avec le solde, plafonne. */
    private long affordable(ShopView.Offer offer) {
        return offer.buy() <= 0 ? 0 : Math.min(MAX_QUANTITY, view.balance() / offer.buy());
    }

    private void trade(String verb) {
        ShopView.Offer offer = selected();
        if (offer == null) {
            return;
        }
        String id = idOf(offer).toString();
        long total = offer.buy() * quantity;
        if ("buy".equals(verb) && total >= 1_000L && total * 10L >= view.balance()) {
            // Un achat qui prend au moins un dixieme du solde : on demande.
            String name = offer.stack().getHoverName().getString();
            int amount = quantity;
            Popup confirm = new Popup(offer.stack(), "Acheter " + amount + " \u00D7 " + name + " ?",
                    offer.course() > 0 ? "Cours de l'heure : " + course(offer.course()) : "", "ACHETER", false,
                    () -> UiClient.send(ShopView.ID, "buy", id, amount));
            openPopup(confirm.sums(List.of(new String[]{"TOTAL", MoneyFormat.exact(total) + " $"},
                    new String[]{"IL TE RESTERA", MoneyFormat.exact(Math.max(0L, view.balance() - total)) + " $"})));
            return;
        }
        UiClient.send(ShopView.ID, verb, id, quantity);
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

    /** La liste des categories, avec le nombre d'objets de chacune. */
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
            p.framed(x, y, width, height, 5.3f, 0xFF141518, 0xFF24262A, 1);
            g.text(Ui.PIXEL, "CATEGORIES", x + 5.3f, y + 5.3f, 3.3f, Ui.Theme.DIM);
            for (int index = 0; index <= ShopCategory.values().length; index++) {
                float rowY = y + 12.0f + index * ROW;
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
            int index = (int) ((mouseY - (y + 12.0f)) / ROW);
            if (index >= 0 && index <= ShopCategory.values().length && index != category) {
                category = index;
                UiScreen.click(1.1f);
                grid.resetScroll();
                refilter();
            }
            return true;
        }
    }

    /** La grille des objets, qui defile. */
    private final class Grid extends UiElement {
        private final Anim.Value scroll = new Anim.Value(0.0f, 18.0f);
        private final Map<Identifier, Anim.Value> hovers = new HashMap<>();
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

        private float total() {
            int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
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
            if (shown.isEmpty()) {
                g.centered(Ui.BODY, search.isEmpty() ? "Rien dans cette categorie" : "Aucun objet ne correspond",
                        x + width / 2.0f, y + 40.0f, 5.3f, Ui.Theme.MUTED);
                return;
            }
            float cardWidth = cardWidth();
            boolean inside = contains(mouseX, mouseY);
            ShopView.Offer hovered = null;
            g.scissor(x - 4, y - 1, width + 8, height + 1);
            for (int index = 0; index < shown.size(); index++) {
                ShopView.Offer offer = shown.get(index);
                float cardX = x + (index % COLUMNS) * (cardWidth + CARD_GAP);
                float cardY = y + (index / COLUMNS) * (CARD_HEIGHT + CARD_GAP) - offset;
                if (cardY > y + height || cardY + CARD_HEIGHT < y) {
                    continue;
                }
                Identifier id = idOf(offer);
                boolean over = inside && mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY
                        && mouseY < cardY + CARD_HEIGHT;
                if (over) {
                    hovered = offer;
                }
                float hover = hovers.computeIfAbsent(id, key -> new Anim.Value(0.0f, 16.0f)).toward(over ? 1.0f : 0.0f);
                card(g, p, offer, cardX, cardY - 1.3f * hover, cardWidth, hover, id.equals(selectedId));
            }
            g.endScissor();
            if (total() > height) {
                float barX = x + width - 2.0f;
                p.round(barX, y, 2.0f, height, 1.0f, 0xFF24262A);
                float thumb = Math.max(12.0f, height * height / total());
                float thumbY = y + (height - thumb) * offset / Math.max(1.0f, total() - height);
                p.round(barX, thumbY, 2.0f, thumb, 1.0f, 0xFF5A5D64);
            }
            if (hovered != null) {
                g.tooltip(tip(hovered));
            }
        }

        private void card(UiGraphics g, Paint p, ShopView.Offer offer, float cardX, float cardY, float cardWidth,
                          float hover, boolean selected) {
            if (hover > 0.01f || selected) {
                p.shadow(cardX + 2, cardY + 4, cardWidth - 4, CARD_HEIGHT - 4, 8,
                        Paint.alpha(selected ? Ui.Theme.RED : 0xFF000000, selected ? 0.22f : 0.3f * hover));
            }
            int edge = selected ? Ui.Theme.RED : Paint.mix(Ui.Theme.EDGE, 0xFF45484F, hover);
            int fill = selected ? 0xFF2A1F20 : Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover);
            p.framed(cardX, cardY, cardWidth, CARD_HEIGHT, 4.7f, fill, edge, selected ? 2 : 1);
            if (offer.course() > 0) {
                int delta = offer.course() - 100;
                int color = delta > 0 ? Ui.Theme.GREEN_L : delta < 0 ? Ui.Theme.RED_LIGHT : Ui.Theme.MUTED;
                g.right(Ui.PIXEL, course(offer.course()), cardX + cardWidth - 4.0f, cardY + 4.0f, 3.3f, color);
            }
            g.item(offer.stack(), cardX + (cardWidth - 16.0f) / 2.0f, cardY + 7.0f, 16.0f, false);
            List<String> name = g.wrap(Ui.BOLD, offer.stack().getHoverName().getString(), 4.3f, cardWidth - 6.0f);
            float nameY = cardY + 26.0f - (name.size() > 1 ? 2.6f : 0.0f);
            for (int line = 0; line < Math.min(2, name.size()); line++) {
                String text = line == 1 && name.size() > 2 ? g.fit(Ui.BOLD, name.get(1) + " ...", 4.3f, cardWidth - 6.0f)
                        : name.get(line);
                g.centered(Ui.BOLD, text, cardX + cardWidth / 2.0f, nameY + line * 5.3f, 4.3f, Ui.Theme.TEXT);
            }
            float priceY = cardY + CARD_HEIGHT - 8.7f;
            String buy = offer.buy() > 0 ? money(offer.buy()) : "";
            String sell = offer.sellable() ? money(offer.sell()) : "";
            // Deux grands prix ne tiennent pas cote a cote : la police se resserre.
            float size = 4.0f;
            while (size > 3.0f && g.width(Ui.BOLD, buy, size) + g.width(Ui.BOLD, sell, size) + 4.0f > cardWidth - 8.0f) {
                size -= 0.25f;
            }
            if (!buy.isEmpty()) {
                g.text(Ui.BOLD, buy, cardX + 4.0f, priceY + (4.0f - size) / 2.0f, size, Ui.Theme.GREEN_L);
            }
            if (!sell.isEmpty()) {
                g.right(Ui.BOLD, sell, cardX + cardWidth - 4.0f, priceY + (4.0f - size) / 2.0f, size,
                        offer.locked() ? Ui.Theme.DIM : Ui.Theme.MUTED);
            }
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
            if (column >= 0 && column < COLUMNS && index >= 0 && index < shown.size()) {
                Identifier id = idOf(shown.get(index));
                if (!id.equals(selectedId)) {
                    selectedId = id;
                    quantity = 1;
                    UiScreen.click(1.2f);
                }
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

    /** La fiche de l'objet choisi : prix, cours, quantite, total, et les deux boutons. */
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
            float panelHeight = 178.0f;
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
            // L'objet.
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
            ShopCategory kind = ShopCategory.byKey(offer.category());
            g.text(Ui.BODY, kind == null ? "" : kind.display(), textX, textY, 4.7f, Ui.Theme.MUTED);
            g.text(Ui.BODY, "Tu en as " + offer.owned(), textX, textY + 6.6f, 4.7f, Ui.Theme.TEXT2);

            // Les prix.
            float boxY = y + 44.0f;
            float boxWidth = (inner - 4.0f) / 2.0f;
            price(g, p, left, boxY, boxWidth, "ACHAT", offer.buy() > 0 ? MoneyFormat.exact(offer.buy()) + " $"
                    : "-", Ui.Theme.GREEN_L, offer.discount() > 0 ? "-" + offer.discount() + " %" : "");
            String sell = !offer.sellable() ? "-" : MoneyFormat.exact(offer.sell()) + " $";
            price(g, p, left + boxWidth + 4.0f, boxY, boxWidth, "VENTE", sell,
                    offer.locked() ? Ui.Theme.DIM : 0xFFEF8A7D,
                    offer.sellable() && offer.sellPercent() != 100 ? offer.sellPercent() + " %" : "");

            // Le cours, la collection.
            float infoY = boxY + 24.0f;
            if (offer.course() > 0) {
                int delta = offer.course() - 100;
                g.text(Ui.PIXEL, "COURS DE L'HEURE " + course(offer.course()) + " · " + view.courseMinutes() + " MIN",
                        left, infoY, 3.3f, delta >= 0 ? Ui.Theme.GREEN_L : Ui.Theme.RED_LIGHT);
                infoY += 6.0f;
            }
            if (offer.locked()) {
                g.text(Ui.PIXEL, "VENTE APRES " + MoneyFormat.shortForm(offer.lockMissing()) + " DE COLLECTE", left,
                        infoY, 3.3f, Ui.Theme.YELLOW);
                infoY += 6.0f;
            } else if (offer.nextStep() > 0) {
                g.text(Ui.PIXEL, "COLLECTION " + MoneyFormat.shortForm(offer.collected()) + " / "
                        + MoneyFormat.shortForm(offer.nextStep()), left, infoY, 3.3f, Ui.Theme.MUTED);
                infoY += 4.7f;
                float progress = Math.min(1.0f, offer.collected() / (float) Math.max(1L, offer.nextStep()));
                p.round(left, infoY, inner, 2.0f, 1.0f, 0xFF2C2E33);
                p.round(left, infoY, Math.max(2.0f, inner * progress), 2.0f, 1.0f, Ui.Theme.YELLOW);
                infoY += 4.7f;
            }

            // La quantite.
            float quantityY = Math.max(infoY + 2.0f, y + 86.0f);
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

            // Le total et les boutons.
            float totalY = presetY + 16.0f;
            long unit = offer.buy() > 0 ? offer.buy() : offer.sell();
            g.text(Ui.PIXEL, offer.buy() > 0 ? "TOTAL" : "TOTAL VENTE", left, totalY + 2.0f, 3.3f, Ui.Theme.MUTED);
            g.right(Ui.WIDE, MoneyFormat.exact(unit * quantity) + " $", left + inner, totalY, 7.3f, Ui.Theme.TEXT);
            float buttonY = totalY + 12.0f;
            float buttonWidth = (inner - 4.0f) / 2.0f;
            boolean canBuy = offer.buy() > 0 && offer.buy() * quantity <= view.balance();
            boolean canSell = offer.sellable() && !offer.locked() && offer.owned() > 0;
            float buy = hover("buy", mouseX, mouseY, left, buttonY, buttonWidth, BUTTON);
            Buttons.draw(g, p, left, buttonY, buttonWidth, BUTTON, "ACHETER", Buttons.GREEN, buy, canBuy, 5.3f);
            float sellHover = hover("sell", mouseX, mouseY, left + buttonWidth + 4.0f, buttonY, buttonWidth, BUTTON);
            Buttons.draw(g, p, left + buttonWidth + 4.0f, buttonY, buttonWidth, BUTTON, "VENDRE", Buttons.RED,
                    sellHover, canSell, 5.3f);
            if (mouseX >= left && mouseX < left + 30 && mouseY >= y + 8 && mouseY < y + 38) {
                g.tooltip(tip(offer));
            }
        }

        private void price(UiGraphics g, Paint p, float boxX, float boxY, float boxWidth, String label, String value,
                           int color, String note) {
            p.framed(boxX, boxY, boxWidth, 19.3f, 3.3f, 0xFF16171A, 0xFF2A2C31, 1);
            g.text(Ui.PIXEL, label, boxX + 4.7f, boxY + 3.6f, 3.3f, Ui.Theme.MUTED);
            if (!note.isEmpty()) {
                g.right(Ui.PIXEL, note, boxX + boxWidth - 4.0f, boxY + 3.6f, 3.3f, Ui.Theme.YELLOW);
            }
            g.text(Ui.WIDE, g.fit(Ui.WIDE, value, 6.0f, boxWidth - 8.0f), boxX + 4.7f, boxY + 9.6f, 6.0f, color);
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
                    case "preset3" -> quantity = (int) Math.max(1L, offer.sellable() && offer.owned() > 0
                            && !offer.locked() ? Math.min(MAX_QUANTITY, offer.owned()) : affordable(offer));
                    case "buy" -> {
                        if (offer.buy() > 0 && offer.buy() * quantity <= view.balance()) {
                            trade("buy");
                        }
                        return true;
                    }
                    case "sell" -> {
                        if (offer.sellable() && !offer.locked() && offer.owned() > 0) {
                            trade("sell");
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
