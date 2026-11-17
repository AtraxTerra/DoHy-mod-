package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.arrakis.DoHyArrakis;
import fr.atraxterra.dohy.arrakis.Hydration;
import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Les barres d'etat, a la place des rangees d'icones du jeu : la sante et l'armure a
 * gauche, la faim (ou la monture) et l'air a droite, chacune avec son icone du jeu et sa
 * valeur en chiffres.
 *
 * <p>La sante descend en deux temps : la barre suit tout de suite, une trainee claire garde
 * un instant la vie perdue puis la rattrape — le coup se lit d'un coup d'oeil. Elle prend la
 * couleur du poison, du wither ou du gel comme les coeurs, l'absorption s'ajoute en or au
 * bout, la regeneration la fait miroiter et elle pulse en rouge sous un quart de vie. La
 * faim se remplit depuis la droite, comme ses cuisses, avec la saturation en filet clair.
 *
 * <p>Sur Arrakis s'ajoute l'eau, au-dessus de la faim : une barre bleue qui se vide depuis
 * la gauche, et qui cerne d'orange quand on a soif.
 *
 * <p>Chaque barre est posee a la hauteur que les compteurs de NeoForge lui donnent et les
 * fait monter d'une rangee, comme les couches du jeu.
 */
final class StatusHud {

    private static final float TEXT = 4.6f;

    private static final Anim.Value HEALTH = new Anim.Value(-1.0f, 20.0f);
    private static final Anim.Value TRAIL = new Anim.Value(-1.0f, 4.0f);
    private static double trailHold;
    private static float lastHealth = -1.0f;
    private static int lastPlayer;
    private static int lastBubble = -1;
    private static final Identifier WATER_SPRITE = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "hud/water");
    private static final Anim.Value WATER = new Anim.Value(-1.0f, 6.0f);

    private StatusHud() {
    }

    // -- Sante ------------------------------------------------------------------------

    static boolean health(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = visiblePlayer(minecraft);
        if (player == null) {
            return true;
        }
        Hud hud = minecraft.gui.hud;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);

        float health = Math.max(0.0f, player.getHealth());
        float max = Math.max(1.0f, player.getMaxHealth());
        float absorb = Math.max(0.0f, player.getAbsorptionAmount());
        float capacity = max + absorb;
        float fill = HudKit.clamp(health / capacity);

        if (lastPlayer != player.getId() || HEALTH.get() < 0.0f) {
            lastPlayer = player.getId();
            HEALTH.set(fill);
            TRAIL.set(fill);
            lastHealth = health;
        }
        if (health < lastHealth - 0.01f) {
            // Le coup : la trainee garde la vie d'avant un instant.
            trailHold = Anim.now() + 0.45d;
            TRAIL.set(Math.max(TRAIL.get(), HEALTH.get()));
        }
        lastHealth = health;
        float shown = HEALTH.toward(fill);
        // Pendant l'arret, la trainee vise sa propre valeur : son horloge avance sans qu'elle bouge.
        float trail = TRAIL.toward(Anim.now() < trailHold ? Math.max(TRAIL.get(), shown) : shown);
        if (trail < shown) {
            TRAIL.set(shown);
            trail = shown;
        }

        int[] colors = healthColors(player);
        int x = HudKit.left(graphics);
        int y = graphics.guiHeight() - hud.leftHeight;
        boolean hardcore = minecraft.level != null && minecraft.level.getLevelData().isHardcore();
        HudKit.sprite(graphics, HudKit.vanilla(heartSprite(player, hardcore)), x, y, 9);

        float barX = x + 10.0f;
        float barY = y + 1.0f;
        float width = HudKit.COLUMN - 10.0f;
        HudKit.bar(paint, barX, barY, width, HudKit.BAR, 0.0f, colors[0], colors[1], false);
        if (trail > shown) {
            HudKit.segment(paint, barX, barY, width, HudKit.BAR, shown, trail, 0xFFFFE6E3, 0xFFE8A9A3, false);
        }
        HudKit.segment(paint, barX, barY, width, HudKit.BAR, 0.0f, shown, colors[0], colors[1], false);
        if (absorb > 0.0f) {
            HudKit.segment(paint, barX, barY, width, HudKit.BAR, shown, shown + absorb / capacity,
                    HudKit.ABSORB_TOP, HudKit.ABSORB_BOTTOM, false);
        }
        if (player.hasEffect(MobEffects.REGENERATION)) {
            shimmer(paint, barX, barY, width, shown);
        }
        if (health / max <= 0.25f) {
            paint.outline(barX - 1.0f, barY - 1.0f, width + 2.0f, HudKit.BAR + 2.0f, (HudKit.BAR + 2.0f) / 2.0f,
                    Paint.alpha(Ui.Theme.RED_LIGHT, 0.25f + 0.65f * Anim.pulse(0.9d)), 1);
        }

        String value = Integer.toString(Mth.ceil(health));
        float right = barX + width - 3.0f;
        if (absorb > 0.0f) {
            String extra = "+" + Mth.ceil(absorb);
            ui.textShaded(Ui.BOLD, extra, right - ui.width(Ui.BOLD, extra, TEXT), barY + 1.25f, TEXT, 0xFFFFE9A6,
                    0x99000000, 0.5f);
            right -= ui.width(Ui.BOLD, extra, TEXT) + 1.5f;
        }
        ui.textShaded(Ui.BOLD, value, right - ui.width(Ui.BOLD, value, TEXT), barY + 1.25f, TEXT, Ui.Theme.TEXT,
                0x99000000, 0.5f);
        hud.leftHeight += HudKit.ROW;
        return true;
    }

    private static int[] healthColors(Player player) {
        if (player.hasEffect(MobEffects.POISON)) {
            return new int[] {HudKit.POISON_TOP, HudKit.POISON_BOTTOM};
        }
        if (player.hasEffect(MobEffects.WITHER)) {
            return new int[] {HudKit.WITHER_TOP, HudKit.WITHER_BOTTOM};
        }
        if (player.isFullyFrozen()) {
            return new int[] {HudKit.FROZEN_TOP, HudKit.FROZEN_BOTTOM};
        }
        return new int[] {HudKit.HEALTH_TOP, HudKit.HEALTH_BOTTOM};
    }

    private static String heartSprite(Player player, boolean hardcore) {
        String kind = player.hasEffect(MobEffects.POISON) ? "poisoned_"
                : player.hasEffect(MobEffects.WITHER) ? "withered_"
                : player.isFullyFrozen() ? "frozen_" : "";
        return "hud/heart/" + kind + (hardcore ? "hardcore_" : "") + "full";
    }

    /** Un reflet qui parcourt la partie pleine de la barre : la regeneration. */
    private static void shimmer(Paint paint, float x, float y, float width, float fill) {
        float inner = width - 2.0f;
        float end = x + 1.0f + inner * fill;
        double t = (Anim.now() % 1.6d) / 1.6d;
        float band = 9.0f;
        float start = x + 1.0f + (float) ((inner + band) * t) - band;
        float a = Math.max(x + 1.0f, start);
        float b = Math.min(end, start + band);
        if (b - a > 0.5f) {
            paint.round(a, y + 1.0f, b - a, HudKit.BAR - 2.0f, (HudKit.BAR - 2.0f) / 2.0f, 0x40FFFFFF);
        }
    }

    // -- Armure -----------------------------------------------------------------------

    static boolean armor(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = visiblePlayer(minecraft);
        if (player == null) {
            return true;
        }
        int armor = player.getArmorValue();
        if (armor <= 0) {
            return true;
        }
        Hud hud = minecraft.gui.hud;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        int x = HudKit.left(graphics);
        int y = graphics.guiHeight() - hud.leftHeight;
        HudKit.sprite(graphics, HudKit.vanilla("hud/armor_full"), x, y, 9);
        float barX = x + 10.0f;
        float width = HudKit.COLUMN - 10.0f;
        HudKit.bar(paint, barX, y + 1.0f, width, HudKit.BAR, armor / 20.0f, HudKit.ARMOR_TOP, HudKit.ARMOR_BOTTOM,
                false);
        String value = Integer.toString(armor);
        ui.textShaded(Ui.BOLD, value, barX + width - 3.0f - ui.width(Ui.BOLD, value, TEXT), y + 2.25f, TEXT,
                Ui.Theme.TEXT, 0x99000000, 0.5f);
        hud.leftHeight += HudKit.ROW;
        return true;
    }

    // -- Faim -------------------------------------------------------------------------

    static boolean food(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = visiblePlayer(minecraft);
        if (player == null || mount(player) != null) {
            return true;
        }
        Hud hud = minecraft.gui.hud;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        FoodData data = player.getFoodData();
        int food = data.getFoodLevel();
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        int x = HudKit.left(graphics) + HudKit.WIDTH - HudKit.COLUMN;
        int y = graphics.guiHeight() - hud.rightHeight;
        float width = HudKit.COLUMN - 10.0f;
        float barY = y + 1.0f;
        HudKit.bar(paint, x, barY, width, HudKit.BAR, food / 20.0f, hunger ? HudKit.HUNGER_TOP : HudKit.FOOD_TOP,
                hunger ? HudKit.HUNGER_BOTTOM : HudKit.FOOD_BOTTOM, true);
        float saturation = HudKit.clamp(data.getSaturationLevel() / 20.0f);
        if (saturation > 0.0f) {
            float inner = width - 2.0f;
            float length = inner * saturation;
            paint.round(x + 1.0f + inner - length, barY + 1.0f, length, 1.4f, 0.7f, Paint.alpha(HudKit.SATURATION,
                    0.8f));
        }
        if (food <= 6) {
            paint.outline(x - 1.0f, barY - 1.0f, width + 2.0f, HudKit.BAR + 2.0f, (HudKit.BAR + 2.0f) / 2.0f,
                    Paint.alpha(0xFFFFA43A, 0.2f + 0.55f * Anim.pulse(1.2d)), 1);
        }
        HudKit.sprite(graphics, HudKit.vanilla(hunger ? "hud/food_full_hunger" : "hud/food_full"),
                x + HudKit.COLUMN - 9, y, 9);
        ui.textShaded(Ui.BOLD, Integer.toString(food), x + 3.0f, barY + 1.25f, TEXT, Ui.Theme.TEXT, 0x99000000,
                0.5f);
        hud.rightHeight += HudKit.ROW;
        return true;
    }

    // -- Monture ----------------------------------------------------------------------

    static boolean mount(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = visiblePlayer(minecraft);
        LivingEntity mount = player == null ? null : mount(player);
        if (mount == null) {
            return true;
        }
        Hud hud = minecraft.gui.hud;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        float health = Math.max(0.0f, mount.getHealth());
        float max = Math.max(1.0f, mount.getMaxHealth());
        int x = HudKit.left(graphics) + HudKit.WIDTH - HudKit.COLUMN;
        int y = graphics.guiHeight() - hud.rightHeight;
        float width = HudKit.COLUMN - 10.0f;
        HudKit.bar(paint, x, y + 1.0f, width, HudKit.BAR, health / max, HudKit.MOUNT_TOP, HudKit.MOUNT_BOTTOM, true);
        HudKit.sprite(graphics, HudKit.vanilla("hud/heart/vehicle_full"), x + HudKit.COLUMN - 9, y, 9);
        ui.textShaded(Ui.BOLD, Integer.toString(Mth.ceil(health)), x + 3.0f, y + 2.25f, TEXT, Ui.Theme.TEXT,
                0x99000000, 0.5f);
        hud.rightHeight += HudKit.ROW;
        return true;
    }

    /** La monture dont le jeu montre la vie (un cheval, pas un bateau ni un cochon sans selle). */
    static LivingEntity mount(Player player) {
        return player.getVehicle() instanceof LivingEntity living && living.showVehicleHealth()
                && living.getMaxHealth() > 0.0f ? living : null;
    }

    // -- Eau, sur Arrakis ---------------------------------------------------------------

    static boolean water(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = visiblePlayer(minecraft);
        if (player == null || minecraft.level == null || minecraft.level.dimension() != DoHyArrakis.DIMENSION) {
            return true;
        }
        Hud hud = minecraft.gui.hud;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        float water = Math.max(0.0f, player.getData(DoHyArrakis.HYDRATION));
        float fill = HudKit.clamp(water / Hydration.MAX);
        if (WATER.get() < 0.0f) {
            WATER.set(fill);
        }
        float shown = WATER.toward(fill);
        int x = HudKit.left(graphics) + HudKit.WIDTH - HudKit.COLUMN;
        int y = graphics.guiHeight() - hud.rightHeight;
        float width = HudKit.COLUMN - 10.0f;
        float barY = y + 1.0f;
        HudKit.bar(paint, x, barY, width, HudKit.BAR, shown, HudKit.WATER_TOP, HudKit.WATER_BOTTOM, true);
        if (water <= Hydration.THIRSTY) {
            paint.outline(x - 1.0f, barY - 1.0f, width + 2.0f, HudKit.BAR + 2.0f, (HudKit.BAR + 2.0f) / 2.0f,
                    Paint.alpha(0xFFFFA43A, 0.25f + 0.6f * Anim.pulse(water <= Hydration.PARCHED ? 0.6d : 1.1d)), 1);
        }
        HudKit.sprite(graphics, WATER_SPRITE, x + HudKit.COLUMN - 9, y, 9);
        ui.textShaded(Ui.BOLD, Integer.toString(Mth.ceil(water)), x + 3.0f, barY + 1.25f, TEXT, Ui.Theme.TEXT,
                0x99000000, 0.5f);
        hud.rightHeight += HudKit.ROW;
        return true;
    }

    // -- Air --------------------------------------------------------------------------

    static boolean air(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = visiblePlayer(minecraft);
        if (player == null) {
            return true;
        }
        int max = Math.max(1, player.getMaxAirSupply());
        int air = Mth.clamp(player.getAirSupply(), 0, max);
        boolean underwater = player.isUnderWater();
        if (!underwater && air >= max) {
            lastBubble = -1;
            return true;
        }
        popSound(player, air, max, underwater);
        Hud hud = minecraft.gui.hud;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        int x = HudKit.left(graphics) + HudKit.WIDTH - HudKit.COLUMN;
        int y = graphics.guiHeight() - hud.rightHeight;
        float width = HudKit.COLUMN - 10.0f;
        HudKit.bar(paint, x, y + 1.0f, width, HudKit.BAR, (float) air / max, HudKit.AIR_TOP, HudKit.AIR_BOTTOM,
                true);
        if (air <= max / 5) {
            paint.outline(x - 1.0f, y, width + 2.0f, HudKit.BAR + 2.0f, (HudKit.BAR + 2.0f) / 2.0f,
                    Paint.alpha(HudKit.AIR_TOP, 0.25f + 0.65f * Anim.pulse(0.7d)), 1);
        }
        HudKit.sprite(graphics, HudKit.vanilla(air > 0 ? "hud/air" : "hud/air_empty"), x + HudKit.COLUMN - 9, y, 9);
        ui.textShaded(Ui.BOLD, Mth.ceil(air / 20.0f) + " s", x + 3.0f, y + 2.25f, TEXT, Ui.Theme.TEXT, 0x99000000,
                0.5f);
        hud.rightHeight += HudKit.ROW;
        return true;
    }

    /**
     * Le petit « pop » de chaque bulle qui creve, que le jeu joue en dessinant ses bulles :
     * sans lui, on perdrait l'avertissement sonore de la noyade.
     */
    private static void popSound(Player player, int air, int max, boolean underwater) {
        int bubble = Mth.clamp(Mth.ceil((air - 2) * 10.0f / max), 0, 10);
        if (!underwater || bubble >= 10) {
            lastBubble = bubble;
            return;
        }
        if (lastBubble >= 0 && bubble < lastBubble) {
            int popped = 10 - bubble;
            float volume = 0.5f + 0.1f * Math.max(0, popped - 3 + 1);
            float pitch = 1.0f + 0.1f * Math.max(0, popped - 5 + 1);
            player.playSound(SoundEvents.BUBBLE_POP, volume, pitch);
        }
        lastBubble = bubble;
    }

    /** Le joueur dont on montre l'etat, si le HUD est visible et qu'il peut prendre des degats. */
    private static Player visiblePlayer(Minecraft minecraft) {
        if (HudKit.hidden(minecraft) || !HudKit.survival(minecraft)) {
            return null;
        }
        return HudKit.cameraPlayer(minecraft);
    }
}
