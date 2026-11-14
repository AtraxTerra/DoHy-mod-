package fr.atraxterra.dohy.earth.disaster;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que le client doit savoir des catastrophes : la liste de celles en cours
 * (pour l'atlas et l'alerte sous la minimap) et ce que le joueur ressent la ou
 * il est (les secousses, la cendre).
 */
@EventBusSubscriber
public final class DisasterNet {

    /** Les familles, dans l'ordre de {@link DisasterKind}. */
    public static final int QUAKE = 0;
    public static final int ERUPTION = 1;
    public static final int HURRICANE = 2;
    public static final int MONSOON = 3;

    /**
     * Une catastrophe en cours.
     *
     * @param kind    {@link #QUAKE}, {@link #ERUPTION}, {@link #HURRICANE} ou {@link #MONSOON}
     * @param radius  sa portee, en blocs
     * @param eye     le rayon de l'oeil d'un ouragan ; 0 sinon
     * @param level   la magnitude fois dix d'un seisme, la categorie d'un ouragan ; 0 sinon
     * @param phase   0 l'alerte, 1 en cours, 2 la fin
     * @param seconds les secondes avant l'eruption pendant l'alerte, celles qui restent sinon
     * @param track   la route a venir d'un ouragan : x, z, x, z...
     */
    public record Entry(int id, int kind, String name, String place, int x, int z, int radius, int eye, int level,
                        int phase, int seconds, int[] track) {
        public boolean covers(double px, double pz) {
            return Math.hypot(px - x, pz - z) <= radius;
        }
    }

    public record Sync(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "disasters"));
        public static final StreamCodec<FriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buffer, sync) -> {
                    buffer.writeVarInt(sync.entries().size());
                    for (Entry entry : sync.entries()) {
                        buffer.writeVarInt(entry.id());
                        buffer.writeVarInt(entry.kind());
                        buffer.writeUtf(entry.name());
                        buffer.writeUtf(entry.place());
                        buffer.writeVarInt(entry.x());
                        buffer.writeVarInt(entry.z());
                        buffer.writeVarInt(entry.radius());
                        buffer.writeVarInt(entry.eye());
                        buffer.writeVarInt(entry.level());
                        buffer.writeVarInt(entry.phase());
                        buffer.writeVarInt(entry.seconds());
                        buffer.writeVarIntArray(entry.track());
                    }
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new Entry(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(),
                                buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                                buffer.readVarIntArray()));
                    }
                    return new Sync(entries);
                });

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /**
     * Ce que le joueur ressent : la secousse (0 a 1), la cendre dans l'air (0 a 1) et le vent d'un ouragan, la
     * poussee par tick que son client ajoute a son mouvement (rien a l'abri).
     */
    public record Feel(float shake, float ash, float windX, float windZ) implements CustomPacketPayload {
        public static final Feel NONE = new Feel(0.0f, 0.0f, 0.0f, 0.0f);
        public static final Type<Feel> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "disaster_feel"));
        public static final StreamCodec<FriendlyByteBuf, Feel> STREAM_CODEC = StreamCodec.of(
                (buffer, feel) -> {
                    buffer.writeFloat(feel.shake());
                    buffer.writeFloat(feel.ash());
                    buffer.writeFloat(feel.windX());
                    buffer.writeFloat(feel.windZ());
                },
                buffer -> new Feel(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));

        @Override
        public Type<Feel> type() {
            return TYPE;
        }
    }

    private DisasterNet() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC);
        registrar.playToClient(Feel.TYPE, Feel.STREAM_CODEC);
    }
}
