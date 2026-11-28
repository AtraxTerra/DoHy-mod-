package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Le plant d'une graine minerale ({@code BlockBotanistMineralSeed}) : un buisson a
 * quatre stades, sans collision, qui se casse d'un coup et ne tient que sur de la
 * terre labouree ({@code canPlaceBlockOn} : {@code Blocks.farmland}).
 *
 * <p>Il ne pousse pas au hasard des ticks comme le ble : son stade suit le temps
 * ecoule ({@link MineralCropBlockEntity}). Ni la poudre d'os ni le totem de
 * fertilite ne l'accelerent, et le bot de farming ne le recolte pas (il ne prend
 * que les {@code CropBlock}) : une recolte se fait a la main.
 *
 * <p>A la casse ({@code harvestBlock}, puis {@code BotanistMineralSeedManager.getDrops}) :
 * avant maturite, la graine revient avec ses caracteres ; mure, la recolte tire
 * {@link MineralSeed#draws(int)} fois dans la liste de la graine, au niveau de
 * Fermier de celui qui casse. L'XP de Fermier vient de {@code job_progression.json},
 * sur le stade 3 seulement, comme pour toute culture.
 */
public class MineralCropBlock extends VegetationBlock implements EntityBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    public static final MapCodec<MineralCropBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            MineralSeed.CODEC.fieldOf("seed").forGetter(MineralCropBlock::seed)
    ).apply(instance, MineralCropBlock::new));

    /** Plus haut a chaque stade : 4, 7, 10 et 13 pixels. */
    private static final VoxelShape[] SHAPES = {
            Block.box(2.0d, 0.0d, 2.0d, 14.0d, 4.0d, 14.0d),
            Block.box(2.0d, 0.0d, 2.0d, 14.0d, 7.0d, 14.0d),
            Block.box(1.0d, 0.0d, 1.0d, 15.0d, 10.0d, 15.0d),
            Block.box(1.0d, 0.0d, 1.0d, 15.0d, 13.0d, 15.0d)
    };

    private final MineralSeed seed;

    public MineralCropBlock(Properties properties, MineralSeed seed) {
        super(properties);
        this.seed = seed;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    public MineralSeed seed() {
        return seed;
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineralCropBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            // Un passage par seconde, decale d'un plant a l'autre pour ne pas tous tomber sur le meme tick.
            if (entity instanceof MineralCropBlockEntity crop
                    && Math.floorMod(tickLevel.getGameTime() + pos.asLong(), MineralCropBlockEntity.TICK_INTERVAL) == 0) {
                crop.serverTick(tickLevel, pos, tickState);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MineralCropBlockEntity crop) {
            crop.plant(level);
        }
    }

    /**
     * Ce que rend le plant, d'apres ses caracteres : la graine avant maturite, la
     * recolte ensuite. Sans plant lisible (bloc pose a la commande, par exemple), la
     * graine par defaut.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        SeedTraits traits = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MineralCropBlockEntity crop
                ? crop.traits() : SeedTraits.DEFAULT;
        if (state.getValue(AGE) < MineralSeed.MATURE) {
            return List.of(MineralSeedItem.create(seedItem(), traits, 1));
        }
        int level = params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player
                ? JobApi.level(player, Job.FARMER) : 0;
        return seed.roll(level, params.getLevel().getRandom(), seedItem());
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        SeedTraits traits = includeData && level.getBlockEntity(pos) instanceof MineralCropBlockEntity crop
                ? crop.traits() : SeedTraits.DEFAULT;
        return MineralSeedItem.create(seedItem(), traits, 1);
    }

    /** Un plant mur scintille, comme chez Paladium ({@code BotanistMineralSeedClientManager.sparkle}). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) >= MineralSeed.MATURE && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.2d + random.nextDouble() * 0.6d,
                    pos.getY() + 0.3d + random.nextDouble() * 0.5d,
                    pos.getZ() + 0.2d + random.nextDouble() * 0.6d,
                    0.0d, 0.0d, 0.0d);
        }
    }

    private net.minecraft.world.item.Item seedItem() {
        return PalaBotanist.SEEDS.get(seed).get();
    }
}
