package fr.atraxterra.dohy.farmland;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Les blocs de la farmland, par leur nom : en 26.2, bien des blocs n'ont plus de champ dans
 * {@code Blocks}. Un nom inconnu donne de l'air et reste note, pour le journal.
 */
final class B {

    private static final Map<String, BlockState> CACHE = new HashMap<>();
    /** Les noms qui n'ont rien donne. */
    static final Set<String> MISSING = new TreeSet<>();

    private B() {
    }

    static BlockState of(String name) {
        BlockState cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        int colon = name.indexOf(':');
        Identifier id = colon < 0 ? Identifier.withDefaultNamespace(name)
                : Identifier.fromNamespaceAndPath(name.substring(0, colon), name.substring(colon + 1));
        BlockState state = BuiltInRegistries.BLOCK.getOptional(id).map(Block::defaultBlockState).orElse(null);
        if (state == null) {
            MISSING.add(name);
            state = Blocks.AIR.defaultBlockState();
        }
        CACHE.put(name, state);
        return state;
    }

    /** La propriete si le bloc l'a ; le bloc tel quel sinon (jamais d'exception). */
    static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
        return state.hasProperty(property) ? state.setValue(property, value) : state;
    }
}
