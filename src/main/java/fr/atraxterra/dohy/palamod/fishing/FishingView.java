package fr.atraxterra.dohy.palamod.fishing;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Une manche du mini-jeu de peche : la barre, ses zones et le temps qu'il
 * reste. {@code rarity} : common, rare, legendary ou mythic (l'icone).
 * {@code speed} : allers-retours du curseur par seconde.
 */
public record FishingView(int stage, String rarity, String label, int color, int durationMs, float speed,
                          List<Zone> zones) implements UiView {

    public static final Identifier ID = UiNet.id("fishing");

    /** Une zone de la barre, de {@code start} a {@code start + width} (0 a 1). */
    public record Zone(boolean levelUp, int multiplier, int color, float start, float width) {
    }

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FishingView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeVarInt(view.stage);
                buffer.writeUtf(view.rarity);
                buffer.writeUtf(view.label);
                buffer.writeInt(view.color);
                buffer.writeVarInt(view.durationMs);
                buffer.writeFloat(view.speed);
                buffer.writeVarInt(view.zones.size());
                for (Zone zone : view.zones) {
                    buffer.writeBoolean(zone.levelUp());
                    buffer.writeVarInt(zone.multiplier());
                    buffer.writeInt(zone.color());
                    buffer.writeFloat(zone.start());
                    buffer.writeFloat(zone.width());
                }
            },
            buffer -> {
                int stage = buffer.readVarInt();
                String rarity = buffer.readUtf();
                String label = buffer.readUtf();
                int color = buffer.readInt();
                int duration = buffer.readVarInt();
                float speed = buffer.readFloat();
                int count = buffer.readVarInt();
                List<Zone> zones = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    zones.add(new Zone(buffer.readBoolean(), buffer.readVarInt(), buffer.readInt(),
                            buffer.readFloat(), buffer.readFloat()));
                }
                return new FishingView(stage, rarity, label, color, duration, speed, zones);
            });
}
