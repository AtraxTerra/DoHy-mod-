package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Le chanvre de NTM NEXT, culture de DoHy (2026-11-23).
 *
 * <p>Dans NTM NEXT, le chanvre ({@code hbm:plant_flower_weed}) est une fleur qui grandit seule
 * en chanvre haut ({@code hbm:plant_tall_weed}) ; casser la couronne rend une fleur et laisse
 * la plante redevenir fleur, qui regrandit. Il n'en sortait guere que de la ficelle et de
 * l'ethanol. DoHy en fait une culture industrielle — du chanvre a fibre, rien a voir avec le
 * cannabis de l'economie souterraine :
 * <ul>
 *   <li>une couronne recoltee a la main rend en plus de la fibre et des graines
 *       ({@link AgriConfig.HempYield}), et de l'XP de Fermier ;</li>
 *   <li>une herbe des climats tempere et chaud lache parfois une fleur, pour commencer un
 *       champ sur la carte de la Terre, ou le chanvre n'a jamais pousse ;</li>
 *   <li>la fibre fait de la ficelle, de la corde de NTM et de la toile ; la toile du papier et
 *       des tableaux ; les graines nourrissent les betes de la mangeoire et les poules, et
 *       brulent dans le biogenerateur (le tag {@link DoHyAgri#FODDER}).</li>
 * </ul>
 *
 * <p>Une seule recolte par plante, quelle que soit la moitie cassee : la couronne casse seule
 * (la plante reste) ou avec le pied (NTM fait tomber la couronne, puis le pied tombe a son
 * tour). Le bonus suit donc la couronne, jamais le pied — sinon casser par le bas doublerait
 * fibre, graines et XP.
 */
@EventBusSubscriber
public final class Hemp {

    /** La fleur de chanvre de NTM NEXT : la semence. */
    public static final Identifier FLOWER = Identifier.fromNamespaceAndPath("hbm", "plant_flower_weed");
    /** Le chanvre haut de NTM NEXT : la plante mure. */
    public static final Identifier TALL = Identifier.fromNamespaceAndPath("hbm", "plant_tall_weed");

    private Hemp() {
    }

    /** La fleur de chanvre si NTM NEXT est installe, sinon {@code null}. */
    public static Item flower() {
        Item item = BuiltInRegistries.ITEM.getOptional(FLOWER).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    /** Vrai pour la couronne (la moitie haute) d'un chanvre haut. */
    static boolean isCrown(BlockState state) {
        return TALL.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    /**
     * Une herbe cassee qui n'a pas lache de semence de cereale : parfois une fleur de chanvre,
     * hors des climats froids. {@code null} sinon, ou sans NTM NEXT.
     */
    static Item grassFlower(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Item flower = flower();
        if (flower == null || Climate.at(level, pos) == Climate.COLD
                || player.getRandom().nextDouble() >= AgriConfig.config().hemp().grassChance()) {
            return null;
        }
        return flower;
    }

    /**
     * La recolte : fibre et graines en plus de la fleur, avant les autres ecouteurs (priorite
     * haute) pour que la tremie du tracteur, le sac d'un bot et les collections les voient.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.getAbilities().instabuild
                || !(event.getLevel() instanceof ServerLevel level) || !isCrown(event.getState())) {
            return;
        }
        AgriConfig.HempYield yield = AgriConfig.config().hemp();
        RandomSource random = player.getRandom();
        BlockPos pos = event.getPos();
        add(event, level, pos, new ItemStack(DoHyAgri.HEMP_FIBER.get(),
                between(random, yield.fiberMin(), yield.fiberMax())));
        add(event, level, pos, new ItemStack(DoHyAgri.HEMP_SEEDS.get(),
                between(random, yield.seedsMin(), yield.seedsMax())));
        if (yield.farmerXp() > 0L) {
            JobApi.grant(player, Job.FARMER, yield.farmerXp());
        }
    }

    private static int between(RandomSource random, int low, int high) {
        return high <= low ? low : low + random.nextInt(high - low + 1);
    }

    private static void add(BlockDropsEvent event, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                    stack));
        }
    }
}
