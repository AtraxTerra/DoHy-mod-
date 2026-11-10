package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Le liant du pillage : la portee de l'arc, l'echange de la fleche de Switch,
 * les modificateurs a l'enclume, la discretion de la cagoule et les bulles.
 */
@EventBusSubscriber
public final class PillageEvents {

    /** Les modificateurs se posent pour 5 niveaux, comme les amulettes. */
    static final int ANVIL_COST = 5;
    /** La cagoule : un monstre te repere a 60 % de sa distance habituelle. */
    static final double HOOD_VISIBILITY = 0.6d;

    /** Les bulles d'aide, par objet : une cle de traduction {@code tooltip.palamod.*}. */
    private static final Map<Supplier<? extends Item>, String> TOOLTIPS = Map.ofEntries(
            Map.entry(PalaPillage.DYNAMITE, "dynamite"),
            Map.entry(PalaPillage.NINJA_DYNAMITE, "ninja_dynamite"),
            Map.entry(PalaPillage.BIG_DYNAMITE, "big_dynamite"),
            Map.entry(PalaPillage.UNCLAIM_FINDER, "unclaim_finder"),
            Map.entry(PalaPillage.UNCLAIM_FINDER_ORANGE, "unclaim_finder_orange"),
            Map.entry(PalaPillage.UNCLAIM_FINDER_RED, "unclaim_finder_red"),
            Map.entry(PalaPillage.UNCLAIM_FINDER_BLUE, "unclaim_finder_blue"),
            Map.entry(PalaPillage.SPAWNER_FINDER, "spawner_finder"),
            Map.entry(PalaPillage.CHEST_EXPLORER, "chest_explorer"),
            Map.entry(PalaPillage.EXTRAPOLATED_BUCKET, "extrapolated_bucket"),
            Map.entry(PalaPillage.SWITCH_ARROW, "switch_arrow"),
            Map.entry(PalaPillage.RANGE_BOW_MODIFIER, "range_bow_modifier"),
            Map.entry(PalaPillage.SPEED_BOW_MODIFIER, "speed_bow_modifier"),
            Map.entry(PalaPillage.EMPTY_BOW_MODIFIER, "empty_bow_modifier"),
            Map.entry(PalaPillage.HOOD_HELMET, "hood_helmet"));

    private PillageEvents() {
    }

    /** Le modificateur Range : la vitesse n'est connue qu'une fois la fleche tiree. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof AbstractArrow arrow)
                || !arrow.entityTags().contains(BowModifier.RANGE.tag())) {
            return;
        }
        arrow.removeTag(BowModifier.RANGE.tag());
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(PaladiumBowItem.RANGE_VELOCITY));
    }

    /** La fleche de Switch, tiree a l'arc en paladium : tireur et cible echangent leur place. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !arrow.entityTags().contains(SwitchArrowItem.TAG)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)
                || !(arrow.getOwner() instanceof ServerPlayer player) || target == player) {
            return;
        }
        arrow.removeTag(SwitchArrowItem.TAG);
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || !weapon.is(PalaPillage.PALADIUM_BOW.get()) || target.level() != player.level()
                || !DefenseGate.check(player, DefenseGate.key(PalaPillage.SWITCH_ARROW.get()))) {
            return;
        }
        Vec3 from = player.position();
        Vec3 to = target.position();
        target.teleportTo(from.x, from.y, from.z);
        player.teleportTo(to.x, to.y, to.z);
        player.level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0f, 1.2f);
        player.level().playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0f, 1.2f);
        if (target instanceof Enemy) {
            FactionApi.stat(player, "use:switch_arrow", 1);
        }
    }

    /** Un modificateur sur un arc en paladium : deux au plus, un de chaque. */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        BowModifier modifier = BowModifier.of(event.getRight());
        if (!left.is(PalaPillage.PALADIUM_BOW.get()) || modifier == null) {
            return;
        }
        List<String> current = PaladiumBowItem.modifiers(left);
        if (current.contains(modifier.key()) || current.size() >= PaladiumBowItem.MAX_MODIFIERS) {
            return;
        }
        List<String> next = new ArrayList<>(current);
        next.add(modifier.key());
        ItemStack output = left.copy();
        output.set(PalaPillage.BOW_MODIFIERS.get(), List.copyOf(next));
        event.setOutput(output);
        event.setXpCost(ANVIL_COST);
        event.setMaterialCost(1);
    }

    /** Le compteur de l'arbre : un arc qui sort de l'enclume avec ses deux modificateurs. */
    @SubscribeEvent
    public static void onAnvilTaken(AnvilCraftEvent.Post event) {
        ItemStack output = event.getOutput();
        if (event.getEntity() instanceof ServerPlayer player && output.is(PalaPillage.PALADIUM_BOW.get())
                && PaladiumBowItem.has(output, BowModifier.RANGE) && PaladiumBowItem.has(output, BowModifier.SPEED)
                && !(PaladiumBowItem.has(event.getLeft(), BowModifier.RANGE)
                && PaladiumBowItem.has(event.getLeft(), BowModifier.SPEED))) {
            FactionApi.stat(player, "bow:modifiers", 1);
        }
    }

    /** La cagoule, une fois debloquee : les monstres te voient de moins loin. */
    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        Entity looking = event.getLookingEntity();
        if (event.getEntity() instanceof ServerPlayer player && looking instanceof Enemy
                && player.getItemBySlot(EquipmentSlot.HEAD).is(PalaPillage.HOOD_HELMET.get())
                && DefenseGate.allows(player, DefenseGate.key(PalaPillage.HOOD_HELMET.get()))) {
            event.modifyVisibility(HOOD_VISIBILITY);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        for (Map.Entry<Supplier<? extends Item>, String> entry : TOOLTIPS.entrySet()) {
            if (stack.is(entry.getKey().get())) {
                event.getToolTip().add(Component.translatable("tooltip.palamod." + entry.getValue())
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
        }
        if (stack.is(PalaPillage.PALADIUM_BOW.get())) {
            List<String> modifiers = PaladiumBowItem.modifiers(stack);
            event.getToolTip().add(Component.translatable("tooltip.palamod.paladium_bow.modifiers",
                    modifiers.size(), PaladiumBowItem.MAX_MODIFIERS).withStyle(ChatFormatting.GRAY));
            for (String key : modifiers) {
                event.getToolTip().add(Component.translatable("item.palamod." + key + "_bow_modifier")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
