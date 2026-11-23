package fr.atraxterra.dohy.faction.enterprise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La loi et l'ordre de la nation (lot 24d), dans ses affaires : les lois qu'elle a votees pour
 * ses pays et leur date, les operations de police annoncees contre elle, les contrats des
 * organisations criminelles, et la derniere operation qu'elle a menee elle-meme.
 */
public final class Justice {

    /** Operations et contrats gardes, finis compris. */
    static final int KEPT = 12;

    static final Codec<Justice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("laws", Map.of())
                    .forGetter(data -> Map.copyOf(data.laws)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("changed", Map.of())
                    .forGetter(data -> Map.copyOf(data.changed)),
            Operation.CODEC.listOf().optionalFieldOf("operations", List.of())
                    .forGetter(data -> List.copyOf(data.operations)),
            Contract.CODEC.listOf().optionalFieldOf("contracts", List.of())
                    .forGetter(data -> List.copyOf(data.contracts)),
            Codec.LONG.optionalFieldOf("crackdown", 0L).forGetter(data -> data.crackdown)
    ).apply(instance, Justice::new));

    /** Le statut de chaque sujet dans les pays de la nation ({@link Law.Topic} vers {@link Law.Status}). */
    final Map<String, String> laws;
    /** Le dernier changement de chaque loi (ms). */
    final Map<String, Long> changed;
    final List<Operation> operations;
    final List<Contract> contracts;
    /** Ta derniere operation reussie contre le crime fait bon effet sur les nations jusqu'a cette heure (ms). */
    long crackdown;

    Justice() {
        this(Map.of(), Map.of(), List.of(), List.of(), 0L);
    }

    private Justice(Map<String, String> laws, Map<String, Long> changed, List<Operation> operations,
                    List<Contract> contracts, long crackdown) {
        this.laws = new HashMap<>(laws);
        this.changed = new HashMap<>(changed);
        this.operations = new ArrayList<>(operations);
        this.contracts = new ArrayList<>(contracts);
        this.crackdown = crackdown;
    }

    boolean isEmpty() {
        return laws.isEmpty() && changed.isEmpty() && operations.isEmpty() && contracts.isEmpty() && crackdown == 0L;
    }

    /** La loi de la nation sur ce sujet ; la loi commune tant qu'elle n'a rien vote. */
    Law.Status law(Law.Topic topic) {
        Law.Status status = Law.Status.of(laws.getOrDefault(topic.name(), ""));
        if (status == null || status == Law.Status.LEGAL && !topic.legalizable()) {
            return topic.common();
        }
        return status;
    }

    long changed(Law.Topic topic) {
        return changed.getOrDefault(topic.name(), 0L);
    }

    void set(Law.Topic topic, Law.Status status, long now) {
        laws.put(topic.name(), status.name());
        changed.put(topic.name(), now);
    }

    Operation operation(String id) {
        for (Operation operation : operations) {
            if (operation.id().equals(id)) {
                return operation;
            }
        }
        return null;
    }

    void put(Operation operation) {
        for (int index = 0; index < operations.size(); index++) {
            if (operations.get(index).id().equals(operation.id())) {
                operations.set(index, operation);
                return;
            }
        }
        operations.add(operation);
        trim(operations, Operation::pending);
    }

    Contract contract(String id) {
        for (Contract contract : contracts) {
            if (contract.id().equals(id)) {
                return contract;
            }
        }
        return null;
    }

    void put(Contract contract) {
        for (int index = 0; index < contracts.size(); index++) {
            if (contracts.get(index).id().equals(contract.id())) {
                contracts.set(index, contract);
                return;
            }
        }
        contracts.add(contract);
        trim(contracts, Contract::open);
    }

    /** Garde les {@link #KEPT} derniers, en retirant d'abord les plus anciens qui sont finis. */
    private static <T> void trim(List<T> list, java.util.function.Predicate<T> alive) {
        for (int index = 0; list.size() > KEPT && index < list.size(); ) {
            if (alive.test(list.get(index))) {
                index++;
            } else {
                list.remove(index);
            }
        }
    }

    /** Ce qui compte encore : annonces en attente, contrats proposes ou en cours. */
    List<Operation> pending() {
        List<Operation> out = new ArrayList<>();
        for (Operation operation : operations) {
            if (operation.pending()) {
                out.add(operation);
            }
        }
        return out;
    }

    List<Contract> open() {
        List<Contract> out = new ArrayList<>();
        for (Contract contract : contracts) {
            if (contract.open()) {
                out.add(contract);
            }
        }
        return out;
    }

    /** Toutes les heures du journal se decalent (essais : faire passer le temps). */
    void shift(long shift) {
        for (int index = 0; index < operations.size(); index++) {
            Operation operation = operations.get(index);
            operations.set(index, new Operation(operation.id(), operation.nation(), operation.countries(),
                    operation.announced() - shift, operation.runs() - shift, operation.state(), operation.strength(),
                    operation.bribed(), operation.hits()));
        }
        for (int index = 0; index < contracts.size(); index++) {
            Contract contract = contracts.get(index);
            contracts.set(index, new Contract(contract.id(), contract.syndicate(), contract.key(), contract.units(),
                    contract.delivered(), contract.reward(), contract.offered() - shift,
                    contract.deadline() > 0L ? contract.deadline() - shift : 0L, contract.state()));
        }
        changed.replaceAll((topic, time) -> time - shift);
        if (crackdown > 0L) {
            crackdown -= shift;
        }
    }

    void clear() {
        laws.clear();
        changed.clear();
        operations.clear();
        contracts.clear();
        crackdown = 0L;
    }
}
