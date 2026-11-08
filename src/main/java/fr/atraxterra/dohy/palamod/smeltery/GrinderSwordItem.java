package fr.atraxterra.dohy.palamod.smeltery;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Broadsword et fastsword ({@code ItemBroadsword}, {@code ItemFastsword}) : des
 * armes du grinder, par un, sans enchantement.
 *
 * <ul>
 *   <li><b>broadsword</b> : lourde — une attaque par seconde
 *       ({@code lastBroadswordAttack}, 1 000 ms), 4 emplacements de
 *       modificateur, +2,8 de degats par niveau de dommage ;</li>
 *   <li><b>fastsword</b> : tenue en main, Celerite IV ({@code PotionEffect(haste,
 *       1, 3)}), et la cible garde moins longtemps son invulnerabilite (18 ticks
 *       au lieu de 20) ; 3 emplacements, +1,5 par niveau de dommage.</li>
 * </ul>
 * Flamme et recul s'appliquent au coup ({@link SmelteryEvents}).
 */
public class GrinderSwordItem extends Item implements ModifiableTool {

    /** L'identifiant des degats de base d'une arme : le modificateur de dommage le remplace. */
    private static final Identifier BASE_ATTACK_DAMAGE = Identifier.withDefaultNamespace("base_attack_damage");

    private final boolean broad;
    private final boolean modifiable;
    /** Les degats de l'arme, comme dans PalaMod (le joueur ajoute son point de base). */
    private final float damage;

    public GrinderSwordItem(boolean broad, boolean modifiable, float damage, Properties properties) {
        super(properties);
        this.broad = broad;
        this.modifiable = modifiable;
        this.damage = damage;
    }

    public boolean broad() {
        return broad;
    }

    public boolean modifiable() {
        return modifiable;
    }

    @Override
    public GrinderModifier.ToolKind kind() {
        return GrinderModifier.ToolKind.SWORD;
    }

    @Override
    public int baseSlots() {
        return broad ? 4 : 3;
    }

    /** {@code getDamages} : la base, plus 2,8 ou 1,5 par niveau de dommage. */
    public float damageWith(ToolModifiers modifiers) {
        return damage + (broad ? 2.8f : 1.5f) * modifiers.level(GrinderModifier.DAMAGE);
    }

    @Override
    public void onModified(ItemStack stack, ToolModifiers modifiers) {
        ItemAttributeModifiers base = components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, base.withModifierAdded(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE, damageWith(modifiers) - 1.0d,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (modifiable) {
            PalaSmeltery.describe(stack, this, tooltip);
        }
    }
}
