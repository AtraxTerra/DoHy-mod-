package fr.atraxterra.dohy.contract;

import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.function.Predicate;

/**
 * Le pouls du rythme : compteur de temps de jeu, rotation des contrats,
 * demande du jour, et progression de tous les objectifs.
 */
@EventBusSubscriber
public final class ContractEvents {

    /** Un pas par minute de jeu. */
    private static final int STEP_TICKS = 20 * 60;

    private static int tickCounter;

    private ContractEvents() {
    }

    /**
     * Compteur central de temps de jeu.
     *
     * <p>C'est la seule horloge du mod. Elle n'avance que serveur en marche,
     * donc rien n'expire pendant que le monde est ferme : c'est ce qui garantit
     * qu'aucun systeme ne punit une absence.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < STEP_TICKS) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ContractApi.data(player).tickPlaytime();
            ContractApi.rollIfDue(player);
            ContractApi.refreshDemand(player);
        }
    }

    /** A la connexion : contrats et demande sont proposes sans attendre. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContractApi.rollIfDue(player);
            ContractApi.refreshDemand(player);
        }
    }

    /** Casse de bloc : minage et recolte. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }

        BlockState state = event.getState();
        boolean exempt = state.is(CollectionEvents.PLACEMENT_EXEMPT);
        if (PlacedBlocks.wasPlayerPlaced(event) && !exempt) {
            return;
        }

        Predicate<String> test = blockTest(state);
        ContractApi.progress(player, ContractType.MINE_BLOCK, test, 1L);
        if (exempt) {
            ContractApi.progress(player, ContractType.HARVEST, test, 1L);
        }
    }

    /** Pose de bloc. */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PlacedBlocks.isPlayerPlaced(event.getLevel(), event.getPos())) {
            return;
        }
        ContractApi.progress(player, ContractType.PLACE_BLOCK, blockTest(event.getState()), 1L);
    }

    /** Mort d'une creature. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer direct
                ? direct
                : event.getEntity().getLastHurtByMob() instanceof ServerPlayer indirect ? indirect : null;
        if (player == null) {
            return;
        }
        ContractApi.progress(player, ContractType.KILL_ENTITY, entityTest(event.getEntity()), 1L);
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContractApi.progress(player, ContractType.FISH, target -> true, 1L);
        }
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContractApi.progress(player, ContractType.SMELT, target -> true,
                    Math.max(1, event.getSmelting().getCount()));
        }
    }

    /**
     * Test de cible pour un bloc : identifiant exact, ou tag si la cible
     * commence par {@code #}.
     */
    private static Predicate<String> blockTest(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return target -> {
            if (!target.startsWith("#")) {
                return target.equals(id.toString());
            }
            TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(),
                    Identifier.bySeparator(target.substring(1), ':'));
            return state.is(tag);
        };
    }

    /**
     * Test de cible pour une entite.
     *
     * <p>{@code #minecraft:hostile} n'est pas un tag vanilla : il est traite a
     * part, via l'interface {@code Enemy} que portent les creatures hostiles.
     * C'est plus robuste qu'un tag qui n'existe pas et qui ne matcherait jamais.
     */
    private static Predicate<String> entityTest(Entity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return target -> {
            if ("#minecraft:hostile".equals(target)) {
                return entity instanceof Enemy;
            }
            if (!target.startsWith("#")) {
                return target.equals(id.toString());
            }
            TagKey<EntityType<?>> tag = TagKey.create(BuiltInRegistries.ENTITY_TYPE.key(),
                    Identifier.bySeparator(target.substring(1), ':'));
            return entity.getType().is(tag);
        };
    }
}
