package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Un ecran de menu generique : un titre, des chiffres en haut a droite, et des
 * sections de cartes.
 *
 * <p>Le menu principal, la banque, les contrats, la boutique, les collections,
 * le musee et les ameliorations n'ont pas de maquette Paladium : ils partagent
 * cette vue, dessinee dans le style des autres (cartes sombres, liseré de
 * couleur, titres en police large). Chaque controleur l'enregistre sous son
 * propre identifiant, ce qui suffit a router ses actions.
 *
 * @param back l'hexagone de gauche renvoie l'action « back »
 */
public record MenuView(Identifier view, String title, String subtitle, List<Stat> stats, List<Section> sections,
                       boolean back) implements UiView {

    /** Cartes larges, quatre par rangee. */
    public static final int GRID = 0;
    /** Lignes sur toute la largeur. */
    public static final int ROWS = 1;
    /** Petites vignettes d'objet, treize par rangee. */
    public static final int TILES = 2;

    public static final int NORMAL = 0;
    /** Grise : pas assez d'argent, deja actif, verrouille. */
    public static final int DISABLED = 1;
    /** Vert : termine, acquis, expose. */
    public static final int DONE = 2;

    /** Un chiffre du cartouche : « LIQUIDE 3.3K ». */
    /** La couleur d'un code de formatage, opaque ; gris pour un style sans couleur. */
    public static int color(net.minecraft.ChatFormatting format) {
        int rgb = switch (format) {
            case BLACK -> 0x000000;
            case DARK_BLUE -> 0x0000AA;
            case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA;
            case DARK_RED -> 0xAA0000;
            case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00;
            case GRAY -> 0xAAAAAA;
            case DARK_GRAY -> 0x555555;
            case BLUE -> 0x5555FF;
            case GREEN -> 0x55FF55;
            case AQUA -> 0x55FFFF;
            case RED -> 0xFF5555;
            case LIGHT_PURPLE -> 0xFF55FF;
            case YELLOW -> 0xFFFF55;
            case WHITE -> 0xFFFFFF;
            default -> 0x55545C;
        };
        return 0xFF000000 | rgb;
    }

    public record Stat(String label, String value, int color) {
        static final StreamCodec<RegistryFriendlyByteBuf, Stat> STREAM_CODEC = StreamCodec.of(
                (buffer, stat) -> {
                    buffer.writeUtf(stat.label());
                    buffer.writeUtf(stat.value());
                    buffer.writeInt(stat.color());
                },
                buffer -> new Stat(buffer.readUtf(), buffer.readUtf(), buffer.readInt()));
    }

    /**
     * Une section.
     *
     * @param icon losange de l'intertitre ({@code section_*}), vide pour aucun
     */
    public record Section(String title, String icon, int layout, List<Card> cards) {
        static final StreamCodec<RegistryFriendlyByteBuf, Section> STREAM_CODEC = StreamCodec.of(
                (buffer, section) -> {
                    buffer.writeUtf(section.title());
                    buffer.writeUtf(section.icon());
                    buffer.writeVarInt(section.layout());
                    Card.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, section.cards());
                },
                buffer -> new Section(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                        Card.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer)));
    }

    /**
     * Une carte.
     *
     * @param lines       lignes visibles sur la carte (deux ou trois)
     * @param badge       pastille en haut a droite, vide pour aucune
     * @param accent      couleur du liseré
     * @param action      action du clic gauche, vide pour aucune
     * @param rightAction action du clic droit, vide pour aucune
     * @param progress    barre en pour mille, -1 pour aucune
     * @param tooltip     lignes de la bulle, en plus du titre et des lignes
     */
    public record Card(ItemStack icon, String title, List<String> lines, String badge, int accent, int state,
                       String action, String rightAction, int progress, List<String> tooltip) {

        public static Card of(ItemStack icon, String title, List<String> lines) {
            return new Card(icon, title, lines, "", 0xFF55545C, NORMAL, "", "", -1, List.of());
        }

        public Card badge(String value) {
            return new Card(icon, title, lines, value, accent, state, action, rightAction, progress, tooltip);
        }

        public Card accent(int color) {
            return new Card(icon, title, lines, badge, color, state, action, rightAction, progress, tooltip);
        }

        public Card state(int value) {
            return new Card(icon, title, lines, badge, accent, value, action, rightAction, progress, tooltip);
        }

        public Card action(String left) {
            return new Card(icon, title, lines, badge, accent, state, left, rightAction, progress, tooltip);
        }

        public Card right(String value) {
            return new Card(icon, title, lines, badge, accent, state, action, value, progress, tooltip);
        }

        public Card progress(long done, long total) {
            int permille = total <= 0 ? 1000 : (int) Math.max(0, Math.min(1000, done * 1000 / total));
            return new Card(icon, title, lines, badge, accent, state, action, rightAction, permille, tooltip);
        }

        public Card tooltip(List<String> value) {
            return new Card(icon, title, lines, badge, accent, state, action, rightAction, progress, value);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.of(
                (buffer, card) -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, card.icon());
                    buffer.writeUtf(card.title());
                    strings(buffer, card.lines());
                    buffer.writeUtf(card.badge());
                    buffer.writeInt(card.accent());
                    buffer.writeVarInt(card.state());
                    buffer.writeUtf(card.action());
                    buffer.writeUtf(card.rightAction());
                    buffer.writeVarInt(card.progress() + 1);
                    strings(buffer, card.tooltip());
                },
                buffer -> new Card(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readUtf(), strings(buffer),
                        buffer.readUtf(), buffer.readInt(), buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(),
                        buffer.readVarInt() - 1, strings(buffer)));
    }

    private static void strings(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value);
        }
    }

    private static List<String> strings(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(buffer.readUtf());
        }
        return values;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                Identifier.STREAM_CODEC.encode(buffer, view.view());
                buffer.writeUtf(view.title());
                buffer.writeUtf(view.subtitle());
                Stat.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, view.stats());
                Section.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, view.sections());
                buffer.writeBoolean(view.back());
            },
            buffer -> new MenuView(Identifier.STREAM_CODEC.decode(buffer), buffer.readUtf(), buffer.readUtf(),
                    Stat.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    Section.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer), buffer.readBoolean()));

    @Override
    public Identifier viewId() {
        return view;
    }
}
