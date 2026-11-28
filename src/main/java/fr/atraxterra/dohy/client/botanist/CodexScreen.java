package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.MutationSeedItem;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import fr.atraxterra.dohy.palamod.botanist.Rarity;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le codex des mutations ({@code UIBotanistCodex} de JobsReborn), au style du kit : les quinze
 * plantes rangees par categorie (ici leur rarete), chacune dans le losange du kit du botaniste —
 * sa graine si elle est decouverte, sa silhouette et « ??? A DECOUVRIR » sinon — et, a droite, la
 * fiche de la plante choisie : famille, variete, role, ses quatre variantes (les etats de la
 * plante, avec leurs durees en bulle), le niveau requis, et les plantes dont la floraison peut la
 * donner (« Peut aussi apparaitre a la floraison de »).
 *
 * <p>Il s'ouvre depuis le scanner de Genis (bouton « LE CODEX ») et ne lit que le codex envoye au
 * client ({@link ClientCodex}) : rien ne part au serveur.
 */
public final class CodexScreen extends UiScreen {

    private static final int W = Shell.WIDTH;
    private static final int H = Shell.HEIGHT;
    private static final int LIST_X = 18;
    private static final int LIST_Y = 52;
    private static final int TILE = 46;
    private static final int TILE_GAP = 10;
    private static final int ROW_H = 64;
    private static final int SHEET_X = 400;
    private static final int SHEET_Y = 48;
    private static final int SHEET_W = 190;
    private static final int SHEET_H = 268;
    private static final int CLOSE_X = W - 30;
    private static final int CLOSE_Y = 12;
    private static final int CLOSE_SIZE = 16;
    private static final int CHIP_Y = 214;
    private static final int CHIP_W = 40;
    private static final int CHIP_H = 16;
    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge.png");
    private static final Identifier DIAMOND_OUTLINE = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge_outline.png");
    private static final int GREEN = 0xFF20FF75;
    private static final int AMBER = 0xFFFFB020;

    /** Une case de la grille : sa plante et sa place. */
    private record Tile(Mutation mutation, int x, int y) {
    }

    private final List<Tile> tiles = new ArrayList<>();
    private Mutation selected;
    /** La variante montree (l'etat de la plante, 1 a 4). */
    private int variant = 3;

    public CodexScreen() {
        super(Component.literal("Codex"), W, H);
        for (Mutation mutation : Mutation.values()) {
            if (ClientCodex.has(mutation)) {
                selected = mutation;
                break;
            }
        }
        if (selected == null) {
            selected = Mutation.IRONWHEAT;
        }
    }

    @Override
    protected void build() {
        tiles.clear();
        int row = 0;
        for (Rarity rarity : Rarity.values()) {
            int column = 0;
            for (Mutation mutation : Mutation.values()) {
                if (mutation.rarity() != rarity) {
                    continue;
                }
                tiles.add(new Tile(mutation, LIST_X + column * (TILE + TILE_GAP) + 6, LIST_Y + row * ROW_H + 14));
                column++;
            }
            row++;
        }
    }

    // -- Le dessin -----------------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        Paint p = g.paint();
        p.shadow(6, 10, W - 12, H - 8, 18, Paint.alpha(0xFF000000, 0.55f));
        p.framed(0, 0, W, H, 8.0f, Ui.Theme.BG1, Ui.Theme.EDGE, 1);
        p.roundGradient(1, 1, W - 2, 38, 7.0f, 0xFF1B2A22, Ui.Theme.BG1);
        g.textShaded(Ui.TITLE, "CODEX", 18, 12, 16, 0xFFFFFFFF, 0xFF0D2016, 1.0f);
        int found = ClientCodex.count();
        g.text(Ui.PIXEL, found + " / " + Mutation.values().length + " DECOUVERTES", 96, 14, 5.0f, GREEN);
        g.text(Ui.BODY, "Les plantes du laboratoire, et celles qui restent a decouvrir.", 96, 23, 5.0f,
                Ui.Theme.TEXT2);
        boolean closeHovered = inside(mouseX, mouseY, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE);
        p.framed(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, 4.0f, closeHovered ? Ui.RED : 0xFF2A2C31, Ui.Theme.LINE2, 1);
        g.texture(Ui.CLOSE, CLOSE_X + 4, CLOSE_Y + 4, 8, 8, 32, 32);

        int row = 0;
        for (Rarity rarity : Rarity.values()) {
            banner(g, rarity, LIST_Y + row * ROW_H);
            row++;
        }
        Tile hovered = null;
        for (Tile tile : tiles) {
            boolean over = inside(mouseX, mouseY, tile.x(), tile.y(), TILE, TILE);
            if (over) {
                hovered = tile;
            }
            tile(g, tile, over);
        }
        sheet(g, mouseX, mouseY);
        if (hovered != null) {
            g.tooltip(tileTooltip(hovered.mutation()));
        }
    }

    /** La banniere d'une categorie : son nom, ses decouvertes, un filet. */
    private void banner(UiGraphics g, Rarity rarity, int y) {
        int found = 0;
        int total = 0;
        for (Mutation mutation : Mutation.values()) {
            if (mutation.rarity() == rarity) {
                total++;
                if (ClientCodex.has(mutation)) {
                    found++;
                }
            }
        }
        Paint p = g.paint();
        p.round(LIST_X, y + 3, 3, 7, 1.5f, rarity.color());
        g.text(Ui.PIXEL, rarity.plural().toUpperCase(Locale.ROOT), LIST_X + 8, y + 3, 6.0f, rarity.color());
        float after = LIST_X + 8 + g.width(Ui.PIXEL, rarity.plural().toUpperCase(Locale.ROOT), 6.0f) + 8;
        g.text(Ui.PIXEL, found + "/" + total, after, y + 4, 4.5f, Ui.Theme.MUTED);
        float lineStart = after + g.width(Ui.PIXEL, found + "/" + total, 4.5f) + 8;
        // Le filet s'arrete avant le niveau de la categorie, ecrit au bout.
        p.rect(lineStart, y + 6, SHEET_X - 58 - lineStart, 1, Paint.alpha(rarity.color(), 0.35f));
        g.text(Ui.PIXEL, "NIV. " + rarity.level(), SHEET_X - 50, y + 4, 4.5f, Ui.Theme.MUTED);
    }

    /** Une plante : le losange, sa graine (ou sa silhouette), son nom ou « ??? ». */
    private void tile(UiGraphics g, Tile tile, boolean hovered) {
        Mutation mutation = tile.mutation();
        boolean known = ClientCodex.has(mutation);
        boolean chosen = mutation == selected;
        int x = tile.x();
        int y = tile.y();
        int tint = known ? Paint.mix(0xFF1E1D23, mutation.rarity().color(), 0.22f) : 0xFF1A1B1F;
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND, x + 7, y, 0.0f, 0.0f, 32, 32, 48, 48, 48, 48, tint);
        int outline = chosen ? 0xFFFFFFFF : hovered ? mutation.rarity().color() : known
                ? Paint.alpha(mutation.rarity().color(), 0.7f) : 0xFF3A3C42;
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND_OUTLINE, x + 7, y, 0.0f, 0.0f, 32, 32, 48, 48, 48, 48,
                outline);
        ItemStack seed = seedOf(mutation);
        if (known) {
            g.item(seed, x + 15, y + 8, 16, false);
        } else {
            // La silhouette : la graine en noir, comme la plante grisee du codex de JobsReborn.
            g.item(seed, x + 15, y + 8, 16, false);
            g.paint().rect(x + 15, y + 8, 16, 16, 0xE0101114);
            g.centered(Ui.PIXEL, "?", x + TILE / 2.0f, y + 13, 6.0f, 0xFF6A6C74);
        }
        String label = known ? ContainerStyle.plain(mutation.label()) : "???";
        g.centered(Ui.BODY, g.fit(Ui.BODY, label, 4.6f, TILE + 6), x + TILE / 2.0f, y + 34, 4.6f,
                known ? Ui.Theme.TEXT : Ui.Theme.DIM);
        if (!known) {
            g.centered(Ui.PIXEL, "A DECOUVRIR", x + TILE / 2.0f, y + 41, 3.4f,
                    mutation.level() <= ClientCodex.level() ? GREEN : Ui.Theme.DIM);
        }
    }

    /** La fiche de la plante choisie. */
    private void sheet(UiGraphics g, double mouseX, double mouseY) {
        Mutation mutation = selected;
        boolean known = ClientCodex.has(mutation);
        Paint p = g.paint();
        p.framed(SHEET_X, SHEET_Y, SHEET_W, SHEET_H, 6.0f, Ui.Theme.BG2, Ui.Theme.LINE, 1);
        int accent = mutation.rarity().color();
        p.roundGradient(SHEET_X + 1, SHEET_Y + 1, SHEET_W - 2, 92, 5.0f, Paint.mix(Ui.Theme.BG2, accent, 0.18f),
                Ui.Theme.BG2);
        // L'apercu : le losange et la graine en grand, ou sa silhouette.
        float cx = SHEET_X + SHEET_W / 2.0f;
        p.glow(cx, SHEET_Y + 46, 44.0f, Paint.alpha(accent, known ? 0.22f : 0.06f));
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND, Math.round(cx - 34), SHEET_Y + 12, 0.0f, 0.0f, 68, 68, 48,
                48, 48, 48, known ? Paint.mix(0xFF1E1D23, accent, 0.25f) : 0xFF16171A);
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND_OUTLINE, Math.round(cx - 34), SHEET_Y + 12, 0.0f, 0.0f, 68,
                68, 48, 48, 48, 48, known ? accent : 0xFF3A3C42);
        ItemStack seed = seedOf(mutation);
        g.item(seed, cx - 16, SHEET_Y + 30, 32, false);
        if (!known) {
            p.rect(cx - 16, SHEET_Y + 30, 32, 32, 0xE6101114);
            g.centered(Ui.PIXEL, "?", cx, SHEET_Y + 40, 10.0f, 0xFF6A6C74);
        }
        float y = SHEET_Y + 88;
        String name = known ? ContainerStyle.plain(mutation.label()).toUpperCase(Locale.ROOT) : "MUTATION INCONNUE";
        g.centered(Ui.BOLD, g.fit(Ui.BOLD, name, 8.0f, SHEET_W - 16), cx, y, 8.0f, known ? 0xFFFFFFFF : Ui.Theme.MUTED);
        y += 11;
        g.centered(Ui.PIXEL, mutation.rarity().label().toUpperCase(Locale.ROOT) + "  -  CLIMAT "
                + Mutation.climateLabel(mutation.climate()).toUpperCase(Locale.ROOT), cx, y, 4.2f, accent);
        y += 10;
        float left = SHEET_X + 12;
        if (known) {
            row(g, "FAMILLE", mutation.sheet().family(), left, y);
            row(g, "VARIETE", mutation.sheet().variety(), left, y + 11);
            row(g, "ROLE", mutation.sheet().role(), left, y + 22);
        } else {
            row(g, "FAMILLE", "Inconnue", left, y);
            row(g, "VARIETE", "Inconnue", left, y + 11);
            row(g, "ROLE", "Inconnu", left, y + 22);
        }
        y += 36;
        // Le niveau requis, ou disponible.
        boolean available = mutation.level() <= ClientCodex.level();
        g.text(Ui.PIXEL, available ? "DISPONIBLE A TON NIVEAU" : "NIVEAU " + mutation.level() + " REQUIS", left, y,
                4.4f, available ? GREEN : AMBER);
        y += 9;
        // Les variantes : les quatre etats de la plante.
        g.text(Ui.PIXEL, "VARIANTES", left, CHIP_Y - 9, 4.4f, Ui.Theme.MUTED);
        String[] roman = {"I", "II", "III", "IV"};
        for (int index = 0; index < 4; index++) {
            int chipX = SHEET_X + 12 + index * (CHIP_W + 4);
            boolean active = known && variant == index + 1;
            boolean over = known && inside(mouseX, mouseY, chipX, CHIP_Y, CHIP_W, CHIP_H);
            int fill = !known ? 0xFF1A1B1F : active ? Paint.mix(Ui.Theme.BG3, accent, 0.45f)
                    : over ? Ui.Theme.CARD_HOVER : Ui.Theme.CARD;
            p.framed(chipX, CHIP_Y, CHIP_W, CHIP_H, 4.0f, fill, active ? accent : Ui.Theme.LINE2, 1);
            g.centered(Ui.PIXEL, known ? roman[index] : "?", chipX + CHIP_W / 2.0f, CHIP_Y + 5, 5.0f,
                    known ? 0xFFFFFFFF : Ui.Theme.DIM);
        }
        float textY = CHIP_Y + CHIP_H + 6;
        if (known) {
            g.wrapped(Ui.BODY, stageLine(mutation, variant), left, textY, 4.8f, SHEET_W - 24, 6.5f, Ui.Theme.TEXT2, 3);
        } else {
            g.wrapped(Ui.BODY, "Decouvre cette mutation pour voir ses variantes.", left, textY, 4.8f, SHEET_W - 24,
                    6.5f, Ui.Theme.MUTED, 2);
        }
        // D'ou elle vient.
        float originY = SHEET_Y + SHEET_H - 34;
        p.rect(left, originY - 5, SHEET_W - 24, 1, Ui.Theme.LINE);
        List<Mutation> parents = mutation.parents();
        String origin;
        if (!parents.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Mutation parent : parents) {
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(ClientCodex.has(parent) ? ContainerStyle.plain(parent.label()) : "???");
            }
            origin = "Peut aussi apparaitre a la floraison de : " + names + ".";
        } else {
            origin = known ? "Une plante de base : sa graine se recolte sur la plante fanee."
                    : "Scanne sa graine au scanner de Genis pour l'ajouter a ton codex.";
        }
        g.wrapped(Ui.BODY, origin, left, originY, 4.6f, SHEET_W - 24, 6.3f, Ui.Theme.TEXT2, 4);
        if (known && inside(mouseX, mouseY, SHEET_X + 12, CHIP_Y, 4 * (CHIP_W + 4), CHIP_H)) {
            int index = (int) ((mouseX - SHEET_X - 12) / (CHIP_W + 4));
            if (index >= 0 && index < 4) {
                g.tooltip(List.of(Component.literal("Variante " + roman[index]).withColor(0xFFFF55),
                        Component.literal(stageLine(mutation, index + 1)).withColor(0xAAAAAA)));
            }
        }
    }

    private static void row(UiGraphics g, String key, String value, float x, float y) {
        g.text(Ui.PIXEL, key, x, y + 1, 4.2f, Ui.Theme.MUTED);
        g.text(Ui.BODY, g.fit(Ui.BODY, value, 5.2f, SHEET_W - 74), x + 50, y, 5.2f, Ui.Theme.TEXT);
    }

    /** Les textes des etats du codex de JobsReborn (« La mutation vient d'etre plantee »...). */
    private static String stageLine(Mutation mutation, int variant) {
        return switch (variant) {
            case 1 -> "Graine : la mutation vient d'etre plantee.";
            case 2 -> "Bourgeon : apres " + minutes(mutation.budTicks()) + ".";
            case 3 -> "Fleurie : apres " + minutes(mutation.bloomTicks()) + ", puis toutes les "
                    + minutes(mutation.rebloomTicks()) + ". Recoltable au clic droit a chaque floraison.";
            default -> "Fanee : apres la derniere recolte, un clic droit rend des graines de la mutation.";
        };
    }

    private static String minutes(int ticks) {
        return ticks / 1200 + " min";
    }

    private List<Component> tileTooltip(Mutation mutation) {
        List<Component> lines = new ArrayList<>();
        boolean known = ClientCodex.has(mutation);
        lines.add(Component.literal(known ? ContainerStyle.plain(mutation.label()).toUpperCase(Locale.ROOT)
                : "MUTATION INCONNUE").withColor(mutation.rarity().color() & 0xFFFFFF));
        if (known) {
            lines.add(Component.literal("Famille : " + mutation.sheet().family()).withColor(0xAAAAAA));
            lines.add(Component.literal("Variete : " + mutation.sheet().variety()).withColor(0xAAAAAA));
        } else {
            lines.add(Component.literal("Fais-la sortir d'une floraison, puis scanne la").withColor(0xAAAAAA));
            lines.add(Component.literal("mutation inconnue pour la decouvrir.").withColor(0xAAAAAA));
        }
        lines.add(mutation.level() <= ClientCodex.level()
                ? Component.literal("Disponible a ton niveau").withColor(0x20FF75)
                : Component.literal("Niveau " + mutation.level() + " requis").withColor(0xFFB020));
        lines.add(Component.literal("Clique pour afficher sa fiche.").withColor(0x777777));
        return lines;
    }

    private static ItemStack seedOf(Mutation mutation) {
        MutationSeedItem item = PalaBotanist.MUTATION_SEEDS.get(mutation).get();
        return new ItemStack(item);
    }

    // -- Les clics -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = designX(event.x());
        double y = designY(event.y());
        if (event.button() == 0) {
            if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
                click(1.0f);
                onClose();
                return true;
            }
            for (Tile tile : tiles) {
                if (inside(x, y, tile.x(), tile.y(), TILE, TILE)) {
                    if (selected != tile.mutation()) {
                        selected = tile.mutation();
                        variant = 3;
                        click(1.1f);
                    }
                    return true;
                }
            }
            if (ClientCodex.has(selected) && inside(x, y, SHEET_X + 12, CHIP_Y, 4 * (CHIP_W + 4), CHIP_H)) {
                int index = (int) ((x - SHEET_X - 12) / (CHIP_W + 4));
                if (index >= 0 && index < 4) {
                    variant = index + 1;
                    click(1.2f);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
