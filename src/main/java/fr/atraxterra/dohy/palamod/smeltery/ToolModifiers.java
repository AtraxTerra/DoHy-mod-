package fr.atraxterra.dohy.palamod.smeltery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Les modificateurs poses sur un outil ({@code upgradearray},
 * {@code modifiersammount}, {@code modifiersmax} du NBT de PalaMod).
 *
 * <p>Un niveau, une entree. L'outil a {@code slots} emplacements, plus deux par
 * modificateur d'emplacement — qui en occupe lui-meme un : net, un de plus. Le
 * modificateur d'emplacement passe meme quand tout est plein.
 *
 * <p><b>Ecart</b> : un outil deja renomme ou enchante recevait zero
 * emplacement dans PalaMod ({@code createDefaultNBT} seulement sans NBT) ; ici,
 * il a ses emplacements comme les autres.
 */
public record ToolModifiers(int slots, List<GrinderModifier> applied) {

    public static final Codec<ToolModifiers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 64).fieldOf("slots").forGetter(ToolModifiers::slots),
            GrinderModifier.CODEC.listOf().fieldOf("applied").forGetter(ToolModifiers::applied)
    ).apply(instance, ToolModifiers::new));

    public static final StreamCodec<ByteBuf, ToolModifiers> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToolModifiers::slots,
            ByteBufCodecs.VAR_INT.map(index -> GrinderModifier.values()[index], GrinderModifier::ordinal)
                    .apply(ByteBufCodecs.list()), ToolModifiers::applied,
            ToolModifiers::new);

    public ToolModifiers {
        applied = List.copyOf(applied);
    }

    public static ToolModifiers empty(int slots) {
        return new ToolModifiers(slots, List.of());
    }

    public int level(GrinderModifier modifier) {
        return (int) applied.stream().filter(modifier::equals).count();
    }

    /** {@code getMaxEnchants} : les emplacements, plus deux par modificateur d'emplacement. */
    public int totalSlots() {
        return slots + 2 * level(GrinderModifier.MORE);
    }

    /** {@code UpgradeHelper.canApplyUpgrade}. */
    public boolean canApply(GrinderModifier modifier, GrinderModifier.ToolKind kind) {
        if (modifier != GrinderModifier.MORE && applied.size() >= totalSlots()) {
            return false;
        }
        if (level(modifier) >= modifier.maxLevel()) {
            return false;
        }
        return modifier.kind() == null || modifier.kind() == kind;
    }

    public ToolModifiers with(GrinderModifier modifier) {
        List<GrinderModifier> next = new ArrayList<>(applied);
        next.add(modifier);
        return new ToolModifiers(slots, next);
    }
}
