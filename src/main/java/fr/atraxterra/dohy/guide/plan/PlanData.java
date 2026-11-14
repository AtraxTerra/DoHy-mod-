package fr.atraxterra.dohy.guide.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Ce que porte un plan de construction : la structure choisie, sa taille, et
 * ou il est pose s'il l'est. Tout tient dans l'objet : l'hologramme se dessine
 * chez le client a partir des plans de son inventaire, sans autre message.
 *
 * @param blueprint l'identifiant du plan ({@link Blueprints})
 * @param variant   la taille ou le palier
 * @param anchor    la pose : dimension, milieu du premier rang au sol, sens
 */
public record PlanData(String blueprint, int variant, Optional<Anchor> anchor) {

    public static final PlanData DEFAULT = new PlanData("grinder", 0, Optional.empty());

    /**
     * Une pose.
     *
     * @param dimension la dimension
     * @param pos       le milieu du premier rang, au sol
     * @param quarter   le regard du joueur a la pose ({@code Direction.get2DDataValue})
     */
    public record Anchor(String dimension, BlockPos pos, int quarter) {

        public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Anchor::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Anchor::pos),
                Codec.INT.fieldOf("quarter").forGetter(Anchor::quarter)
        ).apply(instance, Anchor::new));

        public static final StreamCodec<ByteBuf, Anchor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Anchor::dimension,
                BlockPos.STREAM_CODEC, Anchor::pos,
                ByteBufCodecs.VAR_INT, Anchor::quarter,
                Anchor::new);

        public Anchor turned() {
            return new Anchor(dimension, pos, (quarter + 1) & 3);
        }
    }

    public static final Codec<PlanData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("blueprint").forGetter(PlanData::blueprint),
            Codec.INT.optionalFieldOf("variant", 0).forGetter(PlanData::variant),
            Anchor.CODEC.optionalFieldOf("anchor").forGetter(PlanData::anchor)
    ).apply(instance, PlanData::new));

    public static final StreamCodec<ByteBuf, PlanData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlanData::blueprint,
            ByteBufCodecs.VAR_INT, PlanData::variant,
            ByteBufCodecs.optional(Anchor.STREAM_CODEC), PlanData::anchor,
            PlanData::new);

    public PlanData withBlueprint(String id, int index) {
        return new PlanData(id, index, anchor);
    }

    public PlanData withAnchor(Anchor value) {
        return new PlanData(blueprint, variant, Optional.ofNullable(value));
    }

    /** Le plan de cet objet, ou le grinder par defaut si son identifiant n'existe plus. */
    public Blueprint resolve() {
        Blueprint found = Blueprints.get(blueprint);
        return found != null ? found : Blueprints.all().get(0);
    }
}
