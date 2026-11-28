package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Un essai de l'apprivoisement : la barre, ses zones, le palier et ses points.
 * {@code round} numerote les essais (un appui par essai) ; {@code last} est ce
 * qu'a rapporte l'essai d'avant (0 au premier). {@code speed} : allers-retours
 * du curseur par seconde.
 */
public record TamingView(int round, String familiar, String label, int stage, int points, int target, int last,
                         float speed, List<Zone> zones) implements UiView {

    public static final Identifier ID = UiNet.id("pet_taming");

    /** Une zone de la barre, de {@code start} a {@code start + width} (0 a 1), et ce qu'elle rapporte. */
    public record Zone(int value, int color, float start, float width) {
    }

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TamingView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeVarInt(view.round);
                buffer.writeUtf(view.familiar);
                buffer.writeUtf(view.label);
                buffer.writeVarInt(view.stage);
                buffer.writeInt(view.points);
                buffer.writeVarInt(view.target);
                buffer.writeInt(view.last);
                buffer.writeFloat(view.speed);
                buffer.writeVarInt(view.zones.size());
                for (Zone zone : view.zones) {
                    buffer.writeInt(zone.value());
                    buffer.writeInt(zone.color());
                    buffer.writeFloat(zone.start());
                    buffer.writeFloat(zone.width());
                }
            },
            buffer -> {
                int round = buffer.readVarInt();
                String familiar = buffer.readUtf();
                String label = buffer.readUtf();
                int stage = buffer.readVarInt();
                int points = buffer.readInt();
                int target = buffer.readVarInt();
                int last = buffer.readInt();
                float speed = buffer.readFloat();
                int count = buffer.readVarInt();
                List<Zone> zones = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    zones.add(new Zone(buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readFloat()));
                }
                return new TamingView(round, familiar, label, stage, points, target, last, speed, zones);
            });
}
