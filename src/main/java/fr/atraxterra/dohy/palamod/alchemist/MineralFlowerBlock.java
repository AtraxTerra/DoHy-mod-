package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * La fleur minerale ({@code BaseBlockFlower.onBlockActivated}, propre a
 * {@code FLOWER_MINERAL}) : plantee, elle se change en lingots a la poudre
 * d'os.
 *
 * <p>Les six premieres poudres la font « pousser » sans que rien ne se voie
 * (sa metadonnee, de 0 a 6) ; la septieme la consomme et donne au joueur, au
 * hasard, 2 a 4 lingots de titane, 3 a 5 d'amethyste ou 1 a 2 de paladium.
 * L'original les lachait aux pieds du joueur, ramassables tout de suite : ils
 * vont ici dans son sac, et tombent a la place de la fleur s'il est plein.
 *
 * <p><b>Ecarts</b> : les particules de la poudre d'os a chaque coup (l'original
 * n'en montrait aucune, on ne savait pas ou on en etait), et une fleur qui se
 * brise a la derniere ; en creatif, la poudre n'est pas consommee.
 */
public class MineralFlowerBlock extends FlowerBlock {

    public static final MapCodec<MineralFlowerBlock> CODEC = simpleCodec(MineralFlowerBlock::new);

    /** Les poudres deja recues ; a la septieme, la fleur donne ses lingots. */
    public static final int RIPE = 6;
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, RIPE);

    /** L'effet de niveau des particules de la poudre d'os, comme le sol fertile. */
    private static final int BONEMEAL_PARTICLES = 2005;

    public MineralFlowerBlock(Properties properties) {
        super(new SuspiciousStewEffects(List.of()), properties);
        registerDefaultState(stateDefinition.any().setValue(GROWTH, 0));
    }

    @Override
    public MapCodec<MineralFlowerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GROWTH);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.BONE_MEAL)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        stack.consume(1, player);
        int growth = state.getValue(GROWTH);
        if (growth < RIPE) {
            server.setBlock(pos, state.setValue(GROWTH, growth + 1), Block.UPDATE_CLIENTS);
            server.levelEvent(BONEMEAL_PARTICLES, pos, 15);
            return InteractionResult.SUCCESS;
        }
        server.destroyBlock(pos, false, player);
        ItemStack ingots = harvest(server.getRandom());
        player.getInventory().add(ingots);
        if (!ingots.isEmpty()) {
            popResource(server, pos, ingots);
        }
        return InteractionResult.SUCCESS;
    }

    /** Un tiers chacun : titane (2 a 4), paladium (1 a 2), amethyste (3 a 5). */
    static ItemStack harvest(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> new ItemStack(PalaItems.TITANE_INGOT.get(), 2 + random.nextInt(3));
            case 1 -> new ItemStack(PalaItems.PALADIUM_INGOT.get(), 1 + random.nextInt(2));
            default -> new ItemStack(PalaItems.AMETHYST_INGOT.get(), 3 + random.nextInt(3));
        };
    }
}
