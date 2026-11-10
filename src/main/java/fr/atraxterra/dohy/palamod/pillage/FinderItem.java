package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Un Unclaim Finder ou le Spawner Finder : tenu en main, il s'use d'un point
 * par seconde ({@code onUpdate}, 20 points toutes les 20 ticks sur 180 000) et
 * le HUD affiche ce qu'il trouve ({@code client.FinderHud}).
 *
 * <p>Le calcul se fait chez le client, sur les chunks qu'il a deja : les
 * entites de bloc y sont toutes (coffres, spawners), comme sur Paladium ou le
 * module de HUD lisait les tile entities du monde client.
 */
public class FinderItem extends Item {

    private final FinderType type;

    public FinderItem(FinderType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public FinderType type() {
        return type;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                && entity instanceof LivingEntity living && level.getGameTime() % 20L == 0L) {
            stack.hurtAndBreak(1, living, slot);
        }
    }
}
