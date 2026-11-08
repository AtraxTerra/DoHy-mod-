package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le bloc du totem de fertilite.
 *
 * <p>Il herite de {@link MachineBlock} pour son ticker, mais pas pour la
 * restitution du contenu : le socle laisse tomber l'inventaire d'un
 * {@link MachineBlockEntity}, et le totem n'en a pas — il porte une seule pile
 * de carburant. C'est elle qu'on rend ici, parce que des lingots de paladium
 * perdus en deplacant un totem seraient une perte seche (principe 4 de
 * {@code DESIGN.md}).
 */
public class TotemBlock extends MachineBlock {

    public static final MapCodec<TotemBlock> CODEC = simpleCodec(TotemBlock::new);

    public TotemBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TotemBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof TotemBlockEntity totem
                && !totem.fuel().isEmpty()) {
            Block.popResource(level, pos, totem.fuel().copy());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
