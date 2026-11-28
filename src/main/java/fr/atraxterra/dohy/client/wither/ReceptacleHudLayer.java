package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.hud.HudKit;
import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.wither.PalaWithers;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import fr.atraxterra.dohy.palamod.wither.WitherReceptacleBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.Locale;

/**
 * La barre du receptacle a wither : ses points de vie quand on le regarde —
 * pas a travers les murs, comme sur Paladium —, et l'invocation en cours.
 */
public final class ReceptacleHudLayer implements GuiLayer {

    private static final int WIDTH = 176;
    private static final int HEALTH_COLOR = 0xFFEF3826;
    private static final int SUMMON_COLOR = 0xFFB45CF0;

    private boolean loggedFailure;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            renderInner(graphics);
        } catch (Throwable error) {
            if (!loggedFailure) {
                loggedFailure = true;
                DoHy.LOGGER.error("Le HUD du receptacle a echoue, il est desactive pour cette session", error);
            }
        }
    }

    private void renderInner(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof WitherReceptacleBlockEntity receptacle)) {
            return;
        }
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        Paint paint = new Paint(graphics, HudKit.unit(minecraft));
        WitherKind kind = receptacle.summoning();
        int height = kind == null ? 34 : 56;
        int x = (minecraft.getWindow().getGuiScaledWidth() - WIDTH) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() / 2 + 18;
        // Une carte du HUD : le receptacle dans sa tuile, ses points de vie, l'invocation en cours.
        HudKit.card(paint, x, y, WIDTH, height, 5.0f);
        int tile = Paint.mix(0xFF1A1B1E, SUMMON_COLOR, 0.18f);
        paint.framed(x + 6.0f, y + 7.0f, 20.0f, 20.0f, 4.5f, tile, Paint.mix(tile, SUMMON_COLOR, 0.45f), 1);
        ui.item(new ItemStack(PalaWithers.WITHER_RECEPTACLE.get()), x + 8, y + 9, 16, false);
        int textX = x + 32;
        int barWidth = WIDTH - 40;
        String title = ContainerStyle.plain(PalaWithers.WITHER_RECEPTACLE.get().getName().getString())
                .toUpperCase(Locale.ROOT);
        ui.text(Ui.WIDE, ui.fit(Ui.WIDE, title, 6, barWidth), textX, y + 6, 6, Ui.Theme.TEXT);
        int health = receptacle.health();
        ui.text(Ui.BODY, String.format("%,d / %,d PV", health, WitherReceptacleBlockEntity.MAX_HEALTH)
                .replace(',', ' '), textX, y + 14, 6.5f, Ui.Theme.TEXT2);
        HudKit.bar(paint, textX, y + 24, barWidth, 5, health / (float) WitherReceptacleBlockEntity.MAX_HEALTH,
                0xFFFF5F57, HEALTH_COLOR, false);
        if (kind != null) {
            String name = ContainerStyle.plain(Component.translatable("entity.palamod." + kind.id()).getString());
            int total = kind.summonTicks();
            String line = receptacle.absent() > 0
                    ? "Reviens a 6 blocs : " + Math.max(0, (20 * 60 - receptacle.absent()) / 20) + " s"
                    : "Invocation : " + name + "  " + clock(total - receptacle.progress());
            ui.text(Ui.BODY, ui.fit(Ui.BODY, line, 6.5f, barWidth), textX, y + 34, 6.5f,
                    receptacle.absent() > 0 ? HEALTH_COLOR : Ui.Theme.TEXT2);
            HudKit.bar(paint, textX, y + 44, barWidth, 5, receptacle.progress() / (float) total, 0xFFD08CFF,
                    SUMMON_COLOR, false);
        }
    }

    private static String clock(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
