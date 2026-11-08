package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ce qu'une croquette rapporte, selon le maker : 10 lingots de fer, 5
 * d'amethyste, 3 de titane, 1 de paladium. Les memes quantites a la main et
 * dans la boite a golem.
 */
public enum GolemMetal {
    IRON(10),
    AMETHYST(5),
    TITANE(3),
    PALADIUM(1);

    private final int count;

    GolemMetal(int count) {
        this.count = count;
    }

    public static GolemMetal of(int tier) {
        return values()[Math.clamp(tier, 0, values().length - 1)];
    }

    public ItemStack yield() {
        return switch (this) {
            case IRON -> new ItemStack(Items.IRON_INGOT, count);
            case AMETHYST -> new ItemStack(PalaItems.AMETHYST_INGOT.get(), count);
            case TITANE -> new ItemStack(PalaItems.TITANE_INGOT.get(), count);
            case PALADIUM -> new ItemStack(PalaItems.PALADIUM_INGOT.get(), count);
        };
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
