package fr.atraxterra.dohy.client.ui;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.hud.HudKit;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;

/**
 * Les notifications du jeu au style de celles du kit ({@link ToastLayer}) : progres et defis,
 * recettes debloquees, tutoriel, messages du systeme (sauvegarde, echecs de chargement...),
 * musique en cours.
 *
 * <p>Le jeu garde tout le reste : la place en haut a droite, l'entree et la sortie en glissant,
 * la pile, la duree, le son, les mises a jour (une recette de plus, la progression du tutoriel).
 * Seul le dessin change : la carte du kit (fond en degrade, filet, coins arrondis, voyant de
 * couleur a gauche), l'icone dans sa tuile teintee, et les trois lignes du kit (la nature de la
 * notification, son titre, son detail) dans ses polices, sans accents comme ailleurs dans le
 * kit. Les crochets sont dans {@code mixin/*ToastMixin} ; si l'un d'eux echoue, la notification
 * garde le dessin du jeu.
 *
 * <p>Couleurs : progres et objectifs en jaune, defis en rose (celles du jeu), recettes en vert,
 * tutoriel en cyan, systeme en bleu (en rouge pour un echec), musique a la couleur des notes.
 */
public final class KitToasts {

    private static final float RADIUS = 5.0f;
    private static final float TILE = 20.0f;
    private static final float TEXT_X = 32.0f;
    private static final float KEY = 4.0f;
    private static final float TITLE = 6.8f;
    private static final float LINE = 5.6f;
    /** La couleur du titre du tutoriel, posee par le jeu sur ses premieres lignes. */
    private static final int TUTORIAL_TITLE = 0x500050;

    private static boolean loggedFailure;

    private KitToasts() {
    }

    // -- Les notifications ------------------------------------------------------------------

    /** Un progres, un objectif ou un defi : son icone, sa nature, son titre et sa description. */
    public static boolean advancement(GuiGraphicsExtractor graphics, int width, int height, DisplayInfo display,
                                      ItemStack icon) {
        boolean challenge = display != null && display.getType() == AdvancementType.CHALLENGE;
        int accent = challenge ? Ui.Theme.MAGENTA : Ui.Theme.YELLOW;
        return draw(graphics, width, height, accent, (g, p) -> {
            float tileY = tile(p, height, accent);
            graphics.fakeItem(icon, 9, Math.round(tileY + 2.0f));
            if (display != null) {
                lines(g, width, height, display.getType().getDisplayName().getString(), accent,
                        display.getTitle().getString(), display.getDescription().getString());
            }
        });
    }

    /** Des recettes debloquees : la derniere en icone (le poste de travail en pastille), son nom. */
    public static boolean recipe(GuiGraphicsExtractor graphics, int width, int height, Component title,
                                 Component description, ItemStack unlocked, ItemStack category) {
        int accent = Ui.Theme.GREEN;
        return draw(graphics, width, height, accent, (g, p) -> {
            float tileY = tile(p, height, accent);
            graphics.fakeItem(unlocked, 9, Math.round(tileY + 2.0f));
            if (!category.isEmpty()) {
                Matrix3x2fStack pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(7.0f + TILE - 8.5f, tileY + TILE - 8.5f);
                pose.scale(0.5f, 0.5f);
                graphics.fakeItem(category, 0, 0);
                pose.popMatrix();
            }
            lines(g, width, height, title.getString(), accent, unlocked.getHoverName().getString(),
                    description.getString());
        });
    }

    /**
     * Le tutoriel : son image dans la tuile, son titre et sa consigne (deja coupes par le jeu a
     * sa largeur), et sa barre de progression quand il en a une.
     */
    public static boolean tutorial(GuiGraphicsExtractor graphics, int width, int height, TutorialToast.Icons icon,
                                   List<FormattedCharSequence> content, boolean progressable, float progress,
                                   float smoothed) {
        int accent = Ui.Theme.CYAN;
        return draw(graphics, width, height, accent, (g, p) -> {
            float tileY = tile(p, Math.min(height, 32), accent);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(8.0f, tileY + 1.0f);
            pose.scale(0.9f, 0.9f);
            icon.extractRenderState(graphics, 0, 0);
            pose.popMatrix();
            float bottom = progressable ? height - 7.0f : height;
            stacked(g, width, bottom, "Tutoriel", accent, content, TUTORIAL_TITLE);
            if (progressable) {
                boolean rising = progress >= smoothed;
                int top = rising ? Paint.mix(Ui.Theme.GREEN, 0xFFFFFFFF, 0.25f) : Paint.mix(Ui.Theme.RED, 0xFFFFFFFF, 0.2f);
                int under = rising ? Paint.mix(Ui.Theme.GREEN, 0xFF000000, 0.2f) : Paint.mix(Ui.Theme.RED, 0xFF000000, 0.2f);
                HudKit.bar(p, TEXT_X, height - 7.0f, width - TEXT_X - 6.0f, 4.0f, smoothed, top, under, false);
            }
        });
    }

    /** Un message du systeme : ses lignes de titre, puis ses lignes de message, sans icone. */
    public static boolean system(GuiGraphicsExtractor graphics, int width, int height, SystemToast.SystemToastId id,
                                 List<FormattedCharSequence> title, List<FormattedCharSequence> message) {
        int accent = failure(id) ? Ui.Theme.RED : Ui.Theme.BLUE;
        return draw(graphics, width, height, accent, (g, p) -> {
            float x = 10.0f;
            float max = width - x - 6.0f;
            int count = title.size() + message.size();
            float total = title.size() * (TITLE + 2.2f) + message.size() * (LINE + 2.2f) - 2.2f;
            float y = Math.max(4.0f, (height - total) / 2.0f);
            for (FormattedCharSequence line : title) {
                g.text(Ui.BOLD, g.fit(Ui.BOLD, plain(text(line)), TITLE, max), x, y, TITLE, Ui.Theme.TEXT);
                y += TITLE + 2.2f;
            }
            for (FormattedCharSequence line : message) {
                g.text(Ui.BODY, g.fit(Ui.BODY, plain(text(line)), LINE, max), x, y, LINE, Ui.Theme.TEXT2);
                y += LINE + 2.2f;
            }
            if (count == 0) {
                g.text(Ui.PIXEL, "SYSTEME", x, (height - KEY) / 2.0f, KEY, accent);
            }
        });
    }

    /** La musique en cours : les notes du jeu, a leur couleur changeante, et le titre du morceau. */
    public static boolean nowPlaying(GuiGraphicsExtractor graphics, int width, int height, Identifier notes,
                                     int noteColor, String song) {
        int accent = 0xFF000000 | noteColor;
        return draw(graphics, width, height, accent, (g, p) -> {
            float tileY = tile(p, height, accent);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, notes, 9, Math.round(tileY + 2.0f), 16, 16, accent);
            lines(g, width, height, "Musique", accent, song, "");
        });
    }

    // -- Le dessin ------------------------------------------------------------------------------

    private interface Body {
        void draw(UiGraphics g, Paint p);
    }

    /**
     * La carte et son contenu. Vrai si tout s'est dessine ; au premier echec, on le note une fois
     * et le jeu reprend son propre dessin (faux), pour cette notification et les suivantes.
     */
    private static boolean draw(GuiGraphicsExtractor graphics, int width, int height, int accent, Body body) {
        if (loggedFailure) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float saved = Ui.drawUnit();
        try {
            Paint p = new Paint(graphics, HudKit.unit(minecraft));
            card(p, width, height, accent);
            UiGraphics g = new UiGraphics(graphics, minecraft.font, -1, -1);
            body.draw(g, p);
            return true;
        } catch (Throwable error) {
            loggedFailure = true;
            DoHy.LOGGER.error("Notification du jeu au style du kit impossible, on garde celle du jeu", error);
            return false;
        } finally {
            // Les mesures d'un ecran ouvert (clics, champs) reprennent son echelle.
            Ui.drawUnit(saved);
        }
    }

    /** La carte : son ombre, son filet, son fond en degrade, et le voyant de couleur a gauche. */
    private static void card(Paint p, float width, float height, int accent) {
        p.shadow(0.0f, 2.5f, width, height, 7.0f, 0x66000000);
        float line = 1.0f / p.unit();
        p.round(0.0f, 0.0f, width, height, RADIUS, 0xFF3A3C42);
        p.roundGradient(line, line, width - 2 * line, height - 2 * line, RADIUS - line, 0xFF303238, 0xFF16171A);
        float pillY = 5.0f;
        float pillHeight = height - 10.0f;
        p.shadow(1.6f, pillY, 2.0f, pillHeight, 4.0f, Paint.alpha(accent, 0.45f));
        p.round(1.6f, pillY, 2.0f, pillHeight, 1.0f, accent);
    }

    /** La tuile teintee de l'icone, centree sur la hauteur ; rend son haut. */
    private static float tile(Paint p, float height, int accent) {
        float y = Math.round((height - TILE) / 2.0f);
        int fill = Paint.mix(0xFF1A1B1E, accent, 0.16f);
        p.framed(7.0f, y, TILE, TILE, 4.5f, fill, Paint.mix(fill, accent, 0.35f), 1);
        return y;
    }

    /** Les trois lignes du kit : la nature, le titre, le detail (facultatif), centrees sur la hauteur. */
    private static void lines(UiGraphics g, float width, float height, String key, int keyColor, String title,
                              String line) {
        float max = width - TEXT_X - 6.0f;
        String detail = plain(line).trim();
        float total = KEY + 1.8f + TITLE + (detail.isEmpty() ? 0.0f : 1.4f + LINE);
        float y = (height - total) / 2.0f;
        g.text(Ui.PIXEL, g.fit(Ui.PIXEL, plain(key).toUpperCase(Locale.ROOT), KEY, max), TEXT_X, y, KEY, keyColor);
        y += KEY + 1.8f;
        g.text(Ui.BOLD, g.fit(Ui.BOLD, plain(title), TITLE, max), TEXT_X, y, TITLE, Ui.Theme.TEXT);
        if (!detail.isEmpty()) {
            y += TITLE + 1.4f;
            g.text(Ui.BODY, g.fit(Ui.BODY, detail, LINE, max), TEXT_X, y, LINE, Ui.Theme.TEXT2);
        }
    }

    /**
     * La nature, puis des lignes deja coupees par le jeu : celles a la couleur {@code titleColor}
     * en titre, les autres en detail.
     */
    private static void stacked(UiGraphics g, float width, float height, String key, int keyColor,
                                List<FormattedCharSequence> content, int titleColor) {
        float max = width - TEXT_X - 6.0f;
        float total = KEY + 1.8f;
        for (FormattedCharSequence line : content) {
            total += (color(line) == titleColor ? TITLE : LINE) + 1.4f;
        }
        float y = Math.max(3.0f, (height - total + 1.4f) / 2.0f);
        g.text(Ui.PIXEL, plain(key).toUpperCase(Locale.ROOT), TEXT_X, y, KEY, keyColor);
        y += KEY + 1.8f;
        for (FormattedCharSequence line : content) {
            boolean heading = color(line) == titleColor;
            float size = heading ? TITLE : LINE;
            g.text(heading ? Ui.BOLD : Ui.BODY, g.fit(heading ? Ui.BOLD : Ui.BODY, plain(text(line)), size, max),
                    TEXT_X, y, size, heading ? Ui.Theme.TEXT : Ui.Theme.TEXT2);
            y += size + 1.4f;
        }
    }

    // -- Les textes -------------------------------------------------------------------------------

    /** Un texte sans accents : les polices du kit n'en ont pas. */
    private static String plain(String text) {
        return ContainerStyle.plain(text);
    }

    /** Le texte d'une ligne deja coupee par le jeu. */
    private static String text(FormattedCharSequence line) {
        StringBuilder out = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    /** La couleur du premier caractere d'une ligne (sans couleur : -1). */
    private static int color(FormattedCharSequence line) {
        int[] found = {-1};
        line.accept((index, style, codePoint) -> {
            TextColor color = style.getColor();
            found[0] = color == null ? -1 : color.getValue() & 0xFFFFFF;
            return false;
        });
        return found[0];
    }

    /** Les messages du systeme qui disent un echec ou un danger. */
    private static boolean failure(SystemToast.SystemToastId id) {
        return id == SystemToast.SystemToastId.PACK_LOAD_FAILURE || id == SystemToast.SystemToastId.WORLD_ACCESS_FAILURE
                || id == SystemToast.SystemToastId.PACK_COPY_FAILURE || id == SystemToast.SystemToastId.FILE_DROP_FAILURE
                || id == SystemToast.SystemToastId.LOW_DISK_SPACE || id == SystemToast.SystemToastId.CHUNK_LOAD_FAILURE
                || id == SystemToast.SystemToastId.CHUNK_SAVE_FAILURE
                || id == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING;
    }
}
