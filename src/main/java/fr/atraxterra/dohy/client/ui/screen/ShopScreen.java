package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.client.ui.widget.Dropdown;
import fr.atraxterra.dohy.client.ui.widget.ItemGrid;
import fr.atraxterra.dohy.client.ui.widget.QuantityField;
import fr.atraxterra.dohy.client.ui.widget.TextField;
import fr.atraxterra.dohy.client.ui.widget.UiButton;
import fr.atraxterra.dohy.shop.ShopCategory;
import fr.atraxterra.dohy.ui.view.ShopView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le shop, sur la maquette de l'« Admin Shop » de Paladium : la grille a
 * gauche (categorie, recherche), le solde et la fiche de l'objet choisi a
 * droite, avec la quantite et les deux boutons.
 */
public class ShopScreen extends UiScreen implements ViewScreen<ShopView> {

    private static final int PANEL_Y = 8;

    private ShopView view;
    private final List<ShopView.Offer> shown = new ArrayList<>();
    private int category;
    private String search = "";
    private Identifier selectedId;

    private ItemGrid grid;
    private QuantityField quantity;
    private UiButton buyButton;
    private UiButton sellButton;

    public ShopScreen(ShopView view) {
        super(Component.literal("Shop"), 460, 248);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return ShopView.ID;
    }

    @Override
    public void accept(ShopView next) {
        this.view = next;
        refilter();
    }

    @Override
    protected void build() {
        List<String> options = new ArrayList<>();
        options.add("All");
        for (ShopCategory value : ShopCategory.values()) {
            options.add(value.display());
        }
        add(new CloseButton(442, PANEL_Y + 7, this::onClose));
        grid = add(new ItemGrid(19, PANEL_Y + 99, 8, 5, 24, 2, this::select));
        add(new TextField(178, PANEL_Y + 78, 70, 13, "Rechercher...", 32, text -> {
            search = text;
            refilter();
        }));
        quantity = add(new QuantityField(269, PANEL_Y + 202, 61, 18, 1, 2304));
        buyButton = add(new UiButton(349, PANEL_Y + 205, 43, 13, "ACHETER", UiButton.Style.RED, () -> trade("buy")));
        sellButton = add(new UiButton(400, PANEL_Y + 205, 43, 13, "VENDRE", UiButton.Style.GREEN, () -> trade("sell")));
        // La liste deroulante en dernier : ouverte, elle passe devant la grille.
        add(new Dropdown(18, PANEL_Y + 79, 71, 12, options, category, index -> {
            category = index;
            refilter();
        }));
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
        if (grid == null) {
            return;
        }
        grid.setStacks(shown.stream().map(ShopView.Offer::stack).toList());
        int index = indexOf(selectedId);
        if (index < 0 && !shown.isEmpty() && selectedId == null) {
            index = 0;
            selectedId = idOf(shown.get(0));
        }
        grid.setSelected(index);
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static Identifier idOf(ShopView.Offer offer) {
        return BuiltInRegistries.ITEM.getKey(offer.stack().getItem());
    }

    private int indexOf(Identifier id) {
        for (int index = 0; index < shown.size(); index++) {
            if (idOf(shown.get(index)).equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private void select(int index) {
        selectedId = idOf(shown.get(index));
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

    private void trade(String verb) {
        ShopView.Offer offer = selected();
        if (offer != null) {
            UiClient.send(ShopView.ID, verb, idOf(offer).toString(), quantity.value());
        }
    }

    // -- Dessin ------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(0, PANEL_Y, 460, 240);
        awning(g);
        g.textShaded(Ui.TITLE, "ADMIN SHOP", 230 - g.width(Ui.TITLE, "ADMIN SHOP", 16) / 2, PANEL_Y + 21, 16,
                Ui.RED, Ui.RED_SHADE, 1.0f);
        g.centered(Ui.BOLD, "VENDEZ OU ACHETEZ DES ITEMS", 230, PANEL_Y + 41, 4.2f, Ui.WHITE);

        // La grille.
        g.rect(9, PANEL_Y + 71, 246, 161, Ui.INSET);

        // Le solde.
        int moneyY = PANEL_Y + 71;
        g.border(260, moneyY, 193, 24, 1, Ui.GREEN);
        g.rect(261, moneyY + 1, 191, 22, 0xFF131217);
        g.text(Ui.WIDE, "VOTRE ARGENT", 269, moneyY + 9.5f, 5, Ui.WHITE);
        g.rect(326, moneyY + 6, 1, 12, Ui.WHITE);
        g.texture(Ui.MONEY, 335, moneyY + 8, 14, 8, 56, 34);
        g.text(Ui.WIDE, MoneyFormat.shortForm(view.balance()), 355, moneyY + 8.5f, 7, Ui.WHITE);
        g.right(Ui.WIDE, "$", 445, moneyY + 8.5f, 7, Ui.WHITE);

        // La fiche.
        int detailY = PANEL_Y + 99;
        g.border(260, detailY, 193, 133, 1, Ui.RED);
        ShopView.Offer offer = selected();
        if (offer == null) {
            g.centered(Ui.BODY, "Choisis un objet a gauche", 356, detailY + 60, 5, Ui.MUTED);
            buyButton.enabled = false;
            sellButton.enabled = false;
            return;
        }
        String name = offer.stack().getHoverName().getString().toUpperCase(Locale.ROOT);
        g.textShaded(Ui.TITLE, g.fit(Ui.TITLE, name, 14, 172), 270, detailY + 8, 14, Ui.WHITE, 0xFF8C8B93, 0.8f);

        g.border(269, detailY + 30, 60, 57, 2, Ui.RED);
        g.rect(271, detailY + 32, 56, 53, 0xFF2A1B1C);
        g.item(offer.stack(), 281, detailY + 40, 36, false);

        String owned = "Tu en as " + offer.owned();
        if (offer.nextStep() > 0) {
            owned += "  ·  collection " + MoneyFormat.shortForm(offer.collected()) + " / "
                    + MoneyFormat.shortForm(offer.nextStep());
        }
        g.text(Ui.BODY, owned, 270, detailY + 90, 3.8f, Ui.MUTED);

        price(g, 343, detailY + 32, "PRIX D'ACHAT", Ui.RED, offer.buy() > 0
                ? MoneyFormat.exact(offer.buy()) + (offer.discount() > 0 ? "  (-" + offer.discount() + " %)" : "")
                : "PAS EN VENTE");
        String sell;
        if (!offer.sellable()) {
            sell = "ACHAT SEUL";
        } else if (offer.locked()) {
            sell = "COLLECTE " + MoneyFormat.shortForm(offer.lockMissing()) + " DE PLUS";
        } else {
            sell = MoneyFormat.exact(offer.sell()) + (offer.sellPercent() < 100 ? "  (" + offer.sellPercent() + " %)" : "");
        }
        price(g, 343, detailY + 62, "PRIX DE VENTE", Ui.GREEN, sell);

        g.text(Ui.WIDE, "QUANTITÉ", 270, detailY + 98, 3.8f, Ui.WHITE);
        buyButton.enabled = offer.buy() > 0;
        sellButton.enabled = offer.sellable() && !offer.locked() && offer.owned() > 0;
    }

    private static void price(UiGraphics g, int x, int y, String label, int color, String value) {
        g.text(Ui.WIDE, label, x, y, 3.8f, color);
        g.rect(x - 1, y + 5, 102, 18, Ui.FIELD);
        g.rect(x - 1, y + 22, 102, 1, 0xFF201F25);
        g.texture(Ui.MONEY, x + 5, y + 11, 11, 7, 56, 34);
        g.text(Ui.WIDE, g.fit(Ui.WIDE, value, 5, 78), x + 21, y + 11.5f, 5, Ui.WHITE);
    }

    /** Le store rouge et blanc du haut. */
    private static void awning(UiGraphics g) {
        int stripes = 13;
        int stripe = 32;
        int left = 22;
        for (int index = 0; index < stripes; index++) {
            boolean red = index % 2 == 0;
            int x = left + index * stripe;
            int face = red ? Ui.RED : 0xFFFFFFFF;
            int shade = red ? Ui.RED_SHADE : 0xFFC9C8CE;
            g.rect(x, 0, stripe, 20, face);
            g.rect(x, 20, stripe, 2, shade);
            g.rect(x + 2, 22, stripe - 4, 1, shade);
        }
    }

    @Override
    protected void drawForeground(UiGraphics g, double mouseX, double mouseY) {
        ShopView.Offer offer = selected();
        if (offer != null && mouseX >= 271 && mouseX < 327 && mouseY >= PANEL_Y + 131 && mouseY < PANEL_Y + 184) {
            g.tooltip(offer.stack());
        }
    }
}
