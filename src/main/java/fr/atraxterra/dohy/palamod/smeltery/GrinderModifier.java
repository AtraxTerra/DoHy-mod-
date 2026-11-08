package fr.atraxterra.dohy.palamod.smeltery;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

/**
 * Les modificateurs du grinder ({@code UpgradeHelper.init}) : niveau maximal et
 * outil qui les prend.
 *
 * <table>
 *   <tr><th>modificateur</th><th>max</th><th>outil</th></tr>
 *   <tr><td>fusion</td><td>1</td><td>marteau</td></tr>
 *   <tr><td>fortune</td><td>3</td><td>marteau</td></tr>
 *   <tr><td>vitesse</td><td>3</td><td>marteau</td></tr>
 *   <tr><td>dommage</td><td>3</td><td>broadsword, fastsword</td></tr>
 *   <tr><td>flamme</td><td>1</td><td>broadsword, fastsword</td></tr>
 *   <tr><td>recul</td><td>2</td><td>broadsword, fastsword</td></tr>
 *   <tr><td>emplacement</td><td>2</td><td>tous</td></tr>
 * </table>
 * Le modificateur d'auto-reparation n'a ni recette ni effet dans PalaMod : il
 * n'est pas porte.
 */
public enum GrinderModifier implements StringRepresentable {
    SMELT("smelt", 1, ToolKind.HAMMER, ChatFormatting.DARK_AQUA),
    FORTUNE("fortune", 3, ToolKind.HAMMER, ChatFormatting.GOLD),
    SPEED("speed", 3, ToolKind.HAMMER, ChatFormatting.RED),
    DAMAGE("damage", 3, ToolKind.SWORD, ChatFormatting.DARK_RED),
    FLAME("flame", 1, ToolKind.SWORD, ChatFormatting.GRAY),
    KNOCKBACK("knockback", 2, ToolKind.SWORD, ChatFormatting.DARK_GREEN),
    MORE("more_upgrade", 2, null, ChatFormatting.WHITE);

    public static final Codec<GrinderModifier> CODEC = StringRepresentable.fromEnum(GrinderModifier::values);

    /** Le type d'outil qu'un modificateur demande ({@code upgradeType}). */
    public enum ToolKind {
        HAMMER, SWORD
    }

    private final String id;
    private final int maxLevel;
    private final ToolKind kind;
    private final ChatFormatting color;

    GrinderModifier(String id, int maxLevel, ToolKind kind, ChatFormatting color) {
        this.id = id;
        this.maxLevel = maxLevel;
        this.kind = kind;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** {@code null} : n'importe quel outil a modificateurs (l'emplacement). */
    public ToolKind kind() {
        return kind;
    }

    public Component displayName() {
        return Component.translatable("modifier.palamod." + id).withStyle(color);
    }

    public Item item() {
        return switch (this) {
            case SMELT -> PalaSmeltery.SMELT_MODIFIER.get();
            case FORTUNE -> PalaItems.FORTUNE_MODIFIER.get();
            case SPEED -> PalaItems.SPEED_MODIFIER.get();
            case DAMAGE -> PalaSmeltery.DAMAGE_MODIFIER.get();
            case FLAME -> PalaSmeltery.FLAME_MODIFIER.get();
            case KNOCKBACK -> PalaSmeltery.KNOCKBACK_MODIFIER.get();
            case MORE -> PalaSmeltery.MORE_UPGRADE_MODIFIER.get();
        };
    }

    /** Le modificateur que represente cet objet, ou {@code null}. */
    public static GrinderModifier of(Item item) {
        for (GrinderModifier modifier : values()) {
            if (modifier.item() == item) {
                return modifier;
            }
        }
        return null;
    }
}
