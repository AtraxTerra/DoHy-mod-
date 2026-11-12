package fr.atraxterra.dohy.agri;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Un pain : il nourrit comme toute nourriture, puis {@link Bread#apply} fait le reste. */
public class BreadItem extends Item {

    private final Bread bread;

    public BreadItem(Properties properties, Bread bread) {
        super(properties);
        this.bread = bread;
    }

    public Bread bread() {
        return bread;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide()) {
            bread.apply(entity);
        }
        return result;
    }
}
