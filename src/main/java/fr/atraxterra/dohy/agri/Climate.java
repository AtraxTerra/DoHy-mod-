package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * Les trois climats de l'agriculture.
 *
 * <p>NationsGlory reserve six cereales a des continents. Ici ce sont les
 * biomes qui tiennent lieu de continents : une cereale naturelle ne pousse que
 * dans son climat, lu sur la temperature de base du biome — un biome de mod est
 * donc range tout seul, sans liste a tenir. Le ble et les OGM poussent partout.
 */
public enum Climate {
    COLD("cold", "froid"),
    TEMPERATE("temperate", "tempere"),
    HOT("hot", "chaud");

    private final String key;
    private final String label;

    Climate(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** La cle de traduction de l'infobulle : {@code tooltip.dohy.climate.<key>}. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Le climat d'un endroit. */
    public static Climate at(LevelReader level, BlockPos pos) {
        AgriConfig.Climate bounds = AgriConfig.config().climate();
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        if (temperature < bounds.coldBelow()) {
            return COLD;
        }
        return temperature >= bounds.hotFrom() ? HOT : TEMPERATE;
    }
}
