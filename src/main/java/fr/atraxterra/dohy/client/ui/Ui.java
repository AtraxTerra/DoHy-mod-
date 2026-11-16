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

    /**
     * La palette de l'interface v2, relevee sur les maquettes validees : fonds
     * anthracite en degrade, cartes grises au filet fin, rouge Berskar.
     */
    public static final class Theme {
        public static final int BG0 = 0xFF0D0E10;
        public static final int BG1 = 0xFF121315;
        public static final int BG2 = 0xFF17181B;
        public static final int BG3 = 0xFF1C1D21;
        public static final int SIDEBAR = 0xFF101113;
        public static final int CARD = 0xFF222427;
        public static final int CARD_HOVER = 0xFF2A2C31;
        public static final int EDGE = 0xFF2D2F34;
        public static final int LINE = 0xFF313338;
        public static final int LINE2 = 0xFF3B3E45;
        public static final int INSET = 0xFF141518;
        public static final int TEXT = 0xFFFFFFFF;
        public static final int TEXT2 = 0xFFC1C1C1;
        public static final int MUTED = 0xFF8B8E95;
        public static final int DIM = 0xFF5D6068;
        public static final int RED = 0xFFEF3926;
        public static final int RED_LIGHT = 0xFFF2492F;
        public static final int RED_D = 0xFFC92C1B;
        public static final int RED_DD = 0xFF7E170F;
        public static final int GREEN = 0xFF30CB1F;
        public static final int GREEN_L = 0xFF22E558;
        public static final int YELLOW = 0xFFFAC400;
        public static final int BLUE = 0xFF2F5BEA;
        public static final int CYAN = 0xFF2BC4C4;
        public static final int MAGENTA = 0xFFEC4FB5;
        public static final int ORANGE = 0xFFFF6F00;
        public static final int PINK = 0xFFF3445D;
        public static final int SHADOW = 0xFF000000;

        private Theme() {
        }
    }

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

    /**
     * Les pixels physiques par unite du dessin en cours. Chaque ecran du kit (et les
     * notifications) le pose avant de dessiner : la police pixel s'y cale.
     */
    private static float drawUnit = 3.0f;

    /**
     * La police pixel est une police bitmap (font/pixel.json) : un texel par pixel de
     * police, 12 texels rendus sur 9 unites a la taille de base (8). Un texel vaut donc
     * 0,75 / 8 de la taille demandee.
     */
    private static final float PIXEL_TEXEL = 0.75f / FONT_BASE;

    public static void drawUnit(float unit) {
        drawUnit = Math.max(0.25f, unit);
    }

    public static float drawUnit() {
        return drawUnit;
    }

    /**
     * La taille reellement dessinee. La police pixel n'est nette qu'a un nombre entier
     * de pixels physiques par texel : entre deux, des rangees entieres disparaissent
     * ou doublent (le E perd sa barre, le B devient un D). On l'arrondit donc au
     * multiple le plus proche, jamais en dessous d'un pixel. Les autres polices sont
     * vectorielles et gardent leur taille.
     */
    public static float snap(FontDescription face, float size) {
        if (!PIXEL.equals(face)) {
            return size;
        }
        float perSize = PIXEL_TEXEL * drawUnit;
        int pixels = Math.max(1, Math.round(size * perSize));
        return pixels / perSize;
    }

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
