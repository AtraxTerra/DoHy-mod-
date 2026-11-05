package fr.atraxterra.dohy.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L'etat du rythme de jeu : contrats, demande du jour, niveau global.
 *
 * <p>Un seul attachment pour tout ce qui se compte en <b>minutes de jeu</b>. Le
 * compteur {@code playtimeMinutes} est la reference commune : rotation des
 * contrats, expiration de la demande, remise a zero du bonus de premiere vente.
 * Tout est en temps de jeu et non en temps reel, pour la raison exposee dans
 * {@code MarketDecay} — sinon le week-end decide de tout et les soirees de
 * semaine ne rattrapent rien.
 */
public final class ContractData {

    public static final MapCodec<ContractData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ActiveContract.CODEC.listOf().optionalFieldOf("daily", List.of())
                    .forGetter(data -> List.copyOf(data.daily)),
            ActiveContract.CODEC.listOf().optionalFieldOf("weekly", List.of())
                    .forGetter(data -> List.copyOf(data.weekly)),
            Codec.LONG.optionalFieldOf("playtime_minutes", 0L).forGetter(data -> data.playtimeMinutes),
            Codec.LONG.optionalFieldOf("daily_rolled_at", -1L).forGetter(data -> data.dailyRolledAt),
            Codec.LONG.optionalFieldOf("weekly_rolled_at", -1L).forGetter(data -> data.weeklyRolledAt),
            Identifier.CODEC.optionalFieldOf("demand_item").forGetter(data -> Optional.ofNullable(data.demandItem)),
            Codec.LONG.optionalFieldOf("demand_until", -1L).forGetter(data -> data.demandUntil),
            Codec.LONG.optionalFieldOf("first_sale_at", -1L).forGetter(data -> data.firstSaleAt),
            Codec.LONG.optionalFieldOf("global_xp", 0L).forGetter(data -> data.globalXp)
    ).apply(instance, ContractData::new));

    private final List<ActiveContract> daily = new ArrayList<>();
    private final List<ActiveContract> weekly = new ArrayList<>();

    private long playtimeMinutes;
    private long dailyRolledAt = -1L;
    private long weeklyRolledAt = -1L;
    private Identifier demandItem;
    private long demandUntil = -1L;
    private long firstSaleAt = -1L;
    private long globalXp;

    public ContractData() {
    }

    private ContractData(List<ActiveContract> daily, List<ActiveContract> weekly,
                         long playtimeMinutes, long dailyRolledAt, long weeklyRolledAt,
                         Optional<Identifier> demandItem, long demandUntil,
                         long firstSaleAt, long globalXp) {
        this.daily.addAll(daily);
        this.weekly.addAll(weekly);
        this.playtimeMinutes = playtimeMinutes;
        this.dailyRolledAt = dailyRolledAt;
        this.weeklyRolledAt = weeklyRolledAt;
        this.demandItem = demandItem.orElse(null);
        this.demandUntil = demandUntil;
        this.firstSaleAt = firstSaleAt;
        this.globalXp = globalXp;
    }

    public List<ActiveContract> daily() {
        return daily;
    }

    public List<ActiveContract> weekly() {
        return weekly;
    }

    public void setDaily(List<ActiveContract> contracts) {
        daily.clear();
        daily.addAll(contracts);
    }

    public void setWeekly(List<ActiveContract> contracts) {
        weekly.clear();
        weekly.addAll(contracts);
    }

    public void replace(boolean isWeekly, int index, ActiveContract contract) {
        List<ActiveContract> target = isWeekly ? weekly : daily;
        if (index >= 0 && index < target.size()) {
            target.set(index, contract);
        }
    }

    public long playtimeMinutes() {
        return playtimeMinutes;
    }

    public void tickPlaytime() {
        playtimeMinutes++;
    }

    public long dailyRolledAt() {
        return dailyRolledAt;
    }

    public long weeklyRolledAt() {
        return weeklyRolledAt;
    }

    public void markDailyRolled() {
        dailyRolledAt = playtimeMinutes;
    }

    public void markWeeklyRolled() {
        weeklyRolledAt = playtimeMinutes;
    }

    public Identifier demandItem() {
        return demandUntil > playtimeMinutes ? demandItem : null;
    }

    public long demandUntil() {
        return demandUntil;
    }

    public void setDemand(Identifier item, long untilMinutes) {
        demandItem = item;
        demandUntil = untilMinutes;
    }

    /** Vrai si le bonus de premiere vente est encore disponible. */
    public boolean firstSaleAvailable(long resetMinutes) {
        return firstSaleAt < 0 || playtimeMinutes - firstSaleAt >= resetMinutes;
    }

    public void consumeFirstSale() {
        firstSaleAt = playtimeMinutes;
    }

    public long globalXp() {
        return globalXp;
    }

    public void addGlobalXp(long amount) {
        if (amount > 0) {
            globalXp += amount;
        }
    }

    public boolean isEmpty() {
        return daily.isEmpty() && weekly.isEmpty() && playtimeMinutes == 0
                && globalXp == 0 && demandItem == null;
    }
}
