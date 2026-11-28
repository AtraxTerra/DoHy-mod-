package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Les modeles du laboratoire, ceux de JobsReborn ({@code assets/palamod/botanist},
 * repris par {@code tools/botanist_lab_assets.py}) : lus au premier pot affiche,
 * gardes jusqu'au rechargement des ressources. Un modele illisible est note une
 * fois et n'est plus retente.
 */
public final class BotanistModels {

    static final Identifier POT_TEXTURE = id("textures/botanist/pot.png");
    /** La terre cuite d'une plante qui a mute (lot 2b). */
    static final Identifier POT_MUTATED_TEXTURE = id("textures/botanist/pot_mutated.png");
    static final Identifier SCANNER_TEXTURE = id("textures/botanist/scanner.png");
    static final Identifier NIBBLER_TEXTURE = id("textures/botanist/nibbler.png");

    private static Optional<BedrockModel> pot;
    private static Optional<BedrockModel> scanner;
    private static Optional<BedrockModel> nibbler;
    private static final Map<Mutation, Optional<BedrockModel>> PLANTS = new EnumMap<>(Mutation.class);
    private static final Map<Mutation, Identifier> TEXTURES = new EnumMap<>(Mutation.class);

    private BotanistModels() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path);
    }

    static synchronized BedrockModel pot() {
        if (pot == null) {
            pot = Optional.ofNullable(BedrockModel.load(id("botanist/pot/model.json"), id("botanist/pot/animation.json")));
        }
        return pot.orElse(null);
    }

    static synchronized BedrockModel scanner() {
        if (scanner == null) {
            scanner = Optional.ofNullable(BedrockModel.load(id("botanist/scanner/model.json"),
                    id("botanist/scanner/animation.json")));
        }
        return scanner.orElse(null);
    }

    static synchronized BedrockModel nibbler() {
        if (nibbler == null) {
            nibbler = Optional.ofNullable(BedrockModel.load(id("botanist/nibbler/model.json"),
                    id("botanist/nibbler/animation.json")));
        }
        return nibbler.orElse(null);
    }

    public static synchronized BedrockModel plant(Mutation mutation) {
        return PLANTS.computeIfAbsent(mutation, key -> Optional.ofNullable(BedrockModel.load(
                id("botanist/mutation/" + key.id() + "/model.json"),
                id("botanist/mutation/" + key.id() + "/animation.json")))).orElse(null);
    }

    public static synchronized Identifier texture(Mutation mutation) {
        return TEXTURES.computeIfAbsent(mutation, key -> id("textures/botanist/mutation/" + key.id() + ".png"));
    }

    /** Au rechargement des ressources. */
    static synchronized void clear() {
        pot = null;
        scanner = null;
        nibbler = null;
        PLANTS.clear();
    }
}
