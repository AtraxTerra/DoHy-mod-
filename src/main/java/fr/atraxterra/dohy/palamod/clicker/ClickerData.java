package fr.atraxterra.dohy.palamod.clicker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le clicker d'un joueur ({@code PlayerClickerData}) : ses ClicCoins, ses
 * batiments, ses ameliorations, et l'etat de ses rayons du Clic Shop.
 *
 * <p>Les echeances sont en secondes reelles, comme chez Paladium : une offre
 * du jour dure un jour, que l'on joue ou non, et la « saison » — l'age du
 * clicker, qui ouvre les ameliorations — avance de meme. La production aussi,
 * dans DoHy : {@code paid} dit jusqu'a quelle seconde elle est versee, et ce
 * qui manque se rattrape a la connexion ({@link Clicker}).
 */
public final class ClickerData {

    /** Un rayon du Clic Shop : ses offres, l'heure du tirage, les achats faits. */
    public static final class Shelf {

        static final Codec<Shelf> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(shelf -> List.copyOf(shelf.items)),
                Codec.LONG.optionalFieldOf("updated", 0L).forGetter(shelf -> shelf.updated),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bought", Map.of())
                        .forGetter(shelf -> Map.copyOf(shelf.bought))
        ).apply(instance, Shelf::new));

        final List<String> items = new ArrayList<>();
        long updated;
        final Map<String, Integer> bought = new HashMap<>();

        Shelf() {
        }

        private Shelf(List<String> items, long updated, Map<String, Integer> bought) {
            this.items.addAll(items);
            this.updated = updated;
            this.bought.putAll(bought);
        }
    }

    public static final MapCodec<ClickerData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("points", 0.0d).forGetter(data -> data.points),
            Codec.DOUBLE.optionalFieldOf("total", 0.0d).forGetter(data -> data.total),
            Codec.LONG.optionalFieldOf("started", 0L).forGetter(data -> data.started),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("buildings", Map.of())
                    .forGetter(data -> Map.copyOf(data.buildings)),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("production", Map.of())
                    .forGetter(data -> Map.copyOf(data.production)),
            Codec.STRING.listOf().optionalFieldOf("upgrades", List.of()).forGetter(data -> List.copyOf(data.upgrades)),
            Codec.unboundedMap(Codec.STRING, Shelf.CODEC).optionalFieldOf("shop", Map.of())
                    .forGetter(data -> Map.copyOf(data.shop)),
            Codec.LONG.optionalFieldOf("paid", 0L).forGetter(data -> data.paid)
    ).apply(instance, ClickerData::new));

    double points;
    /** Tout ce qui a ete recolte, depenses comprises : la condition « avoir recolte ». */
    double total;
    /** La premiere ouverture, en secondes depuis l'epoque ; 0 tant que le clicker n'a pas servi. */
    long started;
    final Map<String, Integer> buildings = new HashMap<>();
    final Map<String, Double> production = new HashMap<>();
    final Set<String> upgrades = new LinkedHashSet<>();
    final Map<String, Shelf> shop = new HashMap<>();
    /**
     * La production est versee jusqu'a cette seconde (depuis l'epoque) ; 0 pour
     * un clicker d'avant la production hors ligne : il repart de sa connexion.
     */
    long paid;

    // Rien de ce qui suit n'est sauvegarde.
    /** La production par seconde, recalculee apres un achat et de temps en temps. */
    transient double rps = -1.0d;
    transient final Map<String, Double> rpsByBuilding = new HashMap<>();
    transient long rpsAt;
    transient long lastClick;
    /** Les clics recus pendant cette session : le client s'en sert pour recaler son affichage. */
    transient int clicks;

    public ClickerData() {
    }

    private ClickerData(double points, double total, long started, Map<String, Integer> buildings,
                        Map<String, Double> production, List<String> upgrades, Map<String, Shelf> shop, long paid) {
        this.paid = paid;
        this.points = points;
        this.total = total;
        this.started = started;
        this.buildings.putAll(buildings);
        this.production.putAll(production);
        this.upgrades.addAll(upgrades);
        this.shop.putAll(shop);
    }

    public boolean isEmpty() {
        return started == 0L;
    }

    public double points() {
        return points;
    }

    public double rps() {
        return Math.max(0.0d, rps);
    }

    public int count(String building) {
        return buildings.getOrDefault(building, 0);
    }

    public boolean has(String upgrade) {
        return upgrades.contains(upgrade);
    }

    /** {@code addPoints} : un gain compte aussi dans le total recolte, une depense non. */
    void add(double amount) {
        points += amount;
        if (amount > 0.0d) {
            total += amount;
        }
    }

    /** Les jours ecoules depuis la premiere ouverture : la « saison » des conditions. */
    long days(long nowSeconds) {
        return started <= 0L ? 0L : Math.max(0L, (nowSeconds - started) / 86_400L);
    }
}
