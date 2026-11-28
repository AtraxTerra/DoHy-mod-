package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.world.PlacedBlocks;
import fr.atraxterra.dohy.world.SpawnerBirth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Map;
import java.util.function.Function;

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
 *
 * <p><b>Le bareme de Bedrock</b> (2026-11-15, {@code tools/bedrock_jobs.py}) : une
 * source a un niveau a partir duquel elle rapporte, et parfois plusieurs paliers
 * ({@link JobProgression.Reward}). S'y ajoutent la cuisson ({@code smelted}) et
 * la fabrication ({@code crafted}). Deux garde-fous, devenus necessaires avec des
 * creatures a 80 a 500 XP :
 * <ul>
 *   <li>une creature nee d'un spawner, ou tuee au controleur de spawners, ne
 *       rapporte qu'un quarantieme ({@link #SPAWNER_SHARE}) : le controleur se
 *       frappe deux fois par seconde sans risque, et a pleine valeur il menait au
 *       niveau 100 en quelques heures. Un quarantieme, c'est a peu pres l'ancien
 *       bareme (un zombie : 4 XP) ;</li>
 *   <li>un petit ne rapporte rien : il ne lache rien, et l'elevage tuerait les
 *       veaux a la naissance.</li>
 * </ul>
 */
@EventBusSubscriber
public final class JobXpEvents {

    /** La part d'une creature de spawner : un quarantieme. */
    public static final double SPAWNER_SHARE = 1.0d / 40.0d;

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
        // Ce que rend le bloc avant toute copie : deux metiers, deux chances, chacune sur l'original.
        java.util.List<net.minecraft.world.entity.item.ItemEntity> originals = java.util.List.copyOf(event.getDrops());

        for (Job job : new Job[]{Job.MINER, Job.FARMER, Job.ALCHEMIST}) {
            JobProgression.Reward reward = lookup(table(job, JobProgression.Sources::blocks), blockId, state);
            double xp = reward == null ? 0.0d : reward.at(JobApi.level(player, job));
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
            grant(player, job, xp);
            maybeDoubleDrop(player, event, originals);
        }
    }

    /** Mort d'une creature : Chasseur, et Fermier ou Alchimiste selon la bete. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        ServerPlayer player = attribute(victim, event);
        if (player == null || victim instanceof AgeableMob young && young.isBaby()) {
            return;
        }
        boolean spawner = SpawnerBirth.of(victim);
        grantKill(player, BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), spawner ? SPAWNER_SHARE : 1.0d);
    }

    /**
     * L'XP de metier d'une creature tuee, a la part {@code share} de sa valeur.
     * Appele aussi par le controleur de spawners, dont la creature du mode manuel
     * ne meurt pas vraiment : elle y compte comme une creature de spawner.
     */
    public static void grantKill(ServerPlayer player, Identifier entityId, double share) {
        // Le Chasseur prend les animaux et les monstres (bareme de Bedrock). Le
        // Fermier et l'Alchimiste prennent les leurs s'ils en ont : les creatures
        // qui portent un ingredient de potion comptent aussi pour l'Alchimiste
        // (sorciere, blaze, ghast...), c'est le meme geste qui sert deux metiers.
        for (Job job : new Job[]{Job.HUNTER, Job.FARMER, Job.ALCHEMIST}) {
            JobProgression.Reward reward = lookup(table(job, JobProgression.Sources::entities), entityId, null);
            if (reward != null) {
                grant(player, job, reward.at(JobApi.level(player, job)) * share);
            }
        }
    }

    /**
     * Un objet sorti cuit : pris a la main au four, au haut fourneau ou au fumoir
     * ({@link #onItemSmelted}), au palafurnace, ou cuit sur place par un marteau
     * ou une pioche du dieu en fusion. {@code count} objets de ce type.
     */
    public static void smelted(ServerPlayer player, ItemStack stack, int count) {
        byItem(player, stack, count, JobProgression.Sources::smelted);
    }

    /** Four, haut fourneau, fumoir : ce qu'on retire de la case de resultat, clic ou maj-clic. */
    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            smelted(player, event.getSmelting(), event.getAmountRemoved());
        }
    }

    /** L'etabli et l'inventaire : chaque fabrication, quantite de son resultat comprise. */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            byItem(player, event.getCrafting(), event.getCrafting().getCount(), JobProgression.Sources::crafted);
        }
    }

    private static void byItem(ServerPlayer player, ItemStack stack, int count,
                               Function<JobProgression.Sources, Map<String, JobProgression.Reward>> kind) {
        if (stack.isEmpty() || count <= 0) {
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Job job : Job.values()) {
            JobProgression.Reward reward = table(job, kind).get(id.toString());
            if (reward != null) {
                grant(player, job, reward.at(JobApi.level(player, job)) * count);
            }
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
        JobProgression.Reward reward = table(Job.ALCHEMIST, JobProgression.Sources::actions).get("potion_brewed");
        if (reward != null) {
            grant(player, Job.ALCHEMIST, reward.at(JobApi.level(player, Job.ALCHEMIST)));
        }
    }

    /**
     * Accorde un gain qui peut avoir une fraction (un quarantieme de spawner) : la
     * partie entiere, plus un point tire au sort selon la fraction — la bonne
     * moyenne, sans rien stocker.
     */
    static void grant(ServerPlayer player, Job job, double xp) {
        if (xp <= 0) {
            return;
        }
        long whole = (long) Math.floor(xp);
        if (player.getRandom().nextDouble() < xp - whole) {
            whole++;
        }
        JobApi.grant(player, job, whole);
    }

    /**
     * Duplique les drops avec la probabilite accordee par les ailes completes du
     * musee. Les metiers n'en accordent plus : leurs paliers paient en argent.
     *
     * <p>Les copies rejoignent la liste des drops au lieu d'apparaitre a part : la
     * tremie du tracteur et le sac d'un bot de farming, qui la lisent en dernier,
     * les ramassent avec le reste au lieu de les laisser par terre.
     */
    private static void maybeDoubleDrop(ServerPlayer player, BlockDropsEvent event,
                                        java.util.List<net.minecraft.world.entity.item.ItemEntity> originals) {
        double chance = fr.atraxterra.dohy.museum.MuseumApi.doubleDropBonus(player);
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) {
            return;
        }
        java.util.List<net.minecraft.world.entity.item.ItemEntity> copies = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.item.ItemEntity drop : originals) {
            ItemStack copy = drop.getItem().copy();
            if (!copy.isEmpty()) {
                copies.add(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), drop.getX(), drop.getY(), drop.getZ(), copy));
            }
        }
        event.getDrops().addAll(copies);
    }

    private static Map<String, JobProgression.Reward> table(
            Job job, Function<JobProgression.Sources, Map<String, JobProgression.Reward>> kind) {
        return JobProgression.config().def(job).sources().map(kind).orElse(Map.of());
    }

    /**
     * Cherche une valeur par identifiant, puis par tag.
     *
     * <p>Les tags sont resolus a l'usage et non au chargement : les registres
     * de tags ne sont pas forcement prets au moment ou le datapack est lu.
     */
    private static JobProgression.Reward lookup(Map<String, JobProgression.Reward> table, Identifier id,
                                                BlockState state) {
        JobProgression.Reward direct = table.get(id.toString());
        if (direct != null) {
            return direct;
        }
        if (state == null) {
            return null;
        }
        for (Map.Entry<String, JobProgression.Reward> candidate : table.entrySet()) {
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
        return null;
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
