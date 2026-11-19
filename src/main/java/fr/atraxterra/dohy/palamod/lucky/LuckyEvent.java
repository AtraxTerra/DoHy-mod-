package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Un evenement de lucky block ({@code ALuckyEvent} et son entree dans
 * {@code LuckyEvents}).
 *
 * <p>Son poids au tirage est l'inverse de sa rarete ({@code getWeight},
 * {@code LuckyConst / rarity} avec {@code LuckyConst = 1}) : un evenement de
 * rarete 10 sort dix fois plus souvent qu'un evenement de rarete 100.
 *
 * @param id     le nom de l'evenement dans PalaMod, en minuscules ({@code body_guard})
 * @param name   le nom affiche, sans accent
 * @param icon   l'image de l'ecran d'ouverture, {@code textures/gui/lucky/events/<icon>.png}
 * @param bad    un malus ({@code isBad})
 * @param rarity la rarete de PalaMod
 * @param types  les lucky blocks dont il peut sortir
 * @param action ce qu'il fait, au moment ou le bloc s'ouvre
 */
public record LuckyEvent(String id, String name, String icon, boolean bad, int rarity, Set<LuckyType> types,
                         Action action) {

    /** Ce que fait un evenement : le joueur qui ouvre, et la place du bloc, deja retire. */
    @FunctionalInterface
    public interface Action {
        void perform(ServerPlayer player, ServerLevel level, BlockPos pos);
    }

    public LuckyEvent {
        types = Set.copyOf(types);
    }

    /** Le poids au tirage, avant le bonus du Lucky Pass. */
    public double weight() {
        return 1.0d / Math.max(1, rarity);
    }
}
