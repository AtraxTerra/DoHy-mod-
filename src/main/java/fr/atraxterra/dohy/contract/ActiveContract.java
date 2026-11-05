package fr.atraxterra.dohy.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Un contrat en cours.
 *
 * <p>Le type et la cible sont recopies depuis le datapack au moment du tirage,
 * et non relus a chaque progression : si le fichier change en cours de route,
 * un contrat deja accepte garde les termes sous lesquels il a ete propose.
 */
public record ActiveContract(String id, String type, Optional<String> target,
                             long amount, long progress, boolean weekly,
                             Optional<String> label, Optional<String> icon) {

    public static final Codec<ActiveContract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ActiveContract::id),
            Codec.STRING.fieldOf("type").forGetter(ActiveContract::type),
            Codec.STRING.optionalFieldOf("target").forGetter(ActiveContract::target),
            Codec.LONG.fieldOf("amount").forGetter(ActiveContract::amount),
            Codec.LONG.optionalFieldOf("progress", 0L).forGetter(ActiveContract::progress),
            Codec.BOOL.optionalFieldOf("weekly", false).forGetter(ActiveContract::weekly),
            Codec.STRING.optionalFieldOf("label").forGetter(ActiveContract::label),
            Codec.STRING.optionalFieldOf("icon").forGetter(ActiveContract::icon)
    ).apply(instance, ActiveContract::new));

    public static ActiveContract of(ContractConfig.Entry entry, boolean weekly) {
        return new ActiveContract(entry.id(), entry.type(), entry.target(),
                entry.amount(), 0L, weekly, entry.label(), entry.icon());
    }

    public ContractType kind() {
        return ContractType.byName(type);
    }

    public boolean done() {
        return progress >= amount;
    }

    public ActiveContract advance(long delta) {
        if (delta <= 0 || done()) {
            return this;
        }
        return new ActiveContract(id, type, target, amount,
                Math.min(amount, progress + delta), weekly, label, icon);
    }

    public double ratio() {
        return amount <= 0 ? 1.0d : Math.min(1.0d, (double) progress / amount);
    }
}
