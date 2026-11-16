package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Matrix3x2fStack;

/**
 * La hotbar : une carte aux coins ronds, neuf cases, la case choisie soulignee de rouge qui
 * glisse d'une case a l'autre, la main secondaire dans sa propre carte et la recharge de
 * l'attaque en jauge verticale, du cote oppose.
 *
 * <p>Les objets gardent le rebond du jeu quand on en ramasse un ; les cases sont a la
 * position exacte de celles du jeu, a deux pixels pres de hauteur.
 */
final class HotbarHud {

    private static final int SLOT = 18;
    private static final int PITCH = 20;
    private static final int PAD = 2;
    private static final float RADIUS = 6.0f;

    /** La case choisie, qui glisse ; {@code -1} tant qu'on ne l'a pas encore posee. */
    private static final Anim.Value SELECTION = new Anim.Value(-1.0f, 22.0f);
    private static int seed;

    private HotbarHud() {
    }

    static boolean draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft)) {
            return true;
        }
        if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return false;
        }
        Player player = HudKit.cameraPlayer(minecraft);
        if (player == null) {
            return true;
        }
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        int left = HudKit.left(graphics);
        int top = HudKit.hotbarTop(graphics);

        HudKit.card(paint, left, top, HudKit.WIDTH, HudKit.HOTBAR_HEIGHT, RADIUS);
        for (int slot = 0; slot < 9; slot++) {
            paint.round(left + PAD + slot * PITCH, top + PAD, SLOT, SLOT, 4.0f, 0xFF1F2125);
        }

        int selected = player.getInventory().getSelectedSlot();
        if (SELECTION.get() < 0.0f) {
            SELECTION.set(selected);
        }
        float at = SELECTION.toward(selected);
        float sx = left + PAD + at * PITCH;
        float sy = top + PAD;
        paint.glow(sx + SLOT / 2.0f, sy + SLOT / 2.0f, 15.0f, Paint.alpha(Ui.Theme.RED, 0.22f));
        paint.framed(sx - 1.0f, sy - 1.0f, SLOT + 2.0f, SLOT + 2.0f, 5.0f, Ui.Theme.CARD_HOVER, Ui.Theme.RED, 1);
        paint.round(sx + 5.0f, top + HudKit.HOTBAR_HEIGHT - 1.6f, SLOT - 10.0f, 1.2f, 0.6f, Ui.Theme.RED_LIGHT);

        ItemStack offhand = player.getOffhandItem();
        HumanoidArm offSide = player.getMainArm().getOpposite();
        int offX = offSide == HumanoidArm.LEFT ? left - 4 - HudKit.HOTBAR_HEIGHT : left + HudKit.WIDTH + 4;
        if (!offhand.isEmpty()) {
            HudKit.card(paint, offX, top, HudKit.HOTBAR_HEIGHT, HudKit.HOTBAR_HEIGHT, RADIUS);
            paint.round(offX + PAD, top + PAD, SLOT, SLOT, 4.0f, 0xFF1F2125);
        }

        // La recharge de l'attaque, du cote oppose a la main secondaire, comme le jeu.
        if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && minecraft.player != null) {
            float strength = minecraft.player.getAttackStrengthScale(0.0f);
            if (strength < 1.0f) {
                int gx = offSide == HumanoidArm.RIGHT ? left - 4 - 8 : left + HudKit.WIDTH + 4;
                HudKit.card(paint, gx, top, 8, HudKit.HOTBAR_HEIGHT, 4.0f);
                float fill = (HudKit.HOTBAR_HEIGHT - 6.0f) * HudKit.clamp(strength);
                paint.round(gx + 2.0f, top + 3.0f, 4.0f, HudKit.HOTBAR_HEIGHT - 6.0f, 2.0f, HudKit.TRACK);
                paint.roundGradient(gx + 2.0f, top + 3.0f + (HudKit.HOTBAR_HEIGHT - 6.0f) - fill, 4.0f,
                        Math.max(4.0f, fill), 2.0f, 0xFFFFFFFF, 0xFFB8BCC6);
            }
        }

        // Les objets se dessinent apres les fonds de la strate : ils passent au-dessus des cartes.
        seed = 1;
        for (int slot = 0; slot < 9; slot++) {
            item(graphics, minecraft, delta, player, player.getInventory().getItem(slot),
                    left + PAD + slot * PITCH + 1, top + PAD + 1);
        }
        if (!offhand.isEmpty()) {
            item(graphics, minecraft, delta, player, offhand, offX + PAD + 1, top + PAD + 1);
        }
        return true;
    }

    /** Un objet de la hotbar, avec le rebond du jeu quand on vient d'en ramasser. */
    private static void item(GuiGraphicsExtractor graphics, Minecraft minecraft, DeltaTracker delta, Player player,
                             ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            seed++;
            return;
        }
        float pop = stack.getPopTime() - delta.getGameTimeDeltaPartialTick(false);
        Matrix3x2fStack pose = graphics.pose();
        if (pop > 0.0f) {
            float scale = 1.0f + pop / 5.0f;
            pose.pushMatrix();
            pose.translate(x + 8, y + 12);
            pose.scale(1.0f / scale, (scale + 1.0f) / 2.0f);
            pose.translate(-(x + 8), -(y + 12));
        }
        graphics.item(player, stack, x, y, seed++);
        if (pop > 0.0f) {
            pose.popMatrix();
        }
        graphics.itemDecorations(minecraft.font, stack, x, y);
    }
}
