package fr.atraxterra.dohy.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Briques communes des interfaces.
 *
 * <p>Un seul endroit pour les vignettes, les barres de progression et les
 * boutons de navigation. Sept ecrans ecrits chacun a sa facon finissent par
 * avoir sept conventions differentes : le joueur doit reapprendre chaque
 * fenetre, et une correction de style demande sept modifications.
 */
public final class Gui {

    /** Emplacements de pied de page, convention retenue pour tous les ecrans. */
    public static final int FOOTER_BACK = 48;
    public static final int FOOTER_CLOSE = 49;
    public static final int FOOTER_NEXT = 50;
    public static final int FOOTER_PREVIOUS = 47;

    private Gui() {
    }

    /**
     * Nom lisible d'un item, jamais son identifiant.
     *
     * <p>Afficher {@code minecraft:deepslate} au joueur est une fuite de
     * plomberie. On prend le nom traduit de l'item ; a defaut — item absent du
     * registre, mod desinstalle — on embellit le chemin plutot que de montrer
     * l'identifiant brut.
     */
    public static Component displayName(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item != null) {
            return new ItemStack(item).getHoverName();
        }
        return Component.literal(prettify(id.getPath()));
    }

    /** {@code deepslate_iron_ore} devient {@code Deepslate iron ore}. */
    public static String prettify(String path) {
        String spaced = path.replace('_', ' ').replace('/', ' ');
        if (spaced.isEmpty()) {
            return spaced;
        }
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    /** Vignette : un item, un nom jaune, une description grise. */
    public static ItemStack button(Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(ChatFormatting.YELLOW));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    /** Vignette portant un nom deja mis en forme. */
    public static ItemStack button(Item item, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    /**
     * Vignette dont la pile porte un nombre.
     *
     * <p>Le compte d'une pile est la seule information qu'un ecran de coffre
     * affiche sans survol : un niveau ou un palier s'y lit d'un coup d'oeil, ce
     * qui evite d'ouvrir la description pour la question la plus fréquente.
     */
    public static ItemStack button(Item item, Component name, List<Component> lore, int count) {
        ItemStack stack = button(item, name, lore);
        stack.setCount(Math.max(1, Math.min(64, count)));
        return stack;
    }

    /** Remplissage neutre des bordures. */
    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Component.empty());
        return stack;
    }

    public static ItemStack back(String target) {
        return button(Items.ARROW, "Retour", List.of(
                Component.literal("vers " + target).withStyle(ChatFormatting.GRAY)));
    }

    public static ItemStack close() {
        return button(Items.BARRIER, "Fermer", List.of());
    }

    public static ItemStack previousPage(int page, int pages) {
        return button(Items.PAPER, "Page precedente", List.of(
                Component.literal("page " + page + " / " + pages).withStyle(ChatFormatting.GRAY)));
    }

    public static ItemStack nextPage(int page, int pages) {
        return button(Items.PAPER, "Page suivante", List.of(
                Component.literal("page " + page + " / " + pages).withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Barre de progression.
     *
     * <p>Caracteres pleins puis points : lisible dans une description d'item,
     * ou les barres graphiques ne sont pas disponibles.
     */
    public static Component bar(double ratio, int width) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * width);
        return Component.literal("▏".repeat(filled)).withStyle(ChatFormatting.GREEN)
                .append(Component.literal("·".repeat(Math.max(0, width - filled)))
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("  " + Math.round(ratio * 100) + " %")
                        .withStyle(ChatFormatting.GRAY));
    }

    public static Component bar(long done, long total, int width) {
        Component line = bar(total <= 0 ? 0 : (double) done / total, width);
        return line.copy().append(Component.literal("   " + done + " / " + total)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Ligne « libelle : valeur », grise puis doree. */
    public static Component field(String label, String value) {
        return Component.literal(label + " : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }

    public static Component hint(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Invitation a cliquer, toujours en derniere ligne d'une vignette. */
    public static Component action(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    public static Component good(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    public static Component bad(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    public static Component blank() {
        return Component.empty();
    }

    /** Assemble une description en ignorant les lignes nulles. */
    public static List<Component> lore(Component... lines) {
        List<Component> list = new ArrayList<>();
        for (Component line : lines) {
            if (line != null) {
                list.add(line);
            }
        }
        return list;
    }
}
