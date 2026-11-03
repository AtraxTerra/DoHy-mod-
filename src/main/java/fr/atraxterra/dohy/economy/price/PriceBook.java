package fr.atraxterra.dohy.economy.price;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Le catalogue de prix, tel qu'il a ete charge.
 *
 * <p>Immuable : un rechargement remplace l'instance entiere plutot que de muter
 * celle en service. Une vente en cours ne peut donc pas lire un catalogue a
 * moitie charge.
 */
public record PriceBook(double moneyMultiplier, Map<Identifier, PriceEntry> items) {

    public static final PriceBook EMPTY = new PriceBook(1.0d, Map.of());

    public static final Codec<PriceBook> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Model.CODEC.optionalFieldOf("model", new Model(1.0d)).forGetter(book -> new Model(book.moneyMultiplier())),
            Codec.unboundedMap(Identifier.CODEC, PriceEntry.CODEC).fieldOf("items").forGetter(PriceBook::items)
    ).apply(instance, (model, items) -> new PriceBook(model.moneyMultiplier(), items)));

    /** Le bloc {@code model} du fichier. Seul le multiplicateur sert cote jeu. */
    private record Model(double moneyMultiplier) {
        static final Codec<Model> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("money_multiplier", 1.0d).forGetter(Model::moneyMultiplier)
        ).apply(instance, Model::new));
    }

    public Optional<PriceEntry> entry(Item item) {
        return entry(BuiltInRegistries.ITEM.getKey(item));
    }

    public Optional<PriceEntry> entry(Identifier id) {
        return Optional.ofNullable(items.get(id));
    }

    public Optional<PriceEntry> entry(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : entry(stack.getItem());
    }

    /**
     * Vrai si l'item peut etre vendu.
     *
     * <p>Un item absent du catalogue n'est pas vendable, et c'est voulu : la
     * regle d'equilibrage est que **tout item craftable est en achat seul**.
     * Sinon il existe toujours une recette dont la somme des composants vaut
     * moins que le produit, et c'est une machine a argent. Cette seule regle
     * remplace l'audit de deux mille recettes.
     */
    public boolean isSellable(ItemStack stack) {
        return entry(stack).map(PriceEntry::sellable).orElse(false);
    }

    public int size() {
        return items.size();
    }
}
