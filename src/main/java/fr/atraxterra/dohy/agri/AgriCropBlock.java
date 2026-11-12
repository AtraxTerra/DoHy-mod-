package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.palamod.PalaCropBlock;

/**
 * Une culture de cereale : huit stades, comme le ble, avec une cadence propre
 * ({@link PalaCropBlock} saute une part des ticks de croissance).
 */
public class AgriCropBlock extends PalaCropBlock {

    private final Cereal cereal;

    public AgriCropBlock(Properties properties, Cereal cereal) {
        super(properties, cereal.growth());
        this.cereal = cereal;
    }

    public Cereal cereal() {
        return cereal;
    }
}
