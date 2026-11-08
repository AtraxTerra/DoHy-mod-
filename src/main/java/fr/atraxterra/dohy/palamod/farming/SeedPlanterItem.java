package fr.atraxterra.dohy.palamod.farming;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un seedplanter : il plante un carre de graines autour du joueur.
 *
 * <p><b>Les graines vont dans la case juste a droite du planteur</b>, dans la
 * barre d'action. C'est le mecanisme d'origine
 * ({@code mainInventory[currentItem + 1]}), et il n'est pas arbitraire : il
 * laisse choisir la plante sans interface, et il rend le geste reproductible —
 * le joueur prepare sa barre une fois, puis clique. Le reproduire demande de
 * lire la case selectionnee, d'ou {@code getSelectedSlot()} (releve a la
 * sonde ; {@code Inventory.selected} n'est plus accessible).
 *
 * <p>Ce que fait un clic, releve sur {@code SeedPlanterBase.func_77659_a} :
 *
 * <ul>
 *   <li>un carre de cote {@code (r / 2) * 2} au <b>niveau des pieds</b> du
 *       joueur, et non autour de son regard ;
 *   <li>la case doit etre vide, et le bloc en dessous doit etre du sol laboure
 *       <b>ou de la terre fertilisee</b> ;
 *   <li>une graine consommee par case plantee ;
 *   <li>aucun labour : le planteur plante, il ne prepare pas le champ.
 * </ul>
 *
 * <p>La boucle d'origine va de {@code -r/2} inclus a {@code +r/2} exclu : le
 * carre est donc decale d'un demi-bloc et n'est pas centre sur le joueur. C'est
 * conserve tel quel — un joueur habitue au planteur de Paladium verra le meme
 * carre au meme endroit.
 *
 * <p>Le verrou de metier n'est pas ici : {@code data/jobs.json} le porte depuis
 * le chapitre 1 et {@code JobGateEvents} annule le clic droit avant que cette
 * methode soit atteinte. Ecrire un second test ici donnerait deux verites a
 * tenir d'accord.
 */
public class SeedPlanterItem extends Item {

    /** Le {@code r} d'origine. Le carre plante fait {@code (r / 2) * 2} de cote. */
    private final int radius;

    public SeedPlanterItem(Properties properties, int radius) {
        super(properties);
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }

    /** Cote du carre plante, en blocs. */
    public int side() {
        return (radius / 2) * 2;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // Le client annonce un succes pour que le bras s'anime ; tout le
            // travail est fait par le serveur, seul a pouvoir poser des blocs.
            return InteractionResult.SUCCESS;
        }

        Inventory inventory = player.getInventory();
        int seedSlot = inventory.getSelectedSlot() + 1;
        if (seedSlot >= Inventory.SELECTION_SIZE) {
            return refuse(player, "Place les graines dans la case juste a droite du planteur.");
        }

        ItemStack seeds = inventory.getItem(seedSlot);
        BlockState plant = plantOf(seeds);
        if (plant == null) {
            return refuse(player, "La case a droite du planteur doit contenir des graines.");
        }

        int planted = plant(level, player, seeds, plant);
        if (planted == 0) {
            return refuse(player, "Rien a planter ici : il faut du sol laboure ou de la terre fertilisee, et de la place au-dessus.");
        }

        level.playSound(null, player.blockPosition(), SoundEvents.CROP_PLANTED,
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * La plante que posent ces graines, ou {@code null}.
     *
     * <p>Toutes les graines de ce mod comme de vanilla sont des
     * {@link BlockItem} : {@code eggplant_seeds} porte {@code eggplant_crop}
     * comme {@code wheat_seeds} porte {@code wheat}. Il n'y a donc pas de table
     * de correspondance a tenir, et un plant ajoute plus tard marche sans que
     * cette classe soit touchee.
     */
    private static BlockState plantOf(ItemStack seeds) {
        if (seeds.isEmpty() || !(seeds.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        // Une culture ne tient pas en place toute seule et pousse au hasard :
        // ces deux proprietes suffisent a ecarter les blocs de construction
        // qu'un BlockItem porterait aussi, sans tenir une liste de plantes.
        return state.isRandomlyTicking() ? state : null;
    }

    /** Plante le carre, et rend le nombre de cases plantees. */
    private int plant(Level level, Player player, ItemStack seeds, BlockState plant) {
        int half = radius / 2;
        BlockPos feet = player.blockPosition();
        int planted = 0;

        for (int dx = -half; dx < half; dx++) {
            for (int dz = -half; dz < half; dz++) {
                if (seeds.getCount() <= 0) {
                    return planted;
                }
                BlockPos target = feet.offset(dx, 0, dz);
                if (!level.getBlockState(target).isAir()) {
                    continue;
                }
                if (!isPlantable(level.getBlockState(target.below()))) {
                    continue;
                }
                level.setBlockAndUpdate(target, plant);
                seeds.shrink(1);
                planted++;
            }
        }
        return planted;
    }

    /**
     * Les sols que le planteur accepte.
     *
     * <p>Le sol laboure vanilla et la terre fertilisee du chapitre 3, exactement
     * comme l'original ({@code BlockFarmland} ou {@code BlockConnectedDirt}).
     * La terre fertilisee non labouree en fait partie : c'est elle que le totem
     * surveille, et les deux machines du chapitre travaillent donc sur le meme
     * sol.
     */
    private static boolean isPlantable(BlockState soil) {
        return soil.is(Blocks.FARMLAND)
                || soil.is(PalaBlocks.FERTILIZED_FARMLAND.get())
                || soil.is(PalaBlocks.FERTILIZED_DIRT.get());
    }

    /**
     * Dit pourquoi rien ne s'est passe.
     *
     * <p>Sans ce message, un planteur qui refuse est indiscernable d'un
     * planteur casse — et le mecanisme de la case a droite ne se devine pas.
     * {@code CONSUME} plutot que {@code FAIL} pour que le bras s'anime : le
     * clic a bien ete pris en compte, il n'a simplement rien trouve a faire.
     */
    private static InteractionResult refuse(Player player, String reason) {
        player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.GRAY));
        return InteractionResult.CONSUME;
    }
}
