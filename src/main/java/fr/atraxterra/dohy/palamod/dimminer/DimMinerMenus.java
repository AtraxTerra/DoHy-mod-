package fr.atraxterra.dohy.palamod.dimminer;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Les ecrans de la dimension, en coffres comme le reste de DoHy.
 *
 * <p>{@code UIDimMinerEntry} (avant d'entrer : temps et points restants, et
 * le bouton Creer ou Rejoindre) et {@code UIDimMinerStats} (fin de session :
 * blocs mines, temps passe, minerais, recapitulatif du metier). Les textes de
 * l'original sont gardes, fautes comprises.
 */
public final class DimMinerMenus {

    private DimMinerMenus() {
    }

    public static void openEntry(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new Entry(id, inventory, player),
                Component.literal("Dimension mineur")));
    }

    /** Fige les valeurs a l'instant de la fin : la session est remise a zero juste apres. */
    public static void openStats(ServerPlayer player, DimMinerData data) {
        long gained = JobApi.xp(player, Job.MINER) - data.jobXpStart();
        Stats snapshot = new Stats(data.minedBlocks(), data.timePlayed(), DimMinerManager.oreCounts(data),
                Math.max(0L, gained));
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new StatsMenu(id, inventory, player,
                snapshot), Component.literal("Session finie")));
    }

    private record Stats(long blocks, long minutes, Map<Block, Long> ores, long xp) {
    }

    private static final class Entry extends DisplayMenu {

        private static final int INFO = 11;
        private static final int STATUS = 13;
        private static final int JOIN = 15;

        Entry(int containerId, Inventory inventory, ServerPlayer player) {
            super(containerId, inventory, player, 3);
            refresh();
        }

        @Override
        protected void build() {
            frame();
            set(INFO, Gui.button(Items.BOOK, "Dimension mineur", Gui.lore(
                    Gui.hint("Plongez dans une dimension ou les minerais sont"),
                    Gui.hint("multipliés, uniquement reservée au joueurs aguéris."),
                    Gui.blank(),
                    Gui.field("Paladium", "1 point"),
                    Gui.field("Paladium vert", "5 points"),
                    Gui.field("Pepite d'endium", "501 points"),
                    Gui.hint("Les autres blocs sont gratuits."),
                    Gui.blank(),
                    Gui.hint("Pas de monstres, pas de chute, pas de coffres."),
                    Gui.hint("Sortie : le portail d'arrivee ou /dimminer quitter."))));
            DimMinerData data = DimMinerManager.data(player);
            boolean active = data.isActive();
            List<Component> status = new ArrayList<>();
            status.add(Gui.hint("Avant de rentrer dans la dimension, voici un"));
            status.add(Gui.hint("récapitulatif de votre dimension."));
            status.add(Gui.blank());
            status.add(Gui.field("Temps restant", DimMinerManager.clock(data.remainingSeconds())));
            status.add(Gui.field("Points restants",
                    (active ? data.points() : DimMinerData.TOTAL_POINTS) + "/" + DimMinerData.TOTAL_POINTS));
            set(STATUS, Gui.button(Items.CLOCK, "Informations", status));
            set(JOIN, Gui.button(Items.ENDER_EYE, active ? "Rejoindre la dimension" : "Créer la dimension",
                    Gui.lore(Gui.action("Clic : entrer"))));
            set(22, Gui.close());
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (slot == JOIN) {
                player.closeContainer();
                DimMinerManager.join(player);
            } else if (slot == 22) {
                player.closeContainer();
            }
        }
    }

    private static final class StatsMenu extends DisplayMenu {

        private final Stats stats;

        StatsMenu(int containerId, Inventory inventory, ServerPlayer player, Stats stats) {
            super(containerId, inventory, player, 3);
            this.stats = stats;
            refresh();
        }

        @Override
        protected void build() {
            frame();
            if (stats == null) {
                return;
            }
            set(10, Gui.button(Items.STONE, "Nombre de blocks minés au total", Gui.lore(
                    Gui.good(String.format("%,d", stats.blocks()).replace(',', '.')))));
            set(12, Gui.button(Items.CLOCK, "Temps passé dans la dimension", Gui.lore(
                    Gui.good(DimMinerManager.clock(stats.minutes() * 60L)))));
            List<Component> ores = new ArrayList<>();
            stats.ores().forEach((block, count) -> ores.add(
                    block.getName().copy().withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(" x" + count).withStyle(ChatFormatting.WHITE))));
            if (ores.isEmpty()) {
                ores.add(Gui.hint("Aucun"));
            }
            set(14, Gui.button(Items.DIAMOND_ORE, "Minerais minés", ores));
            int level = JobApi.level(player, Job.MINER);
            set(16, Gui.button(Items.IRON_PICKAXE, "Récap. Métier mineur", Gui.lore(
                    Gui.field("Niveau", level >= JobApi.maxLevel() ? "niveau max" : "lvl " + level),
                    Gui.good("+" + String.format("%,d", stats.xp()).replace(',', '.') + " xp"))));
            set(22, Gui.close());
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (slot == 22) {
                player.closeContainer();
            }
        }
    }
}
