package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * L'arc en paladium ({@code ItemPaladiumBow}) : 900 utilisations, des fleches
 * qui frappent a 3 au lieu de 2, et deux emplacements de modificateur.
 *
 * <ul>
 *   <li><b>Range</b> : la fleche part une fois et demie plus vite, donc plus
 *       loin et plus fort ({@code PillageEvents.onJoin}, la vitesse n'etant
 *       fixee qu'apres {@link #customArrow}) ;</li>
 *   <li><b>Speed</b> : l'arc se bande une fois et demie plus vite.</li>
 * </ul>
 * Les modificateurs se posaient a la Bow Machine ; ici, a l'enclume, un de
 * chaque au plus ({@code PillageEvents.onAnvil}). La fleche de Switch ne
 * fonctionne qu'avec cet arc.
 */
public class PaladiumBowItem extends BowItem {

    /** Les degats de base d'une fleche de cet arc (2 pour l'arc vanilla). */
    static final double ARROW_DAMAGE = 3.0d;
    static final float SPEED_DRAW = 1.5f;
    public static final float RANGE_VELOCITY = 1.5f;
    public static final int MAX_MODIFIERS = 2;

    public PaladiumBowItem(Properties properties) {
        super(properties);
    }

    public static List<String> modifiers(ItemStack stack) {
        return stack.getOrDefault(PalaPillage.BOW_MODIFIERS.get(), List.of());
    }

    public static boolean has(ItemStack stack, BowModifier modifier) {
        return modifiers(stack).contains(modifier.key());
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player && !DefenseGate.check(player, DefenseGate.key(this))) {
            return false;
        }
        if (has(stack, BowModifier.SPEED)) {
            int duration = getUseDuration(stack, entity);
            int charge = (int) ((duration - timeLeft) * SPEED_DRAW);
            timeLeft = Math.max(0, duration - charge);
        }
        return super.releaseUsing(stack, level, entity, timeLeft);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
        arrow.setBaseDamage(ARROW_DAMAGE);
        if (has(weapon, BowModifier.RANGE)) {
            arrow.addTag(BowModifier.RANGE.tag());
        }
        return super.customArrow(arrow, projectile, weapon);
    }
}
