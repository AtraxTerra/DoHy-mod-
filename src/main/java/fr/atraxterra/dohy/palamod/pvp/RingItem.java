package fr.atraxterra.dohy.palamod.pvp;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Un anneau de reparation ({@code BaseItemRing}, {@code BaseItemRingEndium}).
 *
 * <p>Dans l'inventaire, il repare l'armure portee d'un point par piece et par
 * tick, et s'use d'autant : 400, 1000 ou 3200 points selon sa taille. Pose dans
 * la colonne d'anneaux d'un coffre, il repare le contenu du coffre (voir
 * {@link RingRepair}). Les anneaux en paladium servent le paladium, le paladium
 * vert et l'endium mixe ; ceux en endium, l'endium.
 *
 * <p>Solidite s'y applique a l'enclume, comme a tout objet usable en 1.7.10.
 */
public class RingItem extends Item {

    private final boolean endium;

    public RingItem(boolean endium, Properties properties) {
        super(properties);
        this.endium = endium;
    }

    public boolean endium() {
        return endium;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (entity instanceof ServerPlayer player && !player.isSpectator()) {
            RingRepair.repairWorn(level, player, stack, endium);
        }
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }
}
