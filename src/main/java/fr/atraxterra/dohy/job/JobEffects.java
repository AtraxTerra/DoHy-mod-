package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Les effets de perks qui ne sont pas des attributs.
 *
 * <p>Deux types : la recolte accrue (Fermier sur les cultures, Alchimiste sur
 * la verrue du Nether) et le butin double (Chasseur). Les quatre autres —
 * abattage, trouvaille de peche, chute amortie, remise d'enclume — sont partis
 * avec le bucheronnage, la peche, la construction et la forge quand les metiers
 * ont remplace les skills.
 *
 * <h2>La regle qui ne doit pas sauter</h2>
 *
 * <p>Chaque effet qui multiplie des drops passe par le suivi des blocs poses :
 * sans ca, poser puis casser le meme bloc deviendrait une machine a
 * ressources — c'est le fondement anti-exploit du mod, et un perk n'a pas le
 * droit d'y faire une breche.
 */
@EventBusSubscriber
public final class JobEffects {

    /**
     * Les graines qui replantent : celles des villageois et celles de PalaMod.
     * Le tag vanilla seul laissait copier les graines d'aubergine, de cerfeuil et
     * de kiwano, et un champ de PalaMod grossissait tout seul.
     */
    private static final TagKey<Item> REPLANT_SEEDS = TagKey.create(
            BuiltInRegistries.ITEM.key(),
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "replant_seeds"));

    private JobEffects() {
    }

    /**
     * Recolte accrue.
     *
     * <p>La verrue du Nether n'est pas dans {@code #minecraft:crops} : elle est a
     * l'Alchimiste, dont les perks de recolte ne s'appliquaient jusqu'ici a
     * rien. Elle se replante avec son propre fruit, donc seule une verrue mure
     * compte — sinon planter puis casser aussitot la dupliquerait.
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
            harvestBonus(player, event, Job.FARMER);
        } else if (state.is(Blocks.NETHER_WART)
                && state.getValue(BlockStateProperties.AGE_3) >= 3) {
            harvestBonus(player, event, Job.ALCHEMIST);
        }
    }

    /**
     * Recolte accrue : un pourcentage de drops en plus sur une culture mature.
     *
     * <p>La partie entiere est accordee a coup sur, la fraction au tirage. Un
     * bonus de 25 % donne donc un drop supplementaire une fois sur quatre et non
     * « rien, parce que 0,25 arrondi a zero ».
     */
    private static void harvestBonus(ServerPlayer player, BlockDropsEvent event, Job job) {
        double bonus = JobPerks.value(player, job, "harvest_bonus");
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
                if (copy.isEmpty() || copy.is(REPLANT_SEEDS)) {
                    continue;
                }
                player.level().addFreshEntity(new ItemEntity(
                        player.level(), drop.getX(), drop.getY(), drop.getZ(), copy));
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
        double chance = JobPerks.value(player, Job.HUNTER, "loot_bonus");
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
