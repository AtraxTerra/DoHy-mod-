package fr.atraxterra.dohy.pass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractType;

import java.util.Optional;

/**
 * Une quete du pass en cours. Ses termes sont recopies du fichier au tirage,
 * comme ceux d'un contrat : un fichier change en cours de mois ne modifie pas
 * une quete deja proposee.
 */
public record PassQuest(String id, String type, Optional<String> target, long amount, long progress, long xp,
                        Optional<String> label, Optional<String> icon) {

    public static final Codec<PassQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(PassQuest::id),
            Codec.STRING.fieldOf("type").forGetter(PassQuest::type),
            Codec.STRING.optionalFieldOf("target").forGetter(PassQuest::target),
            Codec.LONG.fieldOf("amount").forGetter(PassQuest::amount),
            Codec.LONG.optionalFieldOf("progress", 0L).forGetter(PassQuest::progress),
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(PassQuest::xp),
            Codec.STRING.optionalFieldOf("label").forGetter(PassQuest::label),
            Codec.STRING.optionalFieldOf("icon").forGetter(PassQuest::icon)
    ).apply(instance, PassQuest::new));

    public static PassQuest of(PassConfig.Quest quest) {
        return new PassQuest(quest.id(), quest.type(), quest.target(), Math.max(1L, quest.amount()), 0L, quest.xp(),
                quest.label(), quest.icon());
    }

    public ContractType kind() {
        return ContractType.byName(type);
    }

    public boolean done() {
        return progress >= amount;
    }

    public PassQuest advance(long delta) {
        if (delta <= 0 || done()) {
            return this;
        }
        return new PassQuest(id, type, target, amount, Math.min(amount, progress + delta), xp, label, icon);
    }

    /** Fini d'un coup (administration). */
    public PassQuest finished() {
        return new PassQuest(id, type, target, amount, amount, xp, label, icon);
    }

    /** La meme quete vue comme un contrat : pour la nommer et l'illustrer comme les contrats. */
    public ActiveContract asContract() {
        return new ActiveContract(id, type, target, amount, progress, false, label, icon);
    }

    public double ratio() {
        return amount <= 0 ? 1.0d : Math.min(1.0d, (double) progress / amount);
    }
}
