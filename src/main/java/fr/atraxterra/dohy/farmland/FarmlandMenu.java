package fr.atraxterra.dohy.farmland;

import fr.atraxterra.dohy.farmland.FarmlandMap.Zone;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.spawn.SpawnPortals;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * La farmland vue du spawn (borne DONJONS, carte « Farmland ») : ses coins, ce qui y
 * apparait et combien il y en a, ses regles, et le depart.
 */
public final class FarmlandMenu extends MenuController {

    public static final FarmlandMenu INSTANCE = new FarmlandMenu();

    private FarmlandMenu() {
        super("farmland");
    }

    @Override
    protected void back(ServerPlayer player) {
        SpawnPortals.INSTANCE.open(player);
    }

    /** Ce qu'on lit sur la carte d'un coin : court ; la liste entiere est dans l'infobulle. */
    private static String brief(Zone zone) {
        return switch (zone) {
            case FOREST -> "Monstres, loups, lapins";
            case MEADOW -> "Le betail";
            case HAMLET -> "Villageois";
            case MUSHROOM -> "Champimeuh";
            case HILLS -> "Chevres, et une grotte";
            case JUNGLE -> "Elephants, pandas";
            case LAKE -> "Dauphins, gardiens";
            case BEACH -> "Tortues";
            case SWAMP -> "Sorcieres, slimes";
            case NETHER -> "Blazes, wither, ghasts";
            case END -> "Endermen, shulkers";
            default -> "";
        };
    }

    private static ItemStack icon(Zone zone) {
        String name = switch (zone) {
            case FOREST -> "oak_sapling";
            case MEADOW -> "wheat";
            case HAMLET -> "bell";
            case MUSHROOM -> "red_mushroom";
            case HILLS -> "goat_horn";
            case JUNGLE -> "bamboo";
            case LAKE -> "heart_of_the_sea";
            case BEACH -> "turtle_scute";
            case SWAMP -> "slime_ball";
            case NETHER -> "netherrack";
            case END -> "ender_pearl";
            default -> "grass_block";
        };
        return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(name)).map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.MAP));
    }

    private static int color(Zone zone) {
        return switch (zone) {
            case FOREST, JUNGLE -> 0xFF22E558;
            case MEADOW, HAMLET -> 0xFFFAC400;
            case MUSHROOM, NETHER -> 0xFFEF3826;
            case LAKE, BEACH -> 0xFF4FB8FF;
            case SWAMP -> 0xFF7BC96F;
            case END -> 0xFF9B6BFF;
            default -> 0xFFB8BCC6;
        };
    }

    @Override
    public MenuView view(ServerPlayer player) {
        ServerLevel level = Farmland.level(player.level().getServer());
        Map<String, Integer> counts = level == null ? Map.of() : FarmlandSpawner.census(level);
        Map<Zone, Integer> byZone = new EnumMap<>(Zone.class);
        int total = 0;
        for (FarmlandSpawner.Pool pool : FarmlandSpawner.POOLS) {
            int count = counts.getOrDefault(pool.name(), 0);
            byZone.merge(pool.zone(), count, Integer::sum);
            total += count;
        }
        boolean here = Farmland.inFarmland(player);
        boolean built = level != null && Farmland.ready(level);

        List<Card> go = new ArrayList<>();
        if (here) {
            go.add(Card.of(new ItemStack(Items.COMPASS), "Retour au spawn", List.of("",
                    "ou /spawn")).accent(0xFFFF5A4A).action("leave|rentrer"));
        } else {
            go.add(Card.of(new ItemStack(Items.IRON_SWORD), "Aller a la farmland", List.of(
                            built ? "Arrivee au camp, au centre" : "",
                            "Sortie : la borne RETOUR ou /spawn"))
                    .accent(0xFFFAC400).action("go|y aller"));
        }

        List<Card> zones = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            if (zone == Zone.HUB || zone == Zone.RIM || zone == Zone.VOID) {
                continue;
            }
            int count = byZone.getOrDefault(zone, 0);
            int cap = 0;
            for (FarmlandSpawner.Pool pool : FarmlandSpawner.POOLS) {
                if (pool.zone() == zone) {
                    cap += pool.cap();
                }
            }
            zones.add(Card.of(icon(zone), zone.label(), List.of(brief(zone),
                            count > 0 ? count + " sur place (" + cap + " au plus)" : cap + " au plus"))
                    .accent(color(zone)).tooltip(List.of(zone.creatures().split(" ; "))));
        }

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("CREATURES", String.valueOf(total), total > 0 ? 0xFF22E558 : 0xFFB8BCC6),
                new MenuView.Stat("CHASSEUR", "NIV. " + JobApi.level(player, Job.HUNTER), 0xFFFFFFFF),
                new MenuView.Stat("CARTE", built ? "PRETE" : "A BATIR", built ? 0xFF22E558 : 0xFFFAC400));
        List<MenuView.Section> sections = List.of(
                new MenuView.Section(here ? "TU Y ES" : "PARTIR", "upgrade", MenuView.GRID, go),
                new MenuView.Section("LES COINS", "resources", MenuView.GRID, zones));
        return new MenuView(id(), "Farmland", "", stats, sections, true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if ("go".equals(key)) {
            UiNet.close(player);
            Farmland.enter(player);
        } else if ("leave".equals(key)) {
            UiNet.close(player);
            Farmland.leave(player);
        }
    }
}
