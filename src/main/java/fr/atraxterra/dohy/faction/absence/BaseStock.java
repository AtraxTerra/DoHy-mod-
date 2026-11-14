package fr.atraxterra.dohy.faction.absence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que contiennent les coffres et les machines d'une base : la somme, objet
 * par objet, de tout ce qui est range dans les chunks tenus.
 *
 * <p>Les conteneurs du jeu (coffres, tonneaux, fours, entonnoirs, et les
 * machines qui en sont) sont lus case par case. Les autres blocs a donnees sont
 * lus par la capacite d'objets de NeoForge, sans cote : une meme reserve vue de
 * plusieurs blocs (un multibloc qui rend le meme objet) n'est comptee qu'une
 * fois, et une reserve de plus de {@link #NETWORK} cases (le reseau d'un mod de
 * stockage, vu de chacune de ses prises) est laissee de cote.
 *
 * <p>La difference entre deux releves dit ce que la base a produit (ce qui a
 * augmente) et ce qu'elle a consomme (ce qui a baisse).
 */
public final class BaseStock {

    private static final int NETWORK = 512;

    private BaseStock() {
    }

    /** Les objets ranges dans les chunks tenus de ce joueur (ceux qui sont charges). */
    public static Map<Identifier, Long> scan(MinecraftServer server, UUID owner) {
        Map<Identifier, Long> counts = new HashMap<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<KeptBases.Spot> spots = KeptBases.keptOf(server, owner);
        for (KeptBases.Spot spot : spots) {
            ServerLevel level = KeptBases.level(server, spot.dimension());
            LevelChunk chunk = level == null ? null : level.getChunkSource().getChunkNow(spot.x(), spot.z());
            if (chunk == null) {
                continue;
            }
            for (Map.Entry<BlockPos, BlockEntity> entry : List.copyOf(chunk.getBlockEntities().entrySet())) {
                BlockEntity blockEntity = entry.getValue();
                if (blockEntity.isRemoved()) {
                    continue;
                }
                if (blockEntity instanceof Container container) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        add(counts, container.getItem(slot));
                    }
                    continue;
                }
                ResourceHandler<ItemResource> handler;
                try {
                    handler = level.getCapability(Capabilities.Item.BLOCK, entry.getKey(), null);
                } catch (RuntimeException error) {
                    continue;
                }
                if (handler == null || handler.size() > NETWORK || !seen.add(handler)) {
                    continue;
                }
                for (int slot = 0; slot < handler.size(); slot++) {
                    ItemResource resource = handler.getResource(slot);
                    long amount = handler.getAmountAsLong(slot);
                    if (resource != null && !resource.isEmpty() && amount > 0) {
                        counts.merge(BuiltInRegistries.ITEM.getKey(resource.getItem()), amount, Long::sum);
                    }
                }
            }
        }
        return counts;
    }

    private static void add(Map<Identifier, Long> counts, ItemStack stack) {
        if (!stack.isEmpty()) {
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), (long) stack.getCount(), Long::sum);
        }
    }
}
