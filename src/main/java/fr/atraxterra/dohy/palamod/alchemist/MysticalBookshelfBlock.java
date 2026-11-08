package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bibliotheque mystique ({@code BlockAlchemist.getEnchantPowerBonus}) : elle
 * compte pour quatre bibliotheques autour d'une table d'enchantement. Au-dela
 * de 60 de puissance — seize bibliotheques mystiques — la table ouvre les
 * enchantements de l'Alchimiste ({@link MysticEnchantMenu}).
 */
public class MysticalBookshelfBlock extends Block {

    public static final MapCodec<MysticalBookshelfBlock> CODEC = simpleCodec(MysticalBookshelfBlock::new);

    /** {@code return 4.0F} : la puissance d'une bibliotheque vanilla vaut 1. */
    public static final float POWER = 4.0f;

    public MysticalBookshelfBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public float getEnchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
        return POWER;
    }
}
