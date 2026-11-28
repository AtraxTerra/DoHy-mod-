package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

/**
 * Les barres de vie des withers de PalaWither : la bande de son overlay pour chaque
 * genre ({@code textures/gui/wither/bar}, 2709 x 45, soit la barre de 903 x 15 de sa
 * maquette en triple), et la couleur du genre ({@code IWither.getColor}).
 *
 * <p>La barre de boss d'un wither porte son nom : celui de son type tant qu'il n'est
 * pas renomme, c'est a lui qu'on reconnait le genre.
 */
public final class WitherBars {

    public static final int TEXTURE_WIDTH = 2709;
    public static final int TEXTURE_HEIGHT = 45;

    /** La bande et la couleur d'un genre. */
    public record Bar(Identifier texture, int color) {
    }

    private WitherBars() {
    }

    /** La barre du wither nomme {@code name}, ou {@code null} si ce n'est pas un wither de PalaWither. */
    public static Bar of(Component name) {
        if (name == null || !(name.getContents() instanceof TranslatableContents translatable)) {
            return null;
        }
        String key = translatable.getKey();
        for (WitherKind kind : WitherKind.values()) {
            if (key.equals("entity." + PalaMod.NAMESPACE + "." + kind.id())) {
                int color = color(kind);
                return color == 0 ? null : new Bar(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
                        "textures/gui/wither/bar/" + kind.id() + ".png"), color);
            }
        }
        return null;
    }

    /** La couleur de PalaWither ; 0 pour l'amusant, qui n'en vient pas. */
    private static int color(WitherKind kind) {
        return switch (kind) {
            case BASE -> 0xFF202020;
            case BABY -> 0xFF44B0B5;
            case PASSIVE -> 0xFF504440;
            case THIRSTY -> 0xFFE3C085;
            case PREDATOR -> 0xFFFF8B47;
            case SUPREME -> 0xFFFF7AEE;
            default -> 0;
        };
    }
}
