package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran d'ouverture d'un lucky block ({@code UILuckyBlock}).
 *
 * <p>Avant « Ouvrir », les trois rouleaux montrent des points d'interrogation.
 * Le serveur tire l'evenement au clic et renvoie la vue avec la bande des
 * rouleaux et l'evenement a la case {@code stop} : le client fait tourner, il
 * ne decide de rien.
 *
 * @param type   le numero du {@link LuckyType}
 * @param drawn  l'evenement est tire ; {@code spin} change a chaque tirage
 * @param event  le nom de l'evenement tire, vide avant
 * @param icon   son image, dans {@code palamod:textures/gui/lucky/events/}
 * @param name   son nom affiche
 * @param bad    un malus
 * @param fresh  jamais sorti pour ce joueur : le « NEW » de Paladium
 * @param reel   les images de la bande, tirees dans les evenements du bloc
 * @param stop   la case ou s'arrete chaque rouleau
 * @param found  les evenements deja vus par le joueur
 * @param total  les evenements qui existent
 */
public record LuckyBlockView(BlockPos pos, int type, boolean drawn, String event, String icon, String name, boolean bad,
                             boolean fresh, List<String> reel, int stop, int spin, int found, int total)
        implements UiView {

    public static final Identifier ID = UiNet.id("lucky_block");

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LuckyBlockView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                BlockPos.STREAM_CODEC.encode(buffer, view.pos());
                buffer.writeVarInt(view.type());
                buffer.writeBoolean(view.drawn());
                buffer.writeUtf(view.event());
                buffer.writeUtf(view.icon());
                buffer.writeUtf(view.name());
                buffer.writeBoolean(view.bad());
                buffer.writeBoolean(view.fresh());
                buffer.writeVarInt(view.reel().size());
                for (String icon : view.reel()) {
                    buffer.writeUtf(icon);
                }
                buffer.writeVarInt(view.stop());
                buffer.writeVarInt(view.spin());
                buffer.writeVarInt(view.found());
                buffer.writeVarInt(view.total());
            },
            buffer -> {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
                int type = buffer.readVarInt();
                boolean drawn = buffer.readBoolean();
                String event = buffer.readUtf();
                String icon = buffer.readUtf();
                String name = buffer.readUtf();
                boolean bad = buffer.readBoolean();
                boolean fresh = buffer.readBoolean();
                int size = Math.min(256, buffer.readVarInt());
                List<String> reel = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    reel.add(buffer.readUtf());
                }
                return new LuckyBlockView(pos, type, drawn, event, icon, name, bad, fresh, reel, buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
            });
}
