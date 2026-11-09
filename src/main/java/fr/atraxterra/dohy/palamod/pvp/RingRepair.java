package fr.atraxterra.dohy.palamod.pvp;

import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce que les anneaux reparent, et comment ({@code IRepairable} et ses
 * implementations).
 *
 * <table>
 *   <tr><th>Piece</th><th>Ou</th><th>Anneau</th><th>Capacite ({@code getMaxRepair})</th></tr>
 *   <tr><td>armure paladium, paladium vert, endium mixe</td><td>portee, anneau dans l'inventaire ;
 *       ou dans un coffre</td><td>paladium</td><td>casque 8000, plastron 12000, jambieres 10000,
 *       bottes 7000</td></tr>
 *   <tr><td>armure endium</td><td>portee ; ou dans le coffre en endium</td><td>endium</td><td>sans limite</td></tr>
 *   <tr><td>epee, pioche, hache, pelle en paladium</td><td>coffre seulement</td><td>paladium</td><td>3</td></tr>
 *   <tr><td>epee, pioche, hache en endium</td><td>coffre en endium seulement</td><td>endium</td><td>sans limite</td></tr>
 * </table>
 *
 * <p><b>Deux facons de compter.</b> Une armure portee consomme sa capacite
 * point par point ({@code repair(piece, anneau, joueur)}) ; une piece au
 * coffre la consomme <b>par reparation complete</b> ({@code repair(piece, anneau)} :
 * le compteur ne baisse qu'au retour a zero d'usure). Les 3 des outils en
 * paladium sont donc trois remises a neuf. L'armure en paladium au coffre a
 * en pratique une capacite illimitee — 7000 remises a neuf au moins.
 *
 * <p>La capacite restante est le composant {@link PalaPvp#RING_REPAIR} (le
 * {@code repaired} de l'original), pose au premier passage.
 *
 * <p>L'amethyste, le titane et le paladium vert n'ont rien pour les outils
 * ({@code getMaxRepair} rend 0 hors paladium et endium) : ils ne se reparent pas.
 */
public final class RingRepair {

    /** Capacite illimitee : aucun compteur. */
    private static final int UNLIMITED = -1;

    /**
     * La regle d'une piece.
     *
     * @param endium  repare par les anneaux en endium (et le coffre en endium) plutot qu'en paladium
     * @param worn    reparable portee ({@code isInChest} faux) ; les outils ne le sont qu'au coffre
     * @param budget  capacite, ou {@link #UNLIMITED}
     */
    record Rule(boolean endium, boolean worn, int budget) {
    }

    private static volatile Map<Item, Rule> rules;

    private RingRepair() {
    }

    /** Construite au premier usage : les objets n'existent pas avant l'enregistrement. */
    static Rule rule(ItemStack stack) {
        if (rules == null) {
            Map<Item, Rule> built = new HashMap<>();
            for (var metal : new Item[][]{
                    {PalaEquipment.PALADIUM_HELMET.get(), PalaEquipment.PALADIUM_CHESTPLATE.get(),
                            PalaEquipment.PALADIUM_LEGGINGS.get(), PalaEquipment.PALADIUM_BOOTS.get()},
                    {PalaEquipment.GREEN_PALADIUM_HELMET.get(), PalaEquipment.GREEN_PALADIUM_CHESTPLATE.get(),
                            PalaEquipment.GREEN_PALADIUM_LEGGINGS.get(), PalaEquipment.GREEN_PALADIUM_BOOTS.get()},
                    {PalaEquipment.MIXED_ENDIUM_HELMET.get(), PalaEquipment.MIXED_ENDIUM_CHESTPLATE.get(),
                            PalaEquipment.MIXED_ENDIUM_LEGGINGS.get(), PalaEquipment.MIXED_ENDIUM_BOOTS.get()}}) {
                built.put(metal[0], new Rule(false, true, 8000));
                built.put(metal[1], new Rule(false, true, 12000));
                built.put(metal[2], new Rule(false, true, 10000));
                built.put(metal[3], new Rule(false, true, 7000));
            }
            for (Item piece : new Item[]{PalaEquipment.ENDIUM_HELMET.get(), PalaEquipment.ENDIUM_CHESTPLATE.get(),
                    PalaEquipment.ENDIUM_LEGGINGS.get(), PalaEquipment.ENDIUM_BOOTS.get()}) {
                built.put(piece, new Rule(true, true, UNLIMITED));
            }
            for (Item tool : new Item[]{PalaEquipment.PALADIUM_SWORD.get(), PalaEquipment.PALADIUM_PICKAXE.get(),
                    PalaEquipment.PALADIUM_AXE.get(), PalaEquipment.PALADIUM_SHOVEL.get()}) {
                built.put(tool, new Rule(false, false, 3));
            }
            for (Item tool : new Item[]{PalaEquipment.ENDIUM_SWORD.get(), PalaEquipment.ENDIUM_PICKAXE.get(),
                    PalaEquipment.ENDIUM_AXE.get()}) {
                built.put(tool, new Rule(true, false, UNLIMITED));
            }
            rules = built;
        }
        return stack.isEmpty() ? null : rules.get(stack.getItem());
    }

    /** Un anneau encore utilisable : l'original testait {@code damage < max}. */
    private static boolean usable(ItemStack ring) {
        return !ring.isEmpty() && ring.getDamageValue() < ring.getMaxDamage();
    }

    /** La capacite restante, posee a la capacite pleine au premier passage. */
    private static int remaining(ItemStack piece, Rule rule) {
        if (rule.budget() == UNLIMITED) {
            return Integer.MAX_VALUE;
        }
        Integer left = piece.get(PalaPvp.RING_REPAIR.get());
        if (left == null) {
            piece.set(PalaPvp.RING_REPAIR.get(), rule.budget());
            return rule.budget();
        }
        return left;
    }

    /**
     * Un anneau de l'inventaire sur l'armure portee : un point par piece et
     * par tick ({@code BaseItemRing.onUpdate}, {@code ItemRepairableArmor.repair}).
     */
    static void repairWorn(ServerLevel level, ServerPlayer player, ItemStack ring, boolean endiumRing) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!usable(ring)) {
                return;
            }
            ItemStack piece = player.getItemBySlot(slot);
            Rule rule = rule(piece);
            if (rule == null || !rule.worn() || rule.endium() != endiumRing || piece.getDamageValue() <= 0) {
                continue;
            }
            int left = remaining(piece, rule);
            if (left <= 0) {
                continue;
            }
            ring.hurtAndBreak(1, level, player, item -> { });
            piece.setDamageValue(piece.getDamageValue() - 1);
            if (rule.budget() != UNLIMITED) {
                piece.set(PalaPvp.RING_REPAIR.get(), left - 1);
            }
        }
    }

    /**
     * Un anneau de coffre sur une piece du coffre : un point par tick, la
     * capacite ne baissant qu'a la remise a neuf ({@code repair(piece, anneau)}).
     *
     * @return vrai si l'anneau a servi
     */
    public static boolean repairStored(ServerLevel level, ItemStack piece, ItemStack ring, boolean endiumChest) {
        Rule rule = rule(piece);
        if (rule == null || rule.endium() != endiumChest || piece.getDamageValue() <= 0 || !usable(ring)) {
            return false;
        }
        int left = remaining(piece, rule);
        if (left <= 0) {
            return false;
        }
        ring.hurtAndBreak(1, level, (ServerPlayer) null, item -> { });
        piece.setDamageValue(piece.getDamageValue() - 1);
        if (piece.getDamageValue() <= 0 && rule.budget() != UNLIMITED) {
            piece.set(PalaPvp.RING_REPAIR.get(), left - 1);
        }
        return true;
    }

    /** La capacite restante d'une piece, ou -1 si elle n'en a pas (encore). */
    public static int describe(ItemStack stack) {
        Rule rule = rule(stack);
        if (rule == null || rule.budget() == UNLIMITED) {
            return -1;
        }
        return stack.getOrDefault(PalaPvp.RING_REPAIR.get(), rule.budget());
    }

    /** La capacite pleine d'une piece, pour l'infobulle. */
    public static int capacity(ItemStack stack) {
        Rule rule = rule(stack);
        return rule == null ? -1 : rule.budget();
    }
}
