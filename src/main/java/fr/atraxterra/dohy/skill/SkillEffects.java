package fr.atraxterra.dohy.skill;

import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les effets de perks qui ne sont pas des attributs.
 *
 * <h2>Pourquoi cette classe existe</h2>
 *
 * <p>Trente-trois perks sur cent-soixante etaient <b>declares et inertes</b> :
 * ils apparaissaient dans {@code /skills}, le joueur les voyait se debloquer, et
 * ils ne faisaient rien. Un skill entier etait concerne — monter {@code
 * smithing} jusqu'au niveau 50 ne changeait rien en jeu hormis l'argent verse.
 *
 * <p>Six types sont implementes ici. Les six autres ne sont pas accessibles
 * proprement en 26.2 — il n'y a ni evenement de durabilite, ni evenement
 * d'engrais a os, ni {@code BlockEvent.BreakEvent}, et la vitesse d'un four ne
 * se rattache a aucun joueur — et ils ont donc ete <b>remplaces dans le
 * datapack</b> par des perks equivalents qui, eux, fonctionnent. Mieux vaut un
 * bonus different mais reel qu'un bonus annonce et absent.
 *
 * <h2>La regle qui ne doit pas sauter</h2>
 *
 * <p>Chaque effet qui multiplie des drops passe par le suivi des blocs poses :
 * sans ca, poser puis casser le meme bloc deviendrait une machine a
 * ressources — c'est le fondement anti-exploit du mod, et un perk n'a pas le
 * droit d'y faire une breche.
 */
@EventBusSubscriber
public final class SkillEffects {

    /** Garde-fou de l'abattage : jamais plus de blocs que ca en une casse. */
    private static final int MAX_FELLED = 192;

    /**
     * Vrai pendant un abattage en cours.
     *
     * <p>Indispensable : {@code destroyBlock} refait passer chaque buche par
     * {@link #onBlockDrops}, donc sans ce verrou l'abattage se rappellerait pour
     * chaque buche qu'il vient de casser — une recursion qui explose
     * exponentiellement sur un grand arbre.
     *
     * <p>Les autres effets, eux, continuent de s'appliquer aux buches
     * abattues : l'XP de bucheronnage et le compteur de collection comptent bien
     * l'arbre entier, ce qui est l'effet voulu. Seule la propagation est
     * bloquee.
     */
    private static boolean felling;

    private SkillEffects() {
    }

    /**
     * Recolte et abattage.
     *
     * <p>Les deux partagent cet evenement parce que les deux reagissent a la
     * casse d'un bloc, et qu'il faut la meme verification de bloc pose avant
     * d'accorder quoi que ce soit.
     */
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

        if (state.is(BlockTags.CROPS)) {
            harvestBonus(player, event);
        }
        if (state.is(BlockTags.LOGS) && !felling) {
            felling = true;
            try {
                treeChop(player, event);
            } finally {
                felling = false;
            }
        }
    }

    /**
     * Recolte accrue : un pourcentage de drops en plus sur une culture mature.
     *
     * <p>La partie entiere est accordee a coup sur, la fraction au tirage. Un
     * bonus de 25 % donne donc un drop supplementaire une fois sur quatre et non
     * « rien, parce que 0,25 arrondi a zero ».
     */
    private static void harvestBonus(ServerPlayer player, BlockDropsEvent event) {
        double bonus = SkillPerks.value(player, Skill.FARMING, "harvest_bonus");
        if (bonus <= 0) {
            return;
        }
        int extra = (int) bonus;
        if (player.getRandom().nextDouble() < bonus - extra) {
            extra++;
        }
        if (extra <= 0) {
            return;
        }

        // La liste est copiee avant la boucle : on y ajoute des entites, et
        // iterer en ajoutant leverait une ConcurrentModificationException.
        List<ItemEntity> original = new ArrayList<>(event.getDrops());
        for (int round = 0; round < extra; round++) {
            for (ItemEntity drop : original) {
                ItemStack copy = drop.getItem().copy();
                // Les graines ne sont pas dupliquees : elles servent a replanter,
                // et les multiplier ferait grossir une ferme toute seule.
                if (copy.isEmpty() || copy.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
                    continue;
                }
                player.level().addFreshEntity(new ItemEntity(
                        player.level(), drop.getX(), drop.getY(), drop.getZ(), copy));
            }
        }
    }

    /**
     * Abattage : casser une buche fait tomber celles qui la prolongent.
     *
     * <p>Passe par {@code destroyBlock} avec le joueur comme casseur, donc les
     * drops, l'usure de la hache et le suivi de collection suivent le chemin
     * normal. Le montant du perk est le <b>rayon</b> horizontal : 1 abat un
     * tronc, 3 emporte les branches d'un chene noueux.
     *
     * <p>Il n'y a pas de {@code BlockEvent.BreakEvent} en 26.2 ; c'est la raison
     * pour laquelle l'effet est accroche a {@code BlockDropsEvent}, qui arrive
     * au meme moment.
     */
    private static void treeChop(ServerPlayer player, BlockDropsEvent event) {
        int radius = (int) SkillPerks.value(player, Skill.FORAGING, "tree_chop");
        if (radius <= 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.getMainHandItem().is(ItemTags.AXES)) {
            return;
        }

        BlockPos origin = event.getPos();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        int felled = 0;

        while (!queue.isEmpty() && felled < MAX_FELLED) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        // On ne descend jamais et on reste dans le rayon : sans
                        // ces deux bornes, un plancher de bois pose a cote de
                        // l'arbre partirait avec lui.
                        if (next.getY() < origin.getY()
                                || Math.abs(next.getX() - origin.getX()) > radius
                                || Math.abs(next.getZ() - origin.getZ()) > radius) {
                            continue;
                        }
                        if (!seen.add(next)) {
                            continue;
                        }
                        if (!level.getBlockState(next).is(BlockTags.LOGS)) {
                            continue;
                        }
                        // Une buche posee par le joueur n'est pas abattue : elle
                        // fait partie d'une construction, pas d'un arbre.
                        if (PlacedBlocks.isPlayerPlaced(level, next)) {
                            continue;
                        }
                        queue.add(next);
                        level.destroyBlock(next, true, player);
                        felled++;
                        if (felled >= MAX_FELLED) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Butin accru : chance de doubler les drops d'une creature. */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        ServerPlayer player = killer(event);
        if (player == null) {
            return;
        }
        double chance = SkillPerks.value(player, Skill.COMBAT, "loot_bonus");
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) {
            return;
        }
        List<ItemEntity> original = new ArrayList<>(event.getDrops());
        for (ItemEntity drop : original) {
            ItemStack copy = drop.getItem().copy();
            if (!copy.isEmpty()) {
                player.level().addFreshEntity(new ItemEntity(
                        player.level(), drop.getX(), drop.getY(), drop.getZ(), copy));
            }
        }
    }

    /**
     * Peche : prise double et trouvaille.
     *
     * <p>Les deux sont tirees separement — l'une peut tomber sans l'autre, ce
     * qui rend la peche moins binaire.
     */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        double doubleChance = SkillPerks.doubleDropChance(player, Skill.FISHING);
        if (doubleChance > 0 && player.getRandom().nextDouble() < doubleChance) {
            for (ItemStack stack : new ArrayList<>(event.getDrops())) {
                giveOrDrop(player, stack.copy());
            }
        }

        double treasure = SkillPerks.value(player, Skill.FISHING, "treasure_chance");
        if (treasure > 0 && player.getRandom().nextDouble() < treasure) {
            giveOrDrop(player, treasure(player));
        }
    }

    /**
     * Une trouvaille de peche.
     *
     * <p>Table courte et ecrite en dur plutot qu'une table de butin : ce sont
     * des lots de consolation, pas du contenu a equilibrer, et une table de
     * butin pour six entrees serait un fichier de plus a maintenir.
     */
    private static ItemStack treasure(ServerPlayer player) {
        return switch (player.getRandom().nextInt(6)) {
            case 0 -> new ItemStack(Items.NAUTILUS_SHELL);
            case 1 -> new ItemStack(Items.INK_SAC, 3);
            case 2 -> new ItemStack(Items.PRISMARINE_SHARD, 2);
            case 3 -> new ItemStack(Items.LILY_PAD, 2);
            case 4 -> new ItemStack(Items.AMETHYST_SHARD);
            default -> new ItemStack(Items.LAPIS_LAZULI, 3);
        };
    }

    /** Degats de chute annules sous une certaine hauteur. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        double safe = SkillPerks.value(player, "no_fall_damage_below");
        if (safe > 0 && event.getDistance() <= safe) {
            event.setCanceled(true);
        }
    }

    /**
     * Cout d'enclume reduit.
     *
     * <p>Le cout ne descend jamais sous 1 : a zero, l'enclume devient gratuite
     * et le niveau d'experience cesse d'etre une ressource.
     */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        double reduction = SkillPerks.value(player, "anvil_cost_reduction");
        if (reduction <= 0 || event.getXpCost() <= 1) {
            return;
        }
        int reduced = (int) Math.round(event.getXpCost() * (1.0d - Math.min(0.9d, reduction)));
        event.setXpCost(Math.max(1, reduced));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static ServerPlayer killer(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer direct) {
            return direct;
        }
        if (event.getEntity().getLastHurtByMob() instanceof ServerPlayer indirect) {
            return indirect;
        }
        return null;
    }
}
