package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import fr.atraxterra.dohy.palamod.machine.PalaMachines;
import fr.atraxterra.dohy.palamod.machine.PalaMenus;
import fr.atraxterra.dohy.palamod.machine.VoidstoneContainer;
import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.storage.ChestTier;
import fr.atraxterra.dohy.palamod.storage.PalaChestMenu;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/** Temporaire : les ecrans du lot 4 (machines et coffres), ouverts par le vrai serveur integre. */
final class ShotSamples {
    private ShotSamples() {
    }

    private static int spot;

    private static ShotHarness.Shot server(String name, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    org.slf4j.LoggerFactory.getLogger("dohy-shots").error("dohy-shots: ECHEC serveur " + name, t);
                }
            });
            return null;
        });
    }

    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count);
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void inventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(2, new ItemStack(Items.BREAD, 12));
        player.getInventory().setItem(3, item("palamod:paladium_ingot", 5));
        player.getInventory().setItem(4, item("palamod:eggplant", 30));
        player.getInventory().setItem(9, new ItemStack(Items.COAL, 20));
        player.getInventory().setItem(10, item("palamod:titane_ingot", 14));
    }

    private static void machine(ServerPlayer player, Block block, Consumer<BlockEntity> setup) {
        ServerLevel level = (ServerLevel) player.level();
        inventory(player);
        int index = spot++;
        BlockPos pos = player.blockPosition().offset(-3 + index % 7, 3 + index / 7, 2);
        level.setBlock(pos, block.defaultBlockState(), 3);
        BlockEntity entity = level.getBlockEntity(pos);
        setup.accept(entity);
        player.openMenu((MenuProvider) entity);
    }

    private static void chest(ServerPlayer player, KitMenus.Use use, String title) {
        inventory(player);
        SimpleContainer container = new SimpleContainer(use.rows() * 9);
        container.setItem(0, new ItemStack(Items.WHEAT, 64));
        container.setItem(1, item("palamod:eggplant", 32));
        container.setItem(4, new ItemStack(Items.IRON_INGOT, 12));
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new ChestMenu(use.type(), id, inventory,
                container, use.rows()), Component.literal(title)));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("40_crusher", player -> machine(player, PalaMachines.CRUSHER_BLOCK.get(), be -> {
            Container c = (Container) be;
            c.setItem(0, item("palamod:eggplant", 12));
            c.setItem(1, item("palamod:gold_mixed_coal", 5));
            set(be, "tanks", new int[]{21, 4, 37, 0});
            set(be, "work", 40);
        })));
        ShotHarness.SHOTS.add(server("41_cobblebreaker", player -> machine(player,
                PalaMachines.COBBLEBREAKER_BLOCK.get(), be -> {
                    Container c = (Container) be;
                    c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    c.setItem(1, item("palamod:iron_particle", 7));
                    c.setItem(2, item("palamod:gold_particle", 3));
                    c.setItem(3, item("palamod:diamond_particle", 1));
                })));
        ShotHarness.SHOTS.add(server("42_voidstone", player -> {
            inventory(player);
            player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new MachineMenu(
                    PalaMenus.VOIDSTONE.get(), id, inventory, new VoidstoneContainer(),
                    new SimpleContainerData(MachineMenu.DATA_COUNT), MachineLayout.VOIDSTONE),
                    Component.translatable("item.palamod.voidstone")));
        }));
        ShotHarness.SHOTS.add(server("43_palafurnace", player -> machine(player,
                PalaMachines.PALAFURNACE_BLOCK.get(), be -> {
                    Container c = (Container) be;
                    c.setItem(0, new ItemStack(Items.RAW_IRON, 16));
                    c.setItem(1, new ItemStack(Items.COAL, 8));
                    c.setItem(3, new ItemStack(PalaMachines.FURNACE_UPGRADE.get(), 3));
                })));
        ShotHarness.SHOTS.add(server("44_palaforge", player -> machine(player,
                PalaMachines.PALAFORGE_BLOCK.get(), be -> {
                    Container c = (Container) be;
                    c.setItem(0, item("palamod:paladium_chestplate", 1));
                    c.setItem(2, new ItemStack(Items.COAL, 6));
                })));
        ShotHarness.SHOTS.add(server("45_grinder", player -> machine(player, PalaMachines.GRINDER_BLOCK.get(), be -> {
            Container c = (Container) be;
            c.setItem(5, item("palamod:paladium_ingot", 10));
            set(be, "paladium", 37);
        })));
        ShotHarness.SHOTS.add(server("46_food_machine", player -> machine(player, PalaGolem.FOOD_MACHINE.get(), be -> {
            Container c = (Container) be;
            c.setItem(1, new ItemStack(Items.MELON_SLICE, 20));
            c.setItem(2, new ItemStack(Items.COBBLESTONE, 20));
            c.setItem(3, new ItemStack(Items.ROTTEN_FLESH, 20));
            set(be, "lava", 6400);
        })));
        ShotHarness.SHOTS.add(server("47_golem_box", player -> machine(player, PalaGolem.GOLEM_BOX.get(), be -> {
            Container c = (Container) be;
            c.setItem(3, new ItemStack(PalaGolem.CROQUETTE.get(), 16));
            c.setItem(4, new ItemStack(PalaGolem.CROQUETTE.get(), 16));
        })));
        ShotHarness.SHOTS.add(server("48_palamachine", player -> machine(player, PalaPvp.PALAMACHINE.get(), be -> {
            Container c = (Container) be;
            c.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            c.setItem(1, item("palamod:paladium_ingot", 4));
        })));
        ShotHarness.SHOTS.add(server("49_paladium_chest", player -> {
            inventory(player);
            ChestTier tier = ChestTier.PALADIUM;
            SimpleContainer container = new SimpleContainer(tier.containerSize());
            container.setItem(0, new ItemStack(Items.DIAMOND, 64));
            container.setItem(13, item("palamod:paladium_ingot", 40));
            container.setItem(30, new ItemStack(Items.OAK_LOG, 64));
            player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new PalaChestMenu(
                    PalaStorage.CHEST_MENUS.get(tier).get(), tier, id, inventory, container),
                    Component.literal("Coffre en paladium")));
        }));
        ShotHarness.SHOTS.add(server("50_sell_chest", player -> chest(player, KitMenus.Use.SELL_CHEST,
                "Coffre de vente")));
        ShotHarness.SHOTS.add(server("51_museum", player -> chest(player, KitMenus.Use.MUSEUM_DEPOSIT,
                "Guichet du musee")));
        ShotHarness.SHOTS.add(server("52_mount", player -> chest(player, KitMenus.Use.MOUNT_INVENTORY,
                "Inventaire de Eclair")));
        ShotHarness.SHOTS.add(server("53_golem_storage", player -> chest(player, KitMenus.Use.GOLEM_STORAGE,
                "Inventaire du golem")));
        ShotHarness.SHOTS.add(server("54_ender", player -> chest(player, KitMenus.Use.PALADIUM_ENDER_CHEST,
                "Coffre de l'End en paladium")));
    }
}
