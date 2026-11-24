package fr.atraxterra.dohy.faction.garrison;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La garnison d'un joueur : sa caserne, son niveau, l'equipement, l'ordre du moment, ses soldats
 * et la solde. Gardee sur le joueur (elle le suit a la mort) : les soldats en jeu n'en sont que
 * la projection ({@link GarrisonSoldier}), refaite a chaque deploiement.
 */
public final class Garrison {

    /** Les ordres : garder la base, suivre le joueur, rester au repos, ou en conge (pas deployes). */
    public enum Order {
        GARDE("garde", "Garder la base"),
        SUIVRE("suivre", "Me suivre"),
        REPOS("repos", "Au repos"),
        CONGE("conge", "En conge");

        private final String id;
        private final String label;

        Order(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static Order byId(String id) {
            for (Order order : values()) {
                if (order.id.equals(id)) {
                    return order;
                }
            }
            return GARDE;
        }
    }

    /** Un soldat de la garnison : sa vie est gardee entre deux deploiements (-1 : pleine). */
    public static final class Member {
        static final Codec<Member> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(member -> member.id),
                Codec.STRING.fieldOf("name").forGetter(member -> member.name),
                Codec.STRING.fieldOf("role").forGetter(member -> member.role.id()),
                Codec.FLOAT.optionalFieldOf("health", -1.0f).forGetter(member -> member.health),
                Codec.INT.optionalFieldOf("kills", 0).forGetter(member -> member.kills)
        ).apply(instance, (id, name, role, health, kills) -> new Member(id, name, GarrisonRole.byId(role), health,
                kills)));

        final UUID id;
        final String name;
        final GarrisonRole role;
        float health;
        int kills;

        Member(UUID id, String name, GarrisonRole role, float health, int kills) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.health = health;
            this.kills = kills;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public GarrisonRole role() {
            return role;
        }

        public float health() {
            return health;
        }

        public int kills() {
            return kills;
        }

        /** « Fantassin Marcel ». */
        public String title() {
            return role.label() + " " + name;
        }
    }

    public static final Codec<Garrison> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.optionalFieldOf("barracks").forGetter(garrison -> garrison.barracks),
            Codec.INT.optionalFieldOf("level", 1).forGetter(garrison -> garrison.level),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(garrison -> garrison.tier.ordinal()),
            Codec.STRING.optionalFieldOf("order", Order.GARDE.id()).forGetter(garrison -> garrison.order.id()),
            Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(garrison -> garrison.members),
            Codec.LONG.optionalFieldOf("pay_day", Long.MIN_VALUE).forGetter(garrison -> garrison.payDay),
            Codec.INT.optionalFieldOf("unpaid", 0).forGetter(garrison -> garrison.unpaid)
    ).apply(instance, (barracks, level, tier, order, members, payDay, unpaid) -> new Garrison(barracks, level,
            GarrisonTier.of(tier), Order.byId(order), members, payDay, unpaid)));

    /** La capacite de la caserne a chaque niveau. */
    static final int[] CAPACITY = {4, 8, 12};
    /** Le prix du niveau suivant (2, puis 3). */
    static final long[] LEVEL_COST = {0L, 100_000L, 300_000L};

    Optional<GlobalPos> barracks;
    int level;
    GarrisonTier tier;
    Order order;
    final List<Member> members;
    long payDay;
    int unpaid;

    Garrison(Optional<GlobalPos> barracks, int level, GarrisonTier tier, Order order, List<Member> members, long payDay,
             int unpaid) {
        this.barracks = barracks;
        this.level = Math.max(1, Math.min(CAPACITY.length, level));
        this.tier = tier;
        this.order = order;
        this.members = new ArrayList<>(members);
        this.payDay = payDay;
        this.unpaid = unpaid;
    }

    public static Garrison empty() {
        return new Garrison(Optional.empty(), 1, GarrisonTier.FER, Order.GARDE, List.of(), Long.MIN_VALUE, 0);
    }

    public boolean isEmpty() {
        return barracks.isEmpty() && members.isEmpty() && level == 1 && tier == GarrisonTier.FER;
    }

    public Optional<GlobalPos> barracks() {
        return barracks;
    }

    public int level() {
        return level;
    }

    public int capacity() {
        return CAPACITY[level - 1];
    }

    public GarrisonTier tier() {
        return tier;
    }

    public Order order() {
        return order;
    }

    public List<Member> members() {
        return members;
    }

    public int unpaid() {
        return unpaid;
    }

    /** La solde d'un jour, pour toute la garnison. */
    public long dailyPay() {
        long total = 0L;
        for (Member member : members) {
            total += member.role.pay();
        }
        return total;
    }

    public int count(GarrisonRole role) {
        int count = 0;
        for (Member member : members) {
            if (member.role == role) {
                count++;
            }
        }
        return count;
    }

    public Member member(UUID id) {
        for (Member member : members) {
            if (member.id.equals(id)) {
                return member;
            }
        }
        return null;
    }
}
