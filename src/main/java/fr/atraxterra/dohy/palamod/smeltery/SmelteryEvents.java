package fr.atraxterra.dohy.palamod.smeltery;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Les effets des outils du grinder.
 *
 * <ul>
 *   <li><b>fusion</b> (marteau en paladium) : chaque objet lache par un bloc
 *       sort cuit, s'il a une recette de four ; sinon il sort tel quel ;</li>
 *   <li><b>flamme</b> : la cible brule 10 secondes. <b>Ecart</b> : la fastsword
 *       de PalaMod l'enflammait 200 secondes ({@code sipush 200}, contre
 *       {@code bipush 10} pour la broadsword) — des ticks pris pour des
 *       secondes ; les deux brulent ici 10 secondes ;</li>
 *   <li><b>recul</b> : un demi-bloc de recul par niveau, en plus du coup ;</li>
 *   <li><b>fastsword</b> : Celerite IV tenue en main, et une invulnerabilite
 *       de 18 ticks au lieu de 20 pour la cible.</li>
 * </ul>
 * La fortune passe par l'enchantement Fortune que le grinder pose sur le
 * marteau : sans fusion, c'est la fortune vanilla, comme dans PalaMod.
 */
@EventBusSubscriber
public final class SmelteryEvents {

    private static final float FLAME_SECONDS = 10.0f;
    private static final double KNOCKBACK_PER_LEVEL = 0.5d;
    /** {@code field_70172_ad = 18} : l'invulnerabilite laissee a la cible. */
    private static final int FASTSWORD_INVULNERABILITY = 18;
    /** {@code new PotionEffect(haste, 1, 3)} : Celerite IV. */
    private static final int HASTE_AMPLIFIER = 3;

    private SmelteryEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        ItemStack tool = event.getTool();
        if (!(tool.getItem() instanceof HammerItem hammer) || !hammer.modifiable()
                || PalaSmeltery.modifiers(tool).level(GrinderModifier.SMELT) <= 0
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Les succes : un minerai « duplique » par un marteau fortune et fusion ({@code HammerDuplicateAchievement}).
        if (event.getBreaker() instanceof ServerPlayer player && PalaSmeltery.modifiers(tool).level(GrinderModifier.FORTUNE) > 0
                && event.getState().is(fr.atraxterra.dohy.palamod.achievement.Achievements.ORES)) {
            fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "hammer:duplicate", 1);
            fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "hammer:duplicate:"
                    + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()), 1);
        }
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
            ItemStack smelted = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                    .map(holder -> holder.value().assemble(input))
                    .orElse(ItemStack.EMPTY);
            if (!smelted.isEmpty()) {
                drop.setItem(smelted.copyWithCount(Math.min(smelted.getMaxStackSize(),
                        smelted.getCount() * stack.getCount())));
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || event.getInflictedDamage() <= 0.0f) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof GrinderSwordItem sword)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!sword.broad()) {
            target.invulnerableTime = Math.min(target.invulnerableTime, FASTSWORD_INVULNERABILITY);
        }
        if (!sword.modifiable()) {
            return;
        }
        ToolModifiers modifiers = PalaSmeltery.modifiers(weapon);
        if (modifiers.level(GrinderModifier.FLAME) > 0) {
            target.igniteForSeconds(FLAME_SECONDS);
        }
        int knockback = modifiers.level(GrinderModifier.KNOCKBACK);
        if (knockback > 0) {
            double yaw = Math.toRadians(attacker.getYRot());
            target.knockback(KNOCKBACK_PER_LEVEL * knockback, Math.sin(yaw), -Math.cos(yaw),
                    event.getSource(), event.getInflictedDamage());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.getMainHandItem().getItem() instanceof GrinderSwordItem sword) || sword.broad()) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.HASTE);
        if (current == null || (current.getAmplifier() <= HASTE_AMPLIFIER && current.getDuration() < 5)) {
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 10, HASTE_AMPLIFIER, true, false, false));
        }
    }
}
