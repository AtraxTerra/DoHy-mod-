package fr.atraxterra.dohy.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Le style des ecrans de conteneur (coffres, four, etabli, inventaire), partage par
 * les ecrans du kit et par ceux du jeu qu'on redessine.
 *
 * <p>Les cases d'un conteneur sont placees par le menu, au pixel d'interface : on ne
 * les deplace pas, on dessine autour. Une unite vaut donc ici un pixel d'interface,
 * et {@link Paint} travaille en pixels physiques ({@code guiScale} par unite) pour
 * garder ses coins lisses. A l'echelle d'interface automatique d'un ecran 1080p, une
 * case fait 64 pixels : la taille des maquettes.
 *
 * <p>La fenetre deborde de {@link #HEADER} au-dessus du rectangle du jeu : c'est la
 * bande de l'en-tete (icone, titre, boutons). L'ecran descend de la moitie pour
 * rester centre.
 */
public final class ContainerStyle {

    /** La bande de l'en-tete, au-dessus du rectangle du jeu. */
    public static final int HEADER = 20;
    /** La rangee des boutons de l'en-tete (croix, livre), en coordonnees du panneau. */
    public static final int BUTTON_Y = -14;
    public static final int BUTTON = 13;

    private static final int SLOT_FILL = 0xFF101113;
    private static final int SLOT_EDGE = 0xFF2A2C31;
    private static final int BUTTON_FILL = 0xFF222427;
    private static final int BUTTON_EDGE = 0xFF2E3035;

    private ContainerStyle() {
    }

    public static int guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Un pinceau du kit pour cet ecran : polices pixel calees, coins lisses. */
    public static UiGraphics begin(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int scale = guiScale();
        Ui.drawUnit(scale);
        UiGraphics g = new UiGraphics(graphics, font, mouseX, mouseY);
        g.paint(new Paint(graphics, scale));
        return g;
    }

    /**
     * Le haut du rectangle du jeu une fois la fenetre centree avec son en-tete : on
     * descend de la moitie de l'en-tete, sans jamais sortir l'inventaire par le bas.
     * Calcule depuis la hauteur de l'ecran, donc stable a chaque appel.
     */
    public static int top(int screenHeight, int imageHeight) {
        int base = (screenHeight - imageHeight) / 2;
        return Math.max(base, Math.min(base + HEADER / 2, screenHeight - imageHeight - 1));
    }

    /** Le voile sur le monde, comme les menus du kit (pas de flou). */
    public static void veil(GuiGraphicsExtractor graphics, int width, int height) {
        if (Minecraft.getInstance().level != null) {
            graphics.fill(0, 0, width, height, 0x70000000);
        }
    }

    /** La fenetre : ombre douce, fond, bordure fine, coins arrondis. */
    public static void window(Paint p, float x, float y, float width, float height) {
        p.shadow(x + 4, y + 8, width - 8, height - 6, 14, Paint.alpha(Ui.Theme.SHADOW, 0.55f));
        p.framed(x, y, width, height, 5.0f, Ui.Theme.BG2, 0xFF2C2E33, 1);
    }

    /** La fenetre d'un ecran de {@code width} x {@code height}, avec la bande de l'en-tete. */
    public static void window(Paint p, int width, int height) {
        window(p, 0, -HEADER, width, height + HEADER + 1);
    }

    /** Un encart creux (la zone de la machine). */
    public static void inset(Paint p, float x, float y, float width, float height) {
        p.framed(x, y, width, height, 4.5f, 0xFF141518, 0xFF24262A, 1);
    }

    /**
     * L'en-tete : l'icone dans sa tuile, puis le titre en police de titre, reduit pour
     * laisser {@code reserved} unites aux boutons de droite.
     */
    public static void header(UiGraphics g, Paint p, ItemStack icon, String title, int width, float reserved) {
        header(g, p, icon, title, 8.0f, -15.0f, width - 8.0f - reserved);
    }

    /** L'en-tete en (x, y) : la tuile de 16, puis le titre dans {@code maxWidth} (tuile comprise). */
    public static void header(UiGraphics g, Paint p, ItemStack icon, String title, float x, float y, float maxWidth) {
        int tile = Paint.mix(0xFF1A1B1E, Ui.Theme.RED, 0.18f);
        p.framed(x, y, 16.0f, 16.0f, 4.0f, tile, Paint.mix(tile, Ui.Theme.RED, 0.45f), 1);
        g.item(icon, x + 2.0f, y + 2.0f, 12.0f, false);
        String text = plain(title).toUpperCase(Locale.ROOT);
        // La tuile (20), puis un peu d'air avant les boutons : un titre long ne touche plus la croix.
        maxWidth -= 24.0f;
        float size = 11.0f;
        while (size > 6.0f && g.width(Ui.TITLE, text, size) > maxWidth) {
            size -= 0.5f;
        }
        text = g.fit(Ui.TITLE, text, size, maxWidth);
        g.textShaded(Ui.TITLE, text, x + 20.0f, y + 2.5f + (11.0f - size) / 2.0f, size, Ui.Theme.RED,
                Ui.Theme.RED_DD, 1.0f);
    }

    /** Un texte sans accents : les polices du kit n'en ont pas (titres du jeu traduits). */
    public static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** L'intertitre d'une zone : « CONTENU 27 CASES », « INVENTAIRE ». */
    public static void label(UiGraphics g, float x, float y, String text, String count) {
        g.text(Ui.WIDE, text, x, y, 4.5f, Ui.Theme.TEXT2);
        if (!count.isEmpty()) {
            g.text(Ui.PIXEL, count, x + g.width(Ui.WIDE, text, 4.5f) + 3.0f, y + 0.8f, 3.3f, Ui.Theme.DIM);
        }
    }

    /** L'intertitre d'une rangee de cases, juste au-dessus de sa premiere case. */
    public static void labelAbove(UiGraphics g, Slot first, String text, String count) {
        label(g, first.x - 0.5f, first.y - 8.0f, text, count);
    }

    /** « INVENTAIRE », juste au-dessus de la premiere case du joueur. */
    public static void inventoryLabel(UiGraphics g, Slot first) {
        labelAbove(g, first, "INVENTAIRE", "");
    }

    /** Le fond d'une case : un carre creux arrondi, l'objet (16 x 16) en son centre. */
    public static void slot(Paint p, Slot slot) {
        slot(p, slot.x, slot.y);
    }

    /** Le fond d'une case dont l'objet est en (x, y). */
    public static void slot(Paint p, float x, float y) {
        p.framed(x - 0.5f, y - 0.5f, 17.0f, 17.0f, 2.7f, SLOT_FILL, SLOT_EDGE, 1);
    }

    /** Une case teintee (anneaux d'un coffre, case reservee). */
    public static void slot(Paint p, Slot slot, int tint) {
        p.framed(slot.x - 0.5f, slot.y - 0.5f, 17.0f, 17.0f, 2.7f, Paint.mix(SLOT_FILL, tint, 0.16f),
                Paint.mix(SLOT_EDGE, tint, 0.45f), 1);
    }

    /** La case du resultat : plus grande, cerclee de couleur, avec un halo quand elle est pleine. */
    public static void bigSlot(Paint p, Slot slot, int accent) {
        boolean full = slot.hasItem();
        if (full) {
            p.glow(slot.x + 8.0f, slot.y + 8.0f, 20.0f, Paint.alpha(accent, 0.22f));
        }
        p.framed(slot.x - 4.0f, slot.y - 4.0f, 24.0f, 24.0f, 4.0f, SLOT_FILL,
                full ? accent : Paint.mix(SLOT_EDGE, accent, 0.4f), full ? 2 : 1);
    }

    /** Une jauge horizontale arrondie, et son halo au bout. */
    public static void bar(Paint p, float x, float y, float width, float height, float progress, int color) {
        p.round(x, y, width, height, height / 2.0f, 0xFF24262A);
        float fill = width * Math.max(0.0f, Math.min(1.0f, progress));
        if (fill > 0.4f) {
            p.glow(x + fill, y + height / 2.0f, height * 1.8f, Paint.alpha(color, 0.35f));
            p.round(x, y, Math.max(height, fill), height, height / 2.0f, color);
        }
    }

    /**
     * La flamme du four : l'image du jeu (une image animee de l'atlas de l'interface),
     * montee jusqu'a {@code progress}, sur sa silhouette estompee.
     */
    public static void flame(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, boolean lit,
                             float progress) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 14, 14, 0x30FFFFFF);
        if (!lit) {
            return;
        }
        int height = (int) Math.ceil(Math.max(0.0f, Math.min(1.0f, progress)) * 13.0f) + 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 14, 14, 0, 14 - height, x, y + 14 - height, 14,
                height);
    }

    /** La croix de fermeture, en haut a droite de l'en-tete. */
    public static void close(UiGraphics g, Paint p, int width, boolean hovered) {
        float x = width - 21;
        float y = BUTTON_Y;
        if (hovered) {
            p.glow(x + 6.5f, y + 6.5f, 12.0f, Paint.alpha(Ui.Theme.RED, 0.35f));
        }
        p.framed(x, y, BUTTON, BUTTON, 3.5f, hovered ? Ui.Theme.RED : BUTTON_FILL,
                hovered ? Ui.Theme.RED_LIGHT : BUTTON_EDGE, 1);
        g.texture(Ui.CLOSE, Math.round(x + 3), Math.round(y + 3), 7, 7, 32, 32);
    }

    /** Un bouton carre de l'en-tete avec une icone d'objet (le livre de recettes). */
    public static void iconButton(UiGraphics g, Paint p, float x, float y, ItemStack icon, boolean hovered,
                                  boolean active) {
        int fill = active ? Paint.mix(BUTTON_FILL, Ui.Theme.RED, 0.25f) : hovered ? 0xFF34373D : BUTTON_FILL;
        int edge = active ? Paint.mix(BUTTON_EDGE, Ui.Theme.RED, 0.6f) : hovered ? 0xFF5A5E66 : BUTTON_EDGE;
        p.framed(x, y, BUTTON, BUTTON, 3.5f, fill, edge, 1);
        g.item(icon, x + 2.5f, y + 2.5f, 8.0f, false);
    }

    /** La croix, en coordonnees du panneau. */
    public static boolean overClose(double x, double y, int width) {
        return x >= width - 21 && x < width - 21 + BUTTON && y >= BUTTON_Y && y < BUTTON_Y + BUTTON;
    }

    /** Un clic dans l'en-tete n'est pas un lancer d'objet. */
    public static boolean inHeader(double mouseX, double mouseY, int left, int top, int width) {
        return mouseX >= left && mouseX < left + width && mouseY >= top - HEADER && mouseY < top;
    }

    /**
     * L'icone de l'en-tete : le conteneur que le joueur vise (coffre, tonneau, coffre de
     * l'Ender, chariot...), sinon {@code fallback}. Un bloc sans entite de bloc ou une
     * entite sans inventaire ne compte pas : l'ecran a ete ouvert autrement (commande,
     * objet), et le bloc vise n'a rien a voir.
     */
    public static ItemStack targetIcon(ItemStack fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        HitResult hit = minecraft.hitResult;
        if (minecraft.level != null && hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && minecraft.level.getBlockEntity(block.getBlockPos()) != null) {
            ItemStack stack = new ItemStack(minecraft.level.getBlockState(block.getBlockPos()).getBlock().asItem());
            if (!stack.isEmpty() && !stack.is(Items.AIR)) {
                return stack;
            }
        }
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Container) {
            Entity entity = entityHit.getEntity();
            ItemStack stack = entity.getPickResult();
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        return fallback;
    }
}
