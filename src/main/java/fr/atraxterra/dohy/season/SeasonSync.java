package fr.atraxterra.dohy.season;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Ce que le client doit savoir pour vivre les memes saisons que le serveur :
 * le calendrier, l'heure du serveur (pour corriger une horloge de joueur qui
 * derive) et la projection de la carte (pour la latitude de chaque bloc).
 *
 * <p>Envoye a la connexion, et a tous quand l'horloge des saisons est decalee
 * ({@code /saison admin}). Le client n'en deduit rien qu'il ne pourrait
 * recalculer : la neige, la glace et les cultures restent l'affaire du serveur ;
 * le client s'en sert pour la pluie qui devient neige, les couleurs et l'atlas.
 */
public record SeasonSync(long anchor, long year, long serverNow, boolean miller, double blocksPerDegree,
                         double equatorZ, int minX, int maxX, int minZ, int maxZ, String dimension)
        implements CustomPacketPayload {

    public static final Type<SeasonSync> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "season_sync"));

    public static final StreamCodec<FriendlyByteBuf, SeasonSync> STREAM_CODEC = StreamCodec.of(
            (buffer, sync) -> {
                buffer.writeLong(sync.anchor());
                buffer.writeLong(sync.year());
                buffer.writeLong(sync.serverNow());
                buffer.writeBoolean(sync.miller());
                buffer.writeDouble(sync.blocksPerDegree());
                buffer.writeDouble(sync.equatorZ());
                buffer.writeInt(sync.minX());
                buffer.writeInt(sync.maxX());
                buffer.writeInt(sync.minZ());
                buffer.writeInt(sync.maxZ());
                buffer.writeUtf(sync.dimension());
            },
            buffer -> new SeasonSync(buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readBoolean(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readUtf()));

    /** Le message qui decrit un contexte en service. */
    static SeasonSync of(SeasonClimate.Frame frame, SeasonClimate.Clock clock, long serverNow) {
        return new SeasonSync(clock.anchor(), clock.year(), serverNow, frame.miller(), frame.blocksPerDegree(),
                frame.equatorZ(), frame.minX(), frame.maxX(), frame.minZ(), frame.maxZ(), frame.dimension());
    }

    public SeasonClimate.Frame frame() {
        return new SeasonClimate.Frame(miller, blocksPerDegree, equatorZ, minX, maxX, minZ, maxZ, dimension);
    }

    public SeasonClimate.Clock clock() {
        return new SeasonClimate.Clock(anchor, year);
    }

    @Override
    public Type<SeasonSync> type() {
        return TYPE;
    }
}
