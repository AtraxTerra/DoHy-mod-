package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Marqueur du cave block en endium, sans donnees. */
public class EndiumCaveBlockEntity extends BlockEntity {

    /**
     * Les cave blocks en endium charges, cote serveur. {@link EndiumCaveVision}
     * s'en sert pour ne rien chercher quand il n'y en a aucun pres d'un joueur
     * — le cas de presque tous les joueurs, presque tout le temps.
     */
    private static final Set<EndiumCaveBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    public EndiumCaveBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMiner.ENDIUM_CAVE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    /** Vrai s'il y a un cave block charge a moins de {@code reach} blocs (en x et en z) dans ce monde. */
    static boolean anyNear(Level level, BlockPos center, int reach) {
        for (EndiumCaveBlockEntity cave : LOADED) {
            if (cave.level == level && !cave.isRemoved()
                    && Math.abs(cave.worldPosition.getX() - center.getX()) <= reach
                    && Math.abs(cave.worldPosition.getZ() - center.getZ()) <= reach) {
                return true;
            }
        }
        return false;
    }
}
