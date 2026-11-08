package fr.atraxterra.dohy.palamod.hunter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Les ames d'un spawner customise ({@code TileEntityEmptyMobSpawner} :
 * {@code entityId}, {@code souls}).
 *
 * <p>Le type de creature est fige des la premiere ame (et non « une fois
 * rempli », comme le dit le wiki). Les paliers, releves dans {@code Tier} :
 * 0 a 99 ames, palier 0 ; puis 100, 150, 200 et 250 pour les paliers 1 a 4.
 * 250 est un plafond dur.
 */
public record SpawnerSouls(Optional<Identifier> type, int souls) {

    public static final int MAX_SOULS = 250;
    public static final SpawnerSouls EMPTY = new SpawnerSouls(Optional.empty(), 0);

    /** {@code Tier} : seuils en ames des paliers 1 a 4. */
    private static final int[] THRESHOLDS = {100, 150, 200, 250};

    /** Plafond de creatures autour du spawner, par palier (choix du joueur). */
    public static final int CREATURES_PER_TIER = 3;

    public static final MapCodec<SpawnerSouls> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("type").forGetter(SpawnerSouls::type),
            Codec.intRange(0, MAX_SOULS).fieldOf("souls").forGetter(SpawnerSouls::souls)
    ).apply(instance, SpawnerSouls::new));

    public static final Codec<SpawnerSouls> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnerSouls> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), SpawnerSouls::type,
            ByteBufCodecs.VAR_INT, SpawnerSouls::souls,
            SpawnerSouls::new);

    public boolean isBlank() {
        return type.isEmpty();
    }

    public static int tierOf(int souls) {
        int tier = 0;
        for (int threshold : THRESHOLDS) {
            if (souls >= threshold) {
                tier++;
            }
        }
        return tier;
    }

    public int tier() {
        return tierOf(souls);
    }

    public SpawnerSouls withSoul(Identifier entity) {
        return new SpawnerSouls(Optional.of(entity), Math.min(MAX_SOULS, souls + 1));
    }
}
