package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Le profil RPG : le joueur, son niveau, ses statistiques (l'arbre seul, ou tout compris) et
 * l'arbre de competences ({@link RpgTree}), noeuds pris et choisis, branche par branche dans
 * l'ordre de {@link RpgStat}.
 *
 * @param free    les points libres, choix en cours deduits
 * @param owned   les noeuds pris, un masque par branche
 * @param pending les noeuds choisis, pas encore valides
 * @param sources d'ou viennent les points, pour la bulle du compteur
 * @param respec  le prix de la remise a zero
 */
public record RpgView(String name, int level, long xpIn, long xpNext, int free, int[] owned, int[] pending,
                      List<Row> perso, List<Row> global, List<String> sources, long respec) implements UiView {

    public static final Identifier ID = UiNet.id("rpg");

    /** Une ligne de statistique : l'icone (textures/stats/icon de PalaRPG), le nom, la valeur. */
    public record Row(String icon, String label, String value, int color) {
    }

    @Override
    public Identifier viewId() {
        return ID;
    }

    private static void rows(RegistryFriendlyByteBuf buffer, List<Row> rows) {
        buffer.writeVarInt(rows.size());
        for (Row row : rows) {
            buffer.writeUtf(row.icon());
            buffer.writeUtf(row.label());
            buffer.writeUtf(row.value());
            buffer.writeInt(row.color());
        }
    }

    private static List<Row> rows(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(64, buffer.readVarInt());
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(new Row(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readInt()));
        }
        return rows;
    }

    private static int[] masks(RegistryFriendlyByteBuf buffer) {
        int[] masks = new int[RpgStat.values().length];
        for (int index = 0; index < masks.length; index++) {
            masks[index] = buffer.readVarInt() & RpgTree.ALL;
        }
        return masks;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RpgView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeUtf(view.name());
                buffer.writeVarInt(view.level());
                buffer.writeVarLong(view.xpIn());
                buffer.writeVarLong(view.xpNext());
                buffer.writeVarInt(view.free());
                for (int mask : view.owned()) {
                    buffer.writeVarInt(mask);
                }
                for (int mask : view.pending()) {
                    buffer.writeVarInt(mask);
                }
                rows(buffer, view.perso());
                rows(buffer, view.global());
                buffer.writeVarInt(view.sources().size());
                view.sources().forEach(buffer::writeUtf);
                buffer.writeVarLong(view.respec());
            },
            buffer -> {
                String name = buffer.readUtf();
                int level = buffer.readVarInt();
                long xpIn = buffer.readVarLong();
                long xpNext = buffer.readVarLong();
                int free = buffer.readVarInt();
                int[] owned = masks(buffer);
                int[] pending = masks(buffer);
                List<Row> perso = rows(buffer);
                List<Row> global = rows(buffer);
                int count = Math.min(8, buffer.readVarInt());
                List<String> sources = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    sources.add(buffer.readUtf());
                }
                return new RpgView(name, level, xpIn, xpNext, free, owned, pending, perso, global, sources,
                        buffer.readVarLong());
            });
}
