package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du Lucky Pass ({@code UILuckyPass}) : trois rouleaux de lucky blocks.
 *
 * @param claimed le lancer du jour est fait
 * @param wins    les trois cases du dernier lancer : -1 vide, sinon le numero du type
 * @param spin    change a chaque lancer : les rouleaux tournent
 * @param minutes les minutes avant le prochain lancer
 * @param spins   les lancers faits depuis le debut
 */
public record LuckyPassView(boolean claimed, List<Integer> wins, int spin, long minutes, int spins) implements UiView {

    public static final Identifier ID = UiNet.id("lucky_pass");

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LuckyPassView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeBoolean(view.claimed());
                buffer.writeVarInt(view.wins().size());
                for (int win : view.wins()) {
                    buffer.writeVarInt(win + 1);
                }
                buffer.writeVarInt(view.spin());
                buffer.writeVarLong(view.minutes());
                buffer.writeVarInt(view.spins());
            },
            buffer -> {
                boolean claimed = buffer.readBoolean();
                int size = Math.min(8, buffer.readVarInt());
                List<Integer> wins = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    wins.add(buffer.readVarInt() - 1);
                }
                return new LuckyPassView(claimed, wins, buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt());
            });
}
