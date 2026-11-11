package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;

/**
 * Le decor d'un donjon : de quoi sont faites ses salles.
 *
 * <p>Le wiki ne decrit pas les salles ; il donne a chaque donjon un metier, et
 * les textures de Paladium un element (foret, caverne, flamme, eau). Les
 * themes en decoulent, accordes aux monstres de {@code config/rpgentities} :
 *
 * <ul>
 *   <li><b>Talikus</b>, le Fermier : une grange de bois et de torchis, des
 *       serres aux murs, de la mousse, des bottes de foin ;</li>
 *   <li><b>Nimbria</b>, le Mineur : une galerie d'ardoise etayee de chene, des
 *       filons de minerais de Paladium dans les murs, des rails, des
 *       stalactites ;</li>
 *   <li><b>Vitalys</b>, le Chasseur, dont les monstres sont de feu : une forge
 *       de pierre noire et de briques du Nether, de la lave derriere des
 *       vitraux ;</li>
 *   <li><b>Manelios</b>, l'Alchimiste : un temple englouti de prismarine et de
 *       colonnes blanches, des aquariums aux murs, des cristaux.</li>
 * </ul>
 *
 * @param floorLight   un bloc lumineux affleurant au sol, ou {@code null}
 * @param veins        blocs semes dans les murs (filons), et en plaques entre
 *                     les piliers quand il n'y a pas de fenetre
 * @param window       le vitrage des fenetres hautes, ou {@code null}
 * @param behind       ce qu'on voit derriere le vitrage : le premier remplit
 *                     la niche, les suivants n'en occupent que les coins du
 *                     bas (un corail y garde de l'eau au-dessus de lui)
 * @param obstacleTop  pose parfois sur un obstacle, ou {@code null}
 * @param soil         le sol sous les plantes, ou {@code null} sans plantes
 * @param hanging      ce qui pend du plafond
 * @param rails        une voie ferree traverse la salle
 */
record RoomTheme(Block floor, Block floorAlt, Block floorAccent, Block floorEdge, Block medallion, Block floorLight,
                 Block wallBase, Block wall, Block wallAlt, List<Block> veins, Block trim, Block pillar, Block beam,
                 Block ceiling, Block light, Block lantern,
                 Block window, List<BlockState> behind,
                 List<Block> obstacles, BlockState obstacleTop,
                 Block soil, List<Block> plants, Block carpet,
                 List<BlockState> hanging, boolean rails) {

    static RoomTheme of(Dungeon dungeon) {
        return switch (dungeon) {
            case TALIKUS -> new RoomTheme(
                    Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.COARSE_DIRT, Blocks.SPRUCE_PLANKS,
                    Blocks.HAY_BLOCK, null,
                    Blocks.MUD_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_WOOD, List.of(),
                    Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_OAK_LOG, Blocks.DARK_OAK_LOG,
                    Blocks.DARK_OAK_PLANKS, Blocks.SHROOMLIGHT, Blocks.LANTERN,
                    Blocks.GLASS, List.of(
                            with(Blocks.AZALEA_LEAVES.defaultBlockState(), BlockStateProperties.PERSISTENT, true),
                            with(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(),
                                    BlockStateProperties.PERSISTENT, true)),
                    List.of(Blocks.HAY_BLOCK, Blocks.HAY_BLOCK, Blocks.PUMPKIN, Blocks.MELON, Blocks.COMPOSTER),
                    null,
                    Blocks.MOSS_BLOCK, List.of(Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.FERN,
                            Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET), Blocks.MOSS_CARPET,
                    List.of(Blocks.HANGING_ROOTS.defaultBlockState(), Blocks.HANGING_ROOTS.defaultBlockState(),
                            Blocks.SPORE_BLOSSOM.defaultBlockState()),
                    false);
            case NIMBRIA -> new RoomTheme(
                    Blocks.DEEPSLATE_TILES, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.POLISHED_DEEPSLATE,
                    Blocks.RAW_GOLD_BLOCK, null,
                    Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
                    List.of(PalaBlocks.AMETHYST_ORE.get(), PalaBlocks.TITANE_ORE.get(), PalaBlocks.PALADIUM_ORE.get(),
                            Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
                    Blocks.CHISELED_DEEPSLATE, Blocks.OAK_LOG, Blocks.OAK_LOG,
                    Blocks.TUFF_BRICKS, Blocks.OCHRE_FROGLIGHT, Blocks.LANTERN,
                    null, List.of(),
                    List.of(Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_GOLD_BLOCK,
                            Blocks.COBBLED_DEEPSLATE, Blocks.AMETHYST_BLOCK),
                    with(Blocks.AMETHYST_CLUSTER.defaultBlockState(), BlockStateProperties.FACING, Direction.UP),
                    null, List.of(), null,
                    List.of(with(Blocks.POINTED_DRIPSTONE.defaultBlockState(),
                            BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)),
                    true);
            case VITALYS -> new RoomTheme(
                    Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE,
                    Blocks.SMOOTH_BASALT, Blocks.RED_NETHER_BRICKS, Blocks.SHROOMLIGHT,
                    Blocks.POLISHED_BLACKSTONE, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, List.of(),
                    Blocks.CHISELED_NETHER_BRICKS, Blocks.POLISHED_BASALT, Blocks.POLISHED_BLACKSTONE_BRICKS,
                    Blocks.BLACKSTONE, Blocks.SHROOMLIGHT, Blocks.LANTERN,
                    Blocks.STAINED_GLASS.pick(DyeColor.ORANGE), List.of(Blocks.LAVA.defaultBlockState()),
                    List.of(Blocks.BASALT, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.BONE_BLOCK,
                            Blocks.CHISELED_POLISHED_BLACKSTONE),
                    null,
                    null, List.of(), null,
                    List.of(with(Blocks.WEEPING_VINES.defaultBlockState(), BlockStateProperties.AGE_25, 25)),
                    false);
            case MANELIOS -> new RoomTheme(
                    Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE,
                    Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SEA_LANTERN,
                    Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, List.of(),
                    Blocks.DARK_PRISMARINE, Blocks.QUARTZ_PILLAR, Blocks.DARK_PRISMARINE,
                    Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.SOUL_LANTERN,
                    Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE), List.of(Blocks.WATER.defaultBlockState(),
                            Blocks.TUBE_CORAL_BLOCK.defaultBlockState(),
                            Blocks.FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState()),
                    List.of(Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK,
                            Blocks.QUARTZ_BRICKS),
                    with(Blocks.AMETHYST_CLUSTER.defaultBlockState(), BlockStateProperties.FACING, Direction.UP),
                    null, List.of(), null,
                    List.of(with(Blocks.AMETHYST_CLUSTER.defaultBlockState(),
                            BlockStateProperties.FACING, Direction.DOWN)),
                    false);
        };
    }

    /** L'etat, avec la propriete si le bloc la porte. */
    static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
        return state.hasProperty(property) ? state.setValue(property, value) : state;
    }
}
