package fr.atraxterra.dohy.palamod.fluid;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/**
 * L'eau angelique ({@code PRegister_Fluids.FLUIDS_ANGELICWATER},
 * {@code BlockAngelicWater}, {@code ItemBucketBase("angelicwater")}).
 *
 * <p>Releve au bytecode :
 * <ul>
 *   <li>un fluide de type eau ({@code Material.water}) : on y nage, on s'y
 *       noie, il eteint le feu et humidifie la terre labouree ; densite 200,
 *       plus legere que l'eau ;</li>
 *   <li>pas de source infinie ({@code BlockFluidClassic} : {@code canCreateSources}
 *       reste faux) ;</li>
 *   <li>un joueur dedans regagne un demi-coeur toutes les 40 ticks s'il est
 *       blesse ({@code onEntityCollidedWithBlock}, voir {@link AngelicWaterEvents}) ;</li>
 *   <li>un seau, par un, fait d'une potion de regeneration sur un seau d'eau.</li>
 * </ul>
 * Ses autres usages dans PalaMod (chaudron de l'alchimiste, reservoirs, pistolet
 * a reservoir, familiers) viendront avec leurs modules.
 */
public final class PalaFluids {

    private static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PalaMod.NAMESPACE);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, PalaMod.NAMESPACE);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);

    public static final DeferredHolder<FluidType, FluidType> ANGELIC_WATER_TYPE = TYPES.register(
            "angelic_water", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.palamod.angelic_water")
                    .canSwim(true)
                    .canDrown(true)
                    .canExtinguish(true)
                    .canHydrate(true)
                    .supportsBoating(true)
                    .canConvertToSource(false)
                    .density(200)
                    .viscosity(1000)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    // Les proprietes citent les quatre objets, dont deux declares plus bas : elles
    // sont donc construites DANS les fournisseurs, appeles a l'enregistrement,
    // quand tous les champs existent. Les noms qualifies sont ce qui autorise la
    // reference en avant dans un lambda.
    public static final DeferredHolder<Fluid, FlowingFluid> ANGELIC_WATER = FLUIDS.register(
            "angelic_water", () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(
                    PalaFluids.ANGELIC_WATER_TYPE, PalaFluids.ANGELIC_WATER, PalaFluids.FLOWING_ANGELIC_WATER)
                    .bucket(PalaFluids.ANGELIC_WATER_BUCKET).block(PalaFluids.ANGELIC_WATER_BLOCK)));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_ANGELIC_WATER = FLUIDS.register(
            "flowing_angelic_water", () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(
                    PalaFluids.ANGELIC_WATER_TYPE, PalaFluids.ANGELIC_WATER, PalaFluids.FLOWING_ANGELIC_WATER)
                    .bucket(PalaFluids.ANGELIC_WATER_BUCKET).block(PalaFluids.ANGELIC_WATER_BLOCK)));

    /** Durete 100, comme l'eau vanilla ({@code setHardness(100.0f)}). */
    public static final DeferredBlock<LiquidBlock> ANGELIC_WATER_BLOCK = BLOCKS.registerBlock(
            "angelic_water", properties -> new LiquidBlock(ANGELIC_WATER.get(), properties),
            properties -> properties
                    .mapColor(MapColor.WATER)
                    .replaceable()
                    .noCollision()
                    .strength(100.0f)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid());

    /** Par un, et consomme entier dans un craft : {@code ItemBucketBase} n'a pas de contenant. */
    public static final DeferredItem<BucketItem> ANGELIC_WATER_BUCKET = ITEMS.registerItem(
            "angelic_water_bucket", properties -> new BucketItem(ANGELIC_WATER.get(), properties.stacksTo(1)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(ANGELIC_WATER_BUCKET);

    private PalaFluids() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
