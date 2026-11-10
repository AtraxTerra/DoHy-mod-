package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * L'ecran des metiers : les quatre metiers et les contrats du jour.
 *
 * @param questBonus   bonus d'XP de metier donne par les contrats, en pour mille
 * @param xpMultiplier multiplicateur d'XP de metier actif (boosters), en pour mille au-dela de x1
 */
public record JobsView(List<JobLine> jobs, List<Quest> quests, int questBonus, int xpMultiplier) implements UiView {

    public static final Identifier ID = UiNet.id("jobs");

    /**
     * @param job      nom de l'enum {@code Job}
     * @param progress avancement dans le niveau, en pour mille
     */
    public record JobLine(String job, int level, int maxLevel, int progress, long xp, long xpNext) {
        public static final StreamCodec<RegistryFriendlyByteBuf, JobLine> STREAM_CODEC = StreamCodec.of(
                (buffer, line) -> {
                    buffer.writeUtf(line.job());
                    buffer.writeVarInt(line.level());
                    buffer.writeVarInt(line.maxLevel());
                    buffer.writeVarInt(line.progress());
                    buffer.writeVarLong(line.xp());
                    buffer.writeVarLong(line.xpNext());
                },
                buffer -> new JobLine(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarLong(), buffer.readVarLong()));
    }

    /** Un contrat du jour, tel que l'ecran le montre. */
    public record Quest(String title, String description, long progress, long goal, long reward, boolean done) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Quest> STREAM_CODEC = StreamCodec.of(
                (buffer, quest) -> {
                    buffer.writeUtf(quest.title());
                    buffer.writeUtf(quest.description());
                    buffer.writeVarLong(quest.progress());
                    buffer.writeVarLong(quest.goal());
                    buffer.writeVarLong(quest.reward());
                    buffer.writeBoolean(quest.done());
                },
                buffer -> new Quest(buffer.readUtf(), buffer.readUtf(), buffer.readVarLong(), buffer.readVarLong(),
                        buffer.readVarLong(), buffer.readBoolean()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, JobsView> STREAM_CODEC = StreamCodec.composite(
            JobLine.STREAM_CODEC.apply(ByteBufCodecs.list()), JobsView::jobs,
            Quest.STREAM_CODEC.apply(ByteBufCodecs.list()), JobsView::quests,
            ByteBufCodecs.VAR_INT, JobsView::questBonus,
            ByteBufCodecs.VAR_INT, JobsView::xpMultiplier,
            JobsView::new);

    @Override
    public Identifier viewId() {
        return ID;
    }
}
