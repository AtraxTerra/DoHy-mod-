package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La resolution d'un verrou de technologie (chapitre 23, lot 23d) : cette machine, quel palier
 * la deverrouille, et ce joueur l'a-t-il ?
 *
 * <p>Comme pour les verrous de metier ({@code job/JobGate}), un index par identifiant est tenu a
 * cote des donnees, parce que la question se pose a chaque bloc pose. Il est reconstruit quand la
 * liste des domaines change d'instance — au demarrage et a chaque {@code /dohy reload}. Les motifs
 * a etoile sont resolus contre les registres de blocs et d'objets, espace de noms par espace de
 * noms ; les machines laissees libres ({@code data/tech_free.json}) n'y entrent jamais.
 */
public final class TechGate {

    /** Ce qu'il faut pour poser une machine : son domaine et son palier. */
    public record Lock(TechConfig.Domain domain, TechConfig.Tier tier) {
        /** « Energie II · Vapeur ». */
        public String label() {
            return tier.title(domain);
        }
    }

    private static volatile List<TechConfig.Domain> indexedFrom;
    private static volatile Map<String, Lock> byId = Map.of();

    private TechGate() {
    }

    private static synchronized void ensureIndex() {
        List<TechConfig.Domain> current = TechConfig.domains();
        if (current == indexedFrom) {
            return;
        }
        List<String> free = TechConfig.free();
        Map<String, Set<String>> byNamespace = new HashMap<>();
        for (Identifier key : BuiltInRegistries.BLOCK.keySet()) {
            byNamespace.computeIfAbsent(key.getNamespace(), ignored -> new LinkedHashSet<>()).add(key.toString());
        }
        for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
            byNamespace.computeIfAbsent(key.getNamespace(), ignored -> new LinkedHashSet<>()).add(key.toString());
        }
        Map<String, Lock> ids = new HashMap<>();
        for (TechConfig.Domain domain : current) {
            for (TechConfig.Tier tier : domain.tiers()) {
                Lock lock = new Lock(domain, tier);
                for (String pattern : tier.unlocks()) {
                    if (pattern.indexOf('*') < 0) {
                        if (!free(pattern, free)) {
                            // Le premier palier gagne : deux paliers pour une machine seraient une faute
                            // de donnees (la CI la refuse), et l'ordre d'une table ne doit pas decider.
                            ids.putIfAbsent(pattern, lock);
                        }
                        continue;
                    }
                    int colon = pattern.indexOf(':');
                    String namespace = colon < 0 ? "" : pattern.substring(0, colon);
                    List<String> candidates = new ArrayList<>();
                    if (namespace.isEmpty() || namespace.indexOf('*') >= 0) {
                        byNamespace.values().forEach(candidates::addAll);
                    } else {
                        candidates.addAll(byNamespace.getOrDefault(namespace, Set.of()));
                    }
                    for (String id : candidates) {
                        if (TechConfig.matches(pattern, id) && !free(id, free)) {
                            ids.putIfAbsent(id, lock);
                        }
                    }
                }
            }
        }
        byId = Map.copyOf(ids);
        indexedFrom = current;
    }

    private static boolean free(String id, List<String> free) {
        for (String pattern : free) {
            if (TechConfig.matches(pattern, id)) {
                return true;
            }
        }
        return false;
    }

    /** Le verrou de cet identifiant (« hbm:machine_press »), ou {@code null}. */
    public static Lock of(String id) {
        ensureIndex();
        return byId.get(id);
    }

    public static Lock of(Block block) {
        return of(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    public static Lock of(Item item) {
        return of(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    /** Les machines verrouillees que l'index connait (pour les essais et l'ecran). */
    public static int size() {
        ensureIndex();
        return byId.size();
    }

    /**
     * Vrai si le joueur a le palier de ce verrou : par la recherche, par les etapes de l'ancien
     * arbre, ou d'office. Sans nation, seuls les paliers d'office. Un verrou nul est toujours satisfait.
     */
    public static boolean allows(ServerPlayer player, Lock lock) {
        return lock == null || Tech.reached(FactionApi.data(player), lock.domain(), lock.tier().tier());
    }
}
