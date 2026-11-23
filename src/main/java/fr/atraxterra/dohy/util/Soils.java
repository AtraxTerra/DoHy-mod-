package fr.atraxterra.dohy.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Les sols de terre. En 26.2, le tag {@code minecraft:dirt} ne compte plus que la terre, la terre
 * sterile et la terre racineuse (releve le 2026-11-13) ; ce qu'il rangeait d'autre est passe dans
 * {@code minecraft:grass_blocks} (herbe, podzol, mycelium), {@code moss_blocks} et {@code mud}, que
 * reunit {@code minecraft:substrate_overworld} (lu dans le jar de 26.2 au lot des points ouverts).
 * Les blocs sont aussi nommes un par un, au cas ou un datapack viderait ces tags.
 */
public final class Soils {

    private static final TagKey<Block> SUBSTRATE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("minecraft", "substrate_overworld"));
    private static final TagKey<Block> GRASS = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("minecraft", "grass_blocks"));

    private Soils() {
    }

    /** De la terre, couverte ou non : ce que rangeait le tag {@code minecraft:dirt} avant la 26.2. */
    public static boolean earth(BlockState state) {
        return state.is(SUBSTRATE) || state.is(BlockTags.DIRT) || covered(state) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.PALE_MOSS_BLOCK) || state.is(Blocks.MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    /** Une terre couverte (herbe, podzol, mycelium) : sous un autre bloc, elle redevient de la terre. */
    public static boolean covered(BlockState state) {
        return state.is(GRASS) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }
}
