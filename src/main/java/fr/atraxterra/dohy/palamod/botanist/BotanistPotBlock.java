package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Le pot du botaniste ({@code BlockBotanistPot} de JobsReborn) : on y plante une
 * graine de mutation, puis on repond a ce que la plante reclame jusqu'a ses
 * floraisons. Seul celui qui l'a pose s'en sert ; les autres ne peuvent que le
 * casser.
 *
 * <p>Dans JobsReborn, les pots sont ceux du laboratoire Genis ; le laboratoire
 * (centre de controle, modules) viendra plus tard, et en attendant un pot se
 * fabrique et se pose ou l'on veut. Le climat du pot est celui de son biome.
 *
 * <p>Le bloc n'a pas de modele propre : le pot et sa plante sont ceux de
 * JobsReborn, dessines par {@code BotanistPotRenderer}. Tout l'etat (maitre,
 * plante, stade, chronos, besoin) est dans {@link BotanistPotBlockEntity}.
 */
public class BotanistPotBlock extends BaseEntityBlock {

    public static final MapCodec<BotanistPotBlock> CODEC = simpleCodec(BotanistPotBlock::new);
    /** Le pot de JobsReborn : une panse de 12 sur 10, un rebord de 16 de large jusqu'a 12 pixels. */
    private static final VoxelShape SHAPE = Block.box(0.0d, 0.0d, 0.0d, 16.0d, 12.0d, 16.0d);

    public BotanistPotBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BotanistPotBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            // Un passage par seconde, decale d'un pot a l'autre.
            if (entity instanceof BotanistPotBlockEntity pot
                    && Math.floorMod(tickLevel.getGameTime() + pos.asLong(), BotanistPotBlockEntity.PASS) == 0) {
                pot.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof BotanistPotBlockEntity pot) {
            pot.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BotanistPotBlockEntity pot)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // Une graine tenue sur un pot occupe ne gene pas la recolte : elle ne se plante que dans un pot vide.
        if (stack.getItem() instanceof MutationSeedItem seed && pot.stage() == PotStage.EMPTY) {
            if (player instanceof ServerPlayer server) {
                pot.plant(server, stack, seed.mutation());
            }
            return InteractionResult.SUCCESS;
        }
        boolean tool = stack.getItem() instanceof WateringCanItem || stack.getItem() instanceof GuitarItem;
        boolean wanted = pot.need() == Need.ITEM && stack.is(Mutation.item(pot.needItem()));
        if (!tool && !wanted) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (player instanceof ServerPlayer server) {
            pot.answer(server, stack, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        boolean emptyHand = player.getMainHandItem().isEmpty();
        PotStage stage = level.getBlockEntity(pos) instanceof BotanistPotBlockEntity found ? found.stage()
                : PotStage.EMPTY;
        // La recolte et les graines se prennent quel que soit l'objet tenu ; le reste veut la main vide,
        // pour qu'un bloc tenu se pose a cote du pot comme ailleurs.
        boolean handled = emptyHand || stage == PotStage.BLOOMING || stage == PotStage.WILTED;
        if (!handled) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof BotanistPotBlockEntity pot) {
            pot.use(server, emptyHand);
        }
        return InteractionResult.SUCCESS;
    }

    /** Le pot casse rend le pot (table de butin), et la graine si elle venait d'etre plantee. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BotanistPotBlockEntity pot) {
            ItemStack seed = pot.brokenSeed();
            if (!seed.isEmpty()) {
                drops.add(seed);
            }
        }
        return drops;
    }
}
