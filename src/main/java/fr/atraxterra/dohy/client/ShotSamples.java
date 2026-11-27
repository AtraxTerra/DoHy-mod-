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

/** Temporaire : les cartouches du HUD (finders, receptacle). */
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
            // Ce que le conteneur rend tombe dans la case sous la souris : videe.
            player.getInventory().setItem(13, ItemStack.EMPTY);
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
            command(player, "execute at @p run setblock ~3 ~ ~3 minecraft:chest");
            command(player, "execute at @p run setblock ~-4 ~ ~5 minecraft:barrel");
        }));
        ShotHarness.SHOTS.add(server("h1_finder_bleu", 40, player -> {
            UiNet.close(player);
            command(player, "item replace entity @p weapon.mainhand with palamod:unclaim_finder_blue");
        }));
        ShotHarness.SHOTS.add(server("h2_finder_spawner", 40, player -> {
            command(player, "item replace entity @p weapon.mainhand with palamod:spawner_finder");
        }));
        ShotHarness.SHOTS.add(server("h3_receptacle", 40, player -> {
            command(player, "item replace entity @p weapon.mainhand with minecraft:iron_sword");
            command(player, "execute as @p at @s run tp @s ~ ~ ~ 0 0");
            command(player, "execute at @p run setblock ~ ~1 ~2 palamod:wither_receptacle");
        }));
    }
}
