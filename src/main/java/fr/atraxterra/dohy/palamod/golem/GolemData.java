package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le golem range dans une pierre de gardien ou un mini golem
 * ({@code ItemGuardianStone.getCompound}) : proprietaire, niveau, XP, points,
 * ameliorations, choix, liste blanche, usure du mini golem.
 *
 * <p>Le stockage du golem n'y est pas, comme dans l'original : il tombe a sa
 * mort. <b>Ecart</b> : les choix et la liste blanche y sont, quand
 * GuardianGolem les perdait en passant par la pierre.
 */
public record GolemData(Optional<UUID> owner, String ownerName, int level, int subXp, int points,
                        List<GolemUpgrade> upgrades, Map<GolemChoice, Integer> choices,
                        List<String> whitelist, int feedCooldown, int durability,
                        Optional<Death> death) {

    /** {@code setMaxDamage(144)} : 144 productions dans une boite a golem. */
    public static final int MINI_GOLEM_DURABILITY = 144;

    public static final GolemData EMPTY = new GolemData(Optional.empty(), "", 0, 0, 0, List.of(), Map.of(),
            List.of(), 0, MINI_GOLEM_DURABILITY, Optional.empty());

    /** {@code death_infos} : ce qui a tue le golem, quand et ou. */
    public record Death(String cause, String source, long date, BlockPos pos) {
        public static final Codec<Death> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("cause").forGetter(Death::cause),
                Codec.STRING.fieldOf("source").forGetter(Death::source),
                Codec.LONG.fieldOf("date").forGetter(Death::date),
                BlockPos.CODEC.fieldOf("pos").forGetter(Death::pos)
        ).apply(instance, Death::new));
    }

    public static final Codec<GolemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(GolemData::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(GolemData::ownerName),
            Codec.intRange(0, GuardianGolem.MAX_LEVEL).optionalFieldOf("level", 0).forGetter(GolemData::level),
            Codec.INT.optionalFieldOf("sub_xp", 0).forGetter(GolemData::subXp),
            Codec.INT.optionalFieldOf("points", 0).forGetter(GolemData::points),
            GolemUpgrade.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(GolemData::upgrades),
            Codec.unboundedMap(GolemChoice.CODEC, Codec.INT).optionalFieldOf("choices", Map.of())
                    .forGetter(GolemData::choices),
            Codec.STRING.listOf().optionalFieldOf("whitelist", List.of()).forGetter(GolemData::whitelist),
            Codec.INT.optionalFieldOf("feed_cooldown", 0).forGetter(GolemData::feedCooldown),
            Codec.INT.optionalFieldOf("durability", MINI_GOLEM_DURABILITY).forGetter(GolemData::durability),
            Death.CODEC.optionalFieldOf("death").forGetter(GolemData::death)
    ).apply(instance, GolemData::new));

    public static final StreamCodec<ByteBuf, GolemData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public GolemData {
        upgrades = List.copyOf(upgrades);
        choices = Map.copyOf(choices);
        whitelist = List.copyOf(whitelist);
    }

    public GolemData withDurability(int value) {
        return new GolemData(owner, ownerName, level, subXp, points, upgrades, choices, whitelist, feedCooldown,
                value, death);
    }

    public GolemData withDeath(Optional<Death> value) {
        return new GolemData(owner, ownerName, level, subXp, points, upgrades, choices, whitelist, feedCooldown,
                durability, value);
    }

    public GolemData withWhitelist(List<String> value) {
        return new GolemData(owner, ownerName, level, subXp, points, upgrades, choices, value, feedCooldown,
                durability, death);
    }

    public boolean has(GolemUpgrade upgrade) {
        return upgrades.contains(upgrade);
    }

    /** {@code getUpgradeLevel(30..27)} : le meilleur maker, ou -1 (0 fer ... 3 paladium). */
    public int bestMaker() {
        for (int index = 0; index < GolemUpgrade.MAKERS.length; index++) {
            if (has(GolemUpgrade.MAKERS[index])) {
                return GolemUpgrade.MAKERS.length - 1 - index;
            }
        }
        return -1;
    }
}
