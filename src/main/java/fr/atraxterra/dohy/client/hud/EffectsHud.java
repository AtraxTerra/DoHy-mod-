package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Les effets en cours, en puces sous la carte de faction : l'icone du jeu, le temps qui
 * reste dessous, le niveau en chiffre romain dans le coin, le bord vert pour un bienfait,
 * rouge pour un mal, cyan pour un effet de balise. Une puce clignote ses dix dernieres
 * secondes, comme dans le jeu.
 */
final class EffectsHud {

    private static final int CHIP = 18;
    private static final int CELL = 20;
    private static final int ROW = 26;
    private static final int PER_ROW = 5;
    private static final int GOOD = 0xFF3FBF6F;

    private EffectsHud() {
    }

    static boolean draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft) || minecraft.player == null) {
            return true;
        }
        Screen screen = minecraft.gui.screen();
        if (screen != null && screen.showsActiveEffects()) {
            return true;
        }
        Collection<MobEffectInstance> active = minecraft.player.getActiveEffects();
        if (active.isEmpty()) {
            return true;
        }
        List<MobEffectInstance> good = new ArrayList<>();
        List<MobEffectInstance> other = new ArrayList<>();
        active.stream().sorted(Comparator.reverseOrder()).forEach(effect -> {
            if (!effect.showIcon() || !IClientMobEffectExtensions.of(effect).isVisibleInGui(effect)) {
                return;
            }
            (effect.getEffect().value().isBeneficial() ? good : other).add(effect);
        });
        if (good.isEmpty() && other.isEmpty()) {
            return true;
        }
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        int right = graphics.guiWidth() - StatusCard.MARGIN;
        int y = StatusCard.bottom() + 4;
        y = rows(graphics, paint, ui, good, right, y);
        rows(graphics, paint, ui, other, right, y);
        return true;
    }

    private static int rows(GuiGraphicsExtractor graphics, Paint paint, UiGraphics ui, List<MobEffectInstance> list,
                            int right, int y) {
        for (int index = 0; index < list.size(); index++) {
            int column = index % PER_ROW;
            int row = index / PER_ROW;
            chip(graphics, paint, ui, list.get(index), right - CHIP - column * CELL, y + row * ROW);
        }
        return list.isEmpty() ? y : y + ((list.size() - 1) / PER_ROW + 1) * ROW;
    }

    private static void chip(GuiGraphicsExtractor graphics, Paint paint, UiGraphics ui, MobEffectInstance effect,
                             int x, int y) {
        MobEffectCategory category = effect.getEffect().value().getCategory();
        int edge = effect.isAmbient() ? Ui.Theme.CYAN
                : category == MobEffectCategory.BENEFICIAL ? GOOD
                : category == MobEffectCategory.HARMFUL ? Ui.Theme.RED : Ui.Theme.MUTED;
        paint.shadow(x + 1.0f, y + 2.0f, CHIP - 2.0f, CHIP - 1.0f, 4.0f, Paint.alpha(Ui.Theme.SHADOW, 0.4f));
        paint.framed(x, y, CHIP, CHIP, 4.5f, Ui.Theme.BG2, Paint.mix(Ui.Theme.EDGE, edge, 0.75f), 1);
        float alpha = 1.0f;
        if (!effect.isInfiniteDuration() && effect.endsWithin(200)) {
            alpha = 0.45f + 0.55f * Anim.pulse(0.6d);
        }
        HudKit.sprite(graphics, Hud.getMobEffectSprite(effect.getEffect()), x + 2, y + 2, 14,
                Paint.alpha(0xFFFFFFFF, alpha));
        int amplifier = effect.getAmplifier();
        if (amplifier > 0) {
            String level = roman(amplifier + 1);
            float width = Math.max(6.0f, ui.width(Ui.BOLD, level, 3.4f) + 3.0f);
            paint.framed(x + CHIP - width + 1.5f, y - 1.5f, width, 5.6f, 2.8f, Ui.Theme.BG1, edge, 1);
            ui.text(Ui.BOLD, level, x + CHIP - width + 1.5f + (width - ui.width(Ui.BOLD, level, 3.4f)) / 2.0f,
                    y - 0.4f, 3.4f, Ui.Theme.TEXT);
        }
        if (!effect.isInfiniteDuration()) {
            String time = duration(effect.getDuration());
            ui.centered(Ui.BOLD, time, x + CHIP / 2.0f, y + CHIP + 1.6f, 3.8f,
                    effect.endsWithin(200) ? Ui.Theme.RED_LIGHT : Ui.Theme.TEXT2);
        }
    }

    private static String duration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        if (seconds >= 3600) {
            return seconds / 3600 + "h" + String.format(java.util.Locale.ROOT, "%02d", seconds % 3600 / 60);
        }
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    private static String roman(int value) {
        return switch (value) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }
}
