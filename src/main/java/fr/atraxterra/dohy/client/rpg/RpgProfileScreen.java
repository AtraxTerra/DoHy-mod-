package fr.atraxterra.dohy.client.rpg;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Popup;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.dungeon.RpgStat;
import fr.atraxterra.dohy.palamod.dungeon.RpgTree;
import fr.atraxterra.dohy.palamod.dungeon.RpgView;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le profil RPG, a la maniere de Paladium : a gauche le joueur, son niveau et ses statistiques
 * (PERSO : l'arbre seul ; GLOBAL : runes et equipement de donjon compris) ; a droite l'arbre de
 * competences sur sa croix de lumiere — Esquive en haut, Attaque a gauche, Critique a droite,
 * Defense en bas —, VALIDER et RESET au centre. L'arbre se deplace a la souris (glisser ou molette).
 */
public class RpgProfileScreen extends UiScreen implements ViewScreen<RpgView> {

    private static final int W = Shell.WIDTH;
    private static final int H = Shell.HEIGHT;

    // le panneau de gauche
    private static final float LEFT = 22.0f;
    private static final float LEFT_W = 182.0f;
    private static final float BOX_Y = 96.0f;
    private static final float TAB_Y = 103.0f;
    private static final float TAB_H = 17.0f;
    private static final float LIST_X = 30.0f;
    private static final float LIST_Y = 126.0f;
    private static final float LIST_W = 158.0f;
    private static final float LIST_H = 176.0f;
    private static final float ROW_H = 15.0f;
    private static final float ROW_STEP = 19.0f;

    // l'arbre
    private static final float TREE_X = 214.0f;
    private static final float TREE_Y = 18.0f;
    private static final float TREE_W = 376.0f;
    private static final float TREE_H = 292.0f;
    private static final float GRID = 33.0f;
    private static final float NODE = 26.0f;
    private static final float PAN = 40.0f;
    private static final float BUTTON_W = 54.0f;
    private static final float BUTTON_H = 15.0f;

    /** Les branches : leur sens depuis le centre (x, y), dans l'ordre de RpgStat. */
    private static final int[][] AXIS = {{-1, 0}, {0, -1}, {1, 0}, {0, 1}};
    private static final String[] ICON = {"damage", "dodge", "critical_chance", "resistance"};
    private static final int[] COLOR = {0xFFBFE3FF, 0xFFFFD2D2, 0xFFFFEDC2, 0xFFCDF5CF};
    private static final int[] SHADE = {0xFF2D4B70, 0xFF7A2F2F, 0xFF6E5A2A, 0xFF2F6B39};

    private static final int RED = 0xFFEF3926;
    private static final int GOLD = 0xFFFAC400;

    private RpgView view;
    private boolean global = true;
    private float listScroll;
    private float panY;
    private boolean panning;
    private final double opened = Anim.now();

    public RpgProfileScreen(RpgView view) {
        super(Component.literal("Profil RPG"), W, H);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return RpgView.ID;
    }

    @Override
    public void accept(RpgView next) {
        this.view = next;
    }

    @Override
    protected void build() {
    }

    @Override
    protected float lift() {
        return 6.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.25d)));
    }

    private static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath("palarpg", "textures/" + path + ".png");
    }

    // -- La geometrie de l'arbre -------------------------------------------------------------------

    private float centerX() {
        return TREE_X + TREE_W / 2.0f;
    }

    private float centerY() {
        return TREE_Y + TREE_H / 2.0f + panY;
    }

    private float nodeX(int branch, int node) {
        return centerX() + (AXIS[branch][0] * RpgTree.along(node) - AXIS[branch][1] * RpgTree.side(node)) * GRID;
    }

    private float nodeY(int branch, int node) {
        return centerY() + (AXIS[branch][1] * RpgTree.along(node) + AXIS[branch][0] * RpgTree.side(node)) * GRID;
    }

    private boolean inTree(double x, double y) {
        return x >= TREE_X && x < TREE_X + TREE_W && y >= TREE_Y && y < TREE_Y + TREE_H;
    }

    private boolean pending() {
        for (int mask : view.pending()) {
            if (mask != 0) {
                return true;
            }
        }
        return false;
    }

    private boolean owned() {
        for (int mask : view.owned()) {
            if (mask != 0) {
                return true;
            }
        }
        return false;
    }

    /** L'etat d'un noeud : 3 pris, 2 choisi, 1 ouvert (et payable), 0 ferme. */
    private int state(int branch, int node) {
        int owned = view.owned()[branch];
        int chosen = view.pending()[branch];
        if (RpgTree.has(owned, node)) {
            return 3;
        }
        if (RpgTree.has(chosen, node)) {
            return 2;
        }
        return RpgTree.open(owned | chosen, node) && RpgTree.cost(node) <= view.free() ? 1 : 0;
    }

    private static boolean over(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // -- Le dessin ---------------------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        Paint p = g.paint();
        p.rect(0, 0, W, H, 0xF40A0A0B);
        frame(p);
        drawLeft(g, mouseX, mouseY);
        drawTree(g, mouseX, mouseY);
    }

    /** Le cadre en pixels : un filet gris a deux unites du bord, des coins carres. */
    private static void frame(Paint p) {
        int line = 0xFF3A3B40;
        p.rect(5, 3, W - 10, 1, line);
        p.rect(5, H - 4, W - 10, 1, line);
        p.rect(3, 5, 1, H - 10, line);
        p.rect(W - 4, 5, 1, H - 10, line);
        for (float[] corner : new float[][] {{2, 2}, {W - 5, 2}, {2, H - 5}, {W - 5, H - 5}}) {
            p.rect(corner[0], corner[1], 3, 3, line);
        }
    }

    private void drawLeft(UiGraphics g, double mx, double my) {
        Paint p = g.paint();
        g.textShaded(Ui.TITLE, "PROFIL RPG", LEFT, 20.0f, 22.0f, 0xFFFFFFFF, 0xFF5A5A60, 1.4f);
        String name = view.name().toUpperCase(Locale.ROOT);
        String level = "LV." + view.level();
        float levelWidth = g.width(Ui.WIDE, level, 8.0f);
        g.text(Ui.WIDE, g.fit(Ui.WIDE, name, 8.0f, LEFT_W - levelWidth - 8.0f), LEFT, 62.0f, 8.0f, 0xFFFFFFFF);
        g.right(Ui.WIDE, level, LEFT + LEFT_W, 62.0f, 8.0f, 0xFFFFFFFF);

        // l'XP du niveau
        p.rect(LEFT, 76.0f, LEFT_W, 8.0f, 0xFF222327);
        if (view.xpNext() > 0) {
            float done = Math.min(1.0f, view.xpIn() / (float) view.xpNext());
            if (done > 0.0f) {
                p.rect(LEFT, 76.0f, Math.max(1.0f, LEFT_W * done), 8.0f, RED);
                p.rect(LEFT, 76.0f, Math.max(1.0f, LEFT_W * done), 1.0f, 0xFFF2492F);
            }
        } else {
            p.rect(LEFT, 76.0f, LEFT_W, 8.0f, GOLD);
        }
        if (over(mx, my, LEFT, 74.0f, LEFT_W, 12.0f)) {
            g.tooltip(List.of(Component.literal(view.xpNext() > 0
                    ? view.xpIn() + " / " + view.xpNext() + " XP" : "Niveau maximum")));
        }

        // les statistiques
        p.rect(LEFT, BOX_Y, LEFT_W, 214.0f, 0xFF111214);
        tab(g, "PERSO", LIST_X, !global, mx, my);
        tab(g, "GLOBAL", LIST_X + 79.0f, global, mx, my);
        List<RpgView.Row> rows = global ? view.global() : view.perso();
        float max = Math.max(0.0f, rows.size() * ROW_STEP - 4.0f - LIST_H);
        listScroll = Math.max(0.0f, Math.min(max, listScroll));
        g.scissor((int) LIST_X, (int) LIST_Y, (int) LIST_W + 1, (int) LIST_H);
        for (int index = 0; index < rows.size(); index++) {
            float y = LIST_Y + index * ROW_STEP - listScroll;
            if (y + ROW_H < LIST_Y || y > LIST_Y + LIST_H) {
                continue;
            }
            RpgView.Row row = rows.get(index);
            p.framed(LIST_X, y, LIST_W - 4.0f, ROW_H, 1.5f, 0xFF1F2023, 0xFF2A2B2F, 1);
            icon(g, row.icon(), LIST_X + 4.0f, y + 3.0f, 9.0f);
            g.text(Ui.WIDE, g.fit(Ui.WIDE, row.label(), 4.4f, LIST_W - 60.0f), LIST_X + 17.0f, y + 5.3f, 4.4f,
                    0xFFE9E8EE);
            g.right(Ui.WIDE, row.value(), LIST_X + LIST_W - 9.0f, y + 5.3f, 4.4f, row.color());
        }
        g.endScissor();
        // la barre de defilement
        float trackX = LIST_X + LIST_W + 3.0f;
        p.rect(trackX, LIST_Y, 3.0f, LIST_H, 0xFF26272B);
        float content = rows.size() * ROW_STEP - 4.0f;
        float thumb = content <= LIST_H ? LIST_H : Math.max(16.0f, LIST_H * LIST_H / content);
        float thumbY = LIST_Y + (max <= 0.0f ? 0.0f : (LIST_H - thumb) * listScroll / max);
        p.rect(trackX, thumbY, 3.0f, thumb, 0xFF6E7076);
    }

    private void tab(UiGraphics g, String label, float x, boolean active, double mx, double my) {
        Paint p = g.paint();
        float w = 79.0f;
        boolean hover = !active && over(mx, my, x, TAB_Y, w, TAB_H);
        if (active) {
            p.rect(x, TAB_Y, w, TAB_H, 0xFFE8321F);
            p.rect(x, TAB_Y, w, 1.0f, 0xFFF2492F);
            p.rect(x, TAB_Y + TAB_H - 1.5f, w, 1.5f, 0xFFA82418);
        } else {
            p.rect(x, TAB_Y, w, TAB_H, hover ? 0xFF35363B : 0xFF2B2C30);
        }
        g.centered(Ui.WIDE, label, x + w / 2.0f, TAB_Y + 6.2f, 5.0f, 0xFFFFFFFF);
    }

    /** Une icone de PalaRPG (96 pixels) a la taille voulue. */
    private static void icon(UiGraphics g, String path, float x, float y, float size) {
        Matrix3x2fStack pose = g.raw().pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 32.0f, size / 32.0f);
        g.texture(texture(path), 0, 0, 32, 32, 32, 32);
        pose.popMatrix();
    }

    private void drawTree(UiGraphics g, double mx, double my) {
        Paint p = g.paint();
        p.rect(TREE_X, TREE_Y, TREE_W, TREE_H, 0xFF0C0D0F);
        g.scissor((int) TREE_X, (int) TREE_Y, (int) TREE_W, (int) TREE_H);
        float cx = centerX();
        float cy = centerY();

        // la croix de lumiere
        Matrix3x2fStack pose = g.raw().pose();
        pose.pushMatrix();
        pose.translate(cx - 270.0f, cy - 270.0f);
        g.texture(texture("profile/skilltree/background"), 0, 0, 540, 540, 540, 540, 0xD8FFFFFF);
        pose.popMatrix();

        // les axes
        p.rect(TREE_X, cy - 0.5f, TREE_W, 1.0f, 0xFF08090A);
        p.rect(cx - 0.5f, TREE_Y, 1.0f, TREE_H, 0xFF08090A);

        // les liens, sous les noeuds
        for (int branch = 0; branch < AXIS.length; branch++) {
            for (int node = 1; node < RpgTree.NODES; node++) {
                int parent = RpgTree.parent(node);
                int here = state(branch, node);
                int up = state(branch, parent);
                int color = here == 3 && up == 3 ? RED : here == 2 ? GOLD : 0xFF08090A;
                float width = color == 0xFF08090A ? 1.0f : 1.4f;
                link(p, nodeX(branch, parent), nodeY(branch, parent), nodeX(branch, node), nodeY(branch, node),
                        width, color);
            }
        }

        // les libelles des branches
        g.textShaded(Ui.WIDE, "ESQUIVE", cx - g.width(Ui.WIDE, "ESQUIVE", 8.0f) / 2.0f, cy - 45.0f, 8.0f,
                COLOR[1], SHADE[1], 0.9f);
        g.textShaded(Ui.WIDE, "DEFENSE", cx - g.width(Ui.WIDE, "DEFENSE", 8.0f) / 2.0f, cy + 33.0f, 8.0f,
                COLOR[3], SHADE[3], 0.9f);
        vertical(g, "ATTAQUE", cx - 37.0f, cy, true, COLOR[0], SHADE[0]);
        vertical(g, "CRITIQUE", cx + 37.0f, cy, false, COLOR[2], SHADE[2]);

        // les noeuds
        List<Component> tip = null;
        for (int branch = 0; branch < AXIS.length; branch++) {
            for (int node = 0; node < RpgTree.NODES; node++) {
                float x = nodeX(branch, node);
                float y = nodeY(branch, node);
                boolean hover = inTree(mx, my) && over(mx, my, x - NODE / 2.0f, y - NODE / 2.0f, NODE, NODE);
                node(g, branch, node, x, y, hover);
                if (hover) {
                    tip = tooltip(branch, node);
                }
            }
        }

        // VALIDER et RESET
        button(g, "VALIDER", cx - BUTTON_W / 2.0f, cy - 19.0f, pending(), mx, my);
        button(g, "RESET", cx - BUTTON_W / 2.0f, cy + 3.0f, pending() || owned(), mx, my);
        g.endScissor();

        points(g, mx, my);
        if (tip != null) {
            g.tooltip(tip);
        }
    }

    /** Un lien a angle droit : le long d'un axe, puis de l'autre. */
    private static void link(Paint p, float x1, float y1, float x2, float y2, float width, int color) {
        float half = width / 2.0f;
        if (Math.abs(y1 - y2) < 0.5f) {
            p.rect(Math.min(x1, x2), y1 - half, Math.abs(x2 - x1), width, color);
        } else if (Math.abs(x1 - x2) < 0.5f) {
            p.rect(x1 - half, Math.min(y1, y2), width, Math.abs(y2 - y1), color);
        } else {
            p.rect(x1 - half, Math.min(y1, y2), width, Math.abs(y2 - y1), color);
            p.rect(Math.min(x1, x2), y2 - half, Math.abs(x2 - x1), width, color);
        }
    }

    private static void vertical(UiGraphics g, String text, float x, float y, boolean down, int color, int shade) {
        Matrix3x2fStack pose = g.raw().pose();
        float length = g.width(Ui.WIDE, text, 8.0f);
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) Math.toRadians(down ? 90.0d : -90.0d));
        g.textShaded(Ui.WIDE, text, -length / 2.0f, -4.0f, 8.0f, color, shade, 0.9f);
        pose.popMatrix();
    }

    private void node(UiGraphics g, int branch, int node, float x, float y, boolean hover) {
        Paint p = g.paint();
        int state = state(branch, node);
        float left = x - NODE / 2.0f;
        float top = y - NODE / 2.0f;
        int fill;
        int border;
        int value;
        switch (state) {
            case 3 -> {
                p.glow(x, y, 20.0f, Paint.alpha(RED, 0.30f));
                fill = 0xFF7E1710;
                border = RED;
                value = 0xFFFFFFFF;
            }
            case 2 -> {
                p.glow(x, y, 20.0f, Paint.alpha(GOLD, 0.25f + 0.10f * Anim.pulse(1.2d)));
                fill = 0xFF4A1510;
                border = GOLD;
                value = GOLD;
            }
            case 1 -> {
                fill = hover ? 0xFF3A1714 : 0xFF1B1C1F;
                border = 0xFFB3261E;
                value = 0xFFE9E8EE;
            }
            default -> {
                fill = hover ? 0xFF25262A : 0xFF1B1C1F;
                border = 0xFF3A3B40;
                value = 0xFF7D8088;
            }
        }
        p.framed(left - 1.0f, top - 1.0f, NODE + 2.0f, NODE + 2.0f, 3.5f, 0xFF050506, 0xFF050506, 1);
        p.framed(left, top, NODE, NODE, 3.0f, fill, border, 1);
        p.rect(left + 3.0f, top + 1.0f, NODE - 6.0f, 1.0f, Paint.alpha(0xFFFFFFFF, state >= 2 ? 0.18f : 0.06f));
        String icon = state == 0 ? "stats/icon/locked/" + ICON[branch] : "stats/icon/" + ICON[branch];
        icon(g, icon, x - 6.0f, top + 4.0f, 12.0f);
        int amount = RpgStat.values()[branch].node(node);
        g.centered(Ui.WIDE, "+" + amount + "%", x, top + 18.0f, 4.4f, value);
    }

    private List<Component> tooltip(int branch, int node) {
        RpgStat stat = RpgStat.values()[branch];
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(stat.label() + " +" + stat.node(node) + " %").withStyle(stat.color()));
        int state = state(branch, node);
        String detail = switch (state) {
            case 3 -> "Acquis";
            case 2 -> "Choisi";
            default -> RpgTree.cost(node) + " points";
        };
        lines.add(Component.literal(detail).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA8A8AE))));
        return lines;
    }

    private void button(UiGraphics g, String label, float x, float y, boolean enabled, double mx, double my) {
        Paint p = g.paint();
        boolean hover = enabled && over(mx, my, x, y, BUTTON_W, BUTTON_H) && inTree(mx, my);
        int face = !enabled ? 0xFF6B6C70 : hover ? 0xFFE2E2E6 : 0xFFC9C9CD;
        p.rect(x, y, BUTTON_W, BUTTON_H, 0xFF050506);
        p.rect(x + 1.0f, y + 1.0f, BUTTON_W - 2.0f, BUTTON_H - 2.0f, face);
        p.rect(x + 1.0f, y + BUTTON_H - 3.0f, BUTTON_W - 2.0f, 2.0f, Paint.mix(face, 0xFF000000, 0.3f));
        p.rect(x + 1.0f, y + 1.0f, BUTTON_W - 2.0f, 1.0f, Paint.mix(face, 0xFFFFFFFF, 0.4f));
        g.centered(Ui.WIDE, label, x + BUTTON_W / 2.0f, y + 4.6f, 5.0f, enabled ? 0xFF3A3B40 : 0xFF45464B);
    }

    /** Le compteur des points, en haut a droite de l'arbre. */
    private void points(UiGraphics g, double mx, double my) {
        Paint p = g.paint();
        String label = "POINTS DE COMPETENCE RESTANTS";
        String number = String.valueOf(Math.max(0, view.free()));
        float labelWidth = g.width(Ui.WIDE, label, 4.4f);
        float numberWidth = g.width(Ui.WIDE, number, 7.0f);
        float w = labelWidth + numberWidth + 32.0f;
        float x = TREE_X + TREE_W - w - 6.0f;
        float y = TREE_Y + 6.0f;
        p.framed(x, y, w, 17.0f, 3.0f, 0xF0141518, 0xFF2D2F34, 1);
        g.text(Ui.WIDE, label, x + 7.0f, y + 6.3f, 4.4f, 0xFFE9E8EE);
        float diamond = x + labelWidth + 12.0f;
        Matrix3x2fStack pose = g.raw().pose();
        pose.pushMatrix();
        pose.translate(diamond - 3.5f, y + 1.0f);
        g.texture(texture("profile/skilltree/cost_blur"), 0, 0, 15, 15, 15, 15, 0x99FFFFFF);
        pose.popMatrix();
        pose.pushMatrix();
        pose.translate(diamond, y + 4.5f);
        g.texture(texture("profile/skilltree/cost"), 0, 0, 8, 8, 8, 8);
        pose.popMatrix();
        g.text(Ui.WIDE, number, diamond + 12.0f, y + 5.0f, 7.0f, view.free() > 0 ? 0xFFFFFFFF : 0xFF8B8E95);
        if (over(mx, my, x, y, w, 17.0f)) {
            List<Component> lines = new ArrayList<>();
            for (String source : view.sources()) {
                lines.add(Component.literal(source));
            }
            g.tooltip(lines);
        }
    }

    // -- Les clics --------------------------------------------------------------------------------

    @Override
    protected boolean backgroundClicked(double mx, double my, int button) {
        if (over(mx, my, LIST_X, TAB_Y, 79.0f, TAB_H)) {
            if (global) {
                global = false;
                listScroll = 0.0f;
                click(1.0f);
            }
            return true;
        }
        if (over(mx, my, LIST_X + 79.0f, TAB_Y, 79.0f, TAB_H)) {
            if (!global) {
                global = true;
                listScroll = 0.0f;
                click(1.0f);
            }
            return true;
        }
        if (!inTree(mx, my)) {
            return false;
        }
        float cx = centerX();
        float cy = centerY();
        if (over(mx, my, cx - BUTTON_W / 2.0f, cy - 19.0f, BUTTON_W, BUTTON_H)) {
            if (pending()) {
                click(1.2f);
                UiClient.send(RpgView.ID, "validate", "", 0);
            }
            return true;
        }
        if (over(mx, my, cx - BUTTON_W / 2.0f, cy + 3.0f, BUTTON_W, BUTTON_H)) {
            if (pending()) {
                click(0.9f);
                UiClient.send(RpgView.ID, "cancel", "", 0);
            } else if (owned()) {
                click(0.9f);
                openPopup(new Popup(new ItemStack(Items.GRINDSTONE), "Remettre l'arbre a zero ?",
                        fr.atraxterra.dohy.util.MoneyFormat.exact(view.respec()) + " $", "CONFIRMER", true,
                        () -> UiClient.send(RpgView.ID, "respec", "", 0)));
            }
            return true;
        }
        for (int branch = 0; branch < AXIS.length; branch++) {
            for (int node = 0; node < RpgTree.NODES; node++) {
                float x = nodeX(branch, node);
                float y = nodeY(branch, node);
                if (over(mx, my, x - NODE / 2.0f, y - NODE / 2.0f, NODE, NODE)) {
                    int state = state(branch, node);
                    if (state == 1 || state == 2) {
                        click(state == 1 ? 1.3f : 0.8f);
                        UiClient.send(RpgView.ID, "node", RpgStat.values()[branch].name() + ":" + node, 0);
                    }
                    return true;
                }
            }
        }
        panning = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (panning) {
            panY = Math.max(-PAN, Math.min(PAN, panY + (float) (dragY / scale)));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup == null) {
            double x = designX(mouseX);
            double y = designY(mouseY);
            if (over(x, y, LIST_X, LIST_Y, LIST_W + 8.0f, LIST_H)) {
                listScroll -= (float) scrollY * ROW_STEP;
                return true;
            }
            if (inTree(x, y)) {
                panY = Math.max(-PAN, Math.min(PAN, panY + (float) scrollY * 12.0f));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
