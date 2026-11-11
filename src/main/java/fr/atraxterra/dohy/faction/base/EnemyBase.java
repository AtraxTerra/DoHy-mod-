package fr.atraxterra.dohy.faction.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * La base ennemie d'un joueur (lot 22g), gardee sur lui : une a la fois.
 *
 * @param name     le nom de la faction ennemie
 * @param tier     sa taille ({@link EnemyBaseTier#level()})
 * @param state    {@link #NONE}, {@link #ACTIVE} ou {@link #LOOTED}
 * @param center   le centre de la base, au niveau du sol (dans le Monde)
 * @param coreMin  le coin bas de la salle des coffres
 * @param coreMax  le coin haut de la salle des coffres
 * @param created  sa date de generation, en millisecondes
 * @param style    son genre ({@link EnemyBaseStyle#key()}) ; une base d'avant
 *                 la refonte est une citadelle
 */
public record EnemyBase(String name, int tier, int state, BlockPos center, BlockPos coreMin, BlockPos coreMax,
                        long created, String style) {

    public static final int NONE = 0;
    public static final int ACTIVE = 1;
    public static final int LOOTED = 2;

    public static final EnemyBase EMPTY = new EnemyBase("", 1, NONE, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, 0L,
            EnemyBaseStyle.CITADEL.key());

    public static final MapCodec<EnemyBase> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(EnemyBase::name),
            Codec.INT.optionalFieldOf("tier", 1).forGetter(EnemyBase::tier),
            Codec.INT.optionalFieldOf("state", NONE).forGetter(EnemyBase::state),
            BlockPos.CODEC.optionalFieldOf("center", BlockPos.ZERO).forGetter(EnemyBase::center),
            BlockPos.CODEC.optionalFieldOf("core_min", BlockPos.ZERO).forGetter(EnemyBase::coreMin),
            BlockPos.CODEC.optionalFieldOf("core_max", BlockPos.ZERO).forGetter(EnemyBase::coreMax),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(EnemyBase::created),
            Codec.STRING.optionalFieldOf("style", EnemyBaseStyle.CITADEL.key()).forGetter(EnemyBase::style)
    ).apply(instance, EnemyBase::new));

    public boolean exists() {
        return state != NONE;
    }

    public boolean active() {
        return state == ACTIVE;
    }

    public EnemyBaseTier kind() {
        return EnemyBaseTier.byLevel(tier);
    }

    public EnemyBaseStyle genre() {
        EnemyBaseStyle found = EnemyBaseStyle.byKey(style);
        return found == null ? EnemyBaseStyle.CITADEL : found;
    }

    public EnemyBase looted() {
        return new EnemyBase(name, tier, LOOTED, center, coreMin, coreMax, created, style);
    }

    /** La salle des coffres, en boite : y entrer, c'est avoir pille la base. */
    public AABB core() {
        return new AABB(coreMin.getX(), coreMin.getY(), coreMin.getZ(),
                coreMax.getX() + 1.0d, coreMax.getY() + 1.0d, coreMax.getZ() + 1.0d);
    }
}
