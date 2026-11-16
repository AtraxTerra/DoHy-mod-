package fr.atraxterra.dohy.client.ui;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Les notifications du kit, en bas a droite : elles glissent depuis le bord, une
 * barre fine decompte leur temps, puis elles repartent par ou elles sont venues.
 *
 * <p>Les notifications d'un meme groupe se remplacent au lieu de s'empiler : l'XP
 * d'un metier monte dans la meme carte tant qu'on mine.
 *
 * <p>Une couche de HUD se dessine sous les ecrans. Quand un ecran du kit est ouvert,
 * c'est lui qui redessine les notifications par-dessus ({@link #drawOver}) ; la
 * couche se tait pour ne pas les dessiner deux fois.
 *
 * <p>Les mesures sont celles des maquettes, en unites de maquette (un tiers de
 * pixel de maquette), avec la meme echelle que les menus : une notification a la
 * meme taille sur le HUD et par-dessus un menu.
 */
public final class ToastLayer implements GuiLayer {

    private static final int DESIGN_WIDTH = 608;
    private static final int DESIGN_HEIGHT = 334;
    private static final float WIDTH = 140.0f;
    private static final float GAP = 3.3f;
    private static final float MARGIN = 8.7f;
    private static final float RADIUS = 5.3f;
    private static final float TILE = 16.7f;
    private static final double SLIDE_IN = 0.32d;
    private static final double SLIDE_OUT = 0.26d;
    private static final int MAX = 4;

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean loggedFailure;

    /** Une notification a l'ecran, et l'etat de ses animations. */
    private static final class Entry {
        private UiNet.Toast toast;
        private final double shown = Anim.now();
        private double refreshed = shown;
        private double leaving = -1.0d;
        /** La hauteur de la pile sous la carte : elle glisse quand une voisine part. */
        private final Anim.Value offset = new Anim.Value(-1.0f, 12.0f);

        private Entry(UiNet.Toast toast) {
            this.toast = toast;
        }

        private double life() {
            return Math.max(1.0d, toast.millis() / 1000.0d);
        }

        private boolean gone() {
            return leaving >= 0 && Anim.now() - leaving >= SLIDE_OUT;
        }
    }

    /** Une notification recue du serveur. */
    public static void push(UiNet.Toast toast) {
        if (!toast.group().isEmpty()) {
            for (Entry entry : ENTRIES) {
                if (entry.leaving < 0 && entry.toast.group().equals(toast.group())) {
                    entry.toast = toast;
                    entry.refreshed = Anim.now();
                    return;
                }
            }
        }
        ENTRIES.add(new Entry(toast));
        int alive = 0;
        for (int index = ENTRIES.size() - 1; index >= 0; index--) {
            Entry entry = ENTRIES.get(index);
            if (entry.leaving < 0 && ++alive > MAX) {
                entry.leaving = Anim.now();
            }
        }
    }

    /** Oublie tout : deconnexion, changement de monde. */
    public static void clear() {
        ENTRIES.clear();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ENTRIES.clear();
            return;
        }
        if (minecraft.gui.screen() instanceof UiScreen) {
            return;
        }
        draw(graphics);
    }

    /** Par-dessus un ecran du kit : appele a la fin de son rendu. */
    public static void drawOver(GuiGraphicsExtractor graphics) {
        if (!ENTRIES.isEmpty()) {
            float screenUnit = Ui.drawUnit();
            graphics.nextStratum();
            draw(graphics);
            // Les mesures de l'ecran (clics, champs de texte) reprennent son echelle.
            Ui.drawUnit(screenUnit);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        ENTRIES.removeIf(Entry::gone);
        if (ENTRIES.isEmpty()) {
            return;
        }
        try {
            drawInner(graphics);
        } catch (Throwable error) {
            // Une exception dans le HUD fait tomber le jeu : on degrade vers rien.
            ENTRIES.clear();
            if (!loggedFailure) {
                loggedFailure = true;
                DoHy.LOGGER.error("Les notifications du kit ont echoue", error);
            }
        }
    }

    private static void drawInner(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        // La meme echelle que les menus (UiScreen) : une unite = un nombre entier de pixels.
        float fit = Math.min(screenWidth * 0.96f / DESIGN_WIDTH, screenHeight * 0.96f / DESIGN_HEIGHT);
        float physical = (float) Math.floor(fit * guiScale);
        float scale = physical >= 1.0f ? physical / guiScale : fit;
        float right = screenWidth / scale - MARGIN;
        float bottom = screenHeight / scale - MARGIN;

        Ui.drawUnit(scale * guiScale);
        UiGraphics g = new UiGraphics(graphics, minecraft.font, -1, -1);
        g.paint(new Paint(graphics, scale * guiScale));
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        // De la plus recente, en bas, a la plus ancienne, au-dessus.
        float stack = 0.0f;
        for (int index = ENTRIES.size() - 1; index >= 0; index--) {
            Entry entry = ENTRIES.get(index);
            float height = height(entry.toast);
            if (entry.offset.get() < 0) {
                entry.offset.set(stack);
            }
            float offset = entry.offset.toward(stack);
            drawToast(g, entry, right - WIDTH, bottom - offset - height, height);
            if (entry.leaving < 0) {
                stack += height + GAP;
            }
        }
        pose.popMatrix();
    }

    private static float height(UiNet.Toast toast) {
        return 4.7f + Math.max(TILE, textHeight(toast)) + 5.3f;
    }

    private static float textHeight(UiNet.Toast toast) {
        return 3.3f + 1.3f + 5.7f + (toast.line().isEmpty() ? 0.0f : 1.0f + 4.7f);
    }

    private static void drawToast(UiGraphics g, Entry entry, float baseX, float y, float height) {
        UiNet.Toast toast = entry.toast;
        double now = Anim.now();
        // Entree depuis le bord droit, sortie par le meme chemin.
        float enter = Anim.easeOut(Anim.progress(entry.shown, SLIDE_IN));
        float exit = entry.leaving < 0 ? 0.0f : Anim.easeInOut(Anim.progress(entry.leaving, SLIDE_OUT));
        float slide = (1.0f - enter) + exit;
        float x = baseX + slide * (WIDTH + MARGIN + 6.0f);
        double elapsed = now - entry.refreshed;
        if (entry.leaving < 0 && elapsed >= entry.life()) {
            entry.leaving = now;
        }
        float remaining = (float) Math.max(0.0d, 1.0d - elapsed / entry.life());
        // Un remplacement fait briller la carte un instant.
        float flash = 1.0f - Anim.progress(entry.refreshed, 0.45d);
        if (entry.refreshed == entry.shown) {
            flash = 0.0f;
        }
        int accent = 0xFF000000 | toast.accent();
        Paint p = g.paint();

        p.shadow(x, y + 3.3f, WIDTH, height, 9.0f, 0x66000000);
        float line = 1.0f / p.unit();
        p.round(x, y, WIDTH, height, RADIUS, Paint.mix(0xFF3A3C42, accent, 0.35f * flash));
        p.roundGradient(x + line, y + line, WIDTH - 2 * line, height - 2 * line, RADIUS - line,
                Paint.mix(0xFF303238, accent, 0.10f * flash), 0xFF16171A);

        // Le voyant de gauche, et son halo.
        float pillX = x + 1.3f;
        float pillY = y + 4.7f;
        float pillHeight = height - 9.4f;
        p.shadow(pillX, pillY, 1.7f, pillHeight, 4.0f, Paint.alpha(accent, 0.45f + 0.35f * flash));
        p.round(pillX, pillY, 1.7f, pillHeight, 0.85f, accent);

        // L'icone dans sa tuile teintee.
        float tileX = x + 6.0f;
        float tileY = y + (height - TILE) / 2.0f;
        int tileFill = Paint.mix(0xFF1A1B1E, accent, 0.16f);
        p.framed(tileX, tileY, TILE, TILE, 4.0f, tileFill, Paint.mix(tileFill, accent, 0.35f), 1);
        g.item(toast.icon(), tileX + (TILE - 10.7f) / 2.0f, tileY + (TILE - 10.7f) / 2.0f, 10.7f, false);

        // Les trois lignes.
        float textX = tileX + TILE + 4.7f;
        float textWidth = x + WIDTH - 5.3f - textX;
        float textY = y + (height - textHeight(toast)) / 2.0f;
        g.text(Ui.PIXEL, g.fit(Ui.PIXEL, toast.key(), 3.3f, textWidth), textX, textY, 3.3f, Ui.Theme.MUTED);
        textY += 3.3f + 1.3f;
        g.text(Ui.BOLD, g.fit(Ui.BOLD, toast.title(), 5.7f, textWidth), textX, textY, 5.7f,
                Paint.mix(Ui.Theme.TEXT, accent, 0.5f * flash));
        if (!toast.line().isEmpty()) {
            textY += 5.7f + 1.0f;
            g.text(Ui.BODY, g.fit(Ui.BODY, toast.line(), 4.7f, textWidth), textX, textY, 4.7f, Ui.Theme.TEXT2);
        }

        // Le temps qui reste, en bas.
        float trackX = textX;
        float trackWidth = x + WIDTH - 5.3f - trackX;
        float trackY = y + height - 2.4f;
        p.round(trackX, trackY, trackWidth, 1.0f, 0.5f, 0x14FFFFFF);
        if (remaining > 0.0f) {
            p.round(trackX, trackY, trackWidth * remaining, 1.0f, 0.5f, Paint.alpha(accent, 0.8f));
        }
    }
}
