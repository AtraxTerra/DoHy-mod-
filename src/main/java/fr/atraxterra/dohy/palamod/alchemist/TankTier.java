package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Les quatre tanks ({@code EnumTank}) et leur contenance, en unites : 35, 60,
 * 95 et 135 fioles pleines.
 */
public enum TankTier implements StringRepresentable {
    GOLD("gold", 35_000),
    AMETHYST("amethyst", 60_000),
    TITANE("titane", 95_000),
    PALADIUM("paladium", 135_000);

    public static final Codec<TankTier> CODEC = StringRepresentable.fromEnum(TankTier::values);

    private final String id;
    private final int capacity;

    TankTier(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
