package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.util.RandomSource;

/**
 * Les paliers d'une structure de spawners ({@code Tier} de PalaSpawner).
 *
 * <p>Un palier fixe le nombre de spawners que la structure compte, ce que
 * rend une vague par spawner, et le temps entre deux vagues. Les seuils en
 * ames (100, 150, 200, 250) sont dans {@link SpawnerSouls}.
 */
public enum SpawnerTier {

    ONE(27, 2, 3, 12),
    TWO(54, 3, 5, 15),
    THREE(81, 5, 7, 15),
    FOUR(108, 6, 11, 7);

    private final int maxSpawners;
    private final int entityMin;
    private final int entityMax;
    private final int seconds;

    SpawnerTier(int maxSpawners, int entityMin, int entityMax, int seconds) {
        this.maxSpawners = maxSpawners;
        this.entityMin = entityMin;
        this.entityMax = entityMax;
        this.seconds = seconds;
    }

    /** 1 a 4, comme le palier d'un spawner ({@link SpawnerSouls#tier()}). */
    public int level() {
        return ordinal() + 1;
    }

    public static SpawnerTier ofLevel(int level) {
        return values()[Math.clamp(level, 1, values().length) - 1];
    }

    public int maxSpawners() {
        return maxSpawners;
    }

    public int entityMin() {
        return entityMin;
    }

    public int entityMax() {
        return entityMax;
    }

    /** {@code getSpawnAmount} : entre le minimum et le maximum, bornes comprises. */
    public int amount(RandomSource random) {
        return entityMin + random.nextInt(entityMax - entityMin + 1);
    }

    /**
     * Ticks entre deux vagues : {@code timeMax - timeMax * 0,1 * vitesse}, une
     * amelioration de vitesse retirant donc un dixieme.
     */
    public int interval(int speedUpgrades) {
        int base = seconds * 20;
        return base - base * Math.min(speedUpgrades, 9) / 10;
    }

    public String roman() {
        return new String[] {"I", "II", "III", "IV"}[ordinal()];
    }
}
