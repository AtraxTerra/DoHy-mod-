package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : le tour des conteneurs et des machines. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();

    private ShotSamples() {
    }

    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
                }
            });
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** Pose le bloc au-dessus du joueur et l'ouvre comme d'un clic droit, main vide. */
    private static void block(String name, String id) {
        ShotHarness.SHOTS.add(server(name, 30, player -> {
            UiNet.close(player);
            player.closeContainer();
            Level level = player.level();
            BlockPos pos = player.blockPosition().above(4);
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            if (block == Blocks.AIR) {
                LOG.error("dohy-shots: ECHEC bloc inconnu " + id);
                return;
            }
            level.setBlock(pos, block.defaultBlockState(), 3);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false);
            InteractionResult result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY,
                    InteractionHand.OFF_HAND, hit);
            LOG.info("dohy-shots: bloc " + id + " -> " + result);
        }));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            command(player, "gamerule doDaylightCycle false");
            command(player, "kill @e[type=minecraft:slime]");
            player.setGameMode(GameType.SURVIVAL);
            Economy.set(player, 125_000L);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 12));
            player.getInventory().add(new ItemStack(Items.COAL, 20));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
            player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
            player.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, 9));
            player.getInventory().add(new ItemStack(Items.NETHER_WART, 8));
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, 3));
            player.giveExperienceLevels(30);
        }));
        block("d01_etabli", "minecraft:crafting_table");
        block("d02_four", "minecraft:furnace");
        block("d03_haut_fourneau", "minecraft:blast_furnace");
        block("d04_coffre", "minecraft:chest");
        block("d05_ender", "minecraft:ender_chest");
        block("d06_tonneau", "minecraft:barrel");
        block("d07_entonnoir", "minecraft:hopper");
        block("d08_distributeur", "minecraft:dispenser");
        block("d09_enchant", "minecraft:enchanting_table");
        block("d10_enclume", "minecraft:anvil");
        block("d11_alambic", "minecraft:brewing_stand");
        block("d12_forgeron", "minecraft:smithing_table");
        block("d13_meule", "minecraft:grindstone");
        block("d14_tailleur", "minecraft:stonecutter");
        block("d15_metier", "minecraft:loom");
        block("d16_cartographie", "minecraft:cartography_table");
        block("d17_crusher", "palamod:crusher");
        block("d18_four_pala", "palamod:paladium_furnace");
        block("d19_coffre_pala", "palamod:paladium_chest");
        block("d20_palamachine", "palamod:palamachine");
        block("d21_cryo", "dohy:cryo_chamber");
        block("d22_palette", "dohy:palette");
        block("d23_ender_pala", "palamod:paladium_ender_chest");
        block("d24_grinder", "palamod:grinder");
    }
}
