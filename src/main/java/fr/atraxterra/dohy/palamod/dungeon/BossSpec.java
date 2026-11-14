package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Un mini-boss ou un boss nomme (« Ennemis » du wiki).
 *
 * <p>Le wiki ne donne que les noms et, pour deux mini-boss, « c'est un duo ».
 * Chacun est ici un monstre vanilla agrandi ({@code scale}), nomme, avec deux
 * ou trois capacites tirees de {@link BossAbility}.
 *
 * @param name      le nom affiche (le wiki donne les noms V12 en anglais)
 * @param type      le monstre de base
 * @param scale     sa taille
 * @param duo       deux exemplaires, chacun a 60 % des points de vie
 * @param abilities ses capacites
 * @param dark      la forme Dark d'un boss ({@link #darkForm()})
 * @param skin      le nom sous lequel {@link RpgSkins} cherche son modele : le
 *                  nom d'origine, pour une forme Dark
 */
public record BossSpec(String name, EntityType<? extends Mob> type, float scale, boolean duo,
                       List<BossAbility> abilities, boolean dark, String skin) {

    public static BossSpec of(String name, EntityType<? extends Mob> type, float scale, BossAbility... abilities) {
        return new BossSpec(name, type, scale, false, List.of(abilities), false, name);
    }

    public static BossSpec duo(String name, EntityType<? extends Mob> type, float scale, BossAbility... abilities) {
        return new BossSpec(name, type, scale, true, List.of(abilities), false, name);
    }

    /**
     * Sa forme Dark (mode Difficile, paliers en 0) : le meme monstre, son
     * modele dans sa variante noire, une capacite sombre de plus et une
     * seconde phase a mi-vie ({@link DungeonRun}).
     */
    public BossSpec darkForm() {
        if (dark) {
            return this;
        }
        List<BossAbility> all = new ArrayList<>(abilities);
        BossAbility[] shadows = {BossAbility.DARK_PULSE, BossAbility.SHADOWS, BossAbility.DRAIN};
        all.add(shadows[Math.floorMod(name.hashCode(), shadows.length)]);
        return new BossSpec(name + " Dark", type, scale, duo, List.copyOf(all), true, skin);
    }
}
