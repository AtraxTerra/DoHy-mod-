package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

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
 */
public record BossSpec(String name, EntityType<? extends Mob> type, float scale, boolean duo,
                       List<BossAbility> abilities) {

    public static BossSpec of(String name, EntityType<? extends Mob> type, float scale, BossAbility... abilities) {
        return new BossSpec(name, type, scale, false, List.of(abilities));
    }

    public static BossSpec duo(String name, EntityType<? extends Mob> type, float scale, BossAbility... abilities) {
        return new BossSpec(name, type, scale, true, List.of(abilities));
    }
}
