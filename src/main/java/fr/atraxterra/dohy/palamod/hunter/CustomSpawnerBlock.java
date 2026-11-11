package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Le spawner customise pose ({@code BlockEmptySpawner}).
 *
 * <p><b>Un spawner vanilla, a la demande du joueur.</b> Le bloc porte la block
 * entity du spawner vanilla — ajoutee a son type au demarrage
 * ({@link SpawnerEvents}) — donc il fait apparaitre sa creature exactement comme
 * un spawner de donjon, mob qui tourne dans la cage compris. Dans PalaMod, sans
 * controleur, il ne faisait rien. Ce qui change :
 * <ul>
 *   <li>sous 100 ames (palier 0), il reste vide et ne fait rien apparaitre ;</li>
 *   <li>ses creatures s'empilent en une seule, avec leur nombre au-dessus, et
 *       le palier en fixe le plafond : 3, 6, 9, 12 ({@link MobStacks}) ;</li>
 *   <li>la cage prend la couleur du palier (propriete {@code tier}) ;</li>
 *   <li>casse, il rend son objet avec ses ames, meme a la main ou par une
 *       explosion, et ne lache pas d'XP — sinon poser et casser serait une
 *       ferme a XP.</li>
 * </ul>
 *
 * <p><b>Dans une structure</b> ({@code linked}), il ne fait plus apparaitre de
 * creature : c'est le controleur qui compte ses vagues
 * ({@link SpawnerControllerBlockEntity}). Le spawner n'a alors plus de tick
 * serveur, hormis une verification toutes les cinq secondes : si son
 * controleur a disparu ou si la structure ne tient plus, il redevient un
 * spawner ordinaire.
 */
public class CustomSpawnerBlock extends SpawnerBlock {

    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 4);
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    /** Valeur de {@link PalaHunter#SPAWNER_LINK} d'un spawner sans controleur. */
    public static final long NO_LINK = Long.MIN_VALUE;
    private static final int LINK_CHECK = 100;

    public CustomSpawnerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(TIER, 0).setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER, LINKED);
    }

    /** Lie, le spawner ne tourne plus cote serveur ; la cage continue de tourner chez le client. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide() && state.getValue(LINKED)) {
            return (tickLevel, pos, tickState, entity) -> checkLink(tickLevel, pos, tickState, entity);
        }
        return super.getTicker(level, state, type);
    }

    private static void checkLink(Level level, BlockPos pos, BlockState state, BlockEntity entity) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), (long) LINK_CHECK) != 0L) {
            return;
        }
        long link = entity.getData(PalaHunter.SPAWNER_LINK.get());
        if (link != NO_LINK) {
            BlockPos controller = BlockPos.of(link);
            if (!level.isLoaded(controller)) {
                return;
            }
            if (level.getBlockEntity(controller) instanceof SpawnerControllerBlockEntity owner
                    && owner.covers(pos)) {
                return;
            }
            entity.removeData(PalaHunter.SPAWNER_LINK.get());
        }
        level.setBlock(pos, state.setValue(LINKED, false), Block.UPDATE_CLIENTS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        SpawnerSouls souls = context.getItemInHand().getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        return defaultBlockState().setValue(TIER, souls.tier());
    }

    /** Le spawner prend la creature et les ames de l'objet. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return;
        }
        SpawnerSouls souls = stack.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        spawner.setData(PalaHunter.SPAWNER_SOULS.get(), souls);
        // Sous 100 ames, pas de creature : le spawner reste vide et ne tourne pas
        // a vide. Le type est garde dans les ames, pour la casse.
        if (souls.tier() > 0) {
            souls.type().map(BuiltInRegistries.ENTITY_TYPE::getValue).ifPresent(
                    (EntityType<?> type) -> spawner.setEntityId(type, level.getRandom()));
        }
        spawner.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    /** Pas d'XP a la casse : ce spawner se repose a volonte. */
    @Override
    public int getExpDrop(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity blockEntity,
                          Entity breaker, ItemStack tool) {
        return 0;
    }
}
