package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaArmorMaterials;
import fr.atraxterra.dohy.palamod.equipment.PalaToolMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Les objets que donnent les lucky blocks ({@code luckyblock/init/Items}) : armures,
 * pomme de vitesse, brique de lait, faux billets et epee Couac.
 *
 * <p>Les valeurs viennent du jar de PalaMod ; les armures passent par les materiaux de
 * {@link PalaArmorMaterials}, comme les sets du mod.
 */
@EventBusSubscriber
public final class LuckyItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);

    /** {@code new PotionEffect(jump, 60, 2)} a chaque tic d'armure : Saut III tant qu'on les porte. */
    private static final int AIR_MIN_TICKS = 60;
    private static final int AIR_MIN_AMPLIFIER = 2;
    /** {@code setMaxDamage(300)} : le casque de speleologue. */
    private static final int CAVE_HELMET_DURABILITY = 300;

    // -- Le cochon : « Grouik Grouik » ----------------------------------------------

    public static final DeferredItem<Item> PIG_HELMET = armor("pig_helmet", PalaArmorMaterials.PIG, ArmorType.HELMET);
    public static final DeferredItem<Item> PIG_CHESTPLATE = armor("pig_chestplate", PalaArmorMaterials.PIG,
            ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> PIG_LEGGINGS = armor("pig_leggings", PalaArmorMaterials.PIG,
            ArmorType.LEGGINGS);
    public static final DeferredItem<Item> PIG_BOOTS = armor("pig_boots", PalaArmorMaterials.PIG, ArmorType.BOOTS);

    /** « C'est cheat un peu » : le casque du cochon, avec sa lampe, et 300 de durabilite. */
    public static final DeferredItem<Item> CAVE_HELMET = ITEMS.registerItem("cave_helmet", properties -> new Item(
            properties.humanoidArmor(PalaArmorMaterials.CAVE_HELMET, ArmorType.HELMET)
                    .durability(CAVE_HELMET_DURABILITY)));

    // -- L'homme invisible : une armure qui ne se voit pas --------------------------

    public static final DeferredItem<Item> INVISIBLE_HELMET = armor("invisible_helmet", PalaArmorMaterials.INVISIBLE,
            ArmorType.HELMET);
    public static final DeferredItem<Item> INVISIBLE_CHESTPLATE = armor("invisible_chestplate",
            PalaArmorMaterials.INVISIBLE, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> INVISIBLE_LEGGINGS = armor("invisible_leggings",
            PalaArmorMaterials.INVISIBLE, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> INVISIBLE_BOOTS = armor("invisible_boots", PalaArmorMaterials.INVISIBLE,
            ArmorType.BOOTS);

    // -- « Juste pour flex » : l'allure de l'endium, le fer dessous ------------------

    public static final DeferredItem<Item> FAKE_ENDIUM_HELMET = armor("fake_endium_helmet",
            PalaArmorMaterials.FAKE_ENDIUM, ArmorType.HELMET);
    public static final DeferredItem<Item> FAKE_ENDIUM_CHESTPLATE = armor("fake_endium_chestplate",
            PalaArmorMaterials.FAKE_ENDIUM, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> FAKE_ENDIUM_LEGGINGS = armor("fake_endium_leggings",
            PalaArmorMaterials.FAKE_ENDIUM, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> FAKE_ENDIUM_BOOTS = armor("fake_endium_boots",
            PalaArmorMaterials.FAKE_ENDIUM, ArmorType.BOOTS);

    // -- Cagoule et AirMin ------------------------------------------------------------

    /** La cagoule des lucky blocks ({@code ItemBalaclavaHelmet}) : un casque en fer, pour l'allure. */
    public static final DeferredItem<Item> BALACLAVA_HELMET = armor("balaclava_helmet", PalaArmorMaterials.BALACLAVA,
            ArmorType.HELMET);
    /** Les AirMin ({@code ItemAirMin}) : des bottes en fer, et Saut III tant qu'on les porte. */
    public static final DeferredItem<Item> AIR_MIN_BOOTS = armor("air_min_boots", PalaArmorMaterials.AIR_MIN,
            ArmorType.BOOTS);

    // -- A manger, a boire, a frapper -------------------------------------------------

    /**
     * La pomme de vitesse ({@code BaseItemApple(100, 0.6F, ...)}) : toujours mangeable, par
     * 16, elle rassasie et donne Vitesse VIII pendant 10 secondes.
     */
    public static final DeferredItem<Item> SPEED_APPLE = ITEMS.registerItem("speed_apple", properties -> new Item(
            properties.stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(100).saturationModifier(0.6f).alwaysEdible().build(),
                            Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                                    new MobEffectInstance(MobEffects.SPEED, 200, 7, true, true)))).build())
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    /**
     * La brique de lait ({@code ItemCalcium}) : un lait qui se boit dix fois, et se remplit
     * en tapant une vache.
     */
    public static final DeferredItem<CalciumItem> CALCIUM = ITEMS.registerItem("calcium", CalciumItem::new,
            properties -> properties.durability(CalciumItem.USES)
                    .component(DataComponents.CONSUMABLE, Consumable.builder()
                            .consumeSeconds(1.6f)
                            .animation(ItemUseAnimation.DRINK)
                            .sound(SoundEvents.GENERIC_DRINK)
                            .hasConsumeParticles(false)
                            .build()));

    /** Les faux billets ({@code ItemFakeMoney}) : « Ce sont des billets factices, prends garde a toi. » */
    public static final DeferredItem<Item> FAKE_MONEY = ITEMS.registerItem("fake_money", properties -> new Item(
            properties.component(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Ce sont des billets factices,").withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal("prends garde a toi.").withStyle(ChatFormatting.DARK_GRAY))))));

    /** L'epee Couac ({@code ItemCouacSword}) : une epee en paladium qui caquette a chaque coup. */
    public static final DeferredItem<CouacSwordItem> COUAC_SWORD = ITEMS.registerItem("couac_sword",
            properties -> new CouacSwordItem(properties.sword(PalaToolMaterials.PALADIUM, 3.0f, -2.4f)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            PIG_HELMET, PIG_CHESTPLATE, PIG_LEGGINGS, PIG_BOOTS, CAVE_HELMET,
            INVISIBLE_HELMET, INVISIBLE_CHESTPLATE, INVISIBLE_LEGGINGS, INVISIBLE_BOOTS,
            FAKE_ENDIUM_HELMET, FAKE_ENDIUM_CHESTPLATE, FAKE_ENDIUM_LEGGINGS, FAKE_ENDIUM_BOOTS,
            BALACLAVA_HELMET, AIR_MIN_BOOTS, SPEED_APPLE, CALCIUM, FAKE_MONEY, COUAC_SWORD);

    private LuckyItems() {
    }

    private static DeferredItem<Item> armor(String name, ArmorMaterial material, ArmorType type) {
        return ITEMS.registerItem(name, properties -> new Item(properties.humanoidArmor(material, type)));
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** Les AirMin aux pieds : Saut III, renouvele avant qu'il ne s'eteigne. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.getItemBySlot(EquipmentSlot.FEET).is(AIR_MIN_BOOTS.get())) {
            return;
        }
        MobEffectInstance jump = player.getEffect(MobEffects.JUMP_BOOST);
        if (jump == null || jump.getDuration() < 20) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, AIR_MIN_TICKS, AIR_MIN_AMPLIFIER));
        }
    }

    /** {@code onLeftClickEntity} : la brique de lait se remplit sur une vache, sans la frapper. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        ItemStack held = event.getEntity().getMainHandItem();
        if (!held.is(CALCIUM.get())) {
            return;
        }
        var type = event.getTarget().getType();
        if (type == EntityTypes.COW || type == EntityTypes.MOOSHROOM) {
            held.setDamageValue(0);
            event.setCanceled(true);
        }
    }
}
