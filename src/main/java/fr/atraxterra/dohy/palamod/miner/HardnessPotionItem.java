package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Potion de durete.
 *
 * <p>Releve dans {@code ItemHardnessPotion} : se boit en 20 ticks, donne l'effet
 * de durete pendant 9 600 ticks (8 minutes), rend une fiole vide. La boisson
 * passe par le composant {@code consumable} pose a l'enregistrement ; l'effet est
 * ajoute ici, cote serveur, parce que l'effet n'existe pas encore quand l'objet
 * est construit.
 *
 * <p>PalaMod ne la fabrique pas : c'est une recompense de jetons de metier
 * (Alchimiste, « 3 Hardness Potions »). DoHy n'a pas de jetons ; elle est donc
 * au shop et en creatif.
 */
public class HardnessPotionItem extends Item {

    /** {@code new PotionEffect(HARDNESS.id, 9600, 0)}. */
    public static final int DURATION = 9600;

    public HardnessPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            entity.addEffect(new MobEffectInstance(PalaMiner.HARDNESS, DURATION, 0));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
