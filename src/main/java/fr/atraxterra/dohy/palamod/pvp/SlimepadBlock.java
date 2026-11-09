package fr.atraxterra.dohy.palamod.pvp;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le slimepad ({@code BaseBlockSlimePad}) : un petit trampoline qui projette
 * dans la direction ou regardait le joueur qui l'a pose — vers l'arriere s'il
 * etait accroupi.
 *
 * <p>Releve dans le code :
 * <ul>
 *   <li>huit directions, celles du regard a la pose ({@code yaw * 8 / 360 + 0,5},
 *       plus 4 accroupi) ;</li>
 *   <li>on le traverse (aucune boite de collision) ; tant qu'une entite est
 *       dedans, elle recoit 0,2 vers le haut et 0,2 sur chaque axe de sa
 *       direction, sa chute est annulee, et le slimepad fait son bruit ;</li>
 *   <li>alimente en redstone, il ne fait rien ;</li>
 *   <li>un objet au sol est en plus souleve d'un demi-bloc.</li>
 * </ul>
 *
 * <p>La poussee est appliquee la ou le mouvement de l'entite est calcule : le
 * client pour un joueur, le serveur pour le reste. L'original la faisait des
 * deux cotes, mais le serveur n'envoyait pas la vitesse du joueur — seule la
 * poussee du client comptait, comme ici.
 *
 * <p>Pas porte : la recharge de 10 secondes de la Warzone (un evenement du
 * serveur) et les couleurs au ciseau.
 */
public class SlimepadBlock extends Block {

    public static final MapCodec<SlimepadBlock> CODEC = simpleCodec(SlimepadBlock::new);
    /** Les huit directions, dans l'ordre de l'original : 0 au sud, puis dans le sens horaire. */
    public static final IntegerProperty DIRECTION = IntegerProperty.create("direction", 0, 7);
    /** {@code setBlockBounds(0.125, 0.0625, 0.125, 0.875, 0.625, 0.875)}. */
    private static final VoxelShape SHAPE = Block.box(2, 1, 2, 14, 10, 14);
    private static final double PUSH = 0.2d;

    public SlimepadBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(DIRECTION, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() : context.getRotation();
        boolean back = player != null && player.isShiftKeyDown();
        int direction = (Mth.floor(yaw * 8.0f / 360.0f + 0.5d) + (back ? 4 : 0)) & 7;
        return defaultBlockState().setValue(DIRECTION, direction);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean intersects) {
        if (entity.noPhysics || entity.isRemoved() || entity.isSpectator() || level.hasNeighborSignal(pos)) {
            return;
        }
        // Des deux cotes, comme l'original : c'est le serveur qui compte les
        // degats de chute d'un joueur.
        entity.resetFallDistance();
        if (!entity.isLocalInstanceAuthoritative()) {
            return;
        }
        double dx = 0.0d;
        double dz = 0.0d;
        switch (state.getValue(DIRECTION)) {
            case 0 -> dz = PUSH;
            case 1 -> { dz = PUSH; dx = -PUSH; }
            case 2 -> dx = -PUSH;
            case 3 -> { dx = -PUSH; dz = -PUSH; }
            case 4 -> dz = -PUSH;
            case 5 -> { dz = -PUSH; dx = PUSH; }
            case 6 -> dx = PUSH;
            default -> { dx = PUSH; dz = PUSH; }
        }
        if (entity instanceof ItemEntity) {
            entity.setPos(entity.getX(), entity.getY() + 0.5d, entity.getZ());
        }
        entity.push(dx, PUSH, dz);
        SoundType sound = state.getSoundType();
        level.playSound(entity instanceof Player player ? player : null, pos, sound.getStepSound(),
                SoundSource.BLOCKS, sound.getVolume() / 2.0f, sound.getPitch() * 0.65f);
    }
}
