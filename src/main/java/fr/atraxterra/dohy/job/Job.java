package fr.atraxterra.dohy.job;

/**
 * Les quatre metiers de Paladium : la seule progression du mod.
 *
 * <p><b>Historique.</b> DoHy avait neuf skills, et les metiers n'etaient qu'un
 * nom pose sur quatre d'entre eux — Mineur lisait {@code mining}, Chasseur
 * {@code combat}, Fermier {@code farming}, Alchimiste {@code alchemy}. Les
 * metiers ont <b>remplace</b> les skills : les cinq sans equivalent Paladium
 * (bucheronnage, peche, forge, construction, nucleaire) ont ete retires avec
 * leurs sources d'XP et leurs perks, et chaque metier porte desormais sa propre
 * XP, sa courbe, ses primes de palier, ses deblocages et ses recompenses.
 *
 * <p>{@link #legacyKey()} est la cle du skill d'origine. Elle ne sert qu'a
 * relire les sauvegardes d'avant le remplacement ({@link JobData}) : l'XP de
 * {@code mining} devient celle du Mineur sans que le joueur perde rien.
 *
 * <p>Les niveaux de deblocage releves sur le wiki sont ceux du Paladium Java,
 * gradues sur 100 — cobblebreaker au 25, crusher au 40 — et s'appliquent tels
 * quels a la courbe de {@code job_progression.json}.
 */
public enum Job {

    MINER("miner", "Mineur", "mining"),
    HUNTER("hunter", "Chasseur", "combat"),
    FARMER("farmer", "Fermier", "farming"),
    ALCHEMIST("alchemist", "Alchimiste", "alchemy");

    private final String key;
    private final String display;
    private final String legacyKey;

    Job(String key, String display, String legacyKey) {
        this.key = key;
        this.display = display;
        this.legacyKey = legacyKey;
    }

    /** Cle employee par {@code jobs.json} et {@code job_progression.json}. */
    public String key() {
        return key;
    }

    /** Nom affiche au joueur, celui de Paladium. */
    public String display() {
        return display;
    }

    /** Cle du skill que ce metier remplace, pour relire les anciennes sauvegardes. */
    public String legacyKey() {
        return legacyKey;
    }

    public static Job byKey(String candidate) {
        if (candidate == null) {
            return null;
        }
        for (Job job : values()) {
            if (job.key.equalsIgnoreCase(candidate)) {
                return job;
            }
        }
        return null;
    }

    /**
     * Le metier designe par une cle actuelle <b>ou</b> par l'ancienne cle de
     * skill. Les skills retires ({@code foraging}, {@code fishing}...) ne
     * designent rien : leur XP est abandonnee, c'est la decision prise au
     * remplacement.
     */
    public static Job byKeyOrLegacy(String candidate) {
        Job job = byKey(candidate);
        if (job != null || candidate == null) {
            return job;
        }
        for (Job legacy : values()) {
            if (legacy.legacyKey.equalsIgnoreCase(candidate)) {
                return legacy;
            }
        }
        return null;
    }
}
