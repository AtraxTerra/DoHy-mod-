package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.palamod.botanist.BotanistCodex;
import fr.atraxterra.dohy.palamod.botanist.Mutation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Le codex du joueur, cote client : ce que le serveur en a envoye ({@link BotanistCodex.Sync}) a la
 * connexion, a l'ouverture du scanner et a chaque decouverte. L'ecran du scanner et celui du codex
 * le lisent.
 */
public final class ClientCodex {

    private static volatile Set<String> discovered = Set.of();
    private static volatile int level;

    private ClientCodex() {
    }

    static void accept(BotanistCodex.Sync sync) {
        discovered = Set.copyOf(new LinkedHashSet<>(sync.discovered()));
        level = sync.level();
    }

    public static boolean has(Mutation mutation) {
        return discovered.contains(mutation.id());
    }

    public static int count() {
        int found = 0;
        for (Mutation mutation : Mutation.values()) {
            if (has(mutation)) {
                found++;
            }
        }
        return found;
    }

    /** Le niveau de Fermier du joueur, tel qu'envoye avec le codex. */
    public static int level() {
        return level;
    }

    /** Reste-t-il une plante a decouvrir a son niveau ? (« A decouvrir a ton niveau ».) */
    public static boolean somethingToDiscover() {
        return toDiscover() > 0;
    }

    /** Les plantes qui restent a decouvrir a son niveau. */
    public static int toDiscover() {
        int left = 0;
        for (Mutation mutation : Mutation.values()) {
            if (mutation.level() <= level && !has(mutation)) {
                left++;
            }
        }
        return left;
    }

    /** Le prochain niveau ou de nouvelles plantes s'ouvrent (« Prochain deblocage »), ou -1. */
    public static int nextUnlock() {
        int next = -1;
        for (Mutation mutation : Mutation.values()) {
            if (mutation.level() > level && (next < 0 || mutation.level() < next)) {
                next = mutation.level();
            }
        }
        return next;
    }
}
