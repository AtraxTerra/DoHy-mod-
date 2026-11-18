package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.alchemist.MysticEnchant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L'enchantement Experience Bottler ({@code PMiner.xp_bottler}, applique par
 * {@code AJobsEventHandler.onPlayerEarnXp}) : tant qu'on tient ou porte un
 * objet enchante, l'XP de metier gagnee part en bouteilles au lieu d'aller au
 * metier.
 *
 * <p>Dans l'ordre de l'original : d'abord les bouteilles du meme metier qui ne
 * sont pas pleines ({@value JobBottleItem#DEFAULT_XP} points), puis des fioles
 * en verre du sac, changees en bouteilles de ce metier. Ce qui ne trouve pas
 * de place va au metier, comme d'habitude. L'XP d'une bouteille bue ne repart
 * pas en bouteille.
 *
 * <p><b>Ecart</b> : l'original repartissait l'XP sur toute une pile de
 * bouteilles (deux decimales par bouteille) et changeait la plus grosse pile de
 * fioles d'un coup. Ici une bouteille se remplit apres l'autre, en points
 * entiers : soixante-quatre fioles ne deviennent pas soixante-quatre
 * bouteilles presque vides pour quelques points.
 */
public final class XpBottler {

    private XpBottler() {
    }

    /** L'objet tenu ou une piece d'armure portee a-t-il l'enchantement ? */
    public static boolean active(ServerPlayer player) {
        if (MysticEnchant.XP_BOTTLER.levelOn(player, player.getMainHandItem()) > 0) {
            return true;
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (MysticEnchant.XP_BOTTLER.levelOn(player, player.getItemBySlot(slot)) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Met ce qu'il peut de {@code amount} en bouteilles et renvoie le reste, a
     * verser au metier. Sans l'enchantement, renvoie {@code amount} tel quel.
     */
    public static long bottle(ServerPlayer player, Job job, long amount) {
        if (amount <= 0 || !active(player)) {
            return amount;
        }
        JobBottleItem item = itemFor(job);
        if (item == null) {
            return amount;
        }
        Inventory inventory = player.getInventory();
        long left = amount;
        // 1. Les bouteilles du metier deja entamees.
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item) || JobBottleItem.xp(stack) >= JobBottleItem.DEFAULT_XP) {
                continue;
            }
            left = fill(stack, left);
        }
        // 2. Des fioles en verre, une a une.
        while (left > 0) {
            int slot = glassBottle(inventory);
            if (slot < 0) {
                break;
            }
            ItemStack glass = inventory.getItem(slot);
            glass.shrink(1);
            ItemStack bottle = new ItemStack(item);
            int stored = (int) Math.min(left, JobBottleItem.DEFAULT_XP);
            bottle.set(PalaDungeon.JOB_XP.get(), stored);
            left -= stored;
            if (!inventory.add(bottle)) {
                player.drop(bottle, false);
            }
        }
        if (left != amount) {
            inventory.setChanged();
        }
        return left;
    }

    /**
     * Remplit une pile entamee : chaque bouteille de la pile prend la meme part,
     * pour qu'elles restent identiques, donc empilables.
     */
    private static long fill(ItemStack stack, long left) {
        int current = JobBottleItem.xp(stack);
        int room = JobBottleItem.DEFAULT_XP - current;
        int count = stack.getCount();
        long each = Math.min(room, left / count);
        if (each <= 0) {
            return left;
        }
        stack.set(PalaDungeon.JOB_XP.get(), current + (int) each);
        return left - each * count;
    }

    private static int glassBottle(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.GLASS_BOTTLE)) {
                return slot;
            }
        }
        return -1;
    }

    private static JobBottleItem itemFor(Job job) {
        return switch (job) {
            case MINER -> PalaDungeon.MINER_BOTTLE.get();
            case FARMER -> PalaDungeon.FARMER_BOTTLE.get();
            case HUNTER -> PalaDungeon.HUNTER_BOTTLE.get();
            case ALCHEMIST -> PalaDungeon.ALCHEMIST_BOTTLE.get();
        };
    }
}
