package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Les mesures et les gestes communs du HUD Zephyr.
 *
 * <p>Tout se dessine en pixels d'interface, comme le HUD du jeu, avec la meme emprise : la
 * hotbar fait 182 de large au centre du bas, les barres d'etat tiennent la rangee des coeurs
 * et de la faim, et les compteurs {@code leftHeight} / {@code rightHeight} de NeoForge montent
 * comme ceux du jeu, pour que les HUD des autres mods se posent toujours au-dessus.
 */
final class HudKit {

    /** La largeur de la hotbar, celle du jeu. */
    static final int WIDTH = 182;
    /** La hauteur de la carte de la hotbar. */
    static final int HOTBAR_HEIGHT = 22;
    /** L'air sous la hotbar : la carte flotte au-dessus du bord. */
    static final int BOTTOM = 2;
    /** La largeur d'une colonne de barres (sante a gauche, faim a droite) ; le milieu garde le niveau. */
    static final int COLUMN = 82;
    /** La hauteur d'une barre d'etat. */
    static final int BAR = 7;
    /** Le pas entre deux rangees de barres, celui des rangees de coeurs du jeu. */
    static final int ROW = 10;

    // -- Couleurs des barres : le haut, puis le bas du degrade --------------------------
    static final int HEALTH_TOP = 0xFFFF5F57;
    static final int HEALTH_BOTTOM = 0xFFC8231B;
    static final int POISON_TOP = 0xFFA4D65E;
    static final int POISON_BOTTOM = 0xFF5E8F1F;
    static final int WITHER_TOP = 0xFF6E6A73;
    static final int WITHER_BOTTOM = 0xFF2E2B33;
    static final int FROZEN_TOP = 0xFFBDEBFF;
    static final int FROZEN_BOTTOM = 0xFF5BB4E0;
    static final int ABSORB_TOP = 0xFFFFE070;
    static final int ABSORB_BOTTOM = 0xFFE0A21A;
    static final int FOOD_TOP = 0xFFF7C26B;
    static final int FOOD_BOTTOM = 0xFFC9751F;
    static final int HUNGER_TOP = 0xFFB9C46A;
    static final int HUNGER_BOTTOM = 0xFF6F7A26;
    static final int SATURATION = 0xFFFFF1B0;
    static final int ARMOR_TOP = 0xFFD5E0EC;
    static final int ARMOR_BOTTOM = 0xFF7D8FA3;
    static final int AIR_TOP = 0xFF9BE3FF;
    static final int AIR_BOTTOM = 0xFF2C93D6;
    static final int WATER_TOP = 0xFF6FD3FF;
    static final int WATER_BOTTOM = 0xFF1C72C4;
    static final int MOUNT_TOP = 0xFFFF8FB1;
    static final int MOUNT_BOTTOM = 0xFFC93A68;
    static final int XP_TOP = 0xFFC6F86A;
    static final int XP_BOTTOM = 0xFF5FBF1E;
    static final int JUMP_TOP = 0xFFFFD36B;
    static final int JUMP_BOTTOM = 0xFFE07A10;
    static final int TRACK = 0xFF101113;

    private HudKit() {
    }

    /** Les pixels physiques par pixel d'interface ; la police pixel s'y cale. */
    static float unit(Minecraft minecraft) {
        float unit = Math.max(1, minecraft.getWindow().getGuiScale());
        Ui.drawUnit(unit);
        return unit;
    }

    /** Le joueur que suit la camera, ou {@code null} (camera sur une autre entite). */
    static Player cameraPlayer(Minecraft minecraft) {
        return minecraft.getCameraEntity() instanceof Player player ? player : null;
    }

    static boolean hidden(Minecraft minecraft) {
        return minecraft.gui.hud.isHidden();
    }

    /** Le joueur peut-il prendre des degats : survie et aventure, comme les coeurs du jeu. */
    static boolean survival(Minecraft minecraft) {
        return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    static int left(GuiGraphicsExtractor graphics) {
        return graphics.guiWidth() / 2 - WIDTH / 2;
    }

    static int hotbarTop(GuiGraphicsExtractor graphics) {
        return graphics.guiHeight() - BOTTOM - HOTBAR_HEIGHT;
    }

    static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int size) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
    }

    static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int size, int color) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size, color);
    }

    /** Une carte du kit : son ombre, son fond et son filet. */
    static void card(Paint paint, float x, float y, float width, float height, float radius) {
        paint.shadow(x + 1.5f, y + 2.5f, width - 3.0f, height - 2.0f, 5.0f, Paint.alpha(Ui.Theme.SHADOW, 0.42f));
        paint.framed(x, y, width, height, radius, Ui.Theme.BG2, Ui.Theme.EDGE, 1);
    }

    /**
     * Une barre : la gorge sombre, puis le remplissage en degrade sur {@code fill} de la
     * longueur. {@code fromRight} la remplit depuis la droite (la faim, comme ses cuisses).
     */
    static void bar(Paint paint, float x, float y, float width, float height, float fill, int top, int bottom,
                    boolean fromRight) {
        float radius = height / 2.0f;
        paint.framed(x, y, width, height, radius, TRACK, Ui.Theme.EDGE, 1);
        segment(paint, x, y, width, height, 0.0f, fill, top, bottom, fromRight);
    }

    /** Un morceau de barre, de {@code from} a {@code to} (en fractions de la longueur). */
    static void segment(Paint paint, float x, float y, float width, float height, float from, float to, int top,
                        int bottom, boolean fromRight) {
        float a = clamp(Math.min(from, to));
        float b = clamp(Math.max(from, to));
        float inner = width - 2.0f;
        float length = inner * (b - a);
        if (length < 0.5f) {
            return;
        }
        float start = fromRight ? x + 1.0f + inner * (1.0f - b) : x + 1.0f + inner * a;
        float radius = (height - 2.0f) / 2.0f;
        paint.roundGradient(start, y + 1.0f, Math.max(length, Math.min(inner, height - 2.0f)), height - 2.0f, radius,
                top, bottom);
    }

    static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
