package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : la table d'enchantement, l'enclume et l'alambic au style du kit, et l'ecran du jeu. */
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

    private static ShotHarness.Shot client(String name, int extra, Runnable action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            try {
                action.run();
            } catch (Throwable t) {
                LOG.error("dohy-shots: ECHEC client " + name, t);
            }
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** Pose le bloc au-dessus du joueur (sur de la pierre), l'ouvre d'un clic droit, puis remplit le menu. */
    private static void station(String name, int extra, String id, Consumer<AbstractContainerMenu> fill) {
        ShotHarness.SHOTS.add(server(name, extra, player -> {
            UiNet.close(player);
            player.closeContainer();
            Level level = player.level();
            BlockPos pos = player.blockPosition().above(4);
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, block.defaultBlockState(), 3);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false);
            InteractionResult result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY,
                    InteractionHand.MAIN_HAND, hit);
            LOG.info("dohy-shots: bloc " + id + " -> " + result + " menu " + player.containerMenu);
            if (fill != null) {
                fill.accept(player.containerMenu);
                player.containerMenu.broadcastChanges();
            }
        }));
    }

    /** L'ecran du jeu, sur le meme menu (une sous-classe anonyme n'est pas remplacee par le kit). */
    private static void vanilla() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        Inventory inventory = minecraft.player.getInventory();
        if (screen instanceof EnchantmentScreen enchant) {
            minecraft.gui.setScreen(new EnchantmentScreen(enchant.getMenu(), inventory, enchant.getTitle()) {
            });
        } else if (screen instanceof AnvilScreen anvil) {
            minecraft.gui.setScreen(new AnvilScreen(anvil.getMenu(), inventory, anvil.getTitle()) {
            });
        } else if (screen instanceof BrewingStandScreen brewing) {
            minecraft.gui.setScreen(new BrewingStandScreen(brewing.getMenu(), inventory, brewing.getTitle()) {
            });
        } else {
            LOG.error("dohy-shots: ECHEC pas d'ecran a remplacer : " + screen);
        }
    }

    private static ItemStack worn(ItemStack stack, int damage) {
        stack.setDamageValue(damage);
        return stack;
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
            player.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, 20));
            player.getInventory().add(new ItemStack(Items.BLAZE_POWDER, 6));
            player.getInventory().add(new ItemStack(Items.NETHER_WART, 12));
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 9));
            player.giveExperienceLevels(30);
        }));
        station("e1_enchant", 40, "minecraft:enchanting_table", menu -> {
            menu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        });
        ShotHarness.SHOTS.add(client("e2_enchant_avant", 20, ShotSamples::vanilla));
        station("e3_enclume", 30, "minecraft:anvil", menu -> {
            menu.getSlot(0).set(worn(new ItemStack(Items.IRON_SWORD), 180));
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 2));
        });
        ShotHarness.SHOTS.add(client("e4_enclume_avant", 20, ShotSamples::vanilla));
        station("e5_enclume_erreur", 30, "minecraft:anvil", menu -> {
            menu.getSlot(0).set(worn(new ItemStack(Items.IRON_SWORD), 180));
            menu.getSlot(1).set(new ItemStack(Items.DIRT, 3));
        });
        station("e6_alambic", 90, "minecraft:brewing_stand", menu -> {
            menu.getSlot(4).set(new ItemStack(Items.BLAZE_POWDER, 2));
            menu.getSlot(3).set(new ItemStack(Items.NETHER_WART, 3));
            for (int index = 0; index < 3; index++) {
                menu.getSlot(index).set(PotionContents.createItemStack(Items.POTION, Potions.WATER));
            }
        });
        ShotHarness.SHOTS.add(client("e7_alambic_avant", 20, ShotSamples::vanilla));
        station("e8_ender", 30, "minecraft:ender_chest", null);
        station("e9_ender_pala", 30, "palamod:paladium_ender_chest", null);
        station("e10_alambic_vide", 30, "minecraft:brewing_stand", null);
    }
}
