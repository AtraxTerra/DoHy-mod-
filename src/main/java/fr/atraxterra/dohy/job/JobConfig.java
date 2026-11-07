package fr.atraxterra.dohy.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * La table des deblocages, chargee depuis le datapack.
 *
 * <p>Tout ce qui est chiffre vit dans le datapack — principe 5 de
 * {@code DESIGN.md}. Ici cela veut dire que les chapitres suivants (minerais,
 * cultures, machines) <b>n'ecrivent aucune logique de verrou</b> : ils
 * enregistrent leurs blocs et leurs items, et ajoutent une ligne a
 * {@code jobs.json}. Le verrou est deja la.
 *
 * <p>Corollaire assume : une entree peut nommer un item qui n'existe pas encore.
 * Elle est alors inerte, et le chargement la signale. C'est le meme filet que
 * {@code UpgradeApi.EFFECTS} pour les ameliorations et que
 * {@code SkillConfig.IMPLEMENTED} pour les perks — une regle d'equilibrage
 * posee et jamais appliquee est pire qu'une regle absente, parce qu'elle fait
 * croire que le verrou tient.
 */
@EventBusSubscriber
public final class JobConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "jobs.json");

    private static volatile Config config = Config.FALLBACK;

    private JobConfig() {
    }

    public static Config config() {
        return config;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Table des metiers introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Table des metiers illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            report(config);
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la table des metiers", error);
            return false;
        }
    }

    /**
     * Journalise ce que la table contient, et surtout ce qu'elle promet sans
     * tenir.
     *
     * <p>Appele depuis {@code ServerStartedEvent}, donc les registres sont
     * prets : on peut demander au registre si l'identifiant existe vraiment.
     */
    private static void report(Config loaded) {
        Map<Job, Integer> perJob = new LinkedHashMap<>();
        TreeSet<String> unknownJob = new TreeSet<>();
        TreeSet<String> missing = new TreeSet<>();

        for (Unlock unlock : loaded.unlocks()) {
            Job job = Job.byKey(unlock.job());
            if (job == null) {
                unknownJob.add(unlock.job());
                continue;
            }
            perJob.merge(job, 1, Integer::sum);
            if (!unlock.isTag() && !exists(unlock.id())) {
                missing.add(unlock.id());
            }
        }

        DoHy.LOGGER.info("Metiers charges : {} deblocages ({})",
                loaded.unlocks().size(), describe(perJob));

        if (!unknownJob.isEmpty()) {
            DoHy.LOGGER.error("Metiers inconnus dans jobs.json, entrees ignorees : {}", unknownJob);
        }
        if (!missing.isEmpty()) {
            DoHy.LOGGER.warn("Deblocages portant sur un item ou un bloc non enregistre, "
                    + "donc sans effet ({} entrees) : {}", missing.size(), missing);
            DoHy.LOGGER.warn("Normal tant que les chapitres minerais, cultures et machines "
                    + "ne sont pas faits. Voir /metiers audit.");
        }
    }

    private static String describe(Map<Job, Integer> perJob) {
        List<String> parts = new ArrayList<>();
        for (Job job : Job.values()) {
            parts.add(job.display() + " " + perJob.getOrDefault(job, 0));
        }
        return String.join(", ", parts);
    }

    /** Vrai si l'identifiant designe un item ou un bloc reellement enregistre. */
    public static boolean exists(String raw) {
        Identifier id = parse(raw);
        if (id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.BLOCK.containsKey(id);
    }

    /** {@code null} plutot qu'une exception : une faute de frappe ne doit pas tuer le chargement. */
    public static Identifier parse(String raw) {
        try {
            return Identifier.bySeparator(raw, ':');
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Un deblocage : un item ou un bloc, le metier et le niveau exiges.
     *
     * <p>{@code id} peut commencer par {@code #} pour viser un tag — meme
     * convention que les sources d'XP de {@code skills.json}, pour qu'il n'y ait
     * qu'une syntaxe a connaitre dans tout le datapack.
     *
     * <p>{@code label} est facultatif et sert aux messages : le nom traduit de
     * l'item est preferable, mais un item absent du registre n'en a pas, et
     * afficher {@code palamod:cobblebreaker} au joueur serait une fuite de
     * plomberie.
     */
    public record Unlock(String id, String job, int level, Optional<String> label) {

        static final Codec<Unlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Unlock::id),
                Codec.STRING.fieldOf("job").forGetter(Unlock::job),
                Codec.INT.fieldOf("level").forGetter(Unlock::level),
                Codec.STRING.optionalFieldOf("label").forGetter(Unlock::label)
        ).apply(instance, Unlock::new));

        public boolean isTag() {
            return id.startsWith("#");
        }

        /** L'identifiant sans le {@code #} eventuel. */
        public String plainId() {
            return isTag() ? id.substring(1) : id;
        }
    }

    public record Config(List<Unlock> unlocks) {

        /** Repli : aucun verrou. Un datapack illisible ne doit rien interdire. */
        static final Config FALLBACK = new Config(List.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Unlock.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(Config::unlocks)
        ).apply(instance, Config::new));
    }
}
