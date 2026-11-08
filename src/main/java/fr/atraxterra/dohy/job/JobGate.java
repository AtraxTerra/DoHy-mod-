package fr.atraxterra.dohy.job;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La resolution d'un verrou : cet item, ce bloc, est-il permis a ce joueur ?
 *
 * <p>Un index est tenu a cote de la table du datapack, parce que la question est
 * posee a chaque clic droit : parcourir la liste a chaque fois serait une
 * recherche lineaire dans la boucle d'interaction. L'index est reconstruit quand
 * {@link JobConfig#config()} change d'instance, ce qui arrive au chargement et a
 * chaque {@code /dohy reload} — donc aucune invalidation a ecrire a la main, et
 * pas de cache qui survit a un rechargement.
 *
 * <p>Les tags sont resolus a l'usage et non au chargement, comme dans
 * {@code JobXpEvents} : les registres de tags ne sont pas forcement prets au
 * moment ou le datapack est lu.
 */
public final class JobGate {

    /** Snapshot de la configuration a partir de laquelle l'index a ete bati. */
    private static volatile JobConfig.Config indexedFrom;
    private static volatile Map<String, JobConfig.Unlock> byId = Map.of();
    private static volatile List<JobConfig.Unlock> byTag = List.of();

    private JobGate() {
    }

    private static void ensureIndex() {
        JobConfig.Config current = JobConfig.config();
        if (current == indexedFrom) {
            return;
        }
        Map<String, JobConfig.Unlock> ids = new HashMap<>();
        List<JobConfig.Unlock> tags = new ArrayList<>();
        for (JobConfig.Unlock unlock : current.unlocks()) {
            if (Job.byKey(unlock.job()) == null) {
                continue;
            }
            if (unlock.isTag()) {
                tags.add(unlock);
            } else {
                // Le premier gagne : deux lignes pour le meme item seraient une
                // erreur de datapack, et laisser l'ordre d'iteration d'une map
                // decider du niveau requis rendrait le bug intermittent.
                ids.putIfAbsent(unlock.plainId(), unlock);
            }
        }
        byId = Map.copyOf(ids);
        byTag = List.copyOf(tags);
        indexedFrom = current;
    }

    /**
     * Le verrou portant sur cet item, ou {@code null}.
     *
     * <p>Le test de tag passe par la pile et non par l'item : {@code ItemStack}
     * expose {@code is(TagKey)} de longue date, la ou le holder de registre d'un
     * {@code Item} a change de nom plusieurs fois. On prend la voie stable.
     */
    public static JobConfig.Unlock of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ensureIndex();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        JobConfig.Unlock direct = byId.get(id.toString());
        if (direct != null) {
            return direct;
        }
        for (JobConfig.Unlock unlock : byTag) {
            Identifier tagId = JobConfig.parse(unlock.plainId());
            if (tagId == null) {
                continue;
            }
            // Variable explicitement typee, comme dans JobXpEvents : laisser
            // l'inference deduire le parametre de TagKey depuis l'argument de
            // is(...) marche ici mais se casse des qu'une surcharge s'ajoute.
            TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(), tagId);
            if (stack.is(tag)) {
                return unlock;
            }
        }
        return null;
    }

    /** Le verrou portant sur cet item, hors contexte de pile. Identifiant seul. */
    public static JobConfig.Unlock of(Item item) {
        ensureIndex();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return byId.get(id.toString());
    }

    /** Le verrou portant sur ce bloc, ou {@code null}. */
    public static JobConfig.Unlock of(BlockState state) {
        ensureIndex();
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        JobConfig.Unlock direct = byId.get(id.toString());
        if (direct != null) {
            return direct;
        }
        for (JobConfig.Unlock unlock : byTag) {
            Identifier tagId = JobConfig.parse(unlock.plainId());
            if (tagId == null) {
                continue;
            }
            TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(), tagId);
            if (state.is(tag)) {
                return unlock;
            }
        }
        return null;
    }

    /** Vrai si le joueur satisfait ce verrou. Un verrou nul est toujours satisfait. */
    public static boolean allows(ServerPlayer player, JobConfig.Unlock unlock) {
        if (unlock == null) {
            return true;
        }
        Job job = Job.byKey(unlock.job());
        return job == null || JobApi.meets(player, job, unlock.level());
    }

    /** Tous les deblocages d'un metier, du plus bas niveau au plus haut. */
    public static List<JobConfig.Unlock> unlocksOf(Job job) {
        List<JobConfig.Unlock> list = new ArrayList<>();
        for (JobConfig.Unlock unlock : JobConfig.config().unlocks()) {
            if (job.key().equalsIgnoreCase(unlock.job())) {
                list.add(unlock);
            }
        }
        list.sort((left, right) -> Integer.compare(left.level(), right.level()));
        return list;
    }

    /** Le prochain deblocage d'un metier au-dessus du niveau courant, ou {@code null}. */
    public static JobConfig.Unlock next(ServerPlayer player, Job job) {
        int level = JobApi.level(player, job);
        JobConfig.Unlock best = null;
        for (JobConfig.Unlock unlock : JobConfig.config().unlocks()) {
            if (!job.key().equalsIgnoreCase(unlock.job()) || unlock.level() <= level) {
                continue;
            }
            if (best == null || unlock.level() < best.level()) {
                best = unlock;
            }
        }
        return best;
    }
}
