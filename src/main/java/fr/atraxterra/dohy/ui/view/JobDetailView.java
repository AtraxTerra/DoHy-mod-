package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * La fiche d'un metier : niveau, XP, et les trois listes — deblocages, bonus,
 * recompenses. Les onglets se changent cote client : tout part d'un coup.
 *
 * @param progress  avancement dans le niveau, en pour mille
 * @param nextMoney argent verse au prochain niveau, 0 au maximum
 */
public record JobDetailView(String job, int level, int maxLevel, int progress, long xpInto, long xpNeed,
                            long totalXp, long nextMoney, List<Row> unlocks, List<Row> perks,
                            List<Row> rewards) implements UiView {

    public static final Identifier ID = UiNet.id("job_detail");

    /** Etat d'une ligne. */
    public static final int LOCKED = 0;
    public static final int ACQUIRED = 1;
    /** Declare mais absent du mod : objet pas encore porte, effet pas applique. */
    public static final int MISSING = 2;

    /**
     * Une ligne de liste.
     *
     * @param lines details pour la bulle
     */
    public record Row(ItemStack icon, String title, String detail, int level, int state, List<String> lines) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of(
                (buffer, row) -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, row.icon());
                    buffer.writeUtf(row.title());
                    buffer.writeUtf(row.detail());
                    buffer.writeVarInt(row.level());
                    buffer.writeVarInt(row.state());
                    buffer.writeVarInt(row.lines().size());
                    for (String line : row.lines()) {
                        buffer.writeUtf(line);
                    }
                },
                buffer -> {
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                    String title = buffer.readUtf();
                    String detail = buffer.readUtf();
                    int level = buffer.readVarInt();
                    int state = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    List<String> lines = new java.util.ArrayList<>();
                    for (int index = 0; index < count; index++) {
                        lines.add(buffer.readUtf());
                    }
                    return new Row(icon, title, detail, level, state, lines);
                });
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, JobDetailView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeUtf(view.job());
                buffer.writeVarInt(view.level());
                buffer.writeVarInt(view.maxLevel());
                buffer.writeVarInt(view.progress());
                buffer.writeVarLong(view.xpInto());
                buffer.writeVarLong(view.xpNeed());
                buffer.writeVarLong(view.totalXp());
                buffer.writeVarLong(view.nextMoney());
                Row.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, view.unlocks());
                Row.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, view.perks());
                Row.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, view.rewards());
            },
            buffer -> new JobDetailView(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer)));

    @Override
    public Identifier viewId() {
        return ID;
    }
}
