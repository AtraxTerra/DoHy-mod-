package fr.atraxterra.dohy.palamod.trixium;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.legendary.PalaLegendary;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Le concours Trixium (lot 23a, wiki « Concours Trixium », module
 * {@code trixium} de PalaMod).
 *
 * <ul>
 *   <li><b>Depot</b> : le trixium (1) et le bloc de trixium (9) seulement
 *       ({@code TrixiumSlot.isItemValid}). Ce qui est depose est perdu : il ne
 *       se vend plus.</li>
 *   <li><b>Paliers</b> : une barre personnelle debloque des recompenses, dont
 *       les objets exclusifs. PalaMod lit les paliers dans une configuration
 *       de serveur absente du jar : les seuils et les montants sont de moi.</li>
 *   <li><b>Classement</b> : contre 59 joueurs fictifs ({@link TrixiumRivals}) ;
 *       a la fin de la saison, une prime selon le rang, aux tranches des
 *       recompenses de la V8 (1er, 2e, 3e, 4e, 5-10, 11-20, 21-30, 31-40,
 *       41-60). Les lots reels de Paladium deviennent de l'argent.</li>
 *   <li><b>Factions</b> : un classement pour information, comme sur Paladium.</li>
 * </ul>
 *
 * <p><b>Garde-fou</b> : tout le trixium d'une saison rapporte, paliers et prime
 * comprises, nettement moins que son prix d'achat au shop (3 200 $) ; en
 * acheter pour le deposer fait perdre de l'argent. Il rapporte plus que sa
 * vente (800 $) des le deuxieme palier.
 */
public final class TrixiumContest {

    /** Un palier de la barre : son seuil, et soit de l'argent, soit un objet. */
    public record Milestone(int threshold, long money, Supplier<ItemStack> item, boolean big) {

        public String reward() {
            return item == null ? MoneyFormat.exact(money) + " $" : item.get().getHoverName().getString();
        }

        public ItemStack icon() {
            return item == null ? new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT) : item.get();
        }
    }

    /** Une tranche de la prime de fin de saison. */
    public record Tier(int from, int to, long money) {

        public String ranks() {
            return from == to ? from + (from == 1 ? "er" : "e") : from + "e - " + to + "e";
        }
    }

    public static final List<Milestone> MILESTONES = List.of(
            new Milestone(25, 20_000L, null, false),
            new Milestone(50, 40_000L, null, false),
            new Milestone(100, 0L, () -> new ItemStack(PalaTrixium.CLOUD.get()), true),
            new Milestone(200, 120_000L, null, false),
            new Milestone(300, 180_000L, null, false),
            new Milestone(500, 0L, () -> new ItemStack(PalaTrixium.POCKET_ENDERCHEST.get()), true),
            new Milestone(750, 400_000L, null, false),
            new Milestone(1_000, 600_000L, null, false),
            new Milestone(1_500, 0L, () -> new ItemStack(PalaLegendary.RANDOM.get()), true),
            new Milestone(2_000, 1_200_000L, null, false));

    public static final List<Tier> TIERS = List.of(
            new Tier(1, 1, 2_000_000L),
            new Tier(2, 2, 1_500_000L),
            new Tier(3, 3, 1_000_000L),
            new Tier(4, 4, 750_000L),
            new Tier(5, 10, 400_000L),
            new Tier(11, 20, 200_000L),
            new Tier(21, 30, 100_000L),
            new Tier(31, 40, 60_000L),
            new Tier(41, 60, 30_000L));

    /** Une ligne du classement. */
    public record Entry(String name, String faction, long total, boolean player) {
    }

    private TrixiumContest() {
    }

    public static TrixiumData data(ServerPlayer player) {
        return player.getData(PalaTrixium.DATA.get());
    }

    private static void set(ServerPlayer player, TrixiumData data) {
        player.setData(PalaTrixium.DATA.get(), data);
    }

    public static long prize(int rank) {
        for (Tier tier : TIERS) {
            if (rank >= tier.from() && rank <= tier.to()) {
                return tier.money();
            }
        }
        return 0L;
    }

    // -- Saison --------------------------------------------------------------

    /**
     * Ferme la saison du joueur si elle est finie : la prime de son rang final,
     * les paliers atteints mais pas recuperes (envoyes d'office, comme le
     * Palapass), puis une barre a zero. Sans effet si la saison est en cours.
     */
    public static void catchUp(ServerPlayer player) {
        int now = TrixiumSeason.current();
        TrixiumData data = data(player);
        if (data.season() == 0) {
            set(player, new TrixiumData(now, 0L, 0L, data.lifetime(), data.lastSeason(), data.lastRank(),
                    data.lastDeposit()));
            return;
        }
        // Une saison plus recente que l'horloge : l'horloge d'essai a ete remise a l'heure.
        if (data.season() >= now) {
            return;
        }
        int closed = data.season();
        long deposit = data.deposited();
        int rank = deposit > 0 ? rankAt(closed, deposit, TrixiumSeason.end(closed).toInstant().toEpochMilli() - 1L) : 0;
        int sent = 0;
        for (int i = 0; i < MILESTONES.size(); i++) {
            if (deposit >= MILESTONES.get(i).threshold() && !data.hasClaimed(i)) {
                give(player, MILESTONES.get(i));
                sent++;
            }
        }
        long prize = prize(rank);
        if (prize > 0) {
            Economy.credit(player, prize);
        }
        set(player, data.next(now, closed, rank, deposit));
        if (deposit > 0) {
            tell(player, "Concours Trixium de " + TrixiumSeason.label(closed) + " termine : " + rank
                    + (rank == 1 ? "er" : "e") + " sur " + (TrixiumRivals.COUNT + 1) + " avec "
                    + MoneyFormat.exact(deposit) + " trixium.", ChatFormatting.GOLD);
            if (prize > 0) {
                tell(player, "Prime de classement : +" + MoneyFormat.exact(prize) + " $", ChatFormatting.GREEN);
            }
            if (sent > 0) {
                tell(player, sent + " palier(s) non recupere(s) t'ont ete envoye(s).", ChatFormatting.GREEN);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.6f, 1.0f);
        }
        tell(player, "Nouvelle saison du concours Trixium : " + TrixiumSeason.label(now) + ".", ChatFormatting.AQUA);
    }

    // -- Depot et paliers -------------------------------------------------------

    /** Le trixium que le joueur porte, en unites (un bloc en vaut 9). */
    public static long carried(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        long total = 0L;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            total += value(inventory.getItem(slot));
        }
        return total;
    }

    private static long value(ItemStack stack) {
        if (stack.is(PalaItems.TRIXIUM.get())) {
            return stack.getCount();
        }
        if (stack.is(PalaBlocks.TRIXIUM_BLOCK.get().asItem())) {
            return stack.getCount() * 9L;
        }
        return 0L;
    }

    /** Depose tout le trixium de l'inventaire ; rend ce qui a ete depose. */
    public static long deposit(ServerPlayer player) {
        catchUp(player);
        Inventory inventory = player.getInventory();
        long total = 0L;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            long value = value(stack);
            if (value > 0) {
                total += value;
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (total <= 0) {
            return 0L;
        }
        inventory.setChanged();
        TrixiumData before = data(player);
        TrixiumData after = before.plus(total);
        set(player, after);
        for (int i = 0; i < MILESTONES.size(); i++) {
            int threshold = MILESTONES.get(i).threshold();
            if (before.deposited() < threshold && after.deposited() >= threshold) {
                tell(player, "Palier " + threshold + " atteint : " + MILESTONES.get(i).reward()
                        + " a recuperer dans /trixium.", ChatFormatting.GOLD);
            }
        }
        return total;
    }

    /** Recupere un palier atteint ; rend le refus, ou {@code null}. */
    public static String claim(ServerPlayer player, int index) {
        catchUp(player);
        if (index < 0 || index >= MILESTONES.size()) {
            return "Ce palier n'existe pas.";
        }
        TrixiumData data = data(player);
        Milestone milestone = MILESTONES.get(index);
        if (data.hasClaimed(index)) {
            return "Ce palier est deja recupere.";
        }
        if (data.deposited() < milestone.threshold()) {
            return "Il faut deposer " + MoneyFormat.exact(milestone.threshold() - data.deposited())
                    + " trixium de plus.";
        }
        give(player, milestone);
        set(player, data.withClaimed(index));
        return null;
    }

    /** Les paliers atteints et pas encore recuperes. */
    public static int claimable(ServerPlayer player) {
        TrixiumData data = data(player);
        int count = 0;
        for (int i = 0; i < MILESTONES.size(); i++) {
            if (data.deposited() >= MILESTONES.get(i).threshold() && !data.hasClaimed(i)) {
                count++;
            }
        }
        return count;
    }

    private static void give(ServerPlayer player, Milestone milestone) {
        if (milestone.item() == null) {
            Economy.credit(player, milestone.money());
            return;
        }
        ItemStack stack = milestone.item().get();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // -- Classements ---------------------------------------------------------

    /** Le rang d'un depot a un instant : 1 + les joueurs fictifs strictement devant. */
    public static int rankAt(int season, long deposit, long millis) {
        int ahead = 0;
        for (TrixiumRivals.Rival rival : TrixiumRivals.of(season).rivals()) {
            if (rival.at(millis) > deposit) {
                ahead++;
            }
        }
        return ahead + 1;
    }

    public static int rank(ServerPlayer player) {
        TrixiumData data = data(player);
        return rankAt(data.season(), data.deposited(), TrixiumSeason.nowMillis());
    }

    /** Le classement des joueurs, du premier au dernier ; a egalite, le joueur passe devant. */
    public static List<Entry> players(ServerPlayer player) {
        TrixiumData data = data(player);
        TrixiumRivals.Season season = TrixiumRivals.of(data.season());
        long now = TrixiumSeason.nowMillis();
        List<Entry> entries = new ArrayList<>();
        for (TrixiumRivals.Rival rival : season.rivals()) {
            entries.add(new Entry(rival.name(), rival.faction() < 0 ? "" : season.factions().get(rival.faction()).name(),
                    rival.at(now), false));
        }
        FactionData faction = FactionApi.data(player);
        entries.add(new Entry(player.getName().getString(), faction.exists() ? faction.name() : "",
                data.deposited(), true));
        entries.sort(Comparator.comparingLong(Entry::total).reversed()
                .thenComparing(entry -> !entry.player()));
        return entries;
    }

    /** Le classement des factions, pour information. */
    public static List<Entry> factions(ServerPlayer player) {
        TrixiumData data = data(player);
        TrixiumRivals.Season season = TrixiumRivals.of(data.season());
        long now = TrixiumSeason.nowMillis();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (EventRun.Rival rival : season.factions()) {
            totals.put(rival.name(), 0L);
        }
        for (TrixiumRivals.Rival rival : season.rivals()) {
            if (rival.faction() >= 0) {
                totals.merge(season.factions().get(rival.faction()).name(), rival.at(now), Long::sum);
            }
        }
        List<Entry> entries = new ArrayList<>();
        totals.forEach((name, total) -> entries.add(new Entry(name, "", total, false)));
        FactionData faction = FactionApi.data(player);
        if (faction.exists()) {
            entries.add(new Entry(faction.name(), "", data.deposited(), true));
        }
        entries.sort(Comparator.comparingLong(Entry::total).reversed()
                .thenComparing(entry -> !entry.player()));
        return entries;
    }

    private static void tell(ServerPlayer player, String message, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[Trixium] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(message).withStyle(color)));
    }
}
