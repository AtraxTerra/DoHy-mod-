package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Les bonus de la salle de boost. Le wiki ne dit que son nom : ici, l'autel
 * en propose trois au hasard, chaque joueur en prend un, pour dix minutes et
 * jusqu'a la fin du palier.
 */
public enum Boost {

    STRENGTH("Force I", MobEffects.STRENGTH, 0, Items.BLAZE_POWDER),
    RESISTANCE("Resistance I", MobEffects.RESISTANCE, 0, Items.IRON_CHESTPLATE),
    REGENERATION("Regeneration I", MobEffects.REGENERATION, 0, Items.GHAST_TEAR),
    SPEED("Vitesse I", MobEffects.SPEED, 0, Items.SUGAR),
    ABSORPTION("Absorption II", MobEffects.ABSORPTION, 1, Items.GOLDEN_APPLE),
    HEALTH_BOOST("Vie supplementaire I", MobEffects.HEALTH_BOOST, 0, Items.GLISTERING_MELON_SLICE),
    FIRE_RESISTANCE("Resistance au feu", MobEffects.FIRE_RESISTANCE, 0, Items.MAGMA_CREAM);

    private final String label;
    private final Holder<MobEffect> effect;
    private final int amplifier;
    private final Item icon;

    Boost(String label, Holder<MobEffect> effect, int amplifier, Item icon) {
        this.label = label;
        this.effect = effect;
        this.amplifier = amplifier;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public Holder<MobEffect> effect() {
        return effect;
    }

    public int amplifier() {
        return amplifier;
    }

    public Item icon() {
        return icon;
    }
}
