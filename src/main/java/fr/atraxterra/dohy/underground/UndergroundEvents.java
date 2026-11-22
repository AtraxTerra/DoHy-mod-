package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.faction.enterprise.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * D'ou viennent les graines des affaires illegales (lot 24c) : l'herbe sauvage en lache
 * parfois une, de celles qui poussent la ou elle pousse. Les plantes mures en rendent aussi.
 */
@EventBusSubscriber
public final class UndergroundEvents {

    /** Une herbe sur quarante lache une graine. */
    private static final double SEED_CHANCE = 0.025d;

    private UndergroundEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.getAbilities().instabuild
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!(state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN))) {
            return;
        }
        if (player.getRandom().nextDouble() >= SEED_CHANCE) {
            return;
        }
        BlockPos pos = event.getPos();
        List<Item> seeds = new ArrayList<>();
        for (Line line : Line.values()) {
            if (line.grows() && Latitudes.grows(line, level, pos)) {
                seeds.add(Goods.seed(line));
            }
        }
        if (seeds.isEmpty()) {
            return;
        }
        Item seed = seeds.get(player.getRandom().nextInt(seeds.size()));
        event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                new ItemStack(seed)));
    }

}
