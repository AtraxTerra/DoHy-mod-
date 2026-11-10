package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Les amulettes portees ({@code AmuletHelper}).
 *
 * <p>Une amulette se fond a l'enclume dans un plastron en paladium, en paladium
 * vert, en endium ou en endium mixte : plastron a gauche, amulette a droite,
 * dix niveaux. Le plastron garde la liste de ses amulettes (une de chaque) et
 * les fait agir tant qu'on le porte. L'amulette de voyance demande le palier 1
 * de la branche Defense.
 */
@EventBusSubscriber
public final class Amulets {

    private static final int XP_COST = 10;

    private Amulets() {
    }

    /** Les plastrons qui portent des amulettes ({@code AmuletHelper.getAmulets}). */
    public static boolean isHost(ItemStack stack) {
        return stack.is(PalaEquipment.PALADIUM_CHESTPLATE.get()) || stack.is(PalaEquipment.GREEN_PALADIUM_CHESTPLATE.get())
                || stack.is(PalaEquipment.ENDIUM_CHESTPLATE.get()) || stack.is(PalaEquipment.MIXED_ENDIUM_CHESTPLATE.get());
    }

    public static boolean wears(Player player, AmuletType type) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        List<String> amulets = chest.get(PalaDefense.AMULETS.get());
        return amulets != null && amulets.contains(type.key()) && isHost(chest);
    }

    /** Le bonus d'XP de metier de l'amulette du chasseur, ajoute par {@code JobApi.grant}. */
    public static double xpBonus(ServerPlayer player, Job job) {
        return job == Job.HUNTER && wears(player, AmuletType.HUNTER) ? 0.25d : 0.0d;
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!isHost(left) || !(event.getRight().getItem() instanceof AmuletItem amulet)) {
            return;
        }
        List<String> current = left.getOrDefault(PalaDefense.AMULETS.get(), List.of());
        if (current.contains(amulet.type().key())) {
            return;
        }
        if (amulet.type() == AmuletType.VOYANCE && event.getPlayer() instanceof ServerPlayer player
                && !DefenseGate.allows(player, DefenseGate.key(amulet))) {
            return;
        }
        List<String> next = new ArrayList<>(current);
        next.add(amulet.type().key());
        ItemStack output = left.copy();
        output.set(PalaDefense.AMULETS.get(), List.copyOf(next));
        event.setOutput(output);
        event.setXpCost(XP_COST);
        event.setMaterialCost(1);
    }

    /** Toutes les deux secondes : le feu pour l'amulette de magma, la lueur des monstres pour la voyance. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) {
            return;
        }
        if (wears(player, AmuletType.MAGMA)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false, true));
        }
        if (wears(player, AmuletType.VOYANCE)) {
            ServerLevel level = (ServerLevel) player.level();
            AABB box = player.getBoundingBox().inflate(24.0d);
            for (LivingEntity monster : level.getEntitiesOfClass(LivingEntity.class, box,
                    entity -> entity instanceof Enemy && entity.isAlive())) {
                monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false));
            }
        }
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player player && event.getLookingEntity() instanceof Enemy
                && wears(player, AmuletType.DISCRETION)) {
            event.modifyVisibility(0.5d);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Enemy && event.getSource().getEntity() instanceof Player player
                && wears(player, AmuletType.COMBAT)) {
            event.setAmount(event.getAmount() * 1.15f);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<String> amulets = event.getItemStack().get(PalaDefense.AMULETS.get());
        if (amulets == null || amulets.isEmpty()) {
            return;
        }
        for (String key : amulets) {
            event.getToolTip().add(Component.translatable("item.palamod." + key + "_amulet")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
