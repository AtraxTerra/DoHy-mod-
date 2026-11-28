package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le centre de controle ({@code BlockBotanistControl}) : le pupitre du laboratoire de JobsReborn,
 * pose dans le monde sur deux blocs de large et deux de haut ({@link WideMachineBlock}). Il ouvre le
 * laboratoire de son maitre ({@link LabControlMenu}).
 */
public class LabControlBlock extends WideMachineBlock {

    public static final MapCodec<LabControlBlock> CODEC = simpleCodec(LabControlBlock::new);

    public LabControlBlock(Properties properties) {
        super(properties, Block.box(0.0d, 0.0d, 1.0d, 16.0d, 16.0d, 15.0d), Block.box(0.0d, 0.0d, 1.0d, 16.0d, 13.0d,
                15.0d));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntity machine(BlockPos pos, BlockState state) {
        return new LabControlBlockEntity(pos, state);
    }

    @Override
    protected void placed(ServerPlayer player, BlockEntity machine) {
        if (machine instanceof LabControlBlockEntity control) {
            control.setOwner(player);
        }
    }

    @Override
    protected void open(ServerPlayer player, BlockEntity machine) {
        if (machine instanceof LabControlBlockEntity control && control.canOpen(player)) {
            LabControlMenu.INSTANCE.openAt(player, control.getBlockPos());
        }
    }
}
