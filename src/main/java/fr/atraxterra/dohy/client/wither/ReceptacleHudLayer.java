package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.DoHy;
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
        WitherKind kind = receptacle.summoning();
        int height = kind == null ? 34 : 56;
        int x = (minecraft.getWindow().getGuiScaledWidth() - WIDTH) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() / 2 + 18;
        ui.panelPlain(x, y, WIDTH, height);
        ui.item(new ItemStack(PalaWithers.WITHER_RECEPTACLE.get()), x + 8, y + 9, 16, false);
        int textX = x + 30;
        int barWidth = WIDTH - 38;
        ui.text(Ui.WIDE, "RÉCEPTACLE À WITHER", textX, y + 6, 6, Ui.WHITE);
        int health = receptacle.health();
        ui.text(Ui.BODY, String.format("%,d / %,d PV", health, WitherReceptacleBlockEntity.MAX_HEALTH)
                .replace(',', ' '), textX, y + 14, 6.5f, Ui.TEXT);
        bar(ui, textX, y + 24, barWidth, health / (float) WitherReceptacleBlockEntity.MAX_HEALTH, HEALTH_COLOR);
        if (kind != null) {
            String name = Component.translatable("entity.palamod." + kind.id()).getString();
            int total = kind.summonTicks();
            String line = receptacle.absent() > 0
                    ? "Reviens à 6 blocs : " + Math.max(0, (20 * 60 - receptacle.absent()) / 20) + " s"
                    : "Invocation : " + name + " — " + clock(total - receptacle.progress());
            ui.text(Ui.BODY, line, textX, y + 34, 6.5f, receptacle.absent() > 0 ? HEALTH_COLOR : Ui.TEXT);
            bar(ui, textX, y + 44, barWidth, receptacle.progress() / (float) total, SUMMON_COLOR);
        }
    }

    private static void bar(UiGraphics ui, int x, int y, int width, float progress, int color) {
        ui.rect(x, y, width, 5, 0xFF3D3C46);
        ui.rect(x + 1, y + 1, width - 2, 3, Ui.SLOT_FILL);
        ui.rect(x + 1, y + 1, Math.round((width - 2) * Math.max(0.0f, Math.min(1.0f, progress))), 3, color);
    }

    private static String clock(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
