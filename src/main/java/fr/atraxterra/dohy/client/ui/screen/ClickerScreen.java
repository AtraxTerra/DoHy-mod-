package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.palamod.clicker.ClickerFormat;
import fr.atraxterra.dohy.palamod.clicker.ClickerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Le clicker ({@code UIClicker} de PalaClicker) : a gauche l'aliment a
 * cliquer, le portefeuille et la production par seconde ; au centre les
 * ameliorations, sous leur fleche, et le Clic Shop ; a droite les batiments.
 *
 * <p>Le compteur avance entre deux vues du serveur : de la production chaque
 * seconde, et de chaque clic, envoyes par paquets. Le serveur reste seul juge
 * — a chaque vue, le compteur se recale sur lui.
 */
public class ClickerScreen extends UiScreen implements ViewScreen<ClickerView> {

    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;

    private static final int LEFT_X = 6;
    private static final int COLUMN_W = 124;
    private static final int CENTER_X = 138;
    private static final int CENTER_W = 204;
    private static final int RIGHT_X = 350;
    private static final int TOP = 6;
    private static final int BOTTOM = 264;

    /** L'aliment : son centre, son cote, et le rayon ou un clic compte. */
    private static final int FOOD_X = LEFT_X + COLUMN_W / 2;
    private static final int FOOD_Y = 132;
    private static final int FOOD_SIZE = 56;
    private static final int FOOD_RADIUS = 40;

    private static final int CELL = 20;
    private static final int UPGRADE_COLUMNS = 10;
    private static final int UPGRADES_Y = 24;
    private static final int UPGRADE_ROWS = 4;
    private static final int UPGRADE_ROWS_OPEN = 11;

    private static final int SHELF_W = 64;
    private static final int SHELF_GAP = 4;
    private static final int SHELF_Y = 138;
    private static final int AWNING_H = 42;
    private static final int SLOT = 15;

    private static final int ROW_H = 24;
    private static final int LIST_Y = 24;

    private static final long CLICK_BATCH_MS = 200L;
    private static final long ARM_MS = 3000L;

    private static final Identifier COIN = texture("coin");
    private static final Identifier GLOW = texture("item_glow");
    private static final Identifier OVERLAY = texture("left_overlay");
    private static final Identifier BANNER = texture("click_banner");
    private static final Identifier NAMUU = texture("namuu");
    private static final Identifier SLOT_TEXTURE = texture("slot");
    private static final Identifier UNKNOWN = texture("unknown");

    private static final int GOLD = 0xFFF2B33A;
    private static final int AFFORD = 0xFF6FE08A;
    private static final int TOO_DEAR = 0xFFE0705F;

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/gui/clicker/" + name + ".png");
    }

    /** Un « +N » qui monte depuis le curseur. */
    private record Rise(float x, float y, long start, String text) {
    }

    /** Un aliment qui tombe dans la colonne de gauche. */
    private record Fall(float x, float speed, float spin, long start) {
    }

    private ClickerView view;
    private long viewAt;
    /** Les clics faits depuis l'ouverture, plus ceux que le serveur avait deja comptes. */
    private int clicks;
    private int unsent;
    private long lastBatch;
    private long lastClickAt;
    private boolean pressed;
    private boolean upgradesOpen;
    private float listScroll;
    private float upgradeScroll;
    private String armed = "";
    private long armedAt;
    private final List<Rise> rises = new ArrayList<>();
    private final List<Fall> falls = new ArrayList<>();
    private final Random random = new Random();

    public ClickerScreen(ClickerView view) {
        super(Component.literal("Clicker"), WIDTH, HEIGHT);
        this.view = view;
        this.viewAt = System.currentTimeMillis();
        this.clicks = view.clicks();
    }

    @Override
    public Identifier viewId() {
        return ClickerView.ID;
    }

    @Override
    public void accept(ClickerView next) {
        this.view = next;
        this.viewAt = System.currentTimeMillis();
        this.clicks = Math.max(clicks, next.clicks());
    }

    @Override
    protected void build() {
        add(new CloseButton(WIDTH - 16, 8, this::onClose));
    }

    @Override
    public void removed() {
        flush();
        super.removed();
    }

    // -- Compteur --------------------------------------------------------------

    /** Les ClicCoins a cet instant : la derniere vue, la production depuis, les clics pas encore comptes. */
    private double points(long now) {
        double waiting = Math.max(0, clicks - view.clicks()) * view.click();
        return view.points() + waiting + view.rps() * (now - viewAt) / 1000.0d;
    }

    private void flush() {
        if (unsent > 0) {
            UiClient.send(ClickerView.ID, "click", "", unsent);
            unsent = 0;
        }
        lastBatch = System.currentTimeMillis();
    }

    private void feed(double x, double y) {
        long now = System.currentTimeMillis();
        clicks++;
        unsent++;
        lastClickAt = now;
        rises.add(new Rise((float) x + random.nextInt(9) - 4, (float) y - 6, now,
                "+" + ClickerFormat.compact(view.click())));
        if (falls.size() < 36) {
            falls.add(new Fall(LEFT_X + 8 + random.nextInt(COLUMN_W - 26), 60.0f + random.nextInt(50),
                    random.nextFloat() * 2.0f - 1.0f, now));
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 0.8f + random.nextFloat() * 0.6f, 0.18f));
    }

    // -- Geometrie -------------------------------------------------------------

    private int upgradeRows() {
        return upgradesOpen ? UPGRADE_ROWS_OPEN : UPGRADE_ROWS;
    }

    private int upgradesBottom() {
        return UPGRADES_Y + upgradeRows() * CELL;
    }

    /** L'amelioration sous le curseur, ou -1. */
    private int upgradeAt(double x, double y) {
        if (x < CENTER_X + 2 || x >= CENTER_X + 2 + UPGRADE_COLUMNS * CELL || y < UPGRADES_Y || y >= upgradesBottom()) {
            return -1;
        }
        int column = (int) ((x - CENTER_X - 2) / CELL);
        int row = (int) ((y - UPGRADES_Y + upgradeScroll) / CELL);
        int index = row * UPGRADE_COLUMNS + column;
        return index >= 0 && index < view.upgrades().size() ? index : -1;
    }

    private float maxUpgradeScroll() {
        int rows = (view.upgrades().size() + UPGRADE_COLUMNS - 1) / UPGRADE_COLUMNS;
        return Math.max(0, (rows - upgradeRows()) * CELL);
    }

    private int listRows() {
        return view.buildings().size() + (view.more() ? 1 : 0);
    }

    private float maxListScroll() {
        return Math.max(0, listRows() * ROW_H - (BOTTOM - 2 - LIST_Y));
    }

    /** Le batiment sous le curseur, ou -1 (la ligne « ??? » ne compte pas). */
    private int buildingAt(double x, double y) {
        if (x < RIGHT_X + 2 || x >= RIGHT_X + COLUMN_W - 2 || y < LIST_Y || y >= BOTTOM - 2) {
            return -1;
        }
        int index = (int) ((y - LIST_Y + listScroll) / ROW_H);
        return index >= 0 && index < view.buildings().size() ? index : -1;
    }

    private static int shelfX(int shelf) {
        return CENTER_X + 2 + shelf * (SHELF_W + SHELF_GAP);
    }

    /** Les colonnes d'un rayon : huit offres sur quatre colonnes, quatre sur deux. */
    private static int shelfColumns(ClickerView.Shelf shelf) {
        return "weekly".equals(shelf.type()) ? 4 : 2;
    }

    private static int slotX(int shelfIndex, ClickerView.Shelf shelf, int offer) {
        int columns = shelfColumns(shelf);
        return shelfX(shelfIndex) + (SHELF_W - columns * SLOT) / 2 + (offer % columns) * SLOT;
    }

    private static int slotY(ClickerView.Shelf shelf, int offer) {
        return SHELF_Y + AWNING_H + 2 + (offer / shelfColumns(shelf)) * SLOT;
    }

    /** L'offre sous le curseur : {rayon, offre}, ou {@code null}. */
    private int[] offerAt(double x, double y) {
        if (upgradesOpen) {
            return null;
        }
        List<ClickerView.Shelf> shop = view.shop();
        for (int shelfIndex = 0; shelfIndex < shop.size(); shelfIndex++) {
            ClickerView.Shelf shelf = shop.get(shelfIndex);
            for (int offer = 0; offer < shelf.offers().size(); offer++) {
                int slotX = slotX(shelfIndex, shelf, offer);
                int slotY = slotY(shelf, offer);
                if (x >= slotX && x < slotX + SLOT && y >= slotY && y < slotY + SLOT) {
                    return new int[]{shelfIndex, offer};
                }
            }
        }
        return null;
    }

    private static boolean onFood(double x, double y) {
        double dx = x - FOOD_X;
        double dy = y - FOOD_Y;
        return dx * dx + dy * dy <= FOOD_RADIUS * FOOD_RADIUS;
    }

    private boolean onArrow(double x, double y) {
        return x >= CENTER_X + CENTER_W - 14 && x < CENTER_X + CENTER_W - 2 && y >= TOP + 3 && y < TOP + 15;
    }

    // -- Rendu -----------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        if (unsent > 0 && now - lastBatch >= CLICK_BATCH_MS) {
            flush();
        }
        upgradeScroll = Math.min(upgradeScroll, maxUpgradeScroll());
        listScroll = Math.min(listScroll, maxListScroll());
        double points = points(now);

        g.panelPlain(0, 0, WIDTH, HEIGHT);
        column(g, LEFT_X, COLUMN_W);
        column(g, CENTER_X, CENTER_W);
        column(g, RIGHT_X, COLUMN_W);

        drawLeft(g, mouseX, mouseY, now, points);
        drawUpgrades(g, mouseX, mouseY, points);
        if (!upgradesOpen) {
            drawShop(g, mouseX, mouseY, now, points);
        }
        drawBuildings(g, mouseX, mouseY, points);
    }

    private static void column(UiGraphics g, int x, int width) {
        g.rect(x - 1, TOP - 1, width + 2, BOTTOM - TOP + 2, Ui.FRAME);
        g.rect(x, TOP, width, BOTTOM - TOP, Ui.INSET);
    }

    private void drawLeft(UiGraphics g, double mouseX, double mouseY, long now, double points) {
        g.texture(OVERLAY, LEFT_X, TOP, COLUMN_W, BOTTOM - TOP, 509, 753, 0x50FFFFFF);
        g.textShaded(Ui.TITLE, "CLICKER", LEFT_X + 7, TOP + 6, 11, Ui.RED, Ui.RED_SHADE, 1.0f);

        // Le portefeuille : une piece, le compte, et la production en dessous.
        String amount = ClickerFormat.compact(points);
        float size = 11.0f;
        float width = g.width(Ui.BOLD, amount, size);
        if (width > COLUMN_W - 26) {
            size *= (COLUMN_W - 26) / width;
            width = COLUMN_W - 26;
        }
        float start = FOOD_X - (width + 13) / 2.0f;
        g.texture(COIN, Math.round(start), 31, 11, 11, 64, 64);
        g.text(Ui.BOLD, amount, start + 13, 32, size, Ui.WHITE);
        g.centered(Ui.BODY, "ClicCoins", FOOD_X, 45, 4.4f, Ui.MUTED);
        g.centered(Ui.WIDE, "PAR SECONDE", FOOD_X, 56, 4.2f, Ui.MUTED);
        g.centered(Ui.BOLD, ClickerFormat.rate(view.rps()), FOOD_X, 63, 7, GOLD);

        // Les aliments qui tombent, derriere celui qu'on clique.
        g.scissor(LEFT_X, TOP, COLUMN_W, BOTTOM - TOP);
        for (Iterator<Fall> iterator = falls.iterator(); iterator.hasNext(); ) {
            Fall fall = iterator.next();
            float seconds = (now - fall.start()) / 1000.0f;
            float y = 74 + fall.speed() * seconds + 120.0f * seconds * seconds;
            if (y > BOTTOM) {
                iterator.remove();
                continue;
            }
            g.item(view.clickItem(), fall.x() + fall.spin() * 14.0f * seconds, y, 9, false);
        }
        g.endScissor();
        g.raw().nextStratum();

        // L'aliment : il enfle a chaque clic, s'ecrase tant que le bouton est tenu.
        boolean hovered = onFood(mouseX, mouseY);
        float bounce = (float) Math.exp(-(now - lastClickAt) / 110.0d);
        float scale = (pressed && hovered ? 0.9f : hovered ? 1.05f : 1.0f) + 0.14f * bounce;
        float glow = 96.0f + 10.0f * (float) Math.sin(now / 420.0d) + 18.0f * bounce;
        g.texture(GLOW, Math.round(FOOD_X - glow / 2), Math.round(FOOD_Y - glow / 2), Math.round(glow),
                Math.round(glow), 414, 414, hovered ? 0xFFFFFFFF : 0xC0FFFFFF);
        float food = FOOD_SIZE * scale;
        g.item(view.clickItem(), FOOD_X - food / 2, FOOD_Y - food / 2, food, false);
        g.raw().nextStratum();

        for (Iterator<Rise> iterator = rises.iterator(); iterator.hasNext(); ) {
            Rise rise = iterator.next();
            float age = (now - rise.start()) / 700.0f;
            if (age >= 1.0f) {
                iterator.remove();
                continue;
            }
            int alpha = Math.round(255 * (1.0f - age * age));
            g.centered(Ui.BOLD, rise.text(), rise.x(), rise.y() - 26.0f * age, 6, (alpha << 24) | 0xFFFFFF);
        }

        // Le bandeau du bas : ce que vaut un clic, et le jour de la saison.
        g.texture(BANNER, LEFT_X, BOTTOM - 44, COLUMN_W, 44, 509, 179);
        // La piece du bandeau occupe son haut : le texte passe dessous.
        g.centered(Ui.BOLD, "+" + ClickerFormat.compact(view.click()) + " PAR CLIC", FOOD_X, BOTTOM - 25, 6.0f,
                Ui.WHITE);
        g.centered(Ui.WIDE, "CLIQUE POUR NOURRIR LES NAMUUS", FOOD_X, BOTTOM - 16.5f, 3.2f, Ui.TEXT);
        g.centered(Ui.BODY, "Jour " + view.day() + " du clicker", FOOD_X, BOTTOM - 10, 3.8f, 0xFFD9C9B8);
    }

    private void drawUpgrades(UiGraphics g, double mouseX, double mouseY, double points) {
        g.text(Ui.WIDE, "AMÉLIORATIONS", CENTER_X + 6, TOP + 6, 6, Ui.WHITE);
        g.right(Ui.BODY, view.owned() + " / " + view.upgradeCount(), CENTER_X + CENTER_W - 18, TOP + 7, 4.6f,
                Ui.MUTED);
        // La fleche qui deplie les ameliorations : un chevron, vers le bas puis vers le haut.
        int chevron = onArrow(mouseX, mouseY) ? Ui.WHITE : 0xFFB8B7C0;
        for (int step = 0; step < 4; step++) {
            int row = upgradesOpen ? 3 - step : step;
            g.rect(CENTER_X + CENTER_W - 12 + step, TOP + 7 + row, 8 - 2 * step, 1, chevron);
        }

        int bottom = upgradesBottom();
        List<ClickerView.Upgrade> upgrades = view.upgrades();
        if (upgrades.isEmpty()) {
            g.centered(Ui.BODY, "Aucune amélioration à portée : achète des bâtiments.", CENTER_X + CENTER_W / 2f,
                    UPGRADES_Y + 24, 4.4f, Ui.DISABLED);
        }
        int hovered = upgradeAt(mouseX, mouseY);
        g.scissor(CENTER_X + 2, UPGRADES_Y, UPGRADE_COLUMNS * CELL, bottom - UPGRADES_Y);
        for (int index = 0; index < upgrades.size(); index++) {
            ClickerView.Upgrade upgrade = upgrades.get(index);
            int x = CENTER_X + 2 + (index % UPGRADE_COLUMNS) * CELL;
            int y = Math.round(UPGRADES_Y + (index / UPGRADE_COLUMNS) * CELL - upgradeScroll);
            if (y + CELL < UPGRADES_Y || y > bottom) {
                continue;
            }
            boolean buyable = upgrade.ready() && points >= upgrade.price();
            g.rect(x + 1, y + 1, CELL - 2, CELL - 2, index == hovered ? Ui.SLOT_HOVER : Ui.SLOT_FILL);
            g.border(x + 1, y + 1, CELL - 2, CELL - 2, 1,
                    buyable ? AFFORD : upgrade.ready() ? 0xFF6A6973 : 0xFF3A3940);
            g.item(upgrade.icon(), x + 3, y + 3, 14, false);
        }
        g.raw().nextStratum();
        // Le type, au coin : plusieurs ameliorations d'un batiment portent son icone.
        for (int index = 0; index < upgrades.size(); index++) {
            int x = CENTER_X + 2 + (index % UPGRADE_COLUMNS) * CELL;
            int y = Math.round(UPGRADES_Y + (index / UPGRADE_COLUMNS) * CELL - upgradeScroll);
            if (y + CELL >= UPGRADES_Y && y <= bottom) {
                g.right(Ui.PIXEL, upgrades.get(index).badge(), x + CELL - 2.5f, y + CELL - 7.5f, 4.0f, GOLD);
            }
        }
        // Une amelioration dont les conditions ne sont pas remplies est voilee.
        for (int index = 0; index < upgrades.size(); index++) {
            if (upgrades.get(index).ready()) {
                continue;
            }
            int x = CENTER_X + 2 + (index % UPGRADE_COLUMNS) * CELL;
            int y = Math.round(UPGRADES_Y + (index / UPGRADE_COLUMNS) * CELL - upgradeScroll);
            if (y + CELL >= UPGRADES_Y && y <= bottom) {
                g.rect(x + 2, y + 2, CELL - 4, CELL - 4, 0xA0101014);
            }
        }
        g.endScissor();
    }

    private void drawShop(UiGraphics g, double mouseX, double mouseY, long now, double points) {
        int top = upgradesBottom() + 6;
        g.rect(CENTER_X + 4, top - 3, CENTER_W - 8, 1, Ui.FRAME);
        g.texture(NAMUU, CENTER_X + CENTER_W - 36, top - 2, 30, 27, 209, 188);
        g.text(Ui.WIDE, "CLIC SHOP", CENTER_X + 6, top + 4, 6, Ui.WHITE);
        g.text(Ui.BODY, "Échange tes pièces obtenues via le clicker", CENTER_X + 6, top + 14, 4.2f, Ui.MUTED);

        int[] hovered = offerAt(mouseX, mouseY);
        List<ClickerView.Shelf> shop = view.shop();
        for (int shelfIndex = 0; shelfIndex < shop.size(); shelfIndex++) {
            ClickerView.Shelf shelf = shop.get(shelfIndex);
            int x = shelfX(shelfIndex);
            g.texture(texture(shelf.type()), x, SHELF_Y, SHELF_W, AWNING_H, 328, 218);
            g.centered(Ui.WIDE, g.fit(Ui.WIDE, shelf.title().toUpperCase(java.util.Locale.ROOT), 3.5f, SHELF_W - 12),
                    x + SHELF_W / 2f, SHELF_Y + 3.5f, 3.5f, 0xFF1B1A20);
            g.centered(Ui.BODY, remaining(shelf.seconds() - (now - viewAt) / 1000L), x + SHELF_W / 2f,
                    SHELF_Y + 28.5f, 3.4f, Ui.WHITE);
            if (shelf.offers().isEmpty()) {
                g.centered(Ui.BODY, "Rien pour l'instant", x + SHELF_W / 2f, SHELF_Y + AWNING_H + 8, 3.8f,
                        Ui.DISABLED);
            }
            for (int offer = 0; offer < shelf.offers().size(); offer++) {
                ClickerView.Offer entry = shelf.offers().get(offer);
                int slotX = slotX(shelfIndex, shelf, offer);
                int slotY = slotY(shelf, offer);
                boolean isHovered = hovered != null && hovered[0] == shelfIndex && hovered[1] == offer;
                g.texture(SLOT_TEXTURE, slotX, slotY, SLOT, SLOT, 43, 43, isHovered ? 0xFFFFFFFF : 0xFFC8C8D0);
                if (isArmed(shelf, entry, now)) {
                    g.border(slotX, slotY, SLOT, SLOT, 1, GOLD);
                } else if (entry.left() > 0 && points >= entry.price()) {
                    g.border(slotX, slotY, SLOT, SLOT, 1, 0x806FE08A);
                }
                g.item(entry.stack(), slotX + 2, slotY + 2, SLOT - 4, true);
            }
        }
        g.raw().nextStratum();
        for (int shelfIndex = 0; shelfIndex < shop.size(); shelfIndex++) {
            ClickerView.Shelf shelf = shop.get(shelfIndex);
            for (int offer = 0; offer < shelf.offers().size(); offer++) {
                if (shelf.offers().get(offer).left() <= 0) {
                    g.rect(slotX(shelfIndex, shelf, offer) + 1, slotY(shelf, offer) + 1, SLOT - 2, SLOT - 2,
                            0xB0101014);
                }
            }
        }
    }

    private boolean isArmed(ClickerView.Shelf shelf, ClickerView.Offer offer, long now) {
        return armed.equals(shelf.type() + ":" + offer.id()) && now - armedAt < ARM_MS;
    }

    /** « 3 j 4 h », « 5 h 12 min », « 48 s ». */
    private static String remaining(long seconds) {
        seconds = Math.max(0L, seconds);
        if (seconds >= 86_400L) {
            return seconds / 86_400L + " j " + (seconds % 86_400L) / 3_600L + " h";
        }
        if (seconds >= 3_600L) {
            return seconds / 3_600L + " h " + (seconds % 3_600L) / 60L + " min";
        }
        if (seconds >= 60L) {
            return seconds / 60L + " min";
        }
        return seconds + " s";
    }

    private void drawBuildings(UiGraphics g, double mouseX, double mouseY, double points) {
        g.text(Ui.WIDE, "BÂTIMENTS", RIGHT_X + 6, TOP + 6, 6, Ui.WHITE);
        int hovered = buildingAt(mouseX, mouseY);
        List<ClickerView.Building> buildings = view.buildings();
        g.scissor(RIGHT_X + 1, LIST_Y, COLUMN_W - 2, BOTTOM - 2 - LIST_Y);
        for (int index = 0; index < listRows(); index++) {
            int y = Math.round(LIST_Y + index * ROW_H - listScroll);
            if (y + ROW_H < LIST_Y || y > BOTTOM) {
                continue;
            }
            int x = RIGHT_X + 2;
            int width = COLUMN_W - 4;
            if (index >= buildings.size()) {
                // Le batiment suivant : cache tant que le dernier n'est pas achete.
                g.rect(x, y + 1, width, ROW_H - 2, 0xFF1B1A20);
                g.texture(UNKNOWN, x + 5, y + 5, 14, 14, 42, 42, 0xFF6A6973);
                g.text(Ui.BOLD, "???", x + 26, y + 8, 6, Ui.DISABLED);
                continue;
            }
            ClickerView.Building building = buildings.get(index);
            boolean affordable = points >= building.price() && building.count() < 99;
            g.rect(x, y + 1, width, ROW_H - 2, index == hovered ? Ui.SLOT_HOVER : 0xFF211F27);
            g.rect(x, y + 1, 2, ROW_H - 2, affordable ? AFFORD : building.count() > 0 ? 0xFF6A6973 : 0xFF3A3940);
            g.item(building.icon(), x + 5, y + 3, 18, false);
            g.text(Ui.BOLD, g.fit(Ui.BOLD, building.name(), 4.6f, width - 48), x + 27, y + 4.5f, 4.6f, Ui.TEXT);
            g.texture(COIN, x + 27, y + 13, 6, 6, 64, 64);
            g.text(Ui.BODY, building.count() >= 99 ? "Complet" : ClickerFormat.compact(building.price()), x + 35,
                    y + 13.5f, 4.4f, building.count() >= 99 ? Ui.MUTED : affordable ? AFFORD : TOO_DEAR);
            g.right(Ui.BOLD, String.valueOf(building.count()), x + width - 4, y + 7, 9,
                    building.count() > 0 ? Ui.WHITE : 0xFF4A4955);
        }
        g.endScissor();
        float max = maxListScroll();
        if (max > 0) {
            int track = BOTTOM - 2 - LIST_Y;
            int thumb = Math.max(12, Math.round(track * track / (float) (listRows() * ROW_H)));
            g.rect(RIGHT_X + COLUMN_W - 2, LIST_Y, 1, track, Ui.SCROLL_TRACK);
            g.rect(RIGHT_X + COLUMN_W - 2, LIST_Y + Math.round((track - thumb) * listScroll / max), 1, thumb,
                    Ui.SCROLL_THUMB);
        }
    }

    // -- Bulles ----------------------------------------------------------------

    @Override
    protected void drawForeground(UiGraphics g, double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        double points = points(now);
        int upgradeIndex = upgradeAt(mouseX, mouseY);
        if (upgradeIndex >= 0) {
            ClickerView.Upgrade upgrade = view.upgrades().get(upgradeIndex);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(upgrade.name()));
            lines.add(Component.literal(upgrade.effect()));
            lines.add(Component.literal("Coût : " + ClickerFormat.compact(upgrade.price()) + " ClicCoins"));
            for (String condition : upgrade.conditions()) {
                lines.add(Component.literal((condition.startsWith("+") ? "Fait : " : "À faire : ")
                        + condition.substring(1)));
            }
            lines.add(Component.literal(!upgrade.ready() ? "Conditions non remplies"
                    : points >= upgrade.price() ? "Clic : acheter" : "Pas assez de ClicCoins"));
            g.tooltip(lines);
            return;
        }
        int buildingIndex = buildingAt(mouseX, mouseY);
        if (buildingIndex >= 0) {
            ClickerView.Building building = view.buildings().get(buildingIndex);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(building.name() + " — niveau " + building.count()));
            if (!building.categories().isEmpty()) {
                lines.add(Component.literal(building.categories()));
            }
            lines.add(Component.literal("Production de base : " + ClickerFormat.rate(building.base()) + " / s"));
            lines.add(Component.literal("Production actuelle : " + ClickerFormat.rate(building.rps()) + " / s"));
            lines.add(Component.literal("Production totale : " + ClickerFormat.compact(building.produced())));
            if (!building.bonuses().isEmpty()) {
                lines.add(Component.literal("Améliorations : " + String.join(", ", building.bonuses())));
            }
            if (!building.unlocks().isEmpty()) {
                List<String> names = new ArrayList<>();
                for (ItemStack stack : building.unlocks()) {
                    String name = stack.getHoverName().getString();
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                }
                lines.add(Component.literal("Achats débloqués : " + String.join(", ", names)));
            }
            g.tooltip(lines);
            return;
        }
        int[] offerAt = offerAt(mouseX, mouseY);
        if (offerAt != null) {
            ClickerView.Shelf shelf = view.shop().get(offerAt[0]);
            ClickerView.Offer offer = shelf.offers().get(offerAt[1]);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal((offer.stack().getCount() > 1 ? offer.stack().getCount() + " × " : "")
                    + offer.stack().getHoverName().getString()));
            lines.add(Component.literal("Prix : " + ClickerFormat.compact(offer.price()) + " ClicCoins"));
            lines.add(Component.literal("Restant : " + offer.left()));
            lines.add(Component.literal(offer.left() <= 0 ? "Cette offre est épuisée"
                    : points < offer.price() ? "Pas assez de ClicCoins"
                    : isArmed(shelf, offer, now) ? "Clique encore pour confirmer" : "Clic : acheter"));
            g.tooltip(lines);
        }
    }

    // -- Entrees ---------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = designX(event.x());
        double y = designY(event.y());
        long now = System.currentTimeMillis();
        if (event.button() == 0) {
            if (onFood(x, y)) {
                pressed = true;
                feed(x, y);
                return true;
            }
            if (onArrow(x, y)) {
                upgradesOpen = !upgradesOpen;
                upgradeScroll = 0;
                click(1.0f);
                return true;
            }
            int upgradeIndex = upgradeAt(x, y);
            if (upgradeIndex >= 0) {
                flush();
                UiClient.send(ClickerView.ID, "upgrade", view.upgrades().get(upgradeIndex).id(), 0);
                return true;
            }
            int buildingIndex = buildingAt(x, y);
            if (buildingIndex >= 0) {
                flush();
                UiClient.send(ClickerView.ID, "building", view.buildings().get(buildingIndex).id(), 0);
                return true;
            }
            int[] offerAt = offerAt(x, y);
            if (offerAt != null) {
                ClickerView.Shelf shelf = view.shop().get(offerAt[0]);
                ClickerView.Offer offer = shelf.offers().get(offerAt[1]);
                String key = shelf.type() + ":" + offer.id();
                if (offer.left() > 0 && points(now) >= offer.price()) {
                    if (isArmed(shelf, offer, now)) {
                        // Un achat du Clic Shop se confirme d'un second clic, comme la fenetre de l'original.
                        armed = "";
                        flush();
                        UiClient.send(ClickerView.ID, "shop", key, 0);
                    } else {
                        armed = key;
                        armedAt = now;
                        click(1.2f);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        pressed = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = designX(mouseX);
        if (x >= RIGHT_X) {
            listScroll = (float) Math.max(0, Math.min(maxListScroll(), listScroll - scrollY * ROW_H));
            return true;
        }
        if (x >= CENTER_X && x < CENTER_X + CENTER_W && designY(mouseY) < upgradesBottom() + 4) {
            upgradeScroll = (float) Math.max(0, Math.min(maxUpgradeScroll(), upgradeScroll - scrollY * CELL));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
