package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.faction.enterprise.Line;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * La latitude d'un endroit, pour les plantes des affaires illegales (lot 24c). Sur la carte de
 * la Terre, c'est la vraie ; ailleurs, la temperature du biome en tient lieu : un biome chaud
 * vaut les tropiques, un tempere 30 degres, un frais 48, un froid 65.
 */
final class Latitudes {

    private Latitudes() {
    }

    static double at(LevelReader level, BlockPos pos) {
        EarthData earth = EarthData.get();
        if (earth != null && level instanceof Level world && earth.covers(world)) {
            return earth.latitude(pos.getZ());
        }
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        if (temperature >= 1.0f) {
            return 10.0d;
        }
        if (temperature >= 0.5f) {
            return 30.0d;
        }
        return temperature >= 0.2f ? 48.0d : 65.0d;
    }

    static boolean grows(Line line, LevelReader level, BlockPos pos) {
        return line.growsAt(at(level, pos));
    }
}
