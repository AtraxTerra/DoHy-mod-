package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Le menu du familier ({@link PetView}) et sa roue ({@link PetWheelView}) cote
 * serveur : un clic sur un emplacement puis sur une competence la pose ;
 * RETIRER vide l'emplacement ; l'oeil montre ou cache le familier.
 */
public final class PetMenu {

    private PetMenu() {
    }

    public static void open(ServerPlayer player) {
        PetApi.rollDay(player);
        UiNet.show(player, view(player, "", false));
    }

    static PetView view(ServerPlayer player, String notice, boolean error) {
        PetData data = PetApi.data(player);
        if (!data.has()) {
            return new PetView(false, "", "", 0, 0L, 0L, 0, 0, false, 0, List.of(), skills(player, data),
                    List.of(), 0L, notice, error);
        }
        List<PetView.Slot> slots = new ArrayList<>();
        for (int index = 0; index < PetData.SLOTS; index++) {
            slots.add(new PetView.Slot(data.slot(index), PetApi.lockRemaining(player, index)));
        }
        List<PetView.Mission> missions = new ArrayList<>();
        List<String> ids = data.missions();
        for (int index = 0; index < ids.size(); index++) {
            PetMission mission = PetMission.byId(ids.get(index));
            if (mission != null) {
                missions.add(new PetView.Mission(mission.label(), mission.unit(), data.progress(index),
                        mission.goal(), mission.xp(), data.rewarded(index)));
            }
        }
        long reset = RealClock.minutesUntilNextDay(0) * 60_000L;
        return new PetView(true, data.familiar().id(), data.familiar().label(), data.level(), data.xp(),
                PetApi.xpToNext(data.level()), data.happiness(), PetApi.maxHappiness(data.level()), data.visible(),
                PetApi.slotCount(data.level()), slots, skills(player, data), missions, reset, notice, error);
    }

    private static List<PetView.Skill> skills(ServerPlayer player, PetData data) {
        List<PetView.Skill> out = new ArrayList<>();
        double multiplier = PetApi.multiplier(data);
        for (PetSkill skill : PetSkill.values()) {
            double base = skill.base(data.has() ? data.level() : 0);
            String now = !data.has() || data.level() < skill.unlock() ? ""
                    : skill.format(base * multiplier);
            out.add(new PetView.Skill(skill.id(), skill.label(), skill.description(), skill.active(), skill.unlock(),
                    skill.tiers(), now, skill.cooldownSeconds(),
                    data.has() ? PetApi.cooldownRemaining(player, skill) : 0L));
        }
        return out;
    }

    /** Les clics du menu : {@code assign} (« emplacement:competence »), {@code clear}, {@code visible}. */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        String error = null;
        String done = "";
        switch (action.verb()) {
            case "assign" -> {
                String[] parts = action.argument().split(":", 2);
                PetSkill skill = parts.length == 2 ? PetSkill.byId(parts[1]) : null;
                int slot = parts.length == 2 ? parse(parts[0]) : -1;
                if (skill == null) {
                    error = "Competence inconnue.";
                } else {
                    error = PetApi.assign(player, slot, skill);
                    done = skill.label() + " posee. Elle reste en place 24 heures.";
                }
            }
            case "clear" -> {
                error = PetApi.clear(player, parse(action.argument()));
                done = "Emplacement libere.";
            }
            case "visible" -> {
                PetData data = PetApi.data(player);
                if (data.has()) {
                    PetApi.setVisible(player, !data.visible());
                    done = data.visible() ? "Ton familier est visible." : "Ton familier est cache.";
                }
            }
            default -> {
                return;
            }
        }
        UiNet.show(player, view(player, error != null ? error : done, error != null));
    }

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // -- La roue -------------------------------------------------------------------------------------

    /** La touche de la roue : la roue s'ouvre avec les actives posees. */
    public static void openWheel(ServerPlayer player) {
        PetData data = PetApi.data(player);
        if (!data.has()) {
            PetActives.tell(player, "Tu n'as pas de familier.", net.minecraft.ChatFormatting.RED);
            return;
        }
        List<PetWheelView.Entry> entries = new ArrayList<>();
        for (PetSkill skill : PetApi.actives(data)) {
            entries.add(new PetWheelView.Entry(skill.id(), skill.label(),
                    data.level() < skill.unlock() ? "" : skill.format(PetApi.value(player, skill)),
                    PetApi.cooldownRemaining(player, skill), skill.cooldownSeconds()));
        }
        if (entries.isEmpty()) {
            PetActives.tell(player, "Aucune competence active posee : /pet pour en choisir (des le niveau 10).",
                    net.minecraft.ChatFormatting.GRAY);
            return;
        }
        UiNet.show(player, new PetWheelView(data.familiar().id(), entries));
    }

    /** La touche relachee sur une active : {@code use} et son identifiant. */
    public static void handleWheel(ServerPlayer player, UiNet.Action action) {
        if (!"use".equals(action.verb())) {
            return;
        }
        UiNet.close(player);
        PetSkill skill = PetSkill.byId(action.argument());
        if (skill != null) {
            PetActives.activate(player, skill);
        }
    }
}
