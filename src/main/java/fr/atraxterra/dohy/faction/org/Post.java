package fr.atraxterra.dohy.faction.org;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les postes de la nation (lot 24a) : chacun demande un grade et tient une responsabilite. Les
 * lieutenants qui dirigent les entreprises arrivent avec elles (lot 24b).
 */
public enum Post {
    BRAS_DROIT("Bras droit", Grade.CADRE, "Les autres cadres rendent +15 %, leur loyaute s'use deux fois moins",
            () -> new ItemStack(Items.GOLDEN_HELMET), 0xFFFAC400),
    TRESORIER("Tresorier", Grade.CAPITAINE, "Les salaires et les frais coutent jusqu'a 20 % de moins",
            () -> new ItemStack(Items.GOLD_INGOT), 0xFFFAC400),
    GENERAL("General", Grade.CAPITAINE, "Les nations craignent de t'attaquer",
            () -> new ItemStack(Items.IRON_SWORD), 0xFFEF3826),
    DIPLOMATE("Diplomate", Grade.CAPITAINE, "Traites jusqu'a 30 % moins chers, plus faciles, et un de plus",
            () -> new ItemStack(Items.WRITABLE_BOOK), 0xFF27C4E8),
    OPERATIONS("Chef des operations", Grade.CAPITAINE, "Les affaires illegales risquent jusqu'a 30 % de moins",
            () -> new ItemStack(Items.ENDER_EYE), 0xFFEC4FB5);

    private final String label;
    private final Grade grade;
    private final String effect;
    private final Supplier<ItemStack> icon;
    private final int color;

    Post(String label, Grade grade, String effect, Supplier<ItemStack> icon, int color) {
        this.label = label;
        this.grade = grade;
        this.effect = effect;
        this.icon = icon;
        this.color = color;
    }

    public String label() {
        return label;
    }

    /** Le grade minimal pour tenir le poste. */
    public Grade grade() {
        return grade;
    }

    public String effect() {
        return effect;
    }

    public ItemStack icon() {
        return icon.get();
    }

    public int color() {
        return color;
    }

    public static Post of(String name) {
        for (Post post : values()) {
            if (post.name().equals(name)) {
                return post;
            }
        }
        return null;
    }
}
