package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaToolMaterials;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Les objets du metier de Mineur.
 *
 * <p>Les identifiants sont ceux que {@code data/jobs.json} verrouille deja :
 * {@code amethyst_excavator} au niveau 15, {@code magnet} au 35,
 * {@code moula_stone} au 40... Un caractere de difference laisserait le verrou
 * inerte sans la moindre erreur.
 *
 * <p>Les noms sont ecrits en toutes lettres : {@code tools/check_assets.py} trouve
 * les objets en lisant les appels {@code registerItem("...")} du source.
 */
public final class PalaMiner {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);

    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    /** L'effet de la potion de durete. */
    public static final DeferredHolder<MobEffect, HardnessEffect> HARDNESS =
            EFFECTS.register("hardness", HardnessEffect::new);

    // -- Excavatrices : les materiaux des pelles du meme metal ---------------
    public static final DeferredItem<ExcavatorItem> AMETHYST_EXCAVATOR = track(ITEMS.registerItem(
            "amethyst_excavator", properties -> new ExcavatorItem(PalaToolMaterials.AMETHYST, properties)));
    public static final DeferredItem<ExcavatorItem> TITANE_EXCAVATOR = track(ITEMS.registerItem(
            "titane_excavator", properties -> new ExcavatorItem(PalaToolMaterials.TITANE, properties)));
    public static final DeferredItem<ExcavatorItem> PALADIUM_EXCAVATOR = track(ITEMS.registerItem(
            "paladium_excavator", properties -> new ExcavatorItem(PalaToolMaterials.PALADIUM, properties)));

    // -- Outils de construction : cotes de getRadius(meta) -------------------
    public static final DeferredItem<BuilderWandItem> AMETHYST_BUILDER_WAND = track(ITEMS.registerItem(
            "amethyst_builder_wand", properties -> new BuilderWandItem(6, properties.stacksTo(1))));
    public static final DeferredItem<BuilderWandItem> TITANE_BUILDER_WAND = track(ITEMS.registerItem(
            "titane_builder_wand", properties -> new BuilderWandItem(9, properties.stacksTo(1))));
    public static final DeferredItem<BuilderWandItem> PALADIUM_BUILDER_WAND = track(ITEMS.registerItem(
            "paladium_builder_wand", properties -> new BuilderWandItem(11, properties.stacksTo(1))));
    public static final DeferredItem<BuilderWandItem> ENDIUM_BUILDER_WAND = track(ITEMS.registerItem(
            "endium_builder_wand", properties -> new BuilderWandItem(20, properties.stacksTo(1))));

    // -- Objets de poche -------------------------------------------------------
    // ItemMiner ne fixe pas de taille de pile : 64, comme l'original.
    public static final DeferredItem<MagnetItem> MAGNET = track(ITEMS.registerItem(
            "magnet", MagnetItem::new));
    public static final DeferredItem<Item> MOULA_STONE = track(ITEMS.registerItem(
            "moula_stone", Item::new));

    public static final DeferredItem<HardnessPotionItem> HARDNESS_POTION = track(ITEMS.registerItem(
            "hardness_potion", properties -> new HardnessPotionItem(properties
                    .stacksTo(1)
                    .usingConvertsTo(Items.GLASS_BOTTLE)
                    .component(DataComponents.CONSUMABLE, Consumable.builder()
                            // getMaxItemUseDuration : 20 ticks, une seconde.
                            .consumeSeconds(1.0f)
                            .animation(ItemUseAnimation.DRINK)
                            .sound(SoundEvents.GENERIC_DRINK)
                            .hasConsumeParticles(false)
                            .build()))));

    private PalaMiner() {
    }

    /** L'ordre de l'onglet creatif. */
    public static List<DeferredItem<? extends Item>> ordered() {
        return List.copyOf(ORDER);
    }

    private static <T extends Item> DeferredItem<T> track(DeferredItem<T> item) {
        ORDER.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
    }
}
