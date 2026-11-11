package fr.atraxterra.dohy.job;

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
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Map;

/**
 * Les sources d'XP des metiers.
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
public final class JobXpEvents {

    private JobXpEvents() {
    }

    /** Casse d'un bloc : Mineur, Fermier ou Alchimiste selon le bloc. */
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

        for (Job job : new Job[]{Job.MINER, Job.FARMER, Job.ALCHEMIST}) {
            long xp = blockXp(job, state, blockId);
            if (xp <= 0) {
                continue;
            }
            // L'alchimie recolte aussi des cultures — les verrues du Nether en
            // premier. Sans la condition de maturite, elle serait la faille par
            // laquelle casser des pousses rapporterait autant qu'une recolte,
            // celle que l'agriculture ferme depuis le depart.
            if ((job == Job.FARMER || job == Job.ALCHEMIST) && !isMature(state)) {
                continue;
            }
            JobApi.grant(player, job, xp);
            maybeDoubleDrop(player, event);
        }
    }

    /** Mort d'une creature : Chasseur, et Fermier ou Alchimiste selon la bete. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        ServerPlayer player = attribute(event.getEntity(), event);
        if (player == null) {
            return;
        }
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        long xp = lookup(JobProgression.config().def(Job.HUNTER).sources()
                .map(JobProgression.Sources::entities).orElse(Map.of()), entityId, null);
        if (xp > 0) {
            JobApi.grant(player, Job.HUNTER, xp);
        }

        // Les animaux d'elevage comptent pour le Fermier, pas pour le Chasseur.
        long farmXp = lookup(JobProgression.config().def(Job.FARMER).sources()
                .map(JobProgression.Sources::entities).orElse(Map.of()), entityId, null);
        if (farmXp > 0) {
            JobApi.grant(player, Job.FARMER, farmXp);
        }

        // Les creatures qui portent un ingredient de potion comptent pour
        // l'Alchimiste : sorciere, blaze, ghast, cube de magma, gardien. Elles
        // rapportent aussi au Chasseur, et c'est voulu — c'est le meme geste qui
        // sert deux metiers, exactement comme l'elevage.
        long alchemyXp = lookup(JobProgression.config().def(Job.ALCHEMIST).sources()
                .map(JobProgression.Sources::entities).orElse(Map.of()), entityId, null);
        if (alchemyXp > 0) {
            JobApi.grant(player, Job.ALCHEMIST, alchemyXp);
        }
    }

    /**
     * Potion terminee a l'alambic : Alchimiste.
     *
     * <p>C'est la source principale du metier d'Alchimiste, et la seule qui ne
     * passe ni par un bloc ni par une creature. {@code PlayerBrewedPotionEvent}
     * a ete verifie a la sonde : il porte {@code getEntity()} (le joueur) et
     * {@code getStack()}, et n'est pas annulable — ce qui convient, on ne fait
     * que compter.
     */
    @SubscribeEvent
    public static void onPotionBrewed(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long xp = action(Job.ALCHEMIST, "potion_brewed");
        if (xp > 0) {
            JobApi.grant(player, Job.ALCHEMIST, xp);
        }
    }

    /**
     * Duplique les drops avec la probabilite accordee par les ailes completes du
     * musee. Les metiers n'en accordent plus : leurs paliers paient en argent.
     */
    private static void maybeDoubleDrop(ServerPlayer player, BlockDropsEvent event) {
        double chance = fr.atraxterra.dohy.museum.MuseumApi.doubleDropBonus(player);
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

    private static long blockXp(Job job, BlockState state, Identifier blockId) {
        Map<String, Long> table = JobProgression.config().def(job).sources()
                .map(JobProgression.Sources::blocks).orElse(Map.of());
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

    private static long action(Job job, String name) {
        return JobProgression.config().def(job).sources()
                .map(JobProgression.Sources::actions).orElse(Map.of())
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
