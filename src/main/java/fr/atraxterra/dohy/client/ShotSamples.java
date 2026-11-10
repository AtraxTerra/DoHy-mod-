package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionBranch;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.FactionTree;
import fr.atraxterra.dohy.faction.FactionTreeMenus;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Temporaire : l'arbre de technologie (22b), ouvert par le vrai serveur integre. */
final class ShotSamples {
    private ShotSamples() {
    }

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

    private static void click(MenuController menu, ServerPlayer player, String key) {
        menu.handle(player, new UiNet.Action(menu.id(), "card", key, 0));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("70_tree", player -> {
            player.getInventory().setItem(8, new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.parse("palamod:paladium_ingot")), 100));
            FactionApi.create(player, "Les Aubergines", "Farmeurs du dimanche", List.of("Farmeur", "Chill"), 5);
            double x = player.getX();
            double z = player.getZ();
            FactionApi.claim(player);
            player.teleportTo(x + 16, player.getY(), z);
            FactionApi.claim(player);
            player.teleportTo(x + 32, player.getY(), z);
            FactionApi.claim(player);
            player.teleportTo(x, player.getY(), z);
            Economy.credit(player, 3_000_000L);
            FactionApi.deposit(player, 2_000_000L);
            FactionTree.setTier(player, FactionBranch.DEFENSE, 2);
            FactionTree.start(player, FactionBranch.ECONOMY);
            FactionTreeMenus.Tree.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("71_branch_global", player -> click(FactionTreeMenus.Tree.INSTANCE, player,
                "branch:global")));
        ShotHarness.SHOTS.add(server("72_branch_pillage", player -> {
            FactionTreeMenus.Tree.INSTANCE.open(player);
            click(FactionTreeMenus.Tree.INSTANCE, player, "branch:pillage");
        }));
        ShotHarness.SHOTS.add(server("73_branch_defense", player -> {
            FactionTreeMenus.Tree.INSTANCE.open(player);
            click(FactionTreeMenus.Tree.INSTANCE, player, "branch:defense");
        }));
        ShotHarness.SHOTS.add(server("74_branch_economy", player -> {
            FactionTreeMenus.Tree.INSTANCE.open(player);
            click(FactionTreeMenus.Tree.INSTANCE, player, "branch:economy");
        }));
        ShotHarness.SHOTS.add(server("75_overview", FactionMenus.Overview.INSTANCE::open));
        ShotHarness.SHOTS.add(server("76_settings", player -> {
            FactionTree.setTier(player, FactionBranch.GLOBAL, 6);
            FactionMenus.Settings.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("77_edit", FactionEdit::open));
    }
}
