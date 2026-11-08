package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** L'anti-chute : des bottes enchantees, et le joueur ne tombe plus des bords. */
public final class NoFall {

    private NoFall() {
    }

    public static boolean active(Player player) {
        return MysticEnchant.NO_FALL.levelOn(player, player.getItemBySlot(EquipmentSlot.FEET)) > 0;
    }
}
