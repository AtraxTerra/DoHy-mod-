package fr.atraxterra.dohy.grade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les commandes qu'un grade ou Premium debloque, dans l'ordre de la page des grades.
 *
 * <p>Toutes sont enregistrees pour tout le monde : sans le grade, elles disent lequel il faut.
 * Celles de Paladium qui ne servent qu'a son reseau de serveurs ({@code /re},
 * {@code /serverhistory}, {@code /go}) ne sont pas portees, ni {@code /farmland} (pas de zone de
 * farm commune) ni {@code /skin} (pas d'apparences d'armure dans DoHy).
 */
public enum GradeCommand {
    CRAFT("craft", PlayerGrade.HEROS, false, "Un etabli, partout", () -> Items.CRAFTING_TABLE),
    MINAGE("minage", PlayerGrade.HEROS, false, "Direct a la Mine", () -> Items.IRON_PICKAXE),
    FEED("feed", PlayerGrade.LEGENDE, false, "Rassasie d'un coup", () -> Items.COOKED_BEEF),
    FURNACE("furnace", PlayerGrade.LEGENDE, false, "Cuit ce que tu tiens", () -> Items.FURNACE),
    RANGEMENT("rangement", PlayerGrade.LEGENDE, false, "Trie ton sac", () -> Items.CHEST),
    TPTOGGLE("tptoggle", PlayerGrade.LEGENDE, false, "Refuse les /tpa", () -> Items.ENDER_PEARL),
    CONDENSE("condense", PlayerGrade.DIVINITE, false, "Lingots en blocs", () -> Items.IRON_BLOCK),
    HAT("hat", PlayerGrade.DIVINITE, false, "Un bloc sur la tete", () -> Items.CARVED_PUMPKIN),
    DEATHLOCATE("deathlocate", PlayerGrade.DIVINITE, false, "Ta derniere mort", () -> Items.RECOVERY_COMPASS),
    EC("ec", PlayerGrade.NONE, true, "L'Ender Chest, partout", () -> Items.ENDER_CHEST),
    FLY("fly", PlayerGrade.NONE, true, "Voler au spawn", () -> Items.FEATHER),
    NV("nv", PlayerGrade.NONE, true, "Vision nocturne", () -> Items.GOLDEN_CARROT);

    private final String name;
    private final PlayerGrade grade;
    private final boolean premium;
    private final String description;
    private final Supplier<Item> icon;

    GradeCommand(String name, PlayerGrade grade, boolean premium, String description, Supplier<Item> icon) {
        this.name = name;
        this.grade = grade;
        this.premium = premium;
        this.description = description;
        this.icon = icon;
    }

    /** Le nom de la commande, sans la barre oblique. */
    public String command() {
        return name;
    }

    /** Le grade qu'il faut ; {@link PlayerGrade#NONE} pour une commande de Premium. */
    public PlayerGrade grade() {
        return grade;
    }

    public boolean premium() {
        return premium;
    }

    public String description() {
        return description;
    }

    public Item icon() {
        return icon.get();
    }

    /** « Heros », « Premium » : ce qu'il faut pour s'en servir. */
    public String requirement() {
        return premium ? "Premium" : grade.label();
    }
}
