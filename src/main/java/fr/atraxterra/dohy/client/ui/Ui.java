package fr.atraxterra.dohy.client.ui;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * DoHy UI : la palette, les polices et les images du kit d'interface.
 *
 * <p>Le style est celui des interfaces de Paladium (Paladium-UI, Zephyr-UI) :
 * fond anthracite, cadre gris a coins crantes, titres rouges en police
 * « Minecraft Dungeons », libelles en « Minecraft » large, texte courant en
 * Montserrat. Les couleurs sont relevees sur les captures fournies par le
 * joueur (shop, metiers, machines).
 *
 * <p>Tout le kit vit cote client : le serveur ne connait que les vues
 * ({@code fr.atraxterra.dohy.ui.view}), jamais un pixel.
 */
public final class Ui {

    // -- Palette ---------------------------------------------------------------

    /** Le fond des panneaux. */
    public static final int BG = 0xFF1E1D23;
    /** Le bord exterieur, plus sombre, qui donne les coins crantes. */
    public static final int OUTER = 0xFF15141A;
    /** Le filet du cadre. */
    public static final int FRAME = 0xFF40404A;
    /** Les zones en retrait : grille du shop, listes. */
    public static final int INSET = 0xFF16151A;
    /** Les champs : prix, quantite, recherche. */
    public static final int FIELD = 0xFF2A292F;
    public static final int FIELD_DARK = 0xFF0F0F11;

    public static final int SLOT_BORDER = 0xFFBFBEC3;
    public static final int SLOT_FILL = 0xFF18171C;
    public static final int SLOT_HOVER = 0xFF2C2B33;

    public static final int RED = 0xFFEF3826;
    public static final int RED_DARK = 0xFF89130F;
    public static final int RED_SHADE = 0xFFA82418;
    public static final int GREEN = 0xFF22E558;
    public static final int GREEN_DARK = 0xFF0E8A33;
    public static final int GRAY_BUTTON = 0xFF4A4955;
    public static final int GRAY_BUTTON_DARK = 0xFF2B2A33;

    public static final int WHITE = 0xFFFFFFFF;
    public static final int TEXT = 0xFFE9E8EE;
    public static final int MUTED = 0xFF8C8B93;
    public static final int DISABLED = 0xFF55545C;
    public static final int SCROLL_TRACK = 0xFF3A3940;
    public static final int SCROLL_THUMB = 0xFF9E9E9E;

    // -- Polices -----------------------------------------------------------------

    /** Les grands titres rouges : MÉTIERS, ADMIN SHOP, CRUSHER. */
    public static final FontDescription TITLE = font("title");
    /** Les libelles larges : MINEUR, VOTRE ARGENT, PRIX D'ACHAT. */
    public static final FontDescription WIDE = font("wide");
    /** Les intertitres fins des machines : Fuel, CRAFT, MELT. */
    public static final FontDescription PIXEL = font("pixel");
    /** Le texte courant. */
    public static final FontDescription BODY = font("body");
    public static final FontDescription BOLD = font("bold");

    /** La taille a laquelle chaque police est definie (font/*.json). */
    public static final float FONT_BASE = 8.0f;

    // -- Images ------------------------------------------------------------------

    public static final Identifier CLOSE = texture("icon_close");
    public static final Identifier SEARCH = texture("icon_search");
    public static final Identifier HOME = texture("icon_home");
    public static final Identifier MONEY = texture("money");
    public static final Identifier HEXA = texture("hexa_border");
    public static final Identifier QUEST_DONE = texture("quest_checked");
    public static final Identifier QUEST_TODO = texture("quest_unchecked");
    public static final Identifier HEAT = texture("heat");

    private Ui() {
    }

    public static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/gui/kit/" + path + ".png");
    }

    private static FontDescription font(String name) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, name));
    }

    /** Un texte dans une police du kit. */
    public static MutableComponent text(FontDescription font, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(font));
    }
}
