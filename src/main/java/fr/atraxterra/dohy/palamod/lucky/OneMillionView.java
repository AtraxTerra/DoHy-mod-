package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran de « 1 000 000 joueurs » ({@code GuiOneMillionPlayer}) : un faux gros lot. Les
 * 5 000 $ sont deja partis quand il s'ouvre ; a la fermeture, LuckyMan avoue l'arnaque.
 */
public record OneMillionView(long cost) implements UiView {

    public static final Identifier ID = UiNet.id("one_million");

    /** Les joueurs qui n'ont pas encore ferme l'ecran : l'aveu ne vient qu'une fois. */
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OneMillionView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> buffer.writeVarLong(view.cost()),
            buffer -> new OneMillionView(buffer.readVarLong()));

    static void show(ServerPlayer player) {
        PENDING.add(player.getUUID());
        UiNet.show(player, new OneMillionView(FindiumContent.MILLION_COST));
    }

    /** « RECUPERER », ou la fermeture ({@code onGuiClosed}). */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        if ("claim".equals(action.verb()) && PENDING.remove(player.getUUID())) {
            player.sendSystemMessage(FindiumContent.luckyMan().copy().append(Component.literal(
                    " Oups... On dirait que vous vous etes fait arnaquer").withStyle(ChatFormatting.GRAY)));
        }
    }
}
