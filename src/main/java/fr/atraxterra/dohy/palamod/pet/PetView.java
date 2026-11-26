package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Le menu du familier ({@code /pet}) : son niveau, son bonheur, ses quetes du
 * jour, ses vingt emplacements et toutes les competences. Sans familier,
 * {@code has} est faux et le menu explique la cage.
 */
public record PetView(boolean has, String familiar, String label, int level, long xp, long xpNext, int happiness,
                      int maxHappiness, boolean visible, int slotsOpen, List<Slot> slots, List<Skill> skills,
                      List<Mission> missions, long resetMs, String notice, boolean noticeError) implements UiView {

    public static final Identifier ID = UiNet.id("pet");

    /** Un emplacement : la competence posee ("" si vide) et le verrou qui reste. */
    public record Slot(String skill, long lockMs) {
    }

    /** Une competence : ce qu'elle fait, ses paliers, sa valeur pour ce familier et sa recharge. */
    public record Skill(String id, String label, String description, boolean active, int unlock, String tiers,
                        String now, int cooldownSeconds, long cooldownMs) {
    }

    public record Mission(String label, String unit, int progress, int goal, int xp, boolean done) {
    }

    @Override
    public Identifier viewId() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PetView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeBoolean(view.has);
                buffer.writeUtf(view.familiar);
                buffer.writeUtf(view.label);
                buffer.writeVarInt(view.level);
                buffer.writeVarLong(view.xp);
                buffer.writeVarLong(view.xpNext);
                buffer.writeVarInt(view.happiness);
                buffer.writeVarInt(view.maxHappiness);
                buffer.writeBoolean(view.visible);
                buffer.writeVarInt(view.slotsOpen);
                buffer.writeVarInt(view.slots.size());
                for (Slot slot : view.slots) {
                    buffer.writeUtf(slot.skill());
                    buffer.writeVarLong(slot.lockMs());
                }
                buffer.writeVarInt(view.skills.size());
                for (Skill skill : view.skills) {
                    buffer.writeUtf(skill.id());
                    buffer.writeUtf(skill.label());
                    buffer.writeUtf(skill.description());
                    buffer.writeBoolean(skill.active());
                    buffer.writeVarInt(skill.unlock());
                    buffer.writeUtf(skill.tiers());
                    buffer.writeUtf(skill.now());
                    buffer.writeVarInt(skill.cooldownSeconds());
                    buffer.writeVarLong(skill.cooldownMs());
                }
                buffer.writeVarInt(view.missions.size());
                for (Mission mission : view.missions) {
                    buffer.writeUtf(mission.label());
                    buffer.writeUtf(mission.unit());
                    buffer.writeVarInt(mission.progress());
                    buffer.writeVarInt(mission.goal());
                    buffer.writeVarInt(mission.xp());
                    buffer.writeBoolean(mission.done());
                }
                buffer.writeVarLong(view.resetMs);
                buffer.writeUtf(view.notice);
                buffer.writeBoolean(view.noticeError);
            },
            buffer -> {
                boolean has = buffer.readBoolean();
                String familiar = buffer.readUtf();
                String label = buffer.readUtf();
                int level = buffer.readVarInt();
                long xp = buffer.readVarLong();
                long xpNext = buffer.readVarLong();
                int happiness = buffer.readVarInt();
                int maxHappiness = buffer.readVarInt();
                boolean visible = buffer.readBoolean();
                int slotsOpen = buffer.readVarInt();
                List<Slot> slots = new ArrayList<>();
                for (int count = buffer.readVarInt(), index = 0; index < count; index++) {
                    slots.add(new Slot(buffer.readUtf(), buffer.readVarLong()));
                }
                List<Skill> skills = new ArrayList<>();
                for (int count = buffer.readVarInt(), index = 0; index < count; index++) {
                    skills.add(new Skill(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(),
                            buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                            buffer.readVarLong()));
                }
                List<Mission> missions = new ArrayList<>();
                for (int count = buffer.readVarInt(), index = 0; index < count; index++) {
                    missions.add(new Mission(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
                }
                long resetMs = buffer.readVarLong();
                String notice = buffer.readUtf();
                boolean noticeError = buffer.readBoolean();
                return new PetView(has, familiar, label, level, xp, xpNext, happiness, maxHappiness, visible,
                        slotsOpen, slots, skills, missions, resetMs, notice, noticeError);
            });
}
