package fr.atraxterra.dohy.earth.mine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les mines de la Terre capturees par des factions de joueurs, sur le monde de
 * la carte. Une mine absente de la liste est exploitee par la nation de sa region.
 *
 * @param concessions une par mine capturee, dans l'ordre des captures
 */
public record MineLedger(List<Concession> concessions) {

    /**
     * Une mine tenue par une faction.
     *
     * @param mine    l'identifiant du gisement ({@code kiruna})
     * @param owner   le joueur dont la faction la tient
     * @param faction le nom de sa faction, pour les autres joueurs
     * @param since   la capture, en millisecondes epoch
     * @param paid    jusqu'ou sa production a ete versee, en millisecondes epoch
     */
    public record Concession(String mine, UUID owner, String faction, long since, long paid) {
        public static final Codec<Concession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mine").forGetter(Concession::mine),
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Concession::owner),
                Codec.STRING.optionalFieldOf("faction", "").forGetter(Concession::faction),
                Codec.LONG.fieldOf("since").forGetter(Concession::since),
                Codec.LONG.fieldOf("paid").forGetter(Concession::paid)
        ).apply(instance, Concession::new));

        Concession paidUntil(long time) {
            return new Concession(mine, owner, faction, since, time);
        }

        Concession renamed(String name) {
            return new Concession(mine, owner, name, since, paid);
        }
    }

    public static final MineLedger EMPTY = new MineLedger(List.of());

    public static final MapCodec<MineLedger> MAP_CODEC = Concession.CODEC.listOf().fieldOf("concessions")
            .xmap(MineLedger::new, MineLedger::concessions);

    public MineLedger {
        concessions = List.copyOf(concessions);
    }

    public boolean exists() {
        return !concessions.isEmpty();
    }

    /** La concession de cette mine, ou {@code null} si une nation l'exploite encore. */
    public Concession of(String mine) {
        for (Concession concession : concessions) {
            if (concession.mine().equals(mine)) {
                return concession;
            }
        }
        return null;
    }

    /** Les mines de la faction de ce joueur, dans l'ordre des captures. */
    public List<Concession> of(UUID owner) {
        List<Concession> found = new ArrayList<>();
        for (Concession concession : concessions) {
            if (concession.owner().equals(owner)) {
                found.add(concession);
            }
        }
        return found;
    }

    /** La meme liste, cette concession ajoutee ou remplacee (une seule par mine). */
    public MineLedger with(Concession concession) {
        List<Concession> list = new ArrayList<>(concessions.size() + 1);
        boolean replaced = false;
        for (Concession existing : concessions) {
            if (existing.mine().equals(concession.mine())) {
                list.add(concession);
                replaced = true;
            } else {
                list.add(existing);
            }
        }
        if (!replaced) {
            list.add(concession);
        }
        return new MineLedger(list);
    }

    public MineLedger without(String mine) {
        List<Concession> list = new ArrayList<>(concessions.size());
        for (Concession existing : concessions) {
            if (!existing.mine().equals(mine)) {
                list.add(existing);
            }
        }
        return new MineLedger(list);
    }
}
