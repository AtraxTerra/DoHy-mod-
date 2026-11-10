package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Les modificateurs de l'arc en paladium ({@code BOWUPGRADE_*MODIFIER}). */
public enum BowModifier {
    RANGE("range", () -> PalaPillage.RANGE_BOW_MODIFIER.get()),
    SPEED("speed", () -> PalaPillage.SPEED_BOW_MODIFIER.get());

    private final String key;
    private final Supplier<Item> item;

    BowModifier(String key, Supplier<Item> item) {
        this.key = key;
        this.item = item;
    }

    public String key() {
        return key;
    }

    /** L'etiquette d'entite posee sur une fleche tiree avec ce modificateur. */
    public String tag() {
        return "palamod_bow_" + key;
    }

    public static BowModifier of(ItemStack stack) {
        for (BowModifier modifier : values()) {
            if (stack.is(modifier.item.get())) {
                return modifier;
            }
        }
        return null;
    }
}
