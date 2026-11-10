package fr.atraxterra.dohy.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Le pinceau du kit : des primitives en unites de maquette.
 *
 * <p>L'ecran a deja place et mis a l'echelle le repere ({@link UiScreen}) :
 * ici, une unite vaut un pixel de la maquette, et tout se dessine par
 * {@link GuiGraphicsExtractor} — le rendu de 26.2 est en mode retenu, on ne
 * dessine pas, on decrit.
 *
 * <p>Les polices TrueType sont definies a la taille {@link Ui#FONT_BASE} ; un
 * texte d'une autre taille passe par une mise a l'echelle de la pose.
 */
public final class UiGraphics {

    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final int screenMouseX;
    private final int screenMouseY;
    private final List<Runnable> overlays = new ArrayList<>();
    private double designMouseX;
    private double designMouseY;
    private int boundsWidth;
    private int boundsHeight;

    public UiGraphics(GuiGraphicsExtractor graphics, Font font, int screenMouseX, int screenMouseY) {
        this.graphics = graphics;
        this.font = font;
        this.screenMouseX = screenMouseX;
        this.screenMouseY = screenMouseY;
    }

    /**
     * Le repere de maquette de l'ecran : avec lui, les bulles de texte se
     * dessinent dans le style du kit plutot qu'avec la bulle du jeu.
     */
    void design(double mouseX, double mouseY, int width, int height) {
        this.designMouseX = mouseX;
        this.designMouseY = mouseY;
        this.boundsWidth = width;
        this.boundsHeight = height;
    }

    public GuiGraphicsExtractor raw() {
        return graphics;
    }

    public Font font() {
        return font;
    }

    // -- Formes ----------------------------------------------------------------

    public void rect(int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0) {
            graphics.fill(x, y, x + width, y + height, color);
        }
    }

    /** Un cadre de {@code thickness} unites, a l'interieur du rectangle. */
    public void border(int x, int y, int width, int height, int thickness, int color) {
        rect(x, y, width, thickness, color);
        rect(x, y + height - thickness, width, thickness, color);
        rect(x, y + thickness, thickness, height - 2 * thickness, color);
        rect(x + width - thickness, y + thickness, thickness, height - 2 * thickness, color);
    }

    /**
     * Le panneau de Paladium : un bord sombre aux coins crantes, un filet gris
     * en retrait, et le fond.
     */
    public void panel(int x, int y, int width, int height) {
        rect(x + 3, y, width - 6, height, Ui.OUTER);
        rect(x, y + 3, width, height - 6, Ui.OUTER);
        rect(x + 3, y + 3, width - 6, height - 6, Ui.BG);
        int inset = 5;
        rect(x + inset + 1, y + inset, width - 2 * inset - 2, 2, Ui.FRAME);
        rect(x + inset + 1, y + height - inset - 2, width - 2 * inset - 2, 2, Ui.FRAME);
        rect(x + inset, y + inset + 1, 2, height - 2 * inset - 2, Ui.FRAME);
        rect(x + width - inset - 2, y + inset + 1, 2, height - 2 * inset - 2, Ui.FRAME);
    }

    /** Le panneau sans le filet interieur : pour les coffres, dont les cases touchent presque le bord. */
    public void panelPlain(int x, int y, int width, int height) {
        rect(x + 3, y, width - 6, height, Ui.OUTER);
        rect(x, y + 3, width, height - 6, Ui.OUTER);
        rect(x + 3, y + 3, width - 6, height - 6, Ui.BG);
    }

    /** Un intertitre : le losange ({@code section_*}), le mot, un filet jusqu'a {@code width}. */
    public void section(int x, int y, String icon, String label, int width) {
        texture(Ui.texture(icon), x, y - 2, 10, 10, 56, 56);
        text(Ui.PIXEL, label, x + 14, y, 6, Ui.WHITE);
        float end = x + 14 + width(Ui.PIXEL, label, 6) + 5;
        rect(Math.round(end), y + 3, Math.round(x + width - end), 1, Ui.WHITE);
    }

    /** Une jauge verticale qui monte jusqu'a {@code progress}, dans un filet sombre. */
    public void gauge(int x, int y, int width, int height, float progress, int color) {
        rect(x, y, width, height, 0xFF3D3C46);
        rect(x + 1, y + 1, width - 2, height - 2, Ui.SLOT_FILL);
        int filled = Math.round((height - 2) * Math.max(0f, Math.min(1f, progress)));
        rect(x + 1, y + height - 1 - filled, width - 2, filled, color);
    }

    /** L'icone de chaleur, grise, coloree depuis le bas jusqu'a {@code progress}. */
    public void heat(int x, int y, int size, float progress) {
        texture(Ui.HEAT, x, y, size, size, 44, 44, 0xFF4A4955);
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (clamped <= 0f) {
            return;
        }
        int filled = Math.max(1, Math.round(size * clamped));
        int offset = size - filled;
        float regionV = 44f * offset / size;
        graphics.blit(RenderPipelines.GUI_TEXTURED, Ui.HEAT, x, y + offset, 0.0f, regionV, size, filled,
                44, Math.round(44f * filled / size), 44, 44, 0xFFFFC266);
    }

    /** Une case d'objet : filet clair, fond sombre. */
    public void slot(int x, int y, int size, boolean hovered) {
        rect(x, y, size, size, Ui.SLOT_BORDER);
        rect(x + 1, y + 1, size - 2, size - 2, hovered ? Ui.SLOT_HOVER : Ui.SLOT_FILL);
    }

    /** Un bouton plein : la face, et un liseré sombre dessous. */
    public void button(int x, int y, int width, int height, int face, int shade, boolean hovered) {
        rect(x, y, width, height, shade);
        rect(x, y, width, height - 2, hovered ? lighten(face) : face);
        rect(x, y, width, 1, lighten(face));
    }

    /** Une fleche horizontale grise, remplie de blanc jusqu'a {@code progress}. */
    public void arrow(int x, int y, int length, float progress) {
        int shaft = Math.max(2, length - 6);
        int color = 0xFF3D3C46;
        rect(x, y + 3, shaft, 3, color);
        rect(x + shaft - 2, y, 3, 9, color);
        rect(x + shaft + 1, y + 1, 2, 7, color);
        rect(x + shaft + 3, y + 3, 2, 3, color);
        if (progress > 0) {
            rect(x, y + 3, Math.round(shaft * Math.min(1f, progress)), 3, Ui.WHITE);
        }
    }

    public static int lighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 24);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 24);
        int b = Math.min(255, (color & 0xFF) + 24);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // -- Images ----------------------------------------------------------------

    /** Une image entiere, etiree dans le rectangle. */
    public void texture(Identifier texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Une image entiere, teintee. */
    public void texture(Identifier texture, int x, int y, int width, int height, int textureWidth, int textureHeight,
                        int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height,
                textureWidth, textureHeight, textureWidth, textureHeight, color);
    }

    // -- Texte -----------------------------------------------------------------

    public float width(FontDescription face, String text, float size) {
        return font.width(Ui.text(face, clean(text))) * size / Ui.FONT_BASE;
    }

    /**
     * Les espaces insecables (celles de {@code MoneyFormat}) n'existent pas dans
     * les polices du kit : la police de secours les dessinait en double espace.
     */
    private static String clean(String text) {
        return text.indexOf('\u202F') < 0 && text.indexOf('\u00A0') < 0 ? text
                : text.replace('\u202F', ' ').replace('\u00A0', ' ');
    }

    public void text(FontDescription face, String text, float x, float y, float size, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / Ui.FONT_BASE, size / Ui.FONT_BASE);
        graphics.text(font, Ui.text(face, clean(text)), 0, 0, color, false);
        pose.popMatrix();
    }

    /** Un texte et son ombre portee vers le bas, comme les titres de Paladium. */
    public void textShaded(FontDescription face, String text, float x, float y, float size, int color, int shade,
                           float depth) {
        text(face, text, x, y + depth, size, shade);
        text(face, text, x, y, size, color);
    }

    public void centered(FontDescription face, String text, float centerX, float y, float size, int color) {
        text(face, text, centerX - width(face, text, size) / 2.0f, y, size, color);
    }

    public void right(FontDescription face, String text, float rightX, float y, float size, int color) {
        text(face, text, rightX - width(face, text, size), y, size, color);
    }

    /** Coupe un texte trop long et ajoute « ... ». */
    public String fit(FontDescription face, String text, float size, float maxWidth) {
        if (width(face, text, size) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && width(face, cut + "...", size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** Un texte sur plusieurs lignes ; rend le nombre de lignes. */
    public int wrapped(FontDescription face, String text, float x, float y, float size, float maxWidth,
                       float lineHeight, int color, int maxLines) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(face, candidate, size) > maxWidth && !line.isEmpty()) {
                text(face, line.toString(), x, y + lines * lineHeight, size, color);
                lines++;
                if (lines >= maxLines) {
                    return lines;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            text(face, line.toString(), x, y + lines * lineHeight, size, color);
            lines++;
        }
        return lines;
    }

    // -- Objets ----------------------------------------------------------------

    /** Un objet de {@code size} unites de cote (16 par defaut dans le jeu). */
    public void item(ItemStack stack, float x, float y, float size, boolean decorations) {
        if (stack.isEmpty()) {
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 16.0f, size / 16.0f);
        graphics.item(stack, 0, 0);
        if (decorations) {
            graphics.itemDecorations(font, stack, 0, 0);
        }
        pose.popMatrix();
    }

    // -- Bulles et calques -----------------------------------------------------

    public void tooltip(ItemStack stack) {
        graphics.setTooltipForNextFrame(font, stack, screenMouseX, screenMouseY);
    }

    public void tooltip(List<Component> lines) {
        if (boundsWidth <= 0) {
            graphics.setComponentTooltipForNextFrame(font, lines, screenMouseX, screenMouseY);
            return;
        }
        List<Component> copy = List.copyOf(lines);
        overlay(() -> kitTooltip(copy));
    }

    private record TipRow(FontDescription face, float size, String text, int color, float height) {
    }

    private static final float TIP_TITLE = 4.4f;
    private static final float TIP_BODY = 3.7f;
    private static final float TIP_LINE = 5.4f;
    private static final int TIP_PAD = 4;
    private static final float TIP_MAX = 150.0f;

    /** La bulle du kit : la premiere ligne en titre, le reste en texte courant, coupe a la largeur. */
    private void kitTooltip(List<Component> lines) {
        List<TipRow> rows = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            String text = line.getString();
            if (text.isBlank()) {
                rows.add(new TipRow(Ui.BODY, TIP_BODY, "", 0, TIP_LINE / 2.0f));
                continue;
            }
            boolean title = index == 0;
            FontDescription face = title ? Ui.BOLD : Ui.BODY;
            float size = title ? TIP_TITLE : TIP_BODY;
            int color = color(line);
            for (String part : wrap(face, text, size, TIP_MAX)) {
                rows.add(new TipRow(face, size, part, color, title ? TIP_LINE + 0.8f : TIP_LINE));
            }
        }
        float inner = 0.0f;
        float tall = 0.0f;
        for (TipRow row : rows) {
            inner = Math.max(inner, row.text().isEmpty() ? 0.0f : width(row.face(), row.text(), row.size()));
            tall += row.height();
        }
        int boxWidth = (int) Math.ceil(inner) + 2 * TIP_PAD;
        int boxHeight = (int) Math.ceil(tall) + 2 * TIP_PAD - 1;
        int x = (int) designMouseX + 7;
        if (x + boxWidth > boundsWidth - 2) {
            x = (int) designMouseX - 7 - boxWidth;
        }
        x = Math.max(2, x);
        int y = (int) designMouseY - 5;
        y = Math.max(2, Math.min(y, boundsHeight - 2 - boxHeight));
        rect(x, y, boxWidth, boxHeight, 0xF615141A);
        border(x, y, boxWidth, boxHeight, 1, Ui.FRAME);
        rect(x + 1, y + 1, boxWidth - 2, 1, 0xFF2A2931);
        float rowY = y + TIP_PAD;
        for (TipRow row : rows) {
            if (!row.text().isEmpty()) {
                text(row.face(), row.text(), x + TIP_PAD, rowY + (row.height() - row.size()) / 2.0f - 0.3f,
                        row.size(), row.color());
            }
            rowY += row.height();
        }
    }

    /** La couleur d'une ligne de bulle : la sienne, ou celle de son premier morceau colore. */
    private static int color(Component line) {
        if (line.getStyle().getColor() != null) {
            return 0xFF000000 | line.getStyle().getColor().getValue();
        }
        for (Component sibling : line.getSiblings()) {
            if (sibling.getStyle().getColor() != null) {
                return 0xFF000000 | sibling.getStyle().getColor().getValue();
            }
        }
        return Ui.TEXT;
    }

    /** Coupe un texte en lignes d'au plus {@code maxWidth} unites. */
    public List<String> wrap(FontDescription face, String text, float size, float maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(face, candidate, size) > maxWidth && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    /** Dessine apres tout le reste : listes deroulantes ouvertes. */
    public void overlay(Runnable draw) {
        overlays.add(draw);
    }

    void flushOverlays() {
        for (Runnable overlay : overlays) {
            graphics.nextStratum();
            overlay.run();
        }
        overlays.clear();
    }

    public void scissor(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + width, y + height);
    }

    public void endScissor() {
        graphics.disableScissor();
    }
}
