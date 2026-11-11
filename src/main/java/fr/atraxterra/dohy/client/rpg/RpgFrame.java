package fr.atraxterra.dohy.client.rpg;

import net.minecraft.resources.Identifier;

/**
 * Ce qu'il faut pour dessiner un monstre de donjon a un instant : son modele,
 * sa texture, sa taille, et ou en sont ses animations.
 *
 * <p>Les animations de Paladium sont absolues : chacune pose le corps entier.
 * On en melange donc au plus trois dont les poids font 1 — l'attente et la
 * marche, selon la vitesse, et l'action en cours (attaque, blessure, mort,
 * apparition), qui prend la place des deux autres le temps de jouer.
 *
 * @param baseMillis    l'age du monstre : le temps de l'attente et de la marche
 * @param walkWeight    la part de la marche dans le mouvement de fond, de 0 a 1
 * @param action        l'action en cours, ou {@code null}
 * @param actionMillis  le temps ecoule de l'action
 * @param actionWeight  la part de l'action, de 0 a 1
 */
public record RpgFrame(RpgModels.Entry entry, Identifier texture, float scale, long baseMillis, float walkWeight,
                       RpgModels.Clip action, long actionMillis, float actionWeight) {

    /** Pose le modele. Sa pose de repos vient d'etre remise. */
    void pose() {
        float base = action == null ? 1.0f : 1.0f - actionWeight;
        if (base > 0.001f) {
            float walk = entry.walk() == null ? 0.0f : walkWeight;
            if (walk > 0.001f) {
                entry.walk().animation().apply(baseMillis, base * walk);
            }
            if (entry.idle() != null && walk < 0.999f) {
                entry.idle().animation().apply(baseMillis, base * (1.0f - walk));
            }
        }
        if (action != null && actionWeight > 0.001f) {
            action.animation().apply(actionMillis, actionWeight);
        }
    }
}
