package fr.atraxterra.dohy.collection;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Ce qui fait progresser une collection.
 *
 * <h2>Pourquoi la source et non le ramassage</h2>
 *
 * <p>Compter au ramassage serait plus simple et couvrirait tous les cas, mais
 * ouvrirait un exploit immediat : jeter une pile puis la ramasser, en boucle.
 * On compte donc <b>a la source</b> — casse de bloc, mort de mob, peche — ce qui
 * exclut de surcroit les achats au shop par construction, sans avoir a marquer
 * les items achetes.
 *
 * <h2>L'exemption de plantation</h2>
 *
 * <p>La regle « un bloc pose ne rapporte rien » protege du cycle poser/casser,
 * mais appliquee telle quelle elle interdirait l'agriculture : une culture doit
 * etre plantee pour etre recoltee. Le tag {@code #dohy:placement_exempt} liste
 * les blocs qui murissent sur place et sont donc exemptes. L'exemption est sans
 * danger : on ne duplique pas du blé en le plantant, on investit des graines et
 * du temps.
 *
 * <p>La canne a sucre, le bambou et le cactus n'y figurent pas volontairement :
 * seule la partie poussee au-dessus du bloc plante compte, ce qui est exactement
 * le rendement.
 *
 * <h2>Limite connue</h2>
 *
 * <p>Les drops de mob ne comptent que si la mort est attribuable a un joueur.
 * Une ferme purement passive — degats de chute, suffocation — ne fera donc pas
 * progresser les collections. L'alternative serait d'attribuer au joueur le plus
 * proche, ce qui est faux par principe et se trompe des qu'une mort n'a rien a
 * voir avec lui.
 */
@EventBusSubscriber
public final class CollectionEvents {

    /** Blocs exemptes de la regle sur les blocs poses. */
    public static final TagKey<Block> PLACEMENT_EXEMPT = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "placement_exempt"));

    private CollectionEvents() {
    }

    /**
     * Casse d'un bloc par un joueur.
     *
     * <p>Passe par {@link PlacedBlocks#wasPlayerPlaced}, dont la reponse est
     * memoisee par evenement : d'autres systemes la demanderont pour la meme
     * casse, et consommer la marque priverait le suivant de la bonne reponse.
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }

        boolean exempt = event.getState().is(PLACEMENT_EXEMPT);
        boolean playerPlaced = PlacedBlocks.wasPlayerPlaced(event);
        if (playerPlaced && !exempt) {
            return;
        }

        for (ItemEntity drop : event.getDrops()) {
            record(player, drop.getItem());
        }
    }

    /** Mort d'un mob attribuable a un joueur. */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        ServerPlayer player = attribute(event);
        if (player == null) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            record(player, drop.getItem());
        }
    }

    /** Peche. */
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (ItemStack stack : event.getDrops()) {
            record(player, stack);
        }
    }

    private static ServerPlayer attribute(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer direct) {
            return direct;
        }
        // Fleche, chien, ou tout degat indirect : la victime retient le dernier
        // joueur qui l'a blessee.
        if (event.getEntity().getLastHurtByMob() instanceof ServerPlayer indirect) {
            return indirect;
        }
        return null;
    }

    private static void record(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CollectionApi.record(player,
                BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
    }
}
