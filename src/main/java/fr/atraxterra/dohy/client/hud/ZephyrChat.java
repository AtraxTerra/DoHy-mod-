package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Le chat au style Zephyr : une seule carte arrondie derriere les lignes au lieu d'un
 * rectangle noir par ligne.
 *
 * <p>Le jeu garde tout le reste : quelles lignes montrer, leur fondu, le defilement, les
 * liens et les bulles au survol, les marques des messages systeme. On s'intercale entre le
 * chat et son dessin ({@code ChatComponentMixin}) : les fonds de ligne sont retenus, puis la
 * carte est posee sous leur ensemble, et les autres traits (marques, barre de defilement)
 * sont rejoues par-dessus, dans l'ordre. Le texte passe tout de suite : il se dessine de
 * toute facon au-dessus des fonds.
 *
 * <p>Si la fin du dessin n'est jamais signalee (le crochet n'a pas pris), ou si quelque chose
 * echoue, le chat redevient celui du jeu pour la session.
 */
public final class ZephyrChat implements ChatComponent.ChatGraphicsAccess {

    private static GuiGraphicsExtractor pendingGraphics;
    private static ZephyrChat open;
    private static boolean disabled;

    private final ChatComponent.ChatGraphicsAccess inner;
    private final GuiGraphicsExtractor graphics;
    private final List<Runnable> replay = new ArrayList<>();
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxAlpha;
    private boolean finished;

    private ZephyrChat(ChatComponent.ChatGraphicsAccess inner, GuiGraphicsExtractor graphics) {
        this.inner = inner;
        this.graphics = graphics;
    }

    /** Le debut du dessin du chat : on retient sur quoi il dessine. */
    public static void begin(GuiGraphicsExtractor graphics) {
        pendingGraphics = graphics;
    }

    /** Le chat va dessiner avec {@code access} : on s'intercale, si on peut. */
    public static ChatComponent.ChatGraphicsAccess wrap(ChatComponent.ChatGraphicsAccess access) {
        GuiGraphicsExtractor graphics = pendingGraphics;
        pendingGraphics = null;
        if (disabled || graphics == null || ZephyrHud.broken(ZephyrHud.Part.CHAT)) {
            return access;
        }
        if (open != null && !open.finished) {
            // Le dessin precedent n'a jamais ete termine : le crochet de fin manque.
            disabled = true;
            DoHy.LOGGER.warn("Chat Zephyr : fin du dessin jamais signalee, le chat reste celui du jeu");
            open = null;
            return access;
        }
        open = new ZephyrChat(access, graphics);
        return open;
    }

    /** La fin du dessin du chat : la carte, puis ce qui a ete retenu. */
    public static void finish(ChatComponent.ChatGraphicsAccess access) {
        if (access instanceof ZephyrChat chat) {
            chat.finished = true;
            if (open == chat) {
                open = null;
            }
            try {
                chat.flush();
            } catch (Throwable error) {
                ZephyrHud.fail(ZephyrHud.Part.CHAT, error);
            }
        }
    }

    private void flush() {
        if (maxAlpha > 0 && maxX > minX && maxY > minY) {
            Minecraft minecraft = Minecraft.getInstance();
            float scale = (float) minecraft.gui.hud.getChat().getScale();
            float unit = Math.max(1, minecraft.getWindow().getGuiScale()) * Math.max(0.25f, scale);
            Paint paint = new Paint(graphics, unit);
            float alpha = Math.min(0.92f, maxAlpha / 255.0f * 1.6f);
            float x = minX;
            float y = minY - 1.0f;
            float width = maxX - minX;
            float height = maxY - minY + 2.0f;
            paint.round(x, y, width, height, 3.5f, Paint.alpha(0xFF121315, alpha));
            paint.outline(x, y, width, height, 3.5f, Paint.alpha(Ui.Theme.EDGE, alpha), 1);
        }
        for (Runnable action : replay) {
            action.run();
        }
        replay.clear();
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> update) {
        inner.updatePose(update);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        // Les fonds de ligne commencent a -4 (et celui de la file d'attente a -2) : ils font la carte.
        if (x0 < 0 && x1 > 0) {
            minX = Math.min(minX, Math.min(x0, x1));
            maxX = Math.max(maxX, Math.max(x0, x1));
            minY = Math.min(minY, Math.min(y0, y1));
            maxY = Math.max(maxY, Math.max(y0, y1));
            maxAlpha = Math.max(maxAlpha, color >>> 24);
            return;
        }
        replay.add(() -> inner.fill(x0, y0, x1, y1, color));
    }

    @Override
    public boolean handleMessage(int y, float opacity, FormattedCharSequence text) {
        return inner.handleMessage(y, opacity, text);
    }

    @Override
    public void handleTag(int x0, int y0, int x1, int y1, float opacity, GuiMessageTag tag) {
        replay.add(() -> inner.handleTag(x0, y0, x1, y1, opacity, tag));
    }

    @Override
    public void handleTagIcon(int x, int y, boolean forced, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        replay.add(() -> inner.handleTagIcon(x, y, forced, tag, icon));
    }
}
