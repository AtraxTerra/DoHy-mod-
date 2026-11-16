package fr.atraxterra.dohy.arrakis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * La fonction de densite {@code dohy:arrakis} : une sortie de {@link ArrakisRelief}
 * par usage. Les reglages de bruit d'Arrakis s'en servent pour la densite finale
 * ({@code height}, {@code erosion}), la surface de reference des regles de
 * surface ({@code base}) et le biome ({@code biome} : la source de biomes de
 * NTM Space lit un numero de biome dans la temperature).
 */
public record ArrakisTerrain(Output output) implements DensityFunction.SimpleFunction {

    public enum Output implements StringRepresentable {
        HEIGHT("height", 0.0d, 320.0d),
        BASE("base", 0.0d, 320.0d),
        EROSION("erosion", 0.0d, 0.3d),
        BIOME("biome", 0.0d, 255.0d);

        public static final Codec<Output> CODEC = StringRepresentable.fromEnum(Output::values);

        private final String name;
        private final double min;
        private final double max;

        Output(String name, double min, double max) {
            this.name = name;
            this.min = min;
            this.max = max;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<ArrakisTerrain> MAP_CODEC =
            Output.CODEC.fieldOf("output").xmap(ArrakisTerrain::new, ArrakisTerrain::output);
    private static final KeyDispatchDataCodec<ArrakisTerrain> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        ArrakisRelief.Column column = ArrakisRelief.column(context.blockX(), context.blockZ());
        return switch (output) {
            case HEIGHT -> column.height();
            case BASE -> column.base();
            case EROSION -> column.erosion();
            case BIOME -> Ids.of(column.region());
        };
    }

    @Override
    public double minValue() {
        return output.min;
    }

    @Override
    public double maxValue() {
        return output.max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    /**
     * Les numeros des biomes de Duna dans NTM Space. Ses biomes de planete ne
     * s'inscrivent qu'a l'initialisation de leur classe, que seule sa propre
     * densite declenchait : on l'initialise ici, puis on lit leurs numeros.
     */
    static final class Ids {

        static final String DUNA_BIOMES = "com.hbmspace.dim.duna.biome.BiomeGenBaseDuna";
        private static volatile int[] ids;

        static int of(ArrakisRelief.Region region) {
            int[] known = ids;
            if (known == null) {
                known = resolve();
                ids = known;
            }
            return known[region.ordinal()];
        }

        static int[] resolve() {
            ArrakisRelief.Region[] regions = ArrakisRelief.Region.values();
            int[] out = new int[regions.length];
            for (ArrakisRelief.Region region : regions) {
                out[region.ordinal()] = region.fallbackId;
            }
            try {
                Class<?> duna = Class.forName(DUNA_BIOMES, true, ArrakisTerrain.class.getClassLoader());
                java.lang.reflect.Field id = Class.forName("com.hbmspace.dim.BiomeGenBaseCelestial").getField("biomeID");
                for (ArrakisRelief.Region region : regions) {
                    Object biome = duna.getField(region.field).get(null);
                    if (biome != null) {
                        out[region.ordinal()] = id.getInt(biome);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                DoHy.LOGGER.warn("Arrakis : numeros des biomes de NTM Space illisibles, ceux par defaut servent", e);
            }
            return out;
        }
    }
}
