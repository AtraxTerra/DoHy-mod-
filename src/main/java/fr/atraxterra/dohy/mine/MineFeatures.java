package fr.atraxterra.dohy.mine;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Les features de la Mine. */
public final class MineFeatures {

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, DoHy.MOD_ID);

    /**
     * Les salles a spawner ({@link MineSpawnerRoomFeature}), posees par
     * {@code worldgen/placed_feature/mine/spawner_room} parmi les structures souterraines du biome
     * {@code dohy:mine}.
     */
    public static final DeferredHolder<Feature<?>, MineSpawnerRoomFeature> SPAWNER_ROOM =
            FEATURES.register("mine_spawner_room", () -> new MineSpawnerRoomFeature());

    private MineFeatures() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
