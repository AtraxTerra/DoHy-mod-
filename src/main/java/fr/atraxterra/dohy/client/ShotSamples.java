package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.alchemist.MysticEnchantMenu;
import fr.atraxterra.dohy.palamod.dimminer.DimMinerManager;
import fr.atraxterra.dohy.palamod.dimminer.DimMinerMenus;
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.palamod.dungeon.ShotAccess;
import fr.atraxterra.dohy.palamod.golem.GolemMenus;
import fr.atraxterra.dohy.palamod.golem.GolemUpgrade;
import fr.atraxterra.dohy.palamod.golem.GuardianGolem;
import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.palamod.mount.BarnControllerBlockEntity;
import fr.atraxterra.dohy.palamod.mount.MountGem;
import fr.atraxterra.dohy.palamod.mount.MountManager;
import fr.atraxterra.dohy.palamod.mount.MountMenus;
import fr.atraxterra.dohy.palamod.mount.PalaMount;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Temporaire : les ecrans du lot 3, ouverts par le vrai serveur integre. */
final class ShotSamples {
    private ShotSamples() {
    }

    private static GuardianGolem golem;
    private static BarnControllerBlockEntity barn;

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

    private static void click(MenuController menu, ServerPlayer player, String key, int button) {
        menu.handle(player, new UiNet.Action(menu.id(), "card", key, button));
    }

    private static MountGem gem(ServerPlayer player, String name, float xp, float damage) {
        return new MountGem(UUID.randomUUID(), Optional.of(player.getUUID()), name, xp, 50, 120, damage,
                -1L, -1L, -1L);
    }

    @SuppressWarnings("unchecked")
    private static BarnControllerBlockEntity barn(ServerPlayer player) throws ReflectiveOperationException {
        if (barn == null) {
            barn = new BarnControllerBlockEntity(player.blockPosition(),
                    PalaMount.BARN_CONTROLLER.get().defaultBlockState());
            Field gems = BarnControllerBlockEntity.class.getDeclaredField("gems");
            gems.setAccessible(true);
            List<MountGem> list = (List<MountGem>) gems.get(barn);
            list.add(gem(player, "Tonnerre", 4200.0f, 3.0f));
            list.add(gem(player, "Plume", 900.0f, 0.0f));
            list.add(gem(player, "Rocaille", 12000.0f, 20.0f));
            list.add(new MountGem(UUID.randomUUID(), Optional.of(UUID.randomUUID()), "Brume", 300.0f, 50, 0, 0.0f,
                    -1L, -1L, -1L));
            Field size = BarnControllerBlockEntity.class.getDeclaredField("size");
            size.setAccessible(true);
            size.setInt(barn, 5);
            Field incubator = BarnControllerBlockEntity.class.getDeclaredField("incubator");
            incubator.setAccessible(true);
            incubator.setBoolean(barn, true);
        }
        return barn;
    }

    private static GuardianGolem golem(ServerPlayer player) {
        if (golem == null) {
            ServerLevel level = (ServerLevel) player.level();
            golem = PalaGolem.GUARDIAN_GOLEM.get().create(level, EntitySpawnReason.COMMAND);
            golem.setPos(player.getX() + 2.0d, player.getY(), player.getZ());
            golem.setOwner(player);
            golem.addXp(60_000);
            for (GolemUpgrade upgrade : List.of(GolemUpgrade.GOLEMANCY, GolemUpgrade.UTILS, GolemUpgrade.WAR,
                    GolemUpgrade.FARMER, GolemUpgrade.INNOVATIVE, GolemUpgrade.GREEDY, GolemUpgrade.WITHER_HUNTER,
                    GolemUpgrade.PACIFICATION)) {
                golem.buy(upgrade);
            }
            level.addFreshEntity(golem);
        }
        return golem;
    }

    static void register() {
        ShotHarness.SHOTS.add(server("20_dungeon_paliers", ShotAccess::paliers));
        ShotHarness.SHOTS.add(server("21_dungeon_boost", ShotAccess::boost));
        ShotHarness.SHOTS.add(server("22_dungeon_merchant", ShotAccess::merchant));
        ShotHarness.SHOTS.add(server("23_dungeon_bag", ShotAccess::bag));
        ShotHarness.SHOTS.add(server("24_rpg", player -> {
            Rpg.grantXp(player, 400_000L);
            RpgMenu.INSTANCE.open(player);
            click(RpgMenu.INSTANCE, player, "reserve:ATTACK", 0);
            click(RpgMenu.INSTANCE, player, "reserve:CRIT", 0);
        }));
        ShotHarness.SHOTS.add(server("25_mount_home", player -> {
            MountManager.data(player).equip(gem(player, "Eclair", 6500.0f, 8.0f));
            MountMenus.openHome(player);
        }));
        ShotHarness.SHOTS.add(server("26_mount_barn", player -> {
            try {
                MountMenus.openBarn(player, barn(player));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }));
        ShotHarness.SHOTS.add(server("27_mount_heal", player -> click(MountMenus.Barn.INSTANCE, player, "gem:0", 2)));
        ShotHarness.SHOTS.add(server("28_golem_main", player -> GolemMenus.openMain(player, golem(player))));
        ShotHarness.SHOTS.add(server("29_golem_tree", player -> click(GolemMenus.Main.INSTANCE, player, "tree", 0)));
        ShotHarness.SHOTS.add(server("30_golem_choices", player -> {
            GolemMenus.openMain(player, golem(player));
            click(GolemMenus.Main.INSTANCE, player, "choices", 0);
        }));
        ShotHarness.SHOTS.add(server("31_dimminer_entry", DimMinerMenus::openEntry));
        ShotHarness.SHOTS.add(server("32_dimminer_summary",
                player -> DimMinerMenus.openStats(player, DimMinerManager.data(player))));
        ShotHarness.SHOTS.add(server("33_mystic", player -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_BOOTS));
            player.giveExperienceLevels(40);
            MysticEnchantMenu.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("34_collection_farm", player -> {
            fr.atraxterra.dohy.ui.menu.CollectionsController.INSTANCE.open(player);
            click(fr.atraxterra.dohy.ui.menu.CollectionsController.INSTANCE, player, "category:FARM", 0);
        }));
    }
}
