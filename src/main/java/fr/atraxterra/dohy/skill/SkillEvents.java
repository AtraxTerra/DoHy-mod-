package fr.atraxterra.dohy.skill;

import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;

/**
 * Les sources d'XP.
 *
 * <p>Comme pour les collections, un bloc pose par le joueur ne rapporte rien —
 * sauf s'il figure dans {@code #dohy:placement_exempt}, qui exempte les
 * cultures murissant sur place. Sans cette exemption, l'agriculture ne
 * progresserait jamais.
 *
 * <p>L'XP d'agriculture n'est accordee que sur une <b>culture mature</b> : sans
 * cette condition, casser des pousses a peine semees rapporterait autant qu'une
 * recolte, et l'engrais a os deviendrait une machine a XP.
 */
@EventBusSubscriber
public final class SkillEvents {

    private SkillEvents() {
    }

    /** Casse d'un bloc : minage, agriculture ou bucheronnage selon le bloc. */
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

        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        for (Skill skill : new Skill[]{Skill.MINING, Skill.FARMING, Skill.FORAGING}) {
            long xp = blockXp(skill, state, blockId);
            if (xp <= 0) {
                continue;
            }
            if (skill == Skill.FARMING && !isMature(state)) {
                continue;
            }
            SkillApi.grant(player, skill, xp);
            maybeDoubleDrop(player, skill, event);
        }
    }

    /** Mort d'une creature : combat. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        ServerPlayer player = attribute(event.getEntity(), event);
        if (player == null) {
            return;
        }
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        long xp = lookup(SkillConfig.config().def(Skill.COMBAT).sources()
                .map(SkillConfig.Sources::entities).orElse(Map.of()), entityId, null);
        if (xp > 0) {
            SkillApi.grant(player, Skill.COMBAT, xp);
        }

        // Les animaux d'elevage comptent pour l'agriculture, pas pour le combat.
        long farmXp = lookup(SkillConfig.config().def(Skill.FARMING).sources()
                .map(SkillConfig.Sources::entities).orElse(Map.of()), entityId, null);
        if (farmXp > 0) {
            SkillApi.grant(player, Skill.FARMING, farmXp);
        }
    }

    /** Peche. */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long xp = action(Skill.FISHING, "fish_caught");
        if (xp > 0) {
            SkillApi.grant(player, Skill.FISHING, xp);
        }
    }

    /** Fonte : forge. */
    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long xp = action(Skill.SMITHING, "item_smelted");
        if (xp > 0) {
            SkillApi.grant(player, Skill.SMITHING, xp * Math.max(1, event.getSmelting().getCount()));
        }
    }

    /**
     * Pose d'un bloc : construction.
     *
     * <p>Une seule fois par position, grace au suivi des blocs poses : reposer
     * le meme bloc au meme endroit ne rapporte rien, puisque la position est
     * deja marquee.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PlacedBlocks.isPlayerPlaced(event.getLevel(), event.getPos())) {
            return;
        }
        long xp = action(Skill.BUILDING, "block_placed");
        if (xp > 0) {
            SkillApi.grant(player, Skill.BUILDING, xp);
        }
    }

    /** Duplique les drops avec la probabilite accordee par les perks. */
    private static void maybeDoubleDrop(ServerPlayer player, Skill skill, BlockDropsEvent event) {
        // Les ailes completes du musee s'ajoutent aux perks de skill.
        double chance = SkillPerks.doubleDropChance(player, skill)
                + fr.atraxterra.dohy.museum.MuseumApi.doubleDropBonus(player);
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) {
            return;
        }
        event.getDrops().forEach(drop -> {
            ItemStack copy = drop.getItem().copy();
            if (!copy.isEmpty()) {
                player.level().addFreshEntity(
                        new net.minecraft.world.entity.item.ItemEntity(
                                player.level(), drop.getX(), drop.getY(), drop.getZ(), copy));
            }
        });
    }

    private static long blockXp(Skill skill, BlockState state, Identifier blockId) {
        Map<String, Long> table = SkillConfig.config().def(skill).sources()
                .map(SkillConfig.Sources::blocks).orElse(Map.of());
        return lookup(table, blockId, state);
    }

    /**
     * Cherche une valeur par identifiant, puis par tag.
     *
     * <p>Les tags sont resolus a l'usage et non au chargement : les registres
     * de tags ne sont pas forcement prets au moment ou le datapack est lu.
     */
    private static long lookup(Map<String, Long> table, Identifier id, BlockState state) {
        Long direct = table.get(id.toString());
        if (direct != null) {
            return direct;
        }
        if (state == null) {
            return 0L;
        }
        for (Map.Entry<String, Long> candidate : table.entrySet()) {
            String key = candidate.getKey();
            if (!key.startsWith("#")) {
                continue;
            }
            TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(),
                    Identifier.bySeparator(key.substring(1), ':'));
            if (state.is(tag)) {
                return candidate.getValue();
            }
        }
        return 0L;
    }

    private static long action(Skill skill, String name) {
        return SkillConfig.config().def(skill).sources()
                .map(SkillConfig.Sources::actions).orElse(Map.of())
                .getOrDefault(name, 0L);
    }

    /** Culture arrivee a maturite : son age est au maximum. */
    private static boolean isMature(BlockState state) {
        if (state.hasProperty(BlockStateProperties.AGE_7)) {
            return state.getValue(BlockStateProperties.AGE_7) >= 7;
        }
        if (state.hasProperty(BlockStateProperties.AGE_3)) {
            return state.getValue(BlockStateProperties.AGE_3) >= 3;
        }
        if (state.hasProperty(BlockStateProperties.AGE_2)) {
            return state.getValue(BlockStateProperties.AGE_2) >= 2;
        }
        if (state.hasProperty(BlockStateProperties.AGE_1)) {
            return state.getValue(BlockStateProperties.AGE_1) >= 1;
        }
        // Pas de notion d'age : melon, citrouille, champignon. Toujours mature.
        return true;
    }

    private static ServerPlayer attribute(LivingEntity victim, LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer direct) {
            return direct;
        }
        if (victim.getLastHurtByMob() instanceof ServerPlayer indirect) {
            return indirect;
        }
        return null;
    }
}
