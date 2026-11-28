package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le scanner de Genis ({@code BlockBotanistScanner}) : une machine du laboratoire de JobsReborn.
 *
 * <p>Son modele (celui du jar, dessine par {@code GeneScannerRenderer}) fait plus de quatre blocs de
 * haut dans le laboratoire ; pose dans le monde, il est reduit a deux blocs ({@link TallMachineBlock}) :
 * le scanner occupe le bloc pose et celui du dessus, tourne vers le joueur. Toute la machine (cases,
 * scan, maitre) est dans la moitie basse ({@link GeneScannerBlockEntity}) ; un clic sur la moitie
 * haute ouvre la meme interface.
 */
public class GeneScannerBlock extends TallMachineBlock {

    public static final MapCodec<GeneScannerBlock> CODEC = simpleCodec(GeneScannerBlock::new);

    public GeneScannerBlock(Properties properties) {
        // Le corps du scanner, puis sa tete jusqu'a 14 pixels dans le bloc du dessus.
        super(properties, Block.box(1.0d, 0.0d, 1.0d, 15.0d, 16.0d, 15.0d), Block.box(2.0d, 0.0d, 2.0d, 14.0d, 14.0d,
                14.0d));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntity machine(BlockPos pos, BlockState state) {
        return new GeneScannerBlockEntity(pos, state);
    }

    @Override
    protected void placed(ServerPlayer player, BlockEntity machine) {
        if (machine instanceof GeneScannerBlockEntity scanner) {
            scanner.setOwner(player);
        }
    }
}
