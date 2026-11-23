package fr.atraxterra.dohy.faction.tech;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Le laboratoire de recherche : une paillasse tournee vers celui qui la pose, une cuve de verre,
 * un ecran et des fioles ; l'ecran et la cuve s'allument quand il produit.
 *
 * <p>Il se pose dans un claim de sa nation, dans la limite que fixe le Gouvernement ; le createur
 * passe outre le claim et la limite. Un clic droit avec du combustible le fait tourner sans
 * electricite, un clic droit a main nue ouvre son ecran.
 */
public class ResearchLabBlock extends BaseEntityBlock {

    public static final MapCodec<ResearchLabBlock> CODEC = simpleCodec(ResearchLabBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** La paillasse pleine largeur, et ce qui se tient dessus (cuve, ecran, fioles), au centre. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 10, 16), Block.box(2, 10, 2, 14, 16, 14));

    public ResearchLabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * La pose : refusee hors des claims de sa nation, ou au-dela des laboratoires que permet le
     * Gouvernement. Le refus se decide sur le serveur ; le client, qui ne connait pas la nation,
     * se fait corriger.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getPlayer() instanceof ServerPlayer player
                && context.getLevel() instanceof ServerLevel level) {
            String refusal = refusal(player, level, context.getClickedPos());
            if (refusal != null) {
                player.sendSystemMessage(FactionApi.prefix().append(Component.literal(refusal)
                        .withStyle(ChatFormatting.RED)));
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Pourquoi ce joueur ne peut pas poser un laboratoire ici, ou {@code null}. */
    public static String refusal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return "Il te faut une nation pour poser un laboratoire de recherche (/f create).";
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !FactionApi.claimAt(level, pos).ownedBy(player.getUUID())) {
            return "Le laboratoire se pose dans un de tes claims.";
        }
        for (String key : List.copyOf(data.labs())) {
            if (ResearchLabBlockEntity.gone(level.getServer(), key)) {
                data.removeLab(key);
            }
        }
        int cap = TechPerks.of(data).labs();
        int held = data.labs().size();
        if (!creative && held >= cap) {
            return "Ta nation a deja " + held + (held > 1 ? " laboratoires" : " laboratoire") + " : "
                    + (cap < 2 ? "le Gouvernement III en permet un deuxieme."
                    : cap < 3 ? "le Gouvernement V en permet un troisieme." : "c'est le maximum.");
        }
        return null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchLabBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof ResearchLabBlockEntity lab) {
                lab.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof ResearchLabBlockEntity lab) {
            lab.setOwner(player);
            FactionData data = FactionApi.data(player);
            if (data.exists()) {
                data.addLab(lab.key());
                int cap = TechPerks.of(data).labs();
                player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Laboratoire de recherche pose ("
                        + Math.min(data.labs().size(), cap) + " / " + cap + "). Alimente-le en energie, ou donne-lui"
                        + " du combustible d'un clic droit.").withStyle(ChatFormatting.GREEN)));
            }
        }
    }

    /** Un clic droit avec du combustible : il le brule, a mi-regime, faute d'electricite. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !level.fuelValues().isFuel(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server) || !(server.getBlockEntity(pos) instanceof ResearchLabBlockEntity lab)) {
            return InteractionResult.SUCCESS;
        }
        if (lab.fuelFull()) {
            player.sendSystemMessage(Component.literal("Le laboratoire a deja tout le combustible qu'il peut garder.")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        int each = stack.getBurnTime(RecipeType.SMELTING, server.fuelValues());
        int count = player.isShiftKeyDown() ? stack.getCount() : 1;
        int burned = 0;
        for (int i = 0; i < count && !lab.fuelFull(); i++) {
            lab.addFuel(each);
            burned++;
        }
        if (burned > 0 && !player.getAbilities().instabuild) {
            boolean bucket = stack.is(Items.LAVA_BUCKET);
            stack.shrink(burned);
            if (bucket) {
                ItemStack empty = new ItemStack(Items.BUCKET);
                if (!player.getInventory().add(empty)) {
                    player.drop(empty, false);
                }
            }
        }
        server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4f, 1.3f);
        player.sendSystemMessage(Component.literal("Combustible : " + burned + " de plus, " + lab.fuel() / 20
                + " s de combustion en reserve.").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ResearchLabBlockEntity) {
            ResearchLabMenu.open(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
