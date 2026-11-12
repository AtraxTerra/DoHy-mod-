package fr.atraxterra.dohy.agri;

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

import java.util.List;

/**
 * D'ou viennent les premieres semences : de l'herbe, comme celles du ble.
 *
 * <p>Casser de l'herbe a une petite chance de donner, en plus, une semence de
 * cereale naturelle tiree au hasard. Les OGM n'en sortent jamais : leur seule
 * source est la machine a OGM.
 */
@EventBusSubscriber
public final class AgriEvents {

    private AgriEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.getAbilities().instabuild
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.TALL_GRASS) && !state.is(Blocks.FERN)
                && !state.is(Blocks.LARGE_FERN)) {
            return;
        }
        if (player.getRandom().nextDouble() >= AgriConfig.config().grassSeedChance()) {
            return;
        }
        List<Item> seeds = DoHyAgri.naturalSeeds();
        Item seed = seeds.get(player.getRandom().nextInt(seeds.size()));
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d,
                pos.getZ() + 0.5d, new ItemStack(seed)));
    }
}
