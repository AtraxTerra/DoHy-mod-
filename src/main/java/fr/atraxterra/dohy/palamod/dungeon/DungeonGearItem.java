package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Une piece d'equipement de donjon : armure ou epee.
 *
 * <p>Son bonus ne joue que dans la dimension des donjons, contre leurs
 * monstres : a plein dans son propre donjon, a moitie dans les trois autres
 * (l'antique, a plein partout). Une armure retire {@link DungeonGearTier#armorBonus}
 * des degats par piece portee, plafonne a 60 % au total ; l'epee ajoute
 * {@link DungeonGearTier#swordBonus} a ses coups. Le set antique complet
 * ajoute 10 % de reduction et 20 % de degats.
 */
public class DungeonGearItem extends Item {

    /** Le plafond de reduction, toutes pieces comprises. */
    public static final float MAX_REDUCTION = 0.60f;
    private static final float ANTIQUE_SET_REDUCTION = 0.10f;
    private static final float ANTIQUE_SET_DAMAGE = 0.20f;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Dungeon dungeon;
    private final DungeonGearTier tier;
    private final boolean armor;

    public DungeonGearItem(Dungeon dungeon, DungeonGearTier tier, boolean armor, Properties properties) {
        super(properties);
        this.dungeon = dungeon;
        this.tier = tier;
        this.armor = armor;
    }

    /** Le donjon de la piece, ou {@code null} pour l'antique. */
    public Dungeon dungeon() {
        return dungeon;
    }

    public DungeonGearTier tier() {
        return tier;
    }

    public boolean isArmor() {
        return armor;
    }

    /** Son bonus dans un donjon donne. */
    public float bonusIn(Dungeon where) {
        float base = armor ? tier.armorBonus() : tier.swordBonus();
        return dungeon == null || dungeon == where ? base : base / 2.0f;
    }

    private static boolean antiqueSet(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!(player.getItemBySlot(slot).getItem() instanceof DungeonGearItem gear)
                    || gear.tier != DungeonGearTier.ANTIQUE) {
                return false;
            }
        }
        return true;
    }

    /** La part des degats de monstres retiree par l'armure portee, dans ce donjon. */
    public static float reduction(ServerPlayer player, Dungeon where) {
        float total = 0.0f;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (player.getItemBySlot(slot).getItem() instanceof DungeonGearItem gear && gear.armor) {
                total += gear.bonusIn(where);
            }
        }
        if (antiqueSet(player)) {
            total += ANTIQUE_SET_REDUCTION;
        }
        return Math.min(MAX_REDUCTION, total);
    }

    /** Le supplement de degats contre les monstres, epee en main et set antique. */
    public static float damageBonus(ServerPlayer player, Dungeon where) {
        float bonus = 0.0f;
        if (player.getMainHandItem().getItem() instanceof DungeonGearItem gear && !gear.armor) {
            bonus += gear.bonusIn(where);
        }
        if (antiqueSet(player)) {
            bonus += ANTIQUE_SET_DAMAGE;
        }
        return bonus;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int percent = Math.round((armor ? tier.armorBonus() : tier.swordBonus()) * 100.0f);
        String kind = armor ? "armor" : "sword";
        if (dungeon == null) {
            tooltip.accept(Component.translatable("tooltip.palamod.dungeon_gear.antique." + kind, percent)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("tooltip.palamod.dungeon_gear.antique_set")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.accept(Component.translatable("tooltip.palamod.dungeon_gear." + kind, percent, dungeon.display())
                    .withStyle(dungeon.color()));
        }
        RuneData rune = stack.get(PalaDungeon.RUNE.get());
        if (rune != null) {
            RuneItem.describe(rune, tooltip);
        } else if (armor) {
            tooltip.accept(Component.translatable("tooltip.palamod.dungeon_gear.rune_slot")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (dungeon == null) {
            tooltip.accept(Component.translatable("tooltip.palamod.dungeon_gear.repair_any")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            Item essence = PalaDungeonGear.essence(dungeon, tier.essence());
            tooltip.accept(Component.translatable("tooltip.palamod.dungeon_gear.repair",
                    essence.getName(new ItemStack(essence))).withStyle(ChatFormatting.GRAY));
        }
    }
}
