package fr.atraxterra.dohy.palamod.legendary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Le contenu du gant ({@code upgradearray}, {@code selected} et les
 * {@code LEGENDARYSTONE_<EFFET>_LAST_USE} de l'original).
 *
 * <p>Une pierre de chaque type au plus. {@link #sorted()} les rend dans l'ordre
 * de l'enumeration, celui du defilement ; {@code selected} est un indice dans
 * cette liste. Chaque pierre garde sa propre derniere utilisation, en
 * millisecondes de l'horloge reelle.
 */
public record GauntletData(List<LegendaryEffect> stones, int selected, Map<LegendaryEffect, Long> lastUses) {

    public static final GauntletData EMPTY = new GauntletData(List.of(), 0, Map.of());

    public static final Codec<GauntletData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegendaryEffect.CODEC.listOf().optionalFieldOf("stones", List.of()).forGetter(GauntletData::stones),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(GauntletData::selected),
            Codec.unboundedMap(LegendaryEffect.CODEC, Codec.LONG).optionalFieldOf("last_uses", Map.of())
                    .forGetter(GauntletData::lastUses)
    ).apply(instance, GauntletData::new));

    public static final StreamCodec<ByteBuf, GauntletData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public GauntletData {
        stones = List.copyOf(stones);
        lastUses = Map.copyOf(lastUses);
    }

    /** Les pierres dans l'ordre de l'enumeration. */
    public List<LegendaryEffect> sorted() {
        List<LegendaryEffect> sorted = new ArrayList<>();
        for (LegendaryEffect effect : LegendaryEffect.values()) {
            if (effect != LegendaryEffect.RANDOM && stones.contains(effect)) {
                sorted.add(effect);
            }
        }
        return sorted;
    }

    /** Les six pierres : le gant devient l'Infinity Gauntlet, et ses recharges sont divisees par deux. */
    public boolean full() {
        return sorted().size() == 6;
    }

    /** La pierre selectionnee, ou {@code null} pour un gant vide. */
    public LegendaryEffect current() {
        List<LegendaryEffect> sorted = sorted();
        return sorted.isEmpty() ? null : sorted.get(Math.floorMod(selected, sorted.size()));
    }

    public long lastUse(LegendaryEffect effect) {
        return lastUses.getOrDefault(effect, 0L);
    }

    public boolean has(LegendaryEffect effect) {
        return stones.contains(effect);
    }

    /** {@code applyUpgrade} puis {@code transferCooldown} : la recharge la plus recente l'emporte. */
    public GauntletData withStone(LegendaryEffect effect, long stoneLastUse) {
        List<LegendaryEffect> next = new ArrayList<>(stones);
        if (!next.contains(effect)) {
            next.add(effect);
        }
        Map<LegendaryEffect, Long> uses = new EnumMap<>(LegendaryEffect.class);
        uses.putAll(lastUses);
        long kept = Math.max(stoneLastUse, lastUse(effect));
        if (kept > 0L) {
            uses.put(effect, kept);
        }
        return new GauntletData(next, selected, uses);
    }

    public GauntletData withSelected(int index) {
        return new GauntletData(stones, index, lastUses);
    }

    public GauntletData withLastUse(LegendaryEffect effect, long time) {
        Map<LegendaryEffect, Long> uses = new EnumMap<>(LegendaryEffect.class);
        uses.putAll(lastUses);
        uses.put(effect, time);
        return new GauntletData(stones, selected, uses);
    }
}
