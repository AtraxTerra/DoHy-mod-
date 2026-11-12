package fr.atraxterra.dohy.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * La recompense quotidienne du spawn : une somme a venir chercher a la borne
 * RECOMPENSE, plus grosse chaque jour d'une serie. Le jour change a la meme
 * heure reelle que les contrats ; un jour saute remet la serie a zero.
 *
 * <p>Elle ne se recupere qu'au spawn : c'est la raison d'y repasser.
 */
public final class SpawnDaily extends MenuController {

    public static final SpawnDaily INSTANCE = new SpawnDaily();

    /**
     * Ou en est un joueur.
     *
     * @param day    le dernier jour (cle de {@link ContractApi#today()}) ou il a pris sa recompense
     * @param streak le nombre de jours de suite pris, ce jour-la compris
     */
    public record State(long day, int streak) {
        public static final State NONE = new State(Long.MIN_VALUE, 0);
        public static final MapCodec<State> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.fieldOf("day").forGetter(State::day),
                Codec.INT.fieldOf("streak").forGetter(State::streak)
        ).apply(instance, State::new));

        public boolean isNone() {
            return streak <= 0;
        }
    }

    private SpawnDaily() {
        super("spawn_daily");
    }

    private static State state(ServerPlayer player) {
        return player.getData(DoHySpawn.DAILY.get());
    }

    /** Vrai si la recompense du jour a deja ete prise. */
    public static boolean claimedToday(ServerPlayer player) {
        State state = state(player);
        return !state.isNone() && state.day() == ContractApi.today();
    }

    /** Les jours deja pris dans la serie en cours ; zero si elle est rompue ou jamais commencee. */
    public static int streak(ServerPlayer player) {
        State state = state(player);
        long today = ContractApi.today();
        return !state.isNone() && (state.day() == today || state.day() == today - 1) ? state.streak() : 0;
    }

    /** Le rang, dans la serie, de la prochaine recompense a prendre (a partir de zero). */
    private static int nextIndex(ServerPlayer player) {
        int size = SpawnConfig.config().daily().amounts().size();
        int taken = streak(player);
        return claimedToday(player) ? (taken - 1) % size : taken % size;
    }

    /** Ce que rapporte le jour de rang donne, au niveau global du joueur. */
    public static long amount(ServerPlayer player, int index) {
        SpawnConfig.Daily daily = SpawnConfig.config().daily();
        long base = daily.amounts().get(Math.floorMod(index, daily.amounts().size()));
        return Math.round(base * (1.0d + ContractApi.globalLevel(player) * daily.levelBonus()));
    }

    /** Prend la recompense du jour ; faux si elle l'est deja ou si le joueur n'est pas au spawn. */
    public static boolean claim(ServerPlayer player) {
        if (!SpawnApi.inSpawn(player) || claimedToday(player)) {
            return false;
        }
        int index = nextIndex(player);
        long amount = amount(player, index);
        player.setData(DoHySpawn.DAILY.get(), new State(ContractApi.today(), streak(player) + 1));
        Economy.credit(player, amount);
        player.sendSystemMessage(Component.literal("Recompense du jour : +" + MoneyFormat.exact(amount)
                + " (jour " + (index + 1) + " de la serie).").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static String delay(long minutes) {
        return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
    }

    @Override
    public MenuView view(ServerPlayer player) {
        List<Long> amounts = SpawnConfig.config().daily().amounts();
        boolean claimed = claimedToday(player);
        int next = nextIndex(player);
        long minutes = ContractApi.minutesUntilDailyReset();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SERIE", streak(player) + " jour(s)", 0xFFFAC400),
                new MenuView.Stat(claimed ? "PROCHAINE DANS" : "A PRENDRE", claimed ? delay(minutes)
                        : MoneyFormat.shortForm(amount(player, next)), claimed ? 0xFFFFFFFF : 0xFF22E558));
        List<Card> days = new ArrayList<>();
        for (int index = 0; index < amounts.size(); index++) {
            boolean last = index == amounts.size() - 1;
            Card card = Card.of(new ItemStack(last ? Items.GOLD_BLOCK : Items.GOLD_INGOT), "Jour " + (index + 1),
                    List.of(MoneyFormat.exact(amount(player, index)), last ? "Le gros lot de la serie" : "En liquide"))
                    .badge(String.valueOf(index + 1));
            if (index < next || (claimed && index == next)) {
                card = card.state(MenuView.DONE);
            } else if (index == next) {
                card = card.accent(0xFFFAC400).action("claim|prendre la recompense");
            } else {
                card = card.state(MenuView.DISABLED).tooltip(List.of("Reviens demain, puis les jours suivants."));
            }
            days.add(card);
        }
        List<Card> rules = List.of(
                Card.of(new ItemStack(Items.CLOCK), "Le jour", List.of("Change dans " + delay(minutes),
                        "A la meme heure que les contrats")).accent(0xFF2F5BEA),
                Card.of(new ItemStack(Items.BEACON), "La serie", List.of("Un jour saute : retour au jour 1",
                        "Apres le dernier jour, elle reprend")).accent(0xFF2F5BEA)
                        .tooltip(List.of("La recompense ne se prend qu'ici, au spawn.",
                                "Elle grandit avec ton niveau global.")));
        return new MenuView(id(), "Recompense du jour", "Reviens chaque jour au spawn", stats, List.of(
                new MenuView.Section("LA SERIE", "upgrade", MenuView.GRID, days),
                new MenuView.Section("REGLES", "resources", MenuView.GRID, rules)), false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!"claim".equals(key)) {
            return;
        }
        if (claim(player)) {
            success(player);
        } else {
            failure(player);
            player.sendSystemMessage(Component.literal(SpawnApi.inSpawn(player)
                    ? "Deja prise aujourd'hui. Reviens dans " + delay(ContractApi.minutesUntilDailyReset()) + "."
                    : "La recompense se prend au spawn.").withStyle(ChatFormatting.RED));
        }
    }
}
