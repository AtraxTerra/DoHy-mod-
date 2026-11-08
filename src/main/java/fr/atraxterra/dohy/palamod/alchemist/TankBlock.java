package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tank a seve ({@code BlockTank}). Un seul liquide a la fois, jusqu'a sa
 * contenance ; l'objet garde son contenu quand on le casse.
 *
 * <ul>
 *   <li><b>clic droit avec des fioles de seve</b> : elles s'y vident, toute la
 *       pile, tant que ca tient (une fiole entiere ou rien) ; les fioles
 *       reviennent vides ;</li>
 *   <li><b>accroupi, clic droit avec des fioles</b> : chaque fiole de la pile
 *       gagne un sixieme — six clics la remplissent. C'etait le clic gauche dans
 *       PalaMod ; le clic gauche casse le bloc ici ;</li>
 *   <li><b>main vide</b> : le contenu, dans la barre d'action (l'original
 *       l'ecrivait au-dessus du tank).</li>
 * </ul>
 *
 * <p><b>Ecart</b> : l'original prenait aussi l'eau, la lave et l'eau sulfurique,
 * qui ne servaient qu'au pistolet a tank, non porte. Ce tank ne prend que la
 * seve ; et il ne remplit plus une fiole d'une seve avec celle d'une autre.
 */
public class TankBlock extends BaseEntityBlock {

    public static final MapCodec<TankBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TankTier.CODEC.fieldOf("tier").forGetter(TankBlock::tier),
            propertiesCodec()
    ).apply(instance, TankBlock::new));

    public static final EnumProperty<TankContent> CONTENT = EnumProperty.create("sap", TankContent.class);
    /** Hauteur du liquide en seiziemes, pour le modele. 0 si vide. */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 16);

    private final TankTier tier;

    public TankBlock(TankTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(CONTENT, TankContent.EMPTY).setValue(FILL, 0));
    }

    public TankTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONTENT, FILL);
    }

    /** L'etat qui montre ce contenu. */
    public BlockState withContent(BlockState state, TankSap sap) {
        if (sap == null) {
            return state.setValue(CONTENT, TankContent.EMPTY).setValue(FILL, 0);
        }
        int fill = Math.max(1, Math.min(16, (int) Math.ceil(sap.amount() * 16.0d / tier.capacity())));
        return state.setValue(CONTENT, TankContent.of(sap.type())).setValue(FILL, fill);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withContent(defaultBlockState(), context.getItemInHand().get(PalaAlchemist.TANK_SAP.get()));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PalaAlchemist.FLASK.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TankBlockEntity tank && player instanceof ServerPlayer server) {
            if (player.isShiftKeyDown()) {
                tank.fillFlasks(server, hand, stack);
            } else {
                tank.pourFlasks(server, hand, stack);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TankBlockEntity tank
                && player instanceof ServerPlayer server) {
            AlchemistXp.actionBar(server, tank.describe());
        }
        return InteractionResult.SUCCESS;
    }
}
