package fr.atraxterra.dohy.palamod.pet;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Les attributs et les regles d'apparition du serpent et de l'escargot.
 *
 * <p>PalaMod ne les posait dans aucun biome ({@code Entities} n'appelle pas
 * {@code addSpawn}) : ils sortaient des oeufs et des evenements. Ici, comme
 * l'elephant, ils vivent dans leurs biomes ({@code biome_modifier/snake.json},
 * {@code snail.json}) : le serpent, un monstre, la nuit dans les jungles, les
 * marais, les deserts et les savanes ; l'escargot, au jour, dans les forets,
 * les plaines, au bord des rivieres et dans les marais.
 */
public final class PetEntities {

    private PetEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(PalaPets.SNAKE.get(), Snake.createAttributes().build());
        event.put(PalaPets.SNAIL.get(), Snail.createAttributes().build());
    }

    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(PalaPets.SNAKE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(PalaPets.SNAIL.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Snail::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
