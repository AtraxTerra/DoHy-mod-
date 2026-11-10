package fr.atraxterra.dohy.palamod.dimminer;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Les ecrans de la dimension du Mineur : l'entree, et le bilan de fin de session. */
public final class DimMinerMenus {

    private DimMinerMenus() {
    }

    public static void openEntry(ServerPlayer player) {
        Entry.INSTANCE.open(player);
    }

    /** Fige les valeurs a l'instant de la fin : la session est remise a zero juste apres. */
    public static void openStats(ServerPlayer player, DimMinerData data) {
        long gained = JobApi.xp(player, Job.MINER) - data.jobXpStart();
        Summary.INSTANCE.snapshots.put(player.getUUID(), new Stats(data.minedBlocks(), data.timePlayed(),
                DimMinerManager.oreCounts(data), Math.max(0L, gained)));
        Summary.INSTANCE.open(player);
    }

    private record Stats(long blocks, long minutes, Map<Block, Long> ores, long xp) {
    }

    public static final class Entry extends MenuController {
        public static final Entry INSTANCE = new Entry();

        private Entry() {
            super("dimminer_entry");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            DimMinerData data = DimMinerManager.data(player);
            boolean active = data.isActive();
            long points = active ? data.points() : DimMinerData.TOTAL_POINTS;
            List<Card> cards = List.of(
                    Card.of(new ItemStack(Items.BOOK), "La dimension", List.of("Minerais multiplies",
                                    "Pour les joueurs aguerris"))
                            .accent(0xFFEF3826)
                            .tooltip(List.of("Paladium : 1 point", "Paladium vert : 5 points",
                                    "Pepite d'endium : 501 points", "Les autres blocs sont gratuits.", "",
                                    "Pas de monstres, pas de chute, pas de coffres.",
                                    "Sortie : le portail d'arrivee ou /dimminer quitter.")),
                    Card.of(new ItemStack(Items.CLOCK), "Ta session", List.of(
                                    "Temps restant " + DimMinerManager.clock(data.remainingSeconds()),
                                    "Points " + points + " / " + DimMinerData.TOTAL_POINTS))
                            .accent(0xFFFAC400).progress(points, DimMinerData.TOTAL_POINTS),
                    Card.of(new ItemStack(Items.ENDER_EYE), active ? "Rejoindre" : "Creer la dimension", List.of(
                                    active ? "Ta zone t'attend" : "1 000 points, 3 jours", "Clic : entrer"))
                            .accent(0xFF22E558).action("join|entrer"));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("POINTS", points + " / " + DimMinerData.TOTAL_POINTS, 0xFFFAC400),
                    new MenuView.Stat("TEMPS", DimMinerManager.clock(data.remainingSeconds()), 0xFFFFFFFF));
            return new MenuView(id(), "Dimension mineur", "Un recapitulatif avant d'entrer", stats,
                    List.of(new MenuView.Section("", "", MenuView.GRID, cards)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if ("join".equals(key)) {
                UiNet.close(player);
                DimMinerManager.join(player);
            }
        }
    }

    public static final class Summary extends MenuController {
        public static final Summary INSTANCE = new Summary();
        final Map<UUID, Stats> snapshots = new ConcurrentHashMap<>();

        private Summary() {
            super("dimminer_summary");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Stats stats = snapshots.get(player.getUUID());
            if (stats == null) {
                return new MenuView(id(), "Session finie", "", List.of(), List.of(), false);
            }
            List<String> ores = new ArrayList<>();
            stats.ores().forEach((block, count) -> ores.add(MenuView.text(block.getName().copy().append(" x" + count))));
            int level = JobApi.level(player, Job.MINER);
            List<Card> cards = List.of(
                    Card.of(new ItemStack(Items.STONE), "Blocs mines", List.of(MoneyFormat.exact(stats.blocks()),
                            "Au total")).accent(0xFFEF3826),
                    Card.of(new ItemStack(Items.CLOCK), "Temps passe", List.of(
                            DimMinerManager.clock(stats.minutes() * 60L), "Dans la dimension")).accent(0xFFFAC400),
                    Card.of(new ItemStack(Items.DIAMOND_ORE), "Minerais", List.of(
                            ores.isEmpty() ? "Aucun" : ores.size() + " sorte(s)", "Detail dans la bulle"))
                            .accent(0xFF2F5BEA).tooltip(ores.isEmpty() ? List.of("Aucun") : ores),
                    Card.of(new ItemStack(Items.IRON_PICKAXE), "Metier mineur", List.of(
                            level >= JobApi.maxLevel() ? "Niveau max" : "Niveau " + level,
                            "+" + MoneyFormat.exact(stats.xp()) + " XP")).accent(0xFF22E558));
            return new MenuView(id(), "Session finie", "Le bilan de ta dimension", List.of(),
                    List.of(new MenuView.Section("", "", MenuView.GRID, cards)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
        }
    }
}
