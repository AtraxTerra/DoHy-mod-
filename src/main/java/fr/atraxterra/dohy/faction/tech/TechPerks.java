package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Les bonus des technologies acquises (lot 23b). Dans un domaine, un palier plus haut remplace
 * le bonus du meme type (Energie IV, « -15 % », remplace Energie II, « -10 % ») ; d'un domaine a
 * l'autre, les bonus du meme type s'ajoutent (la recherche +10 % de l'Industrie et +15 % du
 * Nucleaire font +25 %).
 *
 * <p>Ils agissent sur les systemes de DoHy, jamais sur le code de NTM. Tous sont en service depuis
 * le lot 23g (l'Energie et l'Industrie au 23e, la Chimie et l'Agriculture au 23f, le Nucleaire et la
 * Defense au 23g) ; un type que le moteur ne connait pas dit qu'il est « a venir »
 * ({@link #pending(String)}).
 */
public record TechPerks(Map<String, Integer> values) {

    public static final TechPerks NONE = new TechPerks(Map.of());

    public int value(String type) {
        return values.getOrDefault(type, 0);
    }

    /** Les recherches menees a la fois dans les domaines nouveaux : une, puis deux et trois avec le Gouvernement. */
    public int parallel() {
        return Math.max(1, value("parallel"));
    }

    /** Les laboratoires que la nation peut poser (lot 23c). */
    public int labs() {
        return Math.max(1, value("labs"));
    }

    /** La vitesse de recherche, en pour cent. */
    public int research() {
        return value("research");
    }

    /** Les points de taxe du marche international que les technologies effacent. */
    public int marketTax() {
        return value("market_tax");
    }

    /** L'argent des mines de la Terre, en pour cent. */
    public int minesMoney() {
        return value("mines_money");
    }

    /** Les minerais des mines de la Terre, en pour cent. */
    public int minesOre() {
        return value("mines_ore");
    }

    /** L'argent des champs de petrole et de gaz de la Terre, en pour cent. */
    public int oilFields() {
        return value("oil_fields");
    }

    /** L'energie que les machines de DoHy ne consomment plus, en pour cent (Energie III et V), 90 au plus. */
    public int machineEnergy() {
        return Math.max(0, Math.min(90, value("machine_energy")));
    }

    /** L'argent du metier de Mineur (niveaux et primes), en pour cent (Industrie II et VI). */
    public int minerMoney() {
        return Math.max(0, value("miner_money"));
    }

    /** Les cases ajoutees au coffre de la banque (Industrie IV). */
    public int vaultSlots() {
        return Math.max(0, value("vault_slots"));
    }

    /** L'XP du metier de Fermier, en pour cent (Agriculture II). */
    public int farmerXp() {
        return Math.max(0, value("farmer_xp"));
    }

    /** L'argent du metier de Fermier (niveaux et primes), en pour cent (Agriculture IV). */
    public int farmerMoney() {
        return Math.max(0, value("farmer_money"));
    }

    /** La vitesse des machines agricoles de DoHy, en pour cent (Agriculture III et V), 200 au plus. */
    public int agriSpeed() {
        return Math.max(0, Math.min(200, value("agri_speed")));
    }

    /** Les piles ajoutees au largage nucleaire qu'un membre de la nation ouvre le premier (Nucleaire II). */
    public int nuclearLoot() {
        return Math.max(0, Math.min(9, value("nuclear_loot")));
    }

    /** La vie que perdent les soldats des assauts des nations contre cette nation, en pour cent (Defense III et V). */
    public int soldierHp() {
        return Math.max(0, Math.min(50, value("soldier_hp")));
    }

    public static TechPerks of(ServerPlayer player) {
        return of(FactionApi.data(player));
    }

    public static TechPerks of(FactionData data) {
        Map<String, Integer> total = new HashMap<>();
        for (TechConfig.Domain domain : TechConfig.domains()) {
            int level = Tech.level(data, domain);
            Map<String, Integer> local = new HashMap<>();
            for (TechConfig.Tier tier : domain.tiers()) {
                if (tier.tier() <= level) {
                    for (TechConfig.Bonus bonus : tier.bonuses()) {
                        local.merge(bonus.type(), bonus.value(), Math::max);
                    }
                }
            }
            local.forEach((type, value) -> total.merge(type, value, Integer::sum));
        }
        return total.isEmpty() ? NONE : new TechPerks(Map.copyOf(total));
    }

    /** Le lot qui mettra ce bonus en service ; vide s'il agit deja. */
    public static String pending(String type) {
        return switch (type) {
            case "parallel", "research", "market_tax", "mines_money", "mines_ore", "oil_fields", "labs",
                 "research_per_country", "lab_energy", "machine_energy", "miner_money", "vault_slots",
                 "farmer_xp", "agri_speed", "farmer_money", "nuclear_loot", "soldier_hp" -> "";
            default -> "a venir";
        };
    }

    /** Le libelle d'un bonus, avec le lot qui l'attend s'il ne sert pas encore. */
    public static String label(TechConfig.Bonus bonus) {
        String lot = pending(bonus.type());
        return lot.isEmpty() ? bonus.label() : bonus.label() + " (" + lot + ")";
    }
}
