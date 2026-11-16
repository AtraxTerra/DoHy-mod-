package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;

/**
 * L'experience : une barre fine juste au-dessus de la hotbar, et le niveau en medaillon au
 * milieu de la rangee de la sante et de la faim. A cheval, la barre devient celle du saut.
 *
 * <p>Le jeu choisit la barre du moment (experience, saut, reperes des autres joueurs) ; on
 * suit son choix. La barre des reperes reste la sienne : elle a sa place exacte au-dessus de
 * la hotbar et on n'a rien de mieux a montrer a la place.
 */
final class ExperienceHud {

    private static final float HEIGHT = 2.5f;

    private ExperienceHud() {
    }

    /** Le fond de la barre du moment : le notre est dessine avec la barre. */
    static boolean background(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft)) {
            return true;
        }
        return !"LOCATOR".equals(HudAccess.contextual(minecraft.gui.hud));
    }

    static boolean bar(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft)) {
            return true;
        }
        String state = HudAccess.contextual(minecraft.gui.hud);
        if ("LOCATOR".equals(state)) {
            return false;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            return true;
        }
        if (state == null) {
            state = player.jumpableVehicle() != null ? "JUMPABLE_VEHICLE"
                    : minecraft.gameMode.hasExperience() ? "EXPERIENCE" : "EMPTY";
        }
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        float x = HudKit.left(graphics);
        float y = HudKit.hotbarTop(graphics) - HEIGHT - 1.0f;
        if ("JUMPABLE_VEHICLE".equals(state)) {
            PlayerRideableJumping vehicle = player.jumpableVehicle();
            boolean cooling = vehicle != null && vehicle.getJumpCooldown() > 0;
            float scale = cooling ? 0.0f : HudKit.clamp(player.getJumpRidingScale());
            paint.round(x, y - 0.5f, HudKit.WIDTH, HEIGHT + 1.0f, (HEIGHT + 1.0f) / 2.0f, 0xE0101113);
            if (scale > 0.0f) {
                paint.roundGradient(x, y - 0.5f, Math.max(HEIGHT + 1.0f, HudKit.WIDTH * scale), HEIGHT + 1.0f,
                        (HEIGHT + 1.0f) / 2.0f, HudKit.JUMP_TOP, HudKit.JUMP_BOTTOM);
            }
            return true;
        }
        if ("EXPERIENCE".equals(state) && minecraft.gameMode.hasExperience()) {
            float progress = HudKit.clamp(player.experienceProgress);
            paint.round(x, y, HudKit.WIDTH, HEIGHT, HEIGHT / 2.0f, 0xE0101113);
            if (progress > 0.0f) {
                float length = Math.max(HEIGHT, HudKit.WIDTH * progress);
                paint.roundGradient(x, y, length, HEIGHT, HEIGHT / 2.0f, HudKit.XP_TOP, HudKit.XP_BOTTOM);
                paint.glow(x + length - 1.0f, y + HEIGHT / 2.0f, 5.0f, Paint.alpha(HudKit.XP_TOP, 0.35f));
            }
        }
        return true;
    }

    /** Le niveau, en medaillon entre la barre de sante et celle de la faim. */
    static boolean level(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft) || minecraft.gameMode == null || !minecraft.gameMode.hasExperience()) {
            return true;
        }
        Player player = HudKit.cameraPlayer(minecraft);
        if (player == null || player.experienceLevel <= 0) {
            return true;
        }
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        String level = Integer.toString(player.experienceLevel);
        float size = level.length() > 3 ? 4.2f : 5.0f;
        float width = Math.max(15.0f, ui.width(Ui.BOLD, level, size) + 7.0f);
        float cx = graphics.guiWidth() / 2.0f;
        float y = graphics.guiHeight() - 39.0f;
        paint.glow(cx, y + 4.5f, 10.0f, Paint.alpha(HudKit.XP_BOTTOM, 0.25f));
        paint.framed(cx - width / 2.0f, y, width, 9.0f, 4.5f, Ui.Theme.BG2, HudKit.XP_BOTTOM, 1);
        ui.textShaded(Ui.BOLD, level, cx - ui.width(Ui.BOLD, level, size) / 2.0f, y + (9.0f - size) / 2.0f, size,
                0xFFC6F86A, 0x99000000, 0.5f);
        return true;
    }
}
