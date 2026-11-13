package fr.atraxterra.dohy.earth;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les blocs des villes, par leur nom : le generateur de villes ne connait que
 * des noms, pour se lancer aussi hors du jeu (l'apercu des plans). Un nom
 * inconnu de cette version donne de la pierre, et se note une fois au journal.
 */
final class CityBlocks {

    private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private CityBlocks() {
    }

    static BlockState get(String name) {
        return CACHE.computeIfAbsent(name, key -> BuiltInRegistries.BLOCK
                .getOptional(Identifier.withDefaultNamespace(key)).map(Block::defaultBlockState).orElseGet(() -> {
                    if (MISSING.add(key)) {
                        DoHy.LOGGER.warn("Villes : bloc inconnu {}, remplace par de la pierre", key);
                    }
                    return Blocks.STONE.defaultBlockState();
                }));
    }

    /** L'etat avec cette propriete ({@code facing=north}, {@code half=top}…), ou tel quel si elle n'existe pas. */
    static BlockState with(BlockState state, String property, String value) {
        Property<?> found = state.getBlock().getStateDefinition().getProperty(property);
        return found == null ? state : set(state, found, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    static boolean air(BlockState state) {
        return state.isAir();
    }

    static Set<String> missing() {
        return Set.copyOf(MISSING);
    }
}
