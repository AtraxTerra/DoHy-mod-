package fr.atraxterra.dohy.agri;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * Les dix pains, un par cereale. Ils sortent tous de la machine a pain
 * ({@link BreadMachineMenu}) ; leurs fournees sont dans {@code agriculture.json}.
 *
 * <p>La gamme suit celle de NationsGlory : trois pains qui ne font que
 * nourrir, puis des pains a effet de plus en plus chers, jusqu'aux trois pains
 * d'OGM — le pain de combat, le pain qui soigne, et celui qui sauve d'un coup
 * mortel. Les quatre effets qui n'existent pas dans Minecraft sont dans
 * {@link AgriEffects}.
 */
public enum Bread {
    CORN("corn_bread", "Pain de mais", 4, 0, Rarity.COMMON, 1),
    OAT("oat_bread", "Pain d'avoine", 5, 0, Rarity.COMMON, 1),
    RYE("rye_bread", "Pain de seigle", 6, 0, Rarity.COMMON, 1),
    BARLEY("barley_bread", "Pain d'orge", 4, 2, Rarity.UNCOMMON, 1),
    SOY("soy_bread", "Pain de soja", 6, 3, Rarity.UNCOMMON, 1),
    SUNFLOWER("sunflower_bread", "Pain de tournesol", 4, 0, Rarity.UNCOMMON, 1),
    MINER("miner_bread", "Pain du mineur", 4, 0, Rarity.UNCOMMON, 1),
    FONIO("fonio_bread", "Pain de fonio", 6, 4, Rarity.RARE, 1),
    SORGHUM("sorghum_bread", "Pain de sorgho", 2, 4, Rarity.RARE, 2),
    KAMUT("kamut_bread", "Pain de kamut", 4, 0, Rarity.RARE, 1);

    private final String key;
    private final String label;
    private final int nutrition;
    /** Vie rendue a la bouchee, en demi-coeurs. */
    private final int heal;
    private final Rarity rarity;
    private final int loreLines;

    Bread(String key, String label, int nutrition, int heal, Rarity rarity, int loreLines) {
        this.key = key;
        this.label = label;
        this.nutrition = nutrition;
        this.heal = heal;
        this.rarity = rarity;
        this.loreLines = loreLines;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int nutrition() {
        return nutrition;
    }

    public Rarity rarity() {
        return rarity;
    }

    /** Lignes d'infobulle : {@code tooltip.dohy.<key>.<n>}. */
    public int loreLines() {
        return loreLines;
    }

    /** Un pain qui ne fait que nourrir ne se mange que si on a faim ; les autres, toujours. */
    public boolean plain() {
        return this == CORN || this == OAT || this == RYE;
    }

    public static Bread byKey(String key) {
        for (Bread bread : values()) {
            if (bread.key.equals(key)) {
                return bread;
            }
        }
        return null;
    }

    /** Les effets du pain, crees a la demande : les effets de DoHy n'existent pas au chargement de la classe. */
    public List<MobEffectInstance> effects() {
        return switch (this) {
            case CORN, OAT, RYE, KAMUT -> List.of();
            case BARLEY -> List.of(new MobEffectInstance(MobEffects.SPEED, 400, 0),
                    new MobEffectInstance(MobEffects.HASTE, 400, 0));
            case SOY -> List.of(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            case SUNFLOWER -> List.of(new MobEffectInstance(AgriEffects.LIFE_STEAL, 1200, 0));
            case MINER -> List.of(new MobEffectInstance(AgriEffects.PROSPECTING, 6000, 0));
            case FONIO -> List.of(new MobEffectInstance(MobEffects.ABSORPTION, 600, 0),
                    new MobEffectInstance(AgriEffects.LAST_STAND, 600, 0));
            case SORGHUM -> List.of(new MobEffectInstance(MobEffects.STRENGTH, 2400, 1),
                    new MobEffectInstance(MobEffects.SPEED, 2400, 1),
                    new MobEffectInstance(MobEffects.REGENERATION, 600, 1),
                    new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0),
                    new MobEffectInstance(AgriEffects.LIFE_STEAL, 1200, 1));
        };
    }

    /** Ce que le pain fait a celui qui vient de le manger, en plus de le nourrir. */
    public void apply(LivingEntity eater) {
        if (heal > 0) {
            eater.heal(heal);
        }
        if (this == KAMUT) {
            // Le pain qui soigne : il leve ce qui fait du mal, puis en protege.
            List<Holder<MobEffect>> harmful = new ArrayList<>();
            for (MobEffectInstance active : eater.getActiveEffects()) {
                if (active.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                    harmful.add(active.getEffect());
                }
            }
            harmful.forEach(eater::removeEffect);
            eater.addEffect(new MobEffectInstance(AgriEffects.IMMUNITY, 2400, 0));
        }
        for (MobEffectInstance effect : effects()) {
            eater.addEffect(effect);
        }
    }
}
