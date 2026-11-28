package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les modeles de PalaWither ({@code assets/palamod/wither}, repris par
 * {@code tools/palawither_assets.py}) : les cinq withers et le receptacle. Lus au
 * premier dessin, gardes jusqu'au rechargement des ressources ; un modele illisible
 * est note une fois et n'est plus retente.
 */
final class WitherModels {

    /**
     * Les os lumineux du predateur et du supreme : l'anneau sous eux et les eclats qui
     * les entourent ({@code lightmap: false} dans le jar), en pleine lumiere.
     */
    private static final Set<String> GLOW = Set.of("ring", "ring2", "vfx");

    static final Identifier RECEPTACLE_OFF = id("textures/entity/wither_receptacle/off.png");
    static final Identifier RECEPTACLE_ON = id("textures/entity/wither_receptacle/on.png");

    private static final Map<WitherKind, Optional<BedrockModel>> WITHERS = new EnumMap<>(WitherKind.class);
    private static final Map<WitherKind, Identifier> TEXTURES = new EnumMap<>(WitherKind.class);
    private static Optional<BedrockModel> receptacle;

    private WitherModels() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path);
    }

    /** Les genres de PalaWither ; le wither de base et l'amusant gardent le modele du vanilla. */
    static boolean bedrock(WitherKind kind) {
        return switch (kind) {
            case BABY, PASSIVE, THIRSTY, PREDATOR, SUPREME -> true;
            default -> false;
        };
    }

    static synchronized BedrockModel wither(WitherKind kind) {
        if (!bedrock(kind)) {
            return null;
        }
        return WITHERS.computeIfAbsent(kind, key -> Optional.ofNullable(BedrockModel.load(
                id("wither/" + key.id() + "/model.json"), id("wither/" + key.id() + "/animation.json"), GLOW)))
                .orElse(null);
    }

    static synchronized Identifier texture(WitherKind kind) {
        return TEXTURES.computeIfAbsent(kind, key -> id("textures/entity/wither/" + key.id() + ".png"));
    }

    static synchronized BedrockModel receptacle() {
        if (receptacle == null) {
            receptacle = Optional.ofNullable(BedrockModel.load(id("wither/receptacle/model.json"), null));
        }
        return receptacle.orElse(null);
    }

    /** Au rechargement des ressources. */
    static synchronized void clear() {
        WITHERS.clear();
        receptacle = null;
    }
}
