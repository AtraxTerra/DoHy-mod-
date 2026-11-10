package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Temporaire : les ecrans de faction (22a), ouverts par le vrai serveur integre. */
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

    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count);
    }

    static void register() {
        ShotHarness.SHOTS.add(server("60_faction_create", player -> {
            player.getInventory().setItem(8, item("palamod:paladium_ingot", 64));
            player.getInventory().setItem(7, item("palamod:paladium_ingot", 64));
            FactionEdit.open(player);
        }));
        ShotHarness.SHOTS.add(server("61_faction_overview", player -> {
            String refusal = FactionApi.create(player, "Les Aubergines", "Farmeurs du dimanche, mineurs du lundi",
                    List.of("Farmeur", "Chill"), 5);
            if (refusal != null) {
                throw new IllegalStateException(refusal);
            }
            FactionApi.claim(player);
            Economy.credit(player, 2_500_000L);
            FactionApi.deposit(player, 100_000L);
            FactionApi.deposit(player, 1_000_000L);
            FactionApi.withdraw(player, 10_000L);
            FactionApi.addElo(player, 7_200L, "Egg Hunt");
            FactionMenus.Overview.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("62_faction_bank", FactionMenus.Bank.INSTANCE::open));
        ShotHarness.SHOTS.add(server("63_faction_claims", FactionMenus.Claims.INSTANCE::open));
        ShotHarness.SHOTS.add(server("64_faction_ranking", FactionMenus.Ranking.INSTANCE::open));
        ShotHarness.SHOTS.add(server("65_faction_settings", FactionMenus.Settings.INSTANCE::open));
        ShotHarness.SHOTS.add(server("66_faction_edit", FactionEdit::open));
    }
}
