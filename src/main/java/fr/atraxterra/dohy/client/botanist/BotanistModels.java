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
    /** Les machines du lot 2c. */
    static final Identifier CENTRIFUGE_TEXTURE = id("textures/botanist/centrifuge.png");
    static final Identifier SEED_RESERVE_TEXTURE = id("textures/botanist/seed_reserve.png");
    static final Identifier LAB_CONTROL_TEXTURE = id("textures/botanist/lab_control.png");

    private static Optional<BedrockModel> pot;
    private static Optional<BedrockModel> scanner;
    private static Optional<BedrockModel> nibbler;
    private static Optional<BedrockModel> centrifuge;
    private static Optional<BedrockModel> seedReserve;
    private static Optional<BedrockModel> labControl;
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

    static synchronized BedrockModel centrifuge() {
        if (centrifuge == null) {
            centrifuge = Optional.ofNullable(BedrockModel.load(id("botanist/centrifuge/model.json"),
                    id("botanist/centrifuge/animation.json")));
        }
        return centrifuge.orElse(null);
    }

    static synchronized BedrockModel seedReserve() {
        if (seedReserve == null) {
            seedReserve = Optional.ofNullable(BedrockModel.load(id("botanist/seed_reserve/model.json"),
                    id("botanist/seed_reserve/animation.json")));
        }
        return seedReserve.orElse(null);
    }

    static synchronized BedrockModel labControl() {
        if (labControl == null) {
            labControl = Optional.ofNullable(BedrockModel.load(id("botanist/lab_control/model.json"),
                    id("botanist/lab_control/animation.json")));
        }
        return labControl.orElse(null);
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
        centrifuge = null;
        seedReserve = null;
        labControl = null;
        PLANTS.clear();
    }
}
