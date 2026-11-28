package fr.atraxterra.dohy.faction.garrison;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * L'equipement de la garnison : fer a la recrue, puis diamant et paladium, pour toute la
 * garnison d'un coup. Chaque palier donne de la vie, des degats et son armure.
 */
public enum GarrisonTier {
    FER("Fer", 1.0d, 0.0d, 0L, "minecraft:iron"),
    DIAMANT("Diamant", 1.5d, 2.0d, 150_000L, "minecraft:diamond"),
    PALADIUM("Paladium", 2.0d, 4.0d, 450_000L, "palamod:paladium");

    private final String label;
    private final double health;
    private final double damage;
    private final long cost;
    /** Le prefixe des pieces : « minecraft:iron » donne iron_helmet, iron_sword... */
    private final String material;

    GarrisonTier(String label, double health, double damage, long cost, String material) {
        this.label = label;
        this.health = health;
        this.damage = damage;
        this.cost = cost;
        this.material = material;
    }

    public String label() {
        return label;
    }

    /** Le multiplicateur de vie. */
    public double health() {
        return health;
    }

    /** Les degats en plus, au contact comme a l'arc. */
    public double damage() {
        return damage;
    }

    /** Le prix pour passer toute la garnison a ce palier. */
    public long cost() {
        return cost;
    }

    /** Une piece de ce palier (« helmet », « sword »...) ; du fer si l'objet n'existe pas. */
    public Item piece(String suffix) {
        Identifier id = Identifier.parse(material + "_" + suffix);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) {
            item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("iron_" + suffix));
        }
        return item;
    }

    public GarrisonTier next() {
        return this == FER ? DIAMANT : this == DIAMANT ? PALADIUM : null;
    }

    public static GarrisonTier of(int ordinal) {
        GarrisonTier[] tiers = values();
        return tiers[Math.max(0, Math.min(tiers.length - 1, ordinal))];
    }
}
