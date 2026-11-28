package fr.atraxterra.dohy.palamod.mount;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;

import java.util.Locale;

/**
 * Les trois especes du mod Mount ({@code MountType}, {@code DancarokMount},
 * {@code RavirokMount}, {@code TedarokMount}).
 *
 * <p>Relevees dans le jar : PV et cases au niveau 100 (la monture en a
 * {@code valeur x niveau / 100}), vitesse et detente, des multiplicateurs de
 * celles de la monture. Les competences :
 * <ul>
 *   <li>Dancarok : {@code MiningSkill}, la corne ;</li>
 *   <li>Ravirok : {@code FlySkill}, il vole et ne marche pas ;</li>
 *   <li>Tedarok : {@code AutoJumpSkill(5)} (une marche de cinq blocs),
 *       {@code RunningSkill(1,9)}, {@code WaterWalkSkill(1,15)} et
 *       {@code WaterRunSkill(1,9)} au niveau 50.</li>
 * </ul>
 */
public enum MountSpecies {

    DANCAROK("dancarok", "Dancarok", "Monture lourde", 400, 36, 1.0d, 1.0d),
    RAVIROK("ravirok", "Ravirok", "Monture volante", 100, 9, 1.3d, 0.0d),
    TEDAROK("tedarok", "Tedarok", "Monture legere", 200, 18, 1.15d, 3.5d);

    public static final Codec<MountSpecies> CODEC = Codec.STRING.xmap(MountSpecies::byId, MountSpecies::id);

    /** {@code RunningSkill(1,9)} et {@code WaterRunSkill(1,9)} du Tedarok. */
    public static final double TEDAROK_RUN = 1.9d;
    /** {@code WaterRunSkill} : son niveau requis. */
    public static final int WATER_RUN_LEVEL = 50;
    /** {@code AutoJumpSkill(5)} : la hauteur de marche du Tedarok. */
    public static final double TEDAROK_STEP = 5.0d;

    private final String id;
    private final String label;
    private final String description;
    private final int healthAtMax;
    private final int slotsAtMax;
    private final double speed;
    private final double jump;

    MountSpecies(String id, String label, String description, int healthAtMax, int slotsAtMax, double speed,
                 double jump) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.healthAtMax = healthAtMax;
        this.slotsAtMax = slotsAtMax;
        this.speed = speed;
        this.jump = jump;
    }

    public String id() {
        return id;
    }

    /** Le nom de l'espece, nom par defaut d'une monture. */
    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** {@code Mount.hp} : PV au niveau 100. */
    public int healthAtMax() {
        return healthAtMax;
    }

    /** {@code Mount.nbSlots} : cases au niveau 100. */
    public int slotsAtMax() {
        return slotsAtMax;
    }

    /** {@code Mount.speed} : multiplie la vitesse de la monture. */
    public double speed() {
        return speed;
    }

    /** {@code Mount.jumpStrenght} : multiplie le saut. */
    public double jump() {
        return jump;
    }

    /** Seul le Dancarok porte une corne. */
    public boolean hasHorn() {
        return this == DANCAROK;
    }

    public EntityType<? extends Dancarok> entityType() {
        return switch (this) {
            case DANCAROK -> PalaMount.DANCAROK.get();
            case RAVIROK -> PalaMount.RAVIROK.get();
            case TEDAROK -> PalaMount.TEDAROK.get();
        };
    }

    /** Une espece inconnue (donnee abimee) redevient un Dancarok, la seule d'avant. */
    public static MountSpecies byId(String id) {
        for (MountSpecies species : values()) {
            if (species.id.equals(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
                return species;
            }
        }
        return DANCAROK;
    }
}
