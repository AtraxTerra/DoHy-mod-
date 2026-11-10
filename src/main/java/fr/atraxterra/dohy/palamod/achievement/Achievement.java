package fr.atraxterra.dohy.palamod.achievement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Un succes : une ou plusieurs etapes, toutes a remplir, et une recompense en
 * argent (choix du joueur : PalaMod ne declare pas de recompense, Paladium les
 * affichait dans le profil).
 *
 * <p>Les succes « multiples » de PalaMod ({@code MultiAchievement} et ses
 * {@code addSubAchievement}) deviennent des etapes ; les succes simples n'en
 * ont qu'une.
 *
 * @param id          l'identifiant de PalaMod ({@code palamod.craft.crafttools}...)
 * @param category    sa categorie ({@code AchievementCategory})
 * @param name        son nom
 * @param description sa description
 * @param icon        son icone
 * @param reward      l'argent verse a la validation
 * @param steps       ses etapes
 */
public record Achievement(String id, Category category, String name, String description,
                          Supplier<ItemStack> icon, long reward, List<Step> steps) {

    /** Une etape : son libelle, son objectif, et ce qui la mesure. */
    public record Step(String label, long goal, ToLongFunction<ServerPlayer> progress) {

        public long of(ServerPlayer player) {
            return Math.min(goal, Math.max(0L, progress.applyAsLong(player)));
        }

        public boolean done(ServerPlayer player) {
            return progress.applyAsLong(player) >= goal;
        }
    }

    /** Les categories de PalaMod ({@code AchievementCategory}). */
    public enum Category {
        START("Bien debuter"),
        ECONOMY("Economie"),
        JOBS("Metiers"),
        ATTACK_DEFENSE("Attaque et defense"),
        FACTION("Faction"),
        OTHERS("Autres");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public boolean done(ServerPlayer player) {
        for (Step step : steps) {
            if (!step.done(player)) {
                return false;
            }
        }
        return true;
    }

    /** Les etapes remplies. */
    public int stepsDone(ServerPlayer player) {
        int count = 0;
        for (Step step : steps) {
            if (step.done(player)) {
                count++;
            }
        }
        return count;
    }
}
