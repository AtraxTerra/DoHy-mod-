package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Voidstone minage : avale la pierre de l'inventaire et la garde en reserve.
 *
 * <p>Releve dans {@code ItemVoidStoneMinage.check} et
 * {@code PlayerHandler.playerTickEvent} : dans la derniere case de la barre
 * d'action, hors creatif, chaque tick, chaque pile de son filtre est retiree
 * <b>entiere</b>. La pierre et la cobblestone s'ajoutent au compteur, une pour
 * une ; la terre, le gravier, l'herbe, la cobblestone moussue, le granite, la
 * diorite et l'andesite (et leurs formes polies) sont detruits sans etre
 * comptes. L'aspiration est dans {@link MinerPocketEvents}.
 *
 * <p>La reserve sert deux fois :
 * <ul>
 *   <li>posee dans un cobblebreaker, elle en est l'entree : une pierre par
 *       cycle ({@code TileCobbleBreaker}, offsets 871-925) ;</li>
 *   <li>clic droit sur un bloc, elle se pose en void stone au-dessus
 *       ({@code EventsManager.onMinerVoidStoneUse}), coeur du compresseur a
 *       cobblestone ({@link VoidStoneBlockEntity}).</li>
 * </ul>
 */
public class VoidstoneMinageItem extends Item {

    /** Compte dans la reserve. */
    static final Set<Item> COUNTED = Set.of(Items.STONE, Items.COBBLESTONE);

    /** Detruit sans etre compte : le reste du filtre de PRegister_Items. */
    static final Set<Item> DESTROYED = Set.of(
            Items.DIRT, Items.GRAVEL, Items.GRASS_BLOCK, Items.MOSSY_COBBLESTONE,
            Items.GRANITE, Items.POLISHED_GRANITE, Items.DIORITE, Items.POLISHED_DIORITE,
            Items.ANDESITE, Items.POLISHED_ANDESITE);

    public VoidstoneMinageItem(Properties properties) {
        super(properties);
    }

    public static int stone(ItemStack stack) {
        return stack.getOrDefault(PalaMiner.STORED_STONE.get(), 0);
    }

    public static void setStone(ItemStack stack, int amount) {
        stack.set(PalaMiner.STORED_STONE.get(), Math.max(0, amount));
    }

    /** Ajoute sans deborder : un int de pierre, c'est deja deux milliards de blocs. */
    public static void addStone(ItemStack stack, int amount) {
        long next = (long) stone(stack) + amount;
        setStone(stack, (int) Math.min(Integer.MAX_VALUE, next));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        // « Contient N pierres », mot pour mot.
        tooltip.accept(Component.literal("Contient " + stone(stack) + " pierres")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Pose la void stone sur le bloc vise, avec la reserve de l'objet.
     *
     * <p>Comme l'original, la pose se fait toujours au-dessus du bloc clique,
     * quelle que soit la face, et seulement si la place est libre.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos target = context.getClickedPos().above();
        if (!context.getLevel().getBlockState(target).isAir()) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockState state = PalaMiner.VOID_STONE_BLOCK.get().defaultBlockState();

        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
        if (!level.setBlock(target, state, Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }
        if (EventHooks.onBlockPlace(player, snapshot, net.minecraft.core.Direction.UP)) {
            snapshot.restore();
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(target) instanceof VoidStoneBlockEntity voidStone) {
            voidStone.setStone(stone(stack));
        }
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
