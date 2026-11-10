package fr.atraxterra.dohy.faction;

import java.util.HashSet;
import java.util.Set;

/**
 * Ce que la faction a debloque, somme des recompenses des paliers acquis de
 * l'arbre de technologie ({@link FactionTree}).
 *
 * @param claims       chunks revendicables
 * @param homes        F homes ; aucun avant le palier 5 de la branche Globale, comme sur Paladium
 * @param homeDelay    secondes immobile avant la teleportation au home
 * @param raidDamage   part des degats de raid encaissee par les blocs du claim (1 = tout)
 * @param dungeonXp    bonus d'XP de donjon (0,05 = +5 %)
 * @param unlocks      objets debloques : « use:dynamite », « craft:hood_helmet »...
 * @param customBanner blason personnalise (palier 4 de la branche Globale)
 * @param motd         message a la connexion (palier 6 de la branche Globale)
 * @param reserveTier  palier de reserve d'or constructible (branche Economie)
 * @param goldValue    investissement permis par bloc d'or liquide
 * @param interest     multiplicateur des interets de la reserve
 */
public record FactionPerks(int claims, int homes, int homeDelay, float raidDamage, float dungeonXp,
                           Set<String> unlocks, boolean customBanner, boolean motd, int reserveTier, long goldValue,
                           int interest) {

    public static final int BASE_CLAIMS = 5;
    public static final int BASE_HOME_DELAY = 10;
    /** {@code Java | Investissement} : 400 000 $ par bloc d'or liquide, jusqu'a 800 000 avec l'arbre. */
    public static final long BASE_GOLD_VALUE = 400_000L;

    public boolean unlocked(String key) {
        return unlocks.contains(key);
    }

    public static FactionPerks of(FactionData data) {
        Builder builder = new Builder();
        for (FactionBranch branch : FactionBranch.values()) {
            int tier = data.tier(branch.key());
            for (FactionTree.Tier step : FactionTree.tiers(branch)) {
                if (step.number() <= tier) {
                    for (FactionTree.Reward reward : step.rewards()) {
                        reward.apply().accept(builder);
                    }
                }
            }
        }
        return builder.build();
    }

    /** Les recompenses s'ajoutent (claims) ou remplacent la valeur si elles sont meilleures (homes, delai, bonus). */
    public static final class Builder {
        int claims = BASE_CLAIMS;
        int homes;
        int homeDelay = BASE_HOME_DELAY;
        float raidReduction;
        float dungeonXp;
        final Set<String> unlocks = new HashSet<>();
        boolean customBanner;
        boolean motd;
        int reserveTier;
        long goldValue = BASE_GOLD_VALUE;
        int interest = 1;

        public Builder claims(int amount) {
            claims += amount;
            return this;
        }

        public Builder homes(int count) {
            homes = Math.max(homes, count);
            return this;
        }

        public Builder homeDelay(int seconds) {
            homeDelay = Math.min(homeDelay, seconds);
            return this;
        }

        public Builder raidReduction(float part) {
            raidReduction = Math.max(raidReduction, part);
            return this;
        }

        public Builder dungeonXp(float bonus) {
            dungeonXp = Math.max(dungeonXp, bonus);
            return this;
        }

        public Builder unlock(String key) {
            unlocks.add(key);
            return this;
        }

        public Builder customBanner() {
            customBanner = true;
            return this;
        }

        public Builder motd() {
            motd = true;
            return this;
        }

        public Builder reserveTier(int tier) {
            reserveTier = Math.max(reserveTier, tier);
            return this;
        }

        public Builder goldValue(long extra) {
            goldValue += extra;
            return this;
        }

        public Builder interest(int multiplier) {
            interest = Math.max(interest, multiplier);
            return this;
        }

        FactionPerks build() {
            return new FactionPerks(claims, homes, homeDelay, 1.0f - raidReduction, dungeonXp, Set.copyOf(unlocks),
                    customBanner, motd, reserveTier, goldValue, interest);
        }
    }
}
