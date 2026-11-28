package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * La roue du familier : les actives posees (une a six), et ce qu'il reste de leur
 * recharge. Elle s'ouvre tant que la touche est tenue ; relachee, l'active visee
 * part.
 */
public record PetWheelView(String familiar, List<Entry> entries) implements UiView {

    public static final Identifier ID = UiNet.id("pet_wheel");

    public record Entry(String id, String label, String value, long cooldownMs, int cooldownSeconds) {
    }

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PetWheelView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeUtf(view.familiar);
                buffer.writeVarInt(view.entries.size());
                for (Entry entry : view.entries) {
                    buffer.writeUtf(entry.id());
                    buffer.writeUtf(entry.label());
                    buffer.writeUtf(entry.value());
                    buffer.writeVarLong(entry.cooldownMs());
                    buffer.writeVarInt(entry.cooldownSeconds());
                }
            },
            buffer -> {
                String familiar = buffer.readUtf();
                List<Entry> entries = new ArrayList<>();
                for (int count = buffer.readVarInt(), index = 0; index < count; index++) {
                    entries.add(new Entry(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readVarLong(),
                            buffer.readVarInt()));
                }
                return new PetWheelView(familiar, entries);
            });
}
