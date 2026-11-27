package fr.atraxterra.dohy.palamod.pet;

import java.util.Locale;

/**
 * Les competences des familiers, celles du wiki de Paladium (« Java | Competences
 * Passives », « Java | Competences Actives ») : niveau de deblocage, paliers et
 * valeurs, recharge des actives.
 *
 * <p>Une valeur vaut au palier atteint (le plus haut palier inferieur ou egal au
 * niveau du familier), puis se multiplie par le bonheur : 0 a 200 de bonheur
 * donnent 0 a 2 fois la valeur (100 de bonheur : la valeur du tableau).
 */
public enum PetSkill {

    // -- Passives, niveau 5 ----------------------------------------------------------------
    AVIATOR(Kind.PASSIVE, "Aviateur", "Temps de vol en deltaplane", Unit.PERCENT, 5, 0,
            12.5, 18.8, 25, 31.3, 37.5, 50),
    GOOD_PICKAXE(Kind.PASSIVE, "Bonne pioche", "Vitesse de minage", Unit.PERCENT, 5, 0,
            10, 15, 20, 25, 30, 40),
    BUDDING_CHEMIST(Kind.PASSIVE, "Chimiste en herbe", "Duree des potions bues (hors potions d'XP de metier)",
            Unit.PERCENT, 5, 0, 7.5, 11.3, 15, 18.8, 22.5, 30),
    STOMACH(Kind.PASSIVE, "Estomac sur pattes", "Nourriture gagnee en mangeant", Unit.PERCENT, 5, 0,
            25, 37.5, 50, 62.5, 75, 100),
    AERIAL_EXPERT(Kind.PASSIVE, "Expert aerien", "Vitesse en deltaplane", Unit.PERCENT, 5, 0,
            12.5, 18.8, 25, 31.3, 37.5, 50),
    FARMING_EXPERT(Kind.PASSIVE, "Expert agronome", "Chance d'une graine en plus en cassant une culture",
            Unit.PERCENT, 5, 0, 5, 7.5, 10, 12.5, 15, 20),
    MIRACULOUS_EXTRACTOR(Kind.PASSIVE, "Extracteur miraculeux", "Usure en moins des outils de recolte de seve",
            Unit.PERCENT, 5, 0, 7.5, 11.3, 15, 18.8, 22.5, 30),
    FAST_WALKING(Kind.PASSIVE, "Marche rapide", "Vitesse de deplacement", Unit.PERCENT, 5, 0,
            5, 7.5, 10, 12.5, 15, 20),
    SKILLED_FISHERMAN(Kind.PASSIVE, "Pecheur avise", "Attente en moins a la peche", Unit.PERCENT, 5, 0,
            7.5, 11.3, 15, 18.8, 22.5, 30),
    EXPERIENCED_PICKAXE(Kind.PASSIVE, "Pioche experimentee", "XP gagnee par la pioche des dieux", Unit.PERCENT, 5, 0,
            7.5, 11.3, 15, 18.8, 22.5, 30),
    LIGHTWEIGHT(Kind.PASSIVE, "Poids plume", "Degats de chute en moins", Unit.PERCENT, 5, 0,
            25, 37.5, 50, 62.5, 75, 100),
    WEAK_SPOT(Kind.PASSIVE, "Point vital", "Degats aux monstres", Unit.PERCENT, 5, 0,
            5, 7.5, 10, 12.5, 15, 20),

    // -- Passives, niveaux 10 a 70 ------------------------------------------------------------
    EXPERIENCE_ABSORBER(Kind.PASSIVE, "Absorbeur d'experience", "Chance d'XP en plus en ramassant de l'XP",
            Unit.PERCENT, 10, 0, 5, 7.5, 10, 12.5, 15, 20),
    MAGNET(Kind.PASSIVE, "Aimante", "Rayon d'attraction des objets au sol", Unit.BLOCKS, 20, 0,
            3, 4, 5, 6, 8),
    CROQUETTE_MAKER(Kind.PASSIVE, "Artisan de la croquette", "Croquettes en plus a la machine a croquettes",
            Unit.PERCENT, 20, 0, 15, 20, 25, 30, 40),
    SHARP_CRUSHER(Kind.PASSIVE, "Broyeur acere", "Chance d'un lingot en plus au crusher", Unit.PERCENT, 20, 0,
            11.3, 15, 18.8, 22.5, 30),
    FLORAL_CHANCE(Kind.PASSIVE, "Chance florale", "Chance d'une fleur de plus au chaudron", Unit.PERCENT, 20, 0,
            7.5, 10, 12.5, 15, 20),
    GEOLOGY(Kind.PASSIVE, "Geologie", "Particules en plus au cobblebreaker", Unit.PERCENT, 20, 0,
            18.8, 25, 31.3, 37.5, 50),
    SELF_SUFFICIENT(Kind.PASSIVE, "Familier autosuffisant", "Chance qu'une mission ratee compte quand meme",
            Unit.PERCENT, 30, 0, 0.8, 1, 1.3, 1.5, 2),
    MAGIC_PORTAL(Kind.PASSIVE, "Portail magique", "Seve versee en plus dans le portail de l'alchimiste",
            Unit.PERCENT, 30, 0, 11.3, 15, 18.8, 22.5, 30),
    DECREASED_COOLDOWN(Kind.PASSIVE, "Recuperation boostee", "Recharge en moins des competences actives",
            Unit.PERCENT, 30, 0, 7.5, 10, 12.5, 15, 20),
    PORTABLE_COFFIN(Kind.PASSIVE, "Cercueil portable", "A la mort, un coffre garde les objets", Unit.NONE, 40, 0,
            1),
    ENHANCED_GROWTH(Kind.PASSIVE, "Pousse amelioree", "Duree du paladium dans le totem", Unit.PERCENT, 40, 0,
            10, 12.5, 15, 20),
    POWERFUL_KNOCKBACK(Kind.PASSIVE, "Recul puissant", "Puissance du knocker infernal", Unit.PERCENT, 40, 0,
            25, 31.3, 37.5, 50),
    LEGENDARY_EXPERT(Kind.PASSIVE, "Expert legendaire", "Recharge en moins des pierres legendaires", Unit.HOURS, 70, 0,
            1.3, 1.5, 2),

    // -- Actives -------------------------------------------------------------------------------
    TWIRLING_SCYTHE(Kind.ACTIVE, "Faux tournoyante", "Recolte et replante les cultures autour de toi", Unit.BLOCKS,
            10, 12 * 60, 4, 6, 8, 10, 12, 16),
    GRAVITATIONAL_AXE(Kind.ACTIVE, "Hache gravitationnelle", "Abat les buches de PalaMod autour de toi", Unit.BLOCKS,
            10, 2 * 60, 4, 6, 8, 10, 12, 16),
    LUCKY_MINER(Kind.ACTIVE, "Mineur chanceux", "Chance que le prochain minerai mine soit un meilleur minerai",
            Unit.PERCENT, 10, 60, 20, 30, 40, 50, 60, 80),
    ELECTRIC_CLOUD(Kind.ACTIVE, "Nuage electrique", "Des eclairs frappent les monstres proches", Unit.STRIKES,
            10, 10 * 60, 2.5, 3.8, 5, 6.3, 7.5, 10),
    MONSTER_SLAYER(Kind.ACTIVE, "Tueur de monstres", "Neuf coeurs de degats aux monstres autour de toi", Unit.BLOCKS,
            10, 60, 5, 7.5, 10, 12.5, 15, 20),
    POCKET_HAPPINESS(Kind.ACTIVE, "Bonheur de poche", "Rend du bonheur a ton familier", Unit.POINTS,
            30, 24 * 3600, 15, 20, 25, 30, 40),
    ENCHANTED(Kind.ACTIVE, "Enchante", "Niveaux en moins sur la prochaine enclume ou gravure", Unit.LEVELS,
            30, 2 * 3600, 7.5, 10, 12.5, 15, 20),
    UNBREAKABLE_PICKAXE(Kind.ACTIVE, "Pioche incassable", "Mine l'obsidienne plus vite pendant 60 s", Unit.TIMES,
            30, 10 * 60, 3, 4, 5, 6, 8),
    EARTH_BLESSING(Kind.ACTIVE, "Benediction de la terre", "Fait pousser d'un coup les plantes autour de toi",
            Unit.BLOCKS, 50, 15 * 60, 5, 6.3, 7.5, 10),
    QUICK_CHANGE(Kind.ACTIVE, "Changement rapide", "Raccourcit l'attente avant de changer une competence",
            Unit.PERCENT, 50, 24 * 3600, 5, 6.3, 7.5, 10),
    EXPLOSION(Kind.ACTIVE, "Explosion !", "Casse la roche autour de toi, minerais exceptes", Unit.BLOCKS,
            50, 2 * 60, 8, 10, 12, 16),
    BLESSED_EXPLOSION(Kind.ACTIVE, "Explosion benie", "Chance qu'un spawner vide casse en donne un second",
            Unit.PERCENT, 50, 30 * 60, 50, 62.5, 75, 100),
    REPAIR(Kind.ACTIVE, "Reparo !", "Chance que le prochain objet casse se repare", Unit.PERCENT,
            50, 4 * 3600, 40, 50, 60, 80),
    SKILLFUL_VETERINARIAN(Kind.ACTIVE, "Veterinaire adroit", "Le prochain bandage soigne ta monture d'un coup",
            Unit.NONE, 50, 4 * 3600, 1),
    BEDROCK_DRILL(Kind.ACTIVE, "Foreuse a bedrock", "Retire la bedrock sous toi, vers le sud-est", Unit.BLOCKS,
            70, 2 * 3600, 2.5, 3, 4);

    public enum Kind {
        PASSIVE, ACTIVE
    }

    /** L'unite d'une valeur, pour l'afficher. */
    public enum Unit {
        PERCENT("%"), BLOCKS(" blocs"), STRIKES(" eclairs"), POINTS(" de bonheur"), LEVELS(" niveaux"),
        TIMES(""), HOURS(" h"), NONE("");

        final String suffix;

        Unit(String suffix) {
            this.suffix = suffix;
        }
    }

    private final Kind kind;
    private final String label;
    private final String description;
    private final Unit unit;
    private final int unlock;
    /** Recharge en secondes, pour une active. */
    private final int cooldown;
    private final int[] levels;
    private final double[] values;

    PetSkill(Kind kind, String label, String description, Unit unit, int unlock, int cooldown, double... values) {
        this.kind = kind;
        this.label = label;
        this.description = description;
        this.unit = unit;
        this.unlock = unlock;
        this.cooldown = cooldown;
        this.values = values;
        // Les paliers : le niveau de deblocage, puis ceux du wiki au-dessus (20, 40, 60, 80, 100).
        int[] tiers = {20, 40, 60, 80, 100};
        int[] levels = new int[values.length];
        levels[0] = unlock;
        int next = 0;
        for (int index = 1; index < values.length; index++) {
            while (next < tiers.length && tiers[next] <= levels[index - 1]) {
                next++;
            }
            levels[index] = next < tiers.length ? tiers[next] : 100;
            next++;
        }
        this.levels = levels;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Kind kind() {
        return kind;
    }

    public boolean active() {
        return kind == Kind.ACTIVE;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public int unlock() {
        return unlock;
    }

    /** La recharge d'une active, en secondes ; 0 pour une passive. */
    public int cooldownSeconds() {
        return cooldown;
    }

    /** La valeur du palier atteint, avant le bonheur ; 0 sous le niveau de deblocage. */
    public double base(int level) {
        double value = 0.0d;
        for (int index = 0; index < levels.length; index++) {
            if (level >= levels[index]) {
                value = values[index];
            }
        }
        return value;
    }

    /** Les paliers et leurs valeurs, pour l'infobulle : « N5 12,5% · N20 18,8% ... ». */
    public String tiers() {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < levels.length; index++) {
            if (index > 0) {
                out.append(" · ");
            }
            out.append("N").append(levels[index]).append(' ').append(format(values[index]));
        }
        return out.toString();
    }

    /** Une valeur avec son unite. */
    public String format(double value) {
        if (unit == Unit.NONE) {
            return "oui";
        }
        String number = value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(Locale.FRANCE, "%.1f", value);
        return (unit == Unit.TIMES ? "x" : "") + number + unit.suffix;
    }

    public static PetSkill byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (PetSkill skill : values()) {
            if (skill.id().equals(id)) {
                return skill;
            }
        }
        return null;
    }
}
