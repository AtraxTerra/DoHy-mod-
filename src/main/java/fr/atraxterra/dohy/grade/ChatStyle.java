package fr.atraxterra.dohy.grade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le chat des grades : les couleurs a la facon des serveurs ({@code &a}, {@code &l}...) a partir
 * de Heros, les emojis ({@code :coeur:}...) a partir de Legende.
 *
 * <p>Les emojis sont des symboles de la police du jeu (la police Unicode prend le relais pour ceux
 * que la police pixel n'a pas) : ils s'affichent sans pack de ressources. Ils sont ecrits ici en
 * echappements, la source reste en ASCII.
 */
public final class ChatStyle {

    /** Les emojis, dans l'ordre de l'encyclopedie. */
    public static final Map<String, String> EMOJIS = new LinkedHashMap<>();

    static {
        EMOJIS.put(":coeur:", "\u2764");
        EMOJIS.put(":etoile:", "\u2605");
        EMOJIS.put(":sourire:", "\u263A");
        EMOJIS.put(":triste:", "\u2639");
        EMOJIS.put(":soleil:", "\u2600");
        EMOJIS.put(":lune:", "\u263D");
        EMOJIS.put(":nuage:", "\u2601");
        EMOJIS.put(":pluie:", "\u2602");
        EMOJIS.put(":neige:", "\u2744");
        EMOJIS.put(":eclair:", "\u26A1");
        EMOJIS.put(":feu:", "\u2668");
        EMOJIS.put(":epee:", "\u2694");
        EMOJIS.put(":crane:", "\u2620");
        EMOJIS.put(":couronne:", "\u265B");
        EMOJIS.put(":note:", "\u266B");
        EMOJIS.put(":fleur:", "\u273F");
        EMOJIS.put(":cafe:", "\u2615");
        EMOJIS.put(":ok:", "\u2714");
        EMOJIS.put(":non:", "\u2718");
        EMOJIS.put(":fleche:", "\u279C");
        EMOJIS.put(":paix:", "\u262E");
        EMOJIS.put(":yin:", "\u262F");
    }

    private ChatStyle() {
    }

    /** Remplace les codes d'emoji par leur symbole. */
    public static String emojis(String text) {
        if (text.indexOf(':') < 0) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> entry : EMOJIS.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /**
     * Le message avec ses codes de couleur appliques : {@code &0} a {@code &f} pour les couleurs,
     * {@code &l} gras, {@code &o} italique, {@code &n} souligne, {@code &m} barre, {@code &r} pour
     * revenir au texte normal. Le texte illisible ({@code &k}) n'est pas permis ; {@code &&} ecrit
     * un {@code &}.
     */
    public static MutableComponent colors(String text) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '&' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == '&') {
                    run.append('&');
                    index++;
                    continue;
                }
                ChatFormatting format = ChatFormatting.getByCode(code);
                if (format != null && format != ChatFormatting.OBFUSCATED) {
                    if (!run.isEmpty()) {
                        out.append(Component.literal(run.toString()).withStyle(style));
                        run.setLength(0);
                    }
                    // Une couleur (0-9, a-f) repart d'un style neuf, comme sur les serveurs ; un format s'ajoute.
                    boolean color = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
                    style = format == ChatFormatting.RESET ? Style.EMPTY
                            : color ? Style.EMPTY.withColor(format) : style.applyFormat(format);
                    index++;
                    continue;
                }
            }
            run.append(current);
        }
        if (!run.isEmpty()) {
            out.append(Component.literal(run.toString()).withStyle(style));
        }
        return out;
    }

    /** Les lignes d'aide de l'encyclopedie et de la page des grades. */
    public static List<String> colorHelp() {
        return List.of("&a vert, &c rouge, &e jaune, &b bleu clair, &d rose, &6 or...",
                "&l gras, &o italique, &n souligne, &m barre, &r normal");
    }
}
