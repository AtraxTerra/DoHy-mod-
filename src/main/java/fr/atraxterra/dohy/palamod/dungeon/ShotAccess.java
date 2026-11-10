package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.economy.Economy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Temporaire (captures) : des donjons d'exemple sans passer par la teleportation. */
public final class ShotAccess {
    private ShotAccess() {
    }

    public static void paliers(ServerPlayer player) {
        DungeonManager.recordCleared(player, Dungeon.TALIKUS, 37);
        DungeonMenus.openPaliers(player, Dungeon.TALIKUS, InteractionHand.MAIN_HAND);
    }

    private static DungeonRun run(ServerPlayer player) {
        DungeonRun run = new DungeonRun(9999, (net.minecraft.server.level.ServerLevel) player.level(), Dungeon.TALIKUS, 38, player, List.of(player),
                player.blockPosition());
        run.boostOptions = List.of(Boost.STRENGTH, Boost.SPEED, Boost.REGENERATION);
        List<ItemStack> bag = run.bags.get(player.getUUID());
        bag.add(new ItemStack(Items.DIAMOND, 12));
        bag.add(new ItemStack(Items.GOLDEN_APPLE, 3));
        bag.add(new ItemStack(Items.EMERALD, 30));
        bag.add(new ItemStack(Items.ENCHANTED_BOOK));
        bag.add(new ItemStack(Items.NETHERITE_SCRAP, 2));
        return run;
    }

    public static void boost(ServerPlayer player) {
        DungeonMenus.openBoost(player, run(player));
    }

    public static void merchant(ServerPlayer player) {
        Economy.credit(player, 45_000);
        DungeonMenus.openMerchant(player, run(player));
    }

    public static void bag(ServerPlayer player) {
        DungeonMenus.openBag(player, run(player));
    }
}
