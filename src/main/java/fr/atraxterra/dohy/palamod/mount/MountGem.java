package fr.atraxterra.dohy.palamod.mount;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * Ce que porte une gemme de monture : le NBT de {@code ItemMountGemstone}
 * ({@code Owner}, {@code Name}, {@code MountUUID}, {@code Xp}, {@code SharedXp},
 * {@code Food}, {@code Damage}, {@code healStart}, {@code healEnd},
 * {@code expireTime}).
 *
 * <p>Sans proprietaire, la gemme est <b>sauvage</b> : elle sort d'une capture
 * et s'apprivoise dans le gestionnaire d'enclos. Les horodatages sont en
 * millisecondes de temps reel, comme l'original : un soin de 1 h 30 avance
 * aussi jeu ferme. {@code -1} veut dire « pas de soin » et « pas de
 * peremption en cours ».
 */
public record MountGem(UUID mountId, Optional<UUID> owner, String name, float xp, int sharedXp, int food,
                       float damage, long healStart, long healEnd, long expireTime) {

    public static final String DEFAULT_NAME = "Dancarok";
    /** {@code CSWithdrawMount} : 2 592 000 000 ms, trente jours. */
    public static final long EXPIRY_MS = 2_592_000_000L;

    public static final Codec<MountGem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("mount_id").forGetter(MountGem::mountId),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(MountGem::owner),
            Codec.STRING.optionalFieldOf("name", DEFAULT_NAME).forGetter(MountGem::name),
            Codec.FLOAT.optionalFieldOf("xp", 0.0f).forGetter(MountGem::xp),
            Codec.INT.optionalFieldOf("shared_xp", MountData.DEFAULT_SHARED_XP).forGetter(MountGem::sharedXp),
            Codec.INT.optionalFieldOf("food", 0).forGetter(MountGem::food),
            Codec.FLOAT.optionalFieldOf("damage", 0.0f).forGetter(MountGem::damage),
            Codec.LONG.optionalFieldOf("heal_start", -1L).forGetter(MountGem::healStart),
            Codec.LONG.optionalFieldOf("heal_end", -1L).forGetter(MountGem::healEnd),
            Codec.LONG.optionalFieldOf("expire", -1L).forGetter(MountGem::expireTime)
    ).apply(instance, MountGem::new));

    public static final StreamCodec<ByteBuf, MountGem> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** {@code ItemCaptureSword.hitEntity} : XP 0, partage 50 %, sans proprietaire. */
    public static MountGem wild() {
        return new MountGem(UUID.randomUUID(), Optional.empty(), DEFAULT_NAME, 0.0f,
                MountData.DEFAULT_SHARED_XP, 0, 0.0f, -1L, -1L, -1L);
    }

    public boolean isWild() {
        return owner.isEmpty();
    }

    public boolean ownedBy(UUID player) {
        return owner.map(player::equals).orElse(false);
    }

    public int level() {
        return MountLevels.level(xp);
    }

    public float maxHealth() {
        return MountLevels.maxHealth(level());
    }

    public boolean isDead() {
        return damage >= maxHealth();
    }

    public boolean inCare() {
        return healEnd != -1L;
    }

    /** Une gemme perimee le reste : aucun chemin ne remet {@code expireTime} a zero. */
    public boolean isExpired(long now) {
        return expireTime != -1L && expireTime <= now;
    }

    /**
     * {@code tameMountFromGemstone} : nouveau proprietaire, nouvel identifiant,
     * nourriture pleine, PV pleins, partage a 50 %.
     */
    public MountGem tame(UUID player) {
        return new MountGem(UUID.randomUUID(), Optional.of(player), name, xp, MountData.DEFAULT_SHARED_XP,
                MountData.MAX_FOOD, 0.0f, -1L, -1L, -1L);
    }

    public MountGem withCare(long start, long end) {
        return new MountGem(mountId, owner, name, xp, sharedXp, food, damage, start, end, expireTime);
    }

    public MountGem withDamage(float value) {
        return new MountGem(mountId, owner, name, xp, sharedXp, food, value, healStart, healEnd, expireTime);
    }

    public MountGem withExpiry(long value) {
        return new MountGem(mountId, owner, name, xp, sharedXp, food, damage, healStart, healEnd, value);
    }
}
