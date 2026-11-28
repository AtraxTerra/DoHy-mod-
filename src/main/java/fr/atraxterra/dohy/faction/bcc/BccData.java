package fr.atraxterra.dohy.faction.bcc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * Le Base Claim Controller d'un joueur : ou il est, depuis quand, ce qu'il a
 * rapporte, et la derniere pose (quatorze jours entre deux). Garde a la mort.
 */
public final class BccData {

    public static final MapCodec<BccData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(data -> data.dimension),
            Codec.LONG.optionalFieldOf("pos", 0L).forGetter(data -> data.pos),
            Codec.LONG.optionalFieldOf("placed_at", 0L).forGetter(data -> data.placedAt),
            Codec.LONG.optionalFieldOf("last_placed", 0L).forGetter(data -> data.lastPlaced),
            Codec.LONG.optionalFieldOf("paid_days", 0L).forGetter(data -> data.paidDays),
            Codec.LONG.optionalFieldOf("glory", 0L).forGetter(data -> data.glory),
            Codec.LONG.optionalFieldOf("elo", 0L).forGetter(data -> data.elo),
            Codec.LONG.optionalFieldOf("last_raid", 0L).forGetter(data -> data.lastRaid),
            Codec.INT.optionalFieldOf("bought", 0).forGetter(data -> data.bought)
    ).apply(instance, BccData::new));

    String dimension;
    long pos;
    long placedAt;
    long lastPlaced;
    long paidDays;
    long glory;
    long elo;
    long lastRaid;
    /** BCC achetes et pas encore poses (dans l'inventaire). */
    int bought;

    public BccData() {
        this("", 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0);
    }

    private BccData(String dimension, long pos, long placedAt, long lastPlaced, long paidDays, long glory, long elo,
                    long lastRaid, int bought) {
        this.dimension = dimension;
        this.pos = pos;
        this.placedAt = placedAt;
        this.lastPlaced = lastPlaced;
        this.paidDays = paidDays;
        this.glory = glory;
        this.elo = elo;
        this.lastRaid = lastRaid;
        this.bought = bought;
    }

    /** Un BCC pose. */
    public boolean placed() {
        return !dimension.isEmpty();
    }

    public String dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return BlockPos.of(pos);
    }

    public long placedAt() {
        return placedAt;
    }

    public long lastPlaced() {
        return lastPlaced;
    }

    public long glory() {
        return glory;
    }

    public long elo() {
        return elo;
    }

    public long paidDays() {
        return paidDays;
    }

    boolean isEmpty() {
        return dimension.isEmpty() && lastPlaced == 0L && glory == 0L && bought == 0;
    }

    void clearPlaced() {
        dimension = "";
        pos = 0L;
        placedAt = 0L;
        paidDays = 0L;
    }
}
