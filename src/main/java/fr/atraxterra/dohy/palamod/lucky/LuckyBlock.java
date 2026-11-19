package fr.atraxterra.dohy.palamod.lucky;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Un lucky block pose ({@code BlockLuckyBlock}).
 *
 * <p>Clic droit : l'ecran d'ouverture ({@code UILuckyBlock}). C'est en appuyant
 * sur « Ouvrir » que le serveur tire l'evenement ; le bloc disparait et
 * l'evenement se produit a la fermeture de l'ecran. Casse a la main, il se rend
 * tel quel, avec l'evenement deja tire s'il y en a un.
 */
public class LuckyBlock extends BaseEntityBlock {

    private final LuckyType type;

    public LuckyBlock(LuckyType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public LuckyType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new LuckyBlock(type, properties));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LuckyBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            LuckySession.open(server, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** {@code randomDisplayTick} : de la poussiere de la couleur du bloc, sur les faces. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(type.color() & 0xFFFFFF, 1.0f);
        for (int index = 0; index < 2; index++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            switch (random.nextInt(6)) {
                case 0 -> x = pos.getX() - 0.06d;
                case 1 -> x = pos.getX() + 1.06d;
                case 2 -> y = pos.getY() - 0.06d;
                case 3 -> y = pos.getY() + 1.06d;
                case 4 -> z = pos.getZ() - 0.06d;
                default -> z = pos.getZ() + 1.06d;
            }
            level.addParticle(dust, x, y, z, 0.0d, 0.0d, 0.0d);
        }
    }
}
