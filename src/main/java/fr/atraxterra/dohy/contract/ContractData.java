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
 * <h2>Deux horloges, et pourquoi</h2>
 *
 * <p><b>Temps reel</b> pour tout ce qui est un cycle — contrats du jour,
 * contrat de la semaine, demande, bonus de premiere vente. Ce ne sont pas des
 * comptes a rebours mais des <b>cles de periode</b> : on retire des que la cle
 * du jour differe de celle du dernier tirage. Un cycle qui comptait en temps de
 * jeu n'etait pas un cycle : vingt-quatre heures de jeu, a deux heures par
 * soiree, font douze jours.
 *
 * <p><b>Temps de jeu</b> pour ce qui se consomme pendant une session : la duree
 * d'un booster et l'intervalle de l'autosell. Un booster paye trois cents
 * millions ne doit pas fondre parce que la connexion a saute, et l'autosell n'a
 * de sens que joueur present. C'est ce que compte {@code playtimeMinutes}, qui
 * reste par ailleurs un chiffre utile a afficher.
 *
 * <p>{@code lastSeenMinute} est la derniere minute reelle vue en jeu : c'est
 * depuis elle que la banque et le marche rattrapent le temps passe hors ligne.
 */
public final class ContractData {

    public static final MapCodec<ContractData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ActiveContract.CODEC.listOf().optionalFieldOf("daily", List.of())
                    .forGetter(data -> List.copyOf(data.daily)),
            ActiveContract.CODEC.listOf().optionalFieldOf("weekly", List.of())
                    .forGetter(data -> List.copyOf(data.weekly)),
            Codec.LONG.optionalFieldOf("playtime_minutes", 0L).forGetter(data -> data.playtimeMinutes),
            // Cles de periode, et non plus des minutes de jeu : les anciens
            // fichiers n'ont pas ces champs, retombent sur -1, et provoquent un
            // tirage neuf a la premiere connexion. C'est le comportement voulu.
            Codec.LONG.optionalFieldOf("daily_period", -1L).forGetter(data -> data.dailyPeriod),
            Codec.LONG.optionalFieldOf("weekly_period", -1L).forGetter(data -> data.weeklyPeriod),
            Identifier.CODEC.optionalFieldOf("demand_item").forGetter(data -> Optional.ofNullable(data.demandItem)),
            Codec.LONG.optionalFieldOf("demand_period", -1L).forGetter(data -> data.demandPeriod),
            Codec.LONG.optionalFieldOf("first_sale_period", -1L).forGetter(data -> data.firstSalePeriod),
            Codec.LONG.optionalFieldOf("last_seen_minute", -1L).forGetter(data -> data.lastSeenMinute),
            Codec.LONG.optionalFieldOf("global_xp", 0L).forGetter(data -> data.globalXp)
    ).apply(instance, ContractData::new));

    private final List<ActiveContract> daily = new ArrayList<>();
    private final List<ActiveContract> weekly = new ArrayList<>();

    private long playtimeMinutes;
    private long dailyPeriod = -1L;
    private long weeklyPeriod = -1L;
    private Identifier demandItem;
    private long demandPeriod = -1L;
    private long firstSalePeriod = -1L;
    private long lastSeenMinute = -1L;
    private long globalXp;

    public ContractData() {
    }

    private ContractData(List<ActiveContract> daily, List<ActiveContract> weekly,
                         long playtimeMinutes, long dailyPeriod, long weeklyPeriod,
                         Optional<Identifier> demandItem, long demandPeriod,
                         long firstSalePeriod, long lastSeenMinute, long globalXp) {
        this.daily.addAll(daily);
        this.weekly.addAll(weekly);
        this.playtimeMinutes = playtimeMinutes;
        this.dailyPeriod = dailyPeriod;
        this.weeklyPeriod = weeklyPeriod;
        this.demandItem = demandItem.orElse(null);
        this.demandPeriod = demandPeriod;
        this.firstSalePeriod = firstSalePeriod;
        this.lastSeenMinute = lastSeenMinute;
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

    /** Minutes de jeu cumulees. Sert aux boosters et a l'autosell. */
    public long playtimeMinutes() {
        return playtimeMinutes;
    }

    public void tickPlaytime() {
        playtimeMinutes++;
    }

    public long dailyPeriod() {
        return dailyPeriod;
    }

    public void setDailyPeriod(long period) {
        dailyPeriod = period;
    }

    public long weeklyPeriod() {
        return weeklyPeriod;
    }

    public void setWeeklyPeriod(long period) {
        weeklyPeriod = period;
    }

    /**
     * L'item en demande, sans verifier la periode.
     *
     * <p>C'est {@link ContractApi#demandItem} qu'il faut appeler : lui seul sait
     * quelle est la periode courante. Garder la logique d'horloge hors des
     * donnees evite deux sources de verite sur « quel jour sommes-nous ».
     */
    public Identifier rawDemandItem() {
        return demandItem;
    }

    public long demandPeriod() {
        return demandPeriod;
    }

    public void setDemand(Identifier item, long period) {
        demandItem = item;
        demandPeriod = period;
    }

    public long firstSalePeriod() {
        return firstSalePeriod;
    }

    public void setFirstSalePeriod(long period) {
        firstSalePeriod = period;
    }

    /** Derniere minute reelle vue en jeu, ou -1 si le joueur est neuf. */
    public long lastSeenMinute() {
        return lastSeenMinute;
    }

    public void setLastSeenMinute(long minute) {
        lastSeenMinute = minute;
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
                && globalXp == 0 && demandItem == null && lastSeenMinute < 0;
    }
}
