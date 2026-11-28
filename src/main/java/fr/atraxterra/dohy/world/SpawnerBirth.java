package fr.atraxterra.dohy.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Sait si une creature est nee d'un spawner.
 *
 * <p>NeoForge note le type d'apparition d'une creature ({@code Mob.getSpawnType()}) dans
 * {@code Mob.finalizeSpawn}. L'araignee venimeuse ne l'appelle pas (son {@code finalizeSpawn} ne
 * fait rien, pour ne jamais porter de cavalier) : sortie d'un spawner, elle n'avait pas de type.
 * La Mine la refusait donc comme un monstre sauvage, et le Chasseur la payait plein tarif au lieu
 * du quarantieme d'une creature de spawner (verifie en jeu le 2026-11-28 : aucune araignee
 * venimeuse dans sa salle, toutes les autres creatures dans la leur).
 *
 * <p>La marque est posee a la fin de l'apparition ({@link FinalizeSpawnEvent}), que tout spawner
 * declenche avant de faire entrer sa creature dans le monde, et se garde dans la sauvegarde de la
 * creature. La lisent : la Mine et la dimension du Mineur (les seuls monstres admis), et l'XP de
 * metier (le quarantieme d'une creature de spawner).
 */
@EventBusSubscriber
public final class SpawnerBirth {

    /** La marque (une etiquette de la creature, {@code /tag}). */
    public static final String TAG = "dohy.spawner";

    private SpawnerBirth() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() == EntitySpawnReason.SPAWNER) {
            event.getEntity().addTag(TAG);
        }
    }

    /** Vrai pour une creature nee d'un spawner : marquee, ou de type d'apparition {@code SPAWNER}. */
    public static boolean of(Entity entity) {
        return entity.entityTags().contains(TAG)
                || entity instanceof Mob mob && mob.getSpawnType() == EntitySpawnReason.SPAWNER;
    }
}
