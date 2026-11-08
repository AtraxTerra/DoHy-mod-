package fr.atraxterra.dohy.palamod.smeltery;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaToolMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Consumer;

/**
 * Le module smeltery de PalaMod autour du grinder ({@code PSRegister_Items}) :
 * patterns, tetes d'outil, modificateurs, marteaux, broadswords et fastswords.
 *
 * <p>Les modificateurs de fortune et de vitesse existaient deja ({@code
 * PalaItems}, recompenses de metier) : ils deviennent applicables.
 */
public final class PalaSmeltery {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolModifiers>> MODIFIERS =
            COMPONENTS.registerComponentType("grinder_modifiers", builder -> builder
                    .persistent(ToolModifiers.CODEC).networkSynchronized(ToolModifiers.STREAM_CODEC));

    // -- Patterns : des moules, que le grinder ne consomme jamais ---------------
    public static final DeferredItem<Item> AXE_PATTERN = ITEMS.registerItem("axe_pattern", Item::new);
    public static final DeferredItem<Item> HAMMER_PATTERN = ITEMS.registerItem("hammer_pattern", Item::new);
    public static final DeferredItem<Item> SHOVEL_PATTERN = ITEMS.registerItem("shovel_pattern", Item::new);
    public static final DeferredItem<Item> BROADSWORD_PATTERN = ITEMS.registerItem("broadsword_pattern", Item::new);
    public static final DeferredItem<Item> FASTSWORD_PATTERN = ITEMS.registerItem("fastsword_pattern", Item::new);
    public static final DeferredItem<Item> PICKAXE_PATTERN = ITEMS.registerItem("pickaxe_pattern", Item::new);
    public static final DeferredItem<Item> SWORD_PATTERN = ITEMS.registerItem("sword_pattern", Item::new);
    public static final DeferredItem<Item> INGOT_PATTERN = ITEMS.registerItem("ingot_pattern", Item::new);
    public static final DeferredItem<Item> BLOCK_PATTERN = ITEMS.registerItem("block_pattern", Item::new);
    /** « Pattern de base » : le socle de toutes les formes. */
    public static final DeferredItem<Item> SOCKET_PATTERN = ITEMS.registerItem("socket_pattern", Item::new);

    // -- Tetes d'outil ---------------------------------------------------------
    public static final DeferredItem<Item> AXE_HEAD = ITEMS.registerItem("axe_head", Item::new);
    public static final DeferredItem<Item> HAMMER_HEAD = ITEMS.registerItem("hammer_head", Item::new);
    public static final DeferredItem<Item> SHOVEL_HEAD = ITEMS.registerItem("shovel_head", Item::new);
    public static final DeferredItem<Item> BROADSWORD_HEAD = ITEMS.registerItem("broadsword_head", Item::new);
    public static final DeferredItem<Item> FASTSWORD_HEAD = ITEMS.registerItem("fastsword_head", Item::new);
    public static final DeferredItem<Item> PICKAXE_HEAD = ITEMS.registerItem("pickaxe_head", Item::new);
    public static final DeferredItem<Item> SWORD_HEAD = ITEMS.registerItem("sword_head", Item::new);

    // -- Modificateurs ---------------------------------------------------------
    public static final DeferredItem<Item> SMELT_MODIFIER = ITEMS.registerItem("smelt_modifier", Item::new);
    public static final DeferredItem<Item> DAMAGE_MODIFIER = ITEMS.registerItem("damage_modifier", Item::new);
    public static final DeferredItem<Item> FLAME_MODIFIER = ITEMS.registerItem("flame_modifier", Item::new);
    public static final DeferredItem<Item> KNOCKBACK_MODIFIER = ITEMS.registerItem("knockback_modifier", Item::new);
    public static final DeferredItem<Item> MORE_UPGRADE_MODIFIER = ITEMS.registerItem("more_upgrade_modifier", Item::new);
    /** {@code ORB_KNOCKBACK} : l'ingredient du modificateur de recul. */
    public static final DeferredItem<Item> KNOCKBACK_ORB = ITEMS.registerItem("knockback_orb", Item::new);

    // -- Marteaux : pioches en 3 x 3, neuf fois plus lentes ----------------------
    public static final DeferredItem<HammerItem> AMETHYST_HAMMER = ITEMS.registerItem(
            "amethyst_hammer", properties -> new HammerItem(false, hammer(properties, PalaToolMaterials.AMETHYST)));
    public static final DeferredItem<HammerItem> TITANE_HAMMER = ITEMS.registerItem(
            "titane_hammer", properties -> new HammerItem(false, hammer(properties, PalaToolMaterials.TITANE)));
    public static final DeferredItem<HammerItem> PALADIUM_HAMMER = ITEMS.registerItem(
            "paladium_hammer", properties -> new HammerItem(true, hammer(properties, PalaToolMaterials.PALADIUM)));

    // -- Broadswords : ItemBroadsword(degats, durabilite) ----------------------
    // Degats d'ici : 1 (le joueur) + parametre + bonus du materiau, soit ceux de
    // PalaMod — 7,7 / 8,2 / 9. La broadsword en titane avait 20 de durabilite
    // (bipush 20, entre 800 et 3 000) : une coquille, portee a 2 000.
    public static final DeferredItem<GrinderSwordItem> AMETHYST_BROADSWORD = ITEMS.registerItem(
            "amethyst_broadsword", properties -> new GrinderSwordItem(true, false, 7.7f,
                    weapon(properties, PalaToolMaterials.AMETHYST, 3.7f, -3.0f, 800)));
    public static final DeferredItem<GrinderSwordItem> TITANE_BROADSWORD = ITEMS.registerItem(
            "titane_broadsword", properties -> new GrinderSwordItem(true, false, 8.2f,
                    weapon(properties, PalaToolMaterials.TITANE, 3.7f, -3.0f, 2000)));
    public static final DeferredItem<GrinderSwordItem> PALADIUM_BROADSWORD = ITEMS.registerItem(
            "paladium_broadsword", properties -> new GrinderSwordItem(true, true, 9.0f,
                    weapon(properties, PalaToolMaterials.PALADIUM, 2.0f, -3.0f, 3000)));

    // -- Fastswords : 6 / 5 / 7. La fastsword en paladium avait 20 de durabilite
    // (bipush 20, apres 400 et 1 000) : la meme coquille, portee a 2 000.
    public static final DeferredItem<GrinderSwordItem> AMETHYST_FASTSWORD = ITEMS.registerItem(
            "amethyst_fastsword", properties -> new GrinderSwordItem(false, false, 6.0f,
                    weapon(properties, PalaToolMaterials.AMETHYST, 2.0f, -2.4f, 400)));
    public static final DeferredItem<GrinderSwordItem> TITANE_FASTSWORD = ITEMS.registerItem(
            "titane_fastsword", properties -> new GrinderSwordItem(false, false, 5.0f,
                    weapon(properties, PalaToolMaterials.TITANE, 0.5f, -2.4f, 1000)));
    public static final DeferredItem<GrinderSwordItem> PALADIUM_FASTSWORD = ITEMS.registerItem(
            "paladium_fastsword", properties -> new GrinderSwordItem(false, true, 7.0f,
                    weapon(properties, PalaToolMaterials.PALADIUM, 0.0f, -2.4f, 2000)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            SOCKET_PATTERN, INGOT_PATTERN, BLOCK_PATTERN, PICKAXE_PATTERN, AXE_PATTERN, SHOVEL_PATTERN,
            SWORD_PATTERN, HAMMER_PATTERN, BROADSWORD_PATTERN, FASTSWORD_PATTERN,
            PICKAXE_HEAD, AXE_HEAD, SHOVEL_HEAD, SWORD_HEAD, HAMMER_HEAD, BROADSWORD_HEAD, FASTSWORD_HEAD,
            SMELT_MODIFIER, DAMAGE_MODIFIER, FLAME_MODIFIER, KNOCKBACK_MODIFIER, MORE_UPGRADE_MODIFIER, KNOCKBACK_ORB,
            AMETHYST_HAMMER, TITANE_HAMMER, PALADIUM_HAMMER,
            AMETHYST_BROADSWORD, TITANE_BROADSWORD, PALADIUM_BROADSWORD,
            AMETHYST_FASTSWORD, TITANE_FASTSWORD, PALADIUM_FASTSWORD);

    private PalaSmeltery() {
    }

    private static Item.Properties hammer(Item.Properties properties, ToolMaterial material) {
        return properties.pickaxe(material, 1.0f, -2.8f).stacksTo(1);
    }

    private static Item.Properties weapon(Item.Properties properties, ToolMaterial material, float damage,
                                          float speed, int durability) {
        return properties.sword(material, damage, speed).durability(durability).stacksTo(1);
    }

    /** Un moule ({@code ItemPatern}) : le grinder ne l'use pas quand les deux cases en sont. */
    public static boolean isPattern(ItemStack stack) {
        return stack.is(AXE_PATTERN.get()) || stack.is(HAMMER_PATTERN.get()) || stack.is(SHOVEL_PATTERN.get())
                || stack.is(BROADSWORD_PATTERN.get()) || stack.is(FASTSWORD_PATTERN.get())
                || stack.is(PICKAXE_PATTERN.get()) || stack.is(SWORD_PATTERN.get()) || stack.is(INGOT_PATTERN.get())
                || stack.is(BLOCK_PATTERN.get()) || stack.is(SOCKET_PATTERN.get());
    }

    /** Les modificateurs de l'outil, ou aucun. */
    public static ToolModifiers modifiers(ItemStack stack) {
        ToolModifiers modifiers = stack.get(MODIFIERS.get());
        if (modifiers != null) {
            return modifiers;
        }
        return ToolModifiers.empty(stack.getItem() instanceof ModifiableTool tool ? tool.baseSlots() : 0);
    }

    /** L'infobulle des outils a modificateurs : emplacements, puis chaque modificateur et son niveau. */
    static void describe(ItemStack stack, ModifiableTool tool, Consumer<Component> tooltip) {
        ToolModifiers modifiers = modifiers(stack);
        tooltip.accept(Component.translatable("item.palamod.modifiers.slots", modifiers.applied().size(),
                modifiers.totalSlots()).withStyle(ChatFormatting.GRAY));
        for (GrinderModifier modifier : GrinderModifier.values()) {
            int level = modifiers.level(modifier);
            if (level > 0 && modifier != GrinderModifier.MORE) {
                tooltip.accept(Component.empty().append(modifier.displayName()).append(" " + level));
            }
        }
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
    }
}
