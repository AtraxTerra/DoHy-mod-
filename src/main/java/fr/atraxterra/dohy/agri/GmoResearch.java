package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * La recherche de la machine a OGM.
 *
 * <p>Un OGM ne se trouve pas : il se cherche. Sa recette est secrete — neuf
 * objets differents, chacun en quantite precise — et la seule facon de
 * l'apprendre est d'essayer. Un essai consomme toute la composition, juste ou
 * fausse ; rate, il dit quelle part de la composition est dans la recette et
 * donne un indice : la devinette d'un ingredient qui manque, ou la fourchette
 * d'une quantite fausse. C'est le principe de la machine de NationsGlory,
 * regle ici pour un joueur seul.
 *
 * <h2>D'ou vient la recette</h2>
 *
 * <p>Elle se tire de la graine du monde, du joueur, de l'OGM et de sa
 * <i>generation</i> : rien n'est stocke, et deux joueurs n'ont pas la meme, ce
 * qui laisse la recherche a chacun. La generation avance toutes les
 * {@code recipe_lifetime} graines fabriquees : la souche mute, et la recherche
 * recommence.
 */
public final class GmoResearch {

    private GmoResearch() {
    }

    /**
     * Une recette : les objets, dans l'ordre du tirage, et leur quantite.
     */
    public record Recipe(Map<Identifier, Integer> parts) {

        public boolean has(Identifier item) {
            return parts.containsKey(item);
        }

        public int quantity(Identifier item) {
            return parts.getOrDefault(item, 0);
        }
    }

    /**
     * Le verdict d'un essai.
     *
     * @param success    vrai si la composition etait la recette
     * @param percent    part de la composition qui est dans la recette
     * @param rightCount quantites justes
     * @param clues      les indices, dans l'ordre
     */
    public record Verdict(boolean success, int percent, int rightCount, List<Component> clues) {
    }

    public static AgriData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.AGRICULTURE);
    }

    /** La generation en cours de la recette de cet OGM pour ce joueur. */
    public static int generation(ServerPlayer player, Cereal gmo) {
        return data(player).crafted(gmo.key()) / Math.max(1, AgriConfig.config().gmo().recipeLifetime());
    }

    /** Graines encore fabricables avant que la recette change. */
    public static int untilMutation(ServerPlayer player, Cereal gmo) {
        int lifetime = Math.max(1, AgriConfig.config().gmo().recipeLifetime());
        return lifetime - data(player).crafted(gmo.key()) % lifetime;
    }

    public static boolean knows(ServerPlayer player, Cereal gmo) {
        return data(player).knows(gmo.key(), generation(player, gmo));
    }

    /** La recette en cours de cet OGM pour ce joueur. */
    public static Recipe recipe(ServerPlayer player, Cereal gmo) {
        long seed = player.level() instanceof ServerLevel level ? level.getSeed() : 0L;
        return recipe(seed, player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits(),
                gmo, generation(player, gmo));
    }

    static Recipe recipe(long worldSeed, long playerBits, Cereal gmo, int generation) {
        AgriConfig.Gmo config = AgriConfig.config().gmo();
        List<Identifier> pool = new ArrayList<>();
        for (AgriConfig.Ingredient ingredient : config.pool()) {
            if (BuiltInRegistries.ITEM.getOptional(ingredient.item()).isPresent() && !pool.contains(ingredient.item())) {
                pool.add(ingredient.item());
            }
        }
        Random random = new Random(worldSeed * 31L + playerBits * 131L + gmo.key().hashCode() * 8191L
                + generation * 1_000_003L);
        Collections.shuffle(pool, random);
        Map<Identifier, Integer> parts = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(config.ingredients(), pool.size()); index++) {
            Item item = BuiltInRegistries.ITEM.getValue(pool.get(index));
            int most = Math.max(1, Math.min(config.maxQuantity(), new ItemStack(item).getMaxStackSize()));
            parts.put(pool.get(index), 1 + random.nextInt(most));
        }
        return new Recipe(parts);
    }

    /** La fourchette qui contient cette quantite : des tranches alignees, larges de {@code range_width}. */
    static int[] range(int quantity) {
        AgriConfig.Gmo config = AgriConfig.config().gmo();
        int width = Math.max(1, config.rangeWidth());
        int low = (quantity - 1) / width * width + 1;
        return new int[]{low, Math.min(config.maxQuantity(), low + width - 1)};
    }

    /** Juge une composition sans rien consommer. */
    static Verdict judge(Recipe recipe, Map<Identifier, Integer> composition, int clueCount) {
        int inRecipe = 0;
        int right = 0;
        for (Map.Entry<Identifier, Integer> entry : composition.entrySet()) {
            if (recipe.has(entry.getKey())) {
                inRecipe++;
                if (recipe.quantity(entry.getKey()) == entry.getValue()) {
                    right++;
                }
            }
        }
        boolean success = right == recipe.parts().size() && composition.size() == recipe.parts().size();
        int percent = composition.isEmpty() ? 0 : Math.round(inRecipe * 100.0f / composition.size());
        List<Component> clues = new ArrayList<>();
        if (success) {
            return new Verdict(true, 100, right, clues);
        }

        // Les devinettes des ingredients qui manquent, dans l'ordre de la recette.
        List<Identifier> missing = new ArrayList<>();
        for (Identifier item : recipe.parts().keySet()) {
            if (!composition.containsKey(item)) {
                missing.add(item);
            }
        }
        int nextMissing = 0;
        AgriConfig.Gmo config = AgriConfig.config().gmo();
        for (Map.Entry<Identifier, Integer> entry : composition.entrySet()) {
            if (clues.size() >= clueCount) {
                break;
            }
            Identifier item = entry.getKey();
            Component name = name(item);
            if (!recipe.has(item)) {
                MutableComponent clue = Component.empty().append(name).append(" n'entre pas dans la recette");
                if (nextMissing < missing.size()) {
                    clue.append(". A la place : " + config.clue(missing.get(nextMissing++)));
                }
                clues.add(clue);
            } else if (recipe.quantity(item) != entry.getValue()) {
                int[] range = range(recipe.quantity(item));
                if (entry.getValue() >= range[0] && entry.getValue() <= range[1]) {
                    clues.add(Component.empty().append(name).append(entry.getValue() < recipe.quantity(item)
                            ? " : il en faut un peu plus" : " : il en faut un peu moins"));
                } else {
                    clues.add(Component.empty().append(name).append(" : il en faut entre " + range[0] + " et "
                            + range[1]));
                }
            }
        }
        while (clues.size() < clueCount && nextMissing < missing.size()) {
            clues.add(Component.literal("Il manque : " + config.clue(missing.get(nextMissing++))));
        }
        return new Verdict(false, percent, right, clues);
    }

    private static Component name(Identifier item) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(item)).getHoverName();
    }

    /**
     * Lance un essai avec la composition du joueur.
     *
     * @return le refus a afficher, ou {@code null} si l'essai a eu lieu (reussi ou non)
     */
    public static String attempt(ServerPlayer player, Cereal gmo) {
        AgriConfig.Gmo config = AgriConfig.config().gmo();
        int level = JobApi.level(player, Job.FARMER);
        if (level < config.minLevel()) {
            return "Il faut etre Fermier niveau " + config.minLevel() + ".";
        }
        AgriData data = data(player);
        Map<Identifier, Integer> composition = data.composition();
        if (composition.isEmpty()) {
            return "La composition est vide.";
        }
        for (Map.Entry<Identifier, Integer> entry : composition.entrySet()) {
            Item item = BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElse(null);
            if (item == null || AgriInv.count(player, item) < entry.getValue()) {
                return "Il te manque des ingredients de la composition.";
            }
        }
        composition.forEach((id, quantity) -> AgriInv.take(player, BuiltInRegistries.ITEM.getValue(id), quantity));

        int number = data.countAttempt(gmo.key());
        Recipe recipe = recipe(player, gmo);
        Verdict verdict = judge(recipe, composition, level >= config.secondClueLevel() ? 2 : 1);
        if (!verdict.success()) {
            MutableComponent head = Component.literal("Essai " + number + " : " + verdict.percent()
                    + " % de la composition est dans la recette");
            if (level >= config.countHintLevel()) {
                head.append(", " + verdict.rightCount() + " quantite" + (verdict.rightCount() > 1 ? "s" : "")
                        + " juste" + (verdict.rightCount() > 1 ? "s" : ""));
            }
            player.sendSystemMessage(Component.literal("Machine a OGM : ").withStyle(ChatFormatting.RED)
                    .append(head.copy().withStyle(ChatFormatting.GRAY)));
            // Une note du carnet : le verdict, puis un indice par ligne. Chaque
            // partie est un texte traduisible (les noms d'objets), d'ou le separateur.
            List<String> parts = new ArrayList<>();
            parts.add(MenuView.text(head));
            for (Component clue : verdict.clues()) {
                player.sendSystemMessage(Component.literal("  Indice : ").withStyle(ChatFormatting.GOLD)
                        .append(clue.copy().withStyle(ChatFormatting.WHITE)));
                parts.add(MenuView.text(clue));
            }
            data.note(gmo.key(), String.join(AgriData.NOTE_SEPARATOR, parts));
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    0.6f, 0.8f);
            return null;
        }

        int generation = generation(player, gmo);
        boolean discovery = !data.knows(gmo.key(), generation);
        if (discovery) {
            data.learn(gmo.key(), generation);
            JobApi.grant(player, Job.FARMER, config.discoveryXp());
            player.sendSystemMessage(Component.literal("Machine a OGM : recette du " + gmo.label().toLowerCase()
                    + " decouverte en " + number + " essais !").withStyle(ChatFormatting.GOLD));
        }
        data.addCrafted(gmo.key(), config.seedsPerCraft());
        AgriInv.give(player, DoHyAgri.seeds(gmo), config.seedsPerCraft());
        JobApi.grant(player, Job.FARMER, config.craftXp());
        player.sendSystemMessage(Component.literal("Machine a OGM : " + config.seedsPerCraft() + " graines de "
                + gmo.label().toLowerCase() + ".").withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS,
                0.7f, 1.4f);
        if (generation(player, gmo) != generation) {
            // La souche a mute : la recette n'est plus la meme, le carnet ne vaut plus rien.
            data.forgetNotes(gmo.key());
            composition.clear();
            player.sendSystemMessage(Component.literal("Machine a OGM : la souche de " + gmo.label().toLowerCase()
                    + " a mute. Sa recette a change, la recherche reprend.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return null;
    }
}
