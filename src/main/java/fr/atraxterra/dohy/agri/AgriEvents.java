package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que l'agriculture ajoute aux blocs casses.
 *
 * <p><b>Les premieres semences</b> viennent de l'herbe, comme celles du ble :
 * une herbe cassee a une petite chance de lacher une semence d'une cereale
 * <i>du climat ou elle pousse</i>. On trouve donc l'avoine dans la taiga et le
 * soja dans la jungle, la ou ils se plantent. Les OGM n'en sortent jamais.
 *
 * <p><b>Le flair du mineur</b> (pain du mineur) fait lacher un minerai a la
 * pierre cassee, au plus un toutes les quelques secondes : un outil de zone
 * casse des centaines de blocs par seconde, et sans ce delai le pain serait une
 * rente.
 */
@EventBusSubscriber
public final class AgriEvents {

    /** Dernier minerai lache par le flair, en ticks de jeu, par joueur. */
    private static final Map<UUID, Long> LAST_PROSPECT = new ConcurrentHashMap<>();

    private AgriEvents() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_PROSPECT.clear();
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.getAbilities().instabuild
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        Item extra = null;
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)) {
            extra = grassSeed(player, level, pos);
        } else if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            extra = prospect(player, level);
        }
        if (extra != null) {
            event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d,
                    pos.getZ() + 0.5d, new ItemStack(extra)));
        }
    }

    private static Item grassSeed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.getRandom().nextDouble() >= AgriConfig.config().grassSeedChance()) {
            return null;
        }
        List<Item> seeds = DoHyAgri.seedsOf(Climate.at(level, pos));
        return seeds.isEmpty() ? null : seeds.get(player.getRandom().nextInt(seeds.size()));
    }

    private static Item prospect(ServerPlayer player, ServerLevel level) {
        if (!player.hasEffect(AgriEffects.PROSPECTING)) {
            return null;
        }
        AgriConfig.Effects config = AgriConfig.config().effects();
        long now = level.getGameTime();
        Long last = LAST_PROSPECT.get(player.getUUID());
        if (last != null && now - last < config.prospectCooldown() && now >= last) {
            return null;
        }
        if (player.getRandom().nextDouble() >= config.prospectChance()) {
            return null;
        }
        int total = 0;
        for (AgriConfig.Drop drop : config.prospectDrops()) {
            if (BuiltInRegistries.ITEM.getOptional(drop.item()).isPresent()) {
                total += drop.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = player.getRandom().nextInt(total);
        for (AgriConfig.Drop drop : config.prospectDrops()) {
            Item item = BuiltInRegistries.ITEM.getOptional(drop.item()).orElse(null);
            if (item == null) {
                continue;
            }
            roll -= drop.weight();
            if (roll < 0) {
                LAST_PROSPECT.put(player.getUUID(), now);
                return item;
            }
        }
        return null;
    }
}
