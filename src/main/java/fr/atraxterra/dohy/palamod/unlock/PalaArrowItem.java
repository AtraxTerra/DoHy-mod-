package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Une fleche moddee ({@code ItemArrowBase}) : poison, wither, lenteur, givre.
 * Le wiki : « L'utilisation des fleches moddees est exclusive a l'arc en
 * paladium. » D'un autre arc, c'est une fleche ordinaire, comme la fleche de
 * Switch deja portee. L'effet passe a l'impact ({@link UnlockEvents}).
 */
public class PalaArrowItem extends ArrowItem {

    /** Les fleches et leur marque ({@code EventHandlerArrow}, cas 0, 1, 2 et 4). */
    public enum Kind {
        POISON, WITHER, SLOWNESS, FROST;

        public String tag() {
            return "palamod_arrow_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Kind kind;

    public PalaArrowItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        Arrow arrow = new Arrow(level, shooter, ammo.copyWithCount(1), weapon);
        arrow.addTag(kind.tag());
        return arrow;
    }
}
