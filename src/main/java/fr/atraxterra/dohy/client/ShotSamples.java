package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : le metier a tisser et la table de cartographie au style du kit, et l'ecran du jeu. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();

    private static ItemStack map = ItemStack.EMPTY;
    private static ItemStack big = ItemStack.EMPTY;

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
        if (screen instanceof LoomScreen loom) {
            minecraft.gui.setScreen(new LoomScreen(loom.getMenu(), inventory, loom.getTitle()) {
            });
        } else if (screen instanceof CartographyTableScreen table) {
            minecraft.gui.setScreen(new CartographyTableScreen(table.getMenu(), inventory, table.getTitle()) {
            });
        } else {
            LOG.error("dohy-shots: ECHEC pas d'ecran a remplacer : " + screen);
        }
    }

    /** Fait rouler la molette sur la grille des motifs. */
    private static void scroll() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen != null) {
            double x = screen.width / 2.0d;
            double y = screen.height / 2.0d - 40.0d;
            boolean done = screen.mouseScrolled(x, y, 0.0d, -2.0d);
            LOG.info("dohy-shots: molette -> " + done + " sur " + screen.getClass().getName());
        }
    }

    private static ItemStack fullBanner() {
        HolderLookup.RegistryLookup<BannerPattern> patterns = Minecraft.getInstance().getSingleplayerServer()
                .registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers layers = new BannerPatternLayers.Builder()
                .add(patterns.getOrThrow(BannerPatterns.STRIPE_BOTTOM), DyeColor.RED)
                .add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.BLUE)
                .add(patterns.getOrThrow(BannerPatterns.CROSS), DyeColor.YELLOW)
                .add(patterns.getOrThrow(BannerPatterns.CIRCLE_MIDDLE), DyeColor.GREEN)
                .add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLACK)
                .add(patterns.getOrThrow(BannerPatterns.STRIPE_CENTER), DyeColor.ORANGE)
                .build();
        ItemStack banner = new ItemStack(Items.BANNER.pick(DyeColor.WHITE));
        banner.set(DataComponents.BANNER_PATTERNS, layers);
        return banner;
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
            ItemStack center = player.getInventory().getItem(13);
            player.getInventory().setItem(13, ItemStack.EMPTY);
            player.getInventory().setItem(35, center);
            // Deux cartes remplies (apercu des biomes) : une ordinaire, une a sa plus grande echelle.
            ServerLevel level = (ServerLevel) player.level();
            map = MapItem.create(level, player.getBlockX(), player.getBlockZ(), (byte) 1, true, false);
            MapItem.renderBiomePreviewMap(level, map);
            big = MapItem.create(level, player.getBlockX() + 3000, player.getBlockZ(), (byte) 4, true, false);
            MapItem.renderBiomePreviewMap(level, big);
            player.getInventory().setItem(0, map.copy());
            player.getInventory().setItem(1, big.copy());
        }));
        station("l1_metier_vide", 30, "minecraft:loom", null);
        station("l2_metier_motifs", 30, "minecraft:loom", menu -> {
            menu.getSlot(0).set(new ItemStack(Items.BANNER.pick(DyeColor.WHITE)));
            menu.getSlot(1).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 8));
        });
        ShotHarness.SHOTS.add(server("l3_metier_choix", 30, player -> {
            player.containerMenu.clickMenuButton(player, 5);
            player.containerMenu.broadcastChanges();
        }));
        ShotHarness.SHOTS.add(client("l4_metier_defile", 20, ShotSamples::scroll));
        ShotHarness.SHOTS.add(client("l5_metier_avant", 20, ShotSamples::vanilla));
        station("l6_metier_motif_objet", 30, "minecraft:loom", menu -> {
            menu.getSlot(0).set(new ItemStack(Items.BANNER.pick(DyeColor.BLUE)));
            menu.getSlot(1).set(new ItemStack(Items.DYE.pick(DyeColor.YELLOW), 4));
            menu.getSlot(2).set(new ItemStack(Items.FLOWER_BANNER_PATTERN));
        });
        ShotHarness.SHOTS.add(server("l7_metier_motif_objet_choix", 30, player -> {
            player.containerMenu.clickMenuButton(player, 0);
            player.containerMenu.broadcastChanges();
        }));
        station("l8_metier_plein", 30, "minecraft:loom", menu -> {
            menu.getSlot(0).set(fullBanner());
            menu.getSlot(1).set(new ItemStack(Items.DYE.pick(DyeColor.RED), 8));
        });
        station("c1_carto_vide", 30, "minecraft:cartography_table", null);
        station("c2_carto_carte", 30, "minecraft:cartography_table", menu -> menu.getSlot(0).set(map.copy()));
        station("c3_carto_agrandir", 30, "minecraft:cartography_table", menu -> {
            menu.getSlot(0).set(map.copy());
            menu.getSlot(1).set(new ItemStack(Items.PAPER));
        });
        station("c4_carto_copier", 30, "minecraft:cartography_table", menu -> {
            menu.getSlot(0).set(map.copy());
            menu.getSlot(1).set(new ItemStack(Items.MAP));
        });
        station("c5_carto_verrouiller", 30, "minecraft:cartography_table", menu -> {
            menu.getSlot(0).set(map.copy());
            menu.getSlot(1).set(new ItemStack(Items.GLASS_PANE));
        });
        ShotHarness.SHOTS.add(client("c6_carto_avant", 20, ShotSamples::vanilla));
        station("c7_carto_erreur", 30, "minecraft:cartography_table", menu -> {
            menu.getSlot(0).set(big.copy());
            menu.getSlot(1).set(new ItemStack(Items.PAPER));
        });
    }
}
