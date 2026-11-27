package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.wither.WitherBars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Les barres de boss en haut de l'ecran : le nom du jeu au-dessus d'une barre arrondie a la
 * couleur du boss, avec ses crans quand le boss en a, et une lueur au bout. Le jeu garde la
 * main sur la place de chaque barre ; on dessine a sa place et on annule la sienne.
 *
 * <p>Les withers de PalaWither ont la leur : la bande de leur genre dans la barre (celle
 * de l'overlay de PalaWither), et sa couleur pour la lueur.
 *
 * <p>Priorite basse : un mod qui dessine deja sa propre barre (et annule celle du jeu)
 * passe avant, et on ne touche pas a la sienne.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BossBarsHud {

    private static final int INCREMENT = 20;

    private BossBarsHud() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (event.isCanceled() || ZephyrHud.broken(ZephyrHud.Part.BOSS)) {
            return;
        }
        try {
            draw(event.getGuiGraphics(), event.getBossEvent(), event.getX(), event.getY());
            event.setIncrement(INCREMENT);
            event.setCanceled(true);
        } catch (Throwable error) {
            ZephyrHud.fail(ZephyrHud.Part.BOSS, error);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, LerpingBossEvent boss, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        int[] colors = colors(boss.getColor());
        WitherBars.Bar wither = WitherBars.of(boss.getName());
        float barY = y;
        float width = HudKit.WIDTH;
        float height = 5.0f;
        float progress = HudKit.clamp(boss.getProgress());
        paint.shadow(x + 1.0f, barY + 1.5f, width - 2.0f, height, 4.0f, Paint.alpha(Ui.Theme.SHADOW, 0.35f));
        paint.framed(x, barY, width, height, height / 2.0f, HudKit.TRACK, Ui.Theme.EDGE, 1);
        if (progress > 0.0f) {
            float length = Math.max(height - 2.0f, (width - 2.0f) * progress);
            if (wither != null) {
                // La bande du genre, decouverte de gauche a droite comme la barre se remplit.
                int region = Math.max(1, Math.round(WitherBars.TEXTURE_WIDTH * length / (width - 2.0f)));
                paint.image(wither.texture(), x + 1.0f, barY + 1.0f, length, height - 2.0f, 0.0f, 0.0f, region,
                        WitherBars.TEXTURE_HEIGHT, WitherBars.TEXTURE_WIDTH, WitherBars.TEXTURE_HEIGHT, 0xFFFFFFFF);
                paint.glow(x + 1.0f + length, barY + height / 2.0f, 6.0f, Paint.alpha(wither.color(), 0.5f));
            } else {
                paint.roundGradient(x + 1.0f, barY + 1.0f, length, height - 2.0f, (height - 2.0f) / 2.0f,
                        colors[0], colors[1]);
                paint.glow(x + 1.0f + length, barY + height / 2.0f, 6.0f, Paint.alpha(colors[0], 0.4f));
            }
        }
        int notches = switch (boss.getOverlay()) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> 0;
        };
        for (int notch = 1; notch < notches; notch++) {
            paint.rect(x + 1.0f + (width - 2.0f) * notch / notches - 0.25f, barY + 1.0f, 0.5f, height - 2.0f,
                    0x99000000);
        }
        int nameWidth = font.width(boss.getName());
        graphics.text(font, boss.getName(), graphics.guiWidth() / 2 - nameWidth / 2, y - 9, 0xFFFFFFFF, true);
    }

    private static int[] colors(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> new int[] {0xFFFF8FD0, 0xFFD43B93};
            case BLUE -> new int[] {0xFF7FB2FF, 0xFF2F5BEA};
            case RED -> new int[] {0xFFFF6B5E, 0xFFC9231B};
            case GREEN -> new int[] {0xFF8BF07A, 0xFF27A83A};
            case YELLOW -> new int[] {0xFFFFE070, 0xFFE0A21A};
            case PURPLE -> new int[] {0xFFC79BFF, 0xFF7B3BD6};
            case WHITE -> new int[] {0xFFFFFFFF, 0xFFB8BCC6};
            default -> new int[] {0xFFFFFFFF, 0xFFB8BCC6};
        };
    }
}
