package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.defense.PalaDefense;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bati une base ennemie (lot 22g, refonte du 2026-11-11) dans des chunks deja
 * charges, par etapes : le chantier tourne quelques millisecondes par tick
 * ({@link #work(long)}), une forteresse de 129 x 129 ne fige donc pas le
 * serveur.
 *
 * <p><b>Ce que fixe la taille</b> ({@link EnemyBaseTier}), de l'exterieur vers
 * le centre : une douve et un pont ; une <b>enceinte</b> a couches — dont de
 * l'eau prise entre deux murs : une TNT qui la perce explose ensuite dans
 * l'eau, il faut la vider — avec ses tours d'angle et de courtine, sa
 * porterie et un bastion derriere elle ; un anneau de <b>quartiers</b> ; pour
 * les deux grandes tailles, une <b>seconde enceinte</b>, puis un fosse ; et au
 * centre la <b>salle forte</b>, un cube a couches sur ses six faces.
 *
 * <p><b>Ce que fixe le genre</b> ({@link EnemyBaseStyle}) : ce qu'il y a dans
 * les quartiers — casernes et armurerie, champs et silos, halles de coffres,
 * machines (cobblebreakers, crushers, grinder et boite a golem montes, forge),
 * pyramides a balise et trophees — et ou est la salle forte : au sol dans un
 * donjon, enterree sous une grange ou une centrale, cloisonnee en neuf
 * cellules, ou en haut d'une tour pleine. Les quartiers sont tires au hasard :
 * deux bases du meme genre n'ont pas le meme plan.
 *
 * <p>Tout ce qui est pose est vrai : les machines marchent, les coffres ont
 * leur butin, les blocs de minerai sont des blocs de minerai. La base pillee,
 * ses claims tombent et tout cela se demonte.
 */
final class EnemyBaseBuilder {

    record Result(BlockPos center, BlockPos coreMin, BlockPos coreMax) {
    }

    /** Une couche de mur : un bloc, ou un liquide pris entre deux couches pleines. */
    private record Layer(Supplier<BlockState> block, boolean fluid) {
        static Layer solid(Supplier<BlockState> block) {
            return new Layer(block, false);
        }

        static Layer fluid(Supplier<BlockState> block) {
            return new Layer(block, true);
        }
    }

    /**
     * Un terrain a batir dans l'anneau des quartiers, en coordonnees relatives
     * au centre.
     *
     * @param inward la direction du centre : les portes s'ouvrent de ce cote
     */
    private record Lot(int x0, int z0, int x1, int z1, Direction inward) {
        int mx() {
            return (x0 + x1) / 2;
        }

        int mz() {
            return (z0 + z1) / 2;
        }

        boolean alongX() {
            return x1 - x0 >= z1 - z0;
        }
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final ServerLevel level;
    private final RandomSource random;
    private final EnemyBaseTier tier;
    private final EnemyBaseStyle style;
    private final String name;
    private final int cx;
    private final int cz;
    private final int grade;
    private int y0;
    private int coreRadius;
    private int coreHeight;
    private int coreFloor;
    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private final List<BlockPos> shaped = new ArrayList<>();
    private final Map<String, Block> blocks = new HashMap<>();

    // Le plan de la taille : rayons, hauteurs, couches.
    private final int wall;
    private final int wallHeight;
    private final List<Layer> outer;
    private final int innerWall;
    private final int innerHeight;
    private final List<Layer> inner;
    private final int keep;
    private final List<Layer> vaultLayers;
    private final int room;
    private final int lotsIn;
    private final int lotsOut;

    EnemyBaseBuilder(ServerLevel level, RandomSource random, EnemyBaseTier tier, EnemyBaseStyle style, String name,
                     int cx, int cz) {
        this.level = level;
        this.random = random;
        this.tier = tier;
        this.style = style;
        this.name = name;
        this.cx = cx;
        this.cz = cz;
        this.grade = tier.level();
        this.wall = tier.wall();
        Supplier<BlockState> water = () -> Blocks.WATER.defaultBlockState();
        Supplier<BlockState> obsidian = () -> Blocks.OBSIDIAN.defaultBlockState();
        switch (tier) {
            case OUTPOST -> {
                wallHeight = 8;
                outer = List.of(Layer.solid(this::stoneBricks), Layer.fluid(water), Layer.solid(this::stoneBricks));
                innerWall = 0;
                innerHeight = 0;
                inner = List.of();
                keep = 7;
                vaultLayers = List.of(Layer.solid(this::stoneBricks), Layer.fluid(water), Layer.solid(obsidian));
                room = 4;
                lotsIn = keep + 2;
            }
            case BASE -> {
                wallHeight = 11;
                outer = List.of(Layer.solid(obsidian), Layer.fluid(water), Layer.solid(this::trappedObsidian),
                        Layer.solid(this::hardened));
                innerWall = 18;
                innerHeight = 8;
                inner = List.of(Layer.solid(this::stoneBricks), Layer.fluid(water), Layer.solid(this::hardened));
                keep = 11;
                vaultLayers = List.of(Layer.solid(this::hardened), Layer.fluid(water), Layer.solid(obsidian),
                        Layer.solid(this::boomObsidian), Layer.solid(this::stoneBricks));
                room = 5;
                lotsIn = innerWall + 2;
            }
            default -> {
                wallHeight = 15;
                outer = List.of(Layer.solid(this::hardened), Layer.fluid(water), Layer.solid(this::wallTrap),
                        Layer.solid(obsidian), Layer.fluid(water), Layer.solid(this::hardened));
                innerWall = 28;
                innerHeight = 11;
                inner = List.of(Layer.solid(this::hardened), Layer.fluid(water), Layer.solid(this::wallTrap),
                        Layer.solid(obsidian));
                keep = 16;
                vaultLayers = List.of(Layer.solid(this::hardened), Layer.fluid(water), Layer.solid(this::anvilLayer),
                        Layer.solid(this::witherLayer), Layer.fluid(water), Layer.solid(this::megaBoomObsidian),
                        Layer.solid(() -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()));
                room = 6;
                lotsIn = innerWall + 2;
            }
        }
        this.lotsOut = wall - outer.size() - 1;
    }

    /** Le proprietaire fictif des claims : le meme pour une meme base. */
    static UUID owner(String name, int x, int z) {
        return UUID.nameUUIDFromBytes(("dohy:enemy_base:" + name + ":" + x + ":" + z).getBytes());
    }

    /** Un claim deja pose (un joueur, une autre base) dans l'emprise ? */
    static boolean overlapsClaim(ServerLevel level, int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                if (chunk.getData(Factions.CLAIM.get()).claimed()) {
                    return true;
                }
            }
        }
        return false;
    }

    static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path));
    }

    // -- Chantier -------------------------------------------------------------------

    /** Choisit le sol et prepare toutes les etapes. */
    void plan() {
        y0 = ground();
        for (int x = -tier.half(); x <= tier.half(); x++) {
            int column = x;
            steps.add(() -> flatten(column));
        }
        perimeter();
        districts();
        vault();
        steps.add(this::garrison);
        steps.add(() -> {
            for (BlockPos pos : shaped) {
                level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), FLAGS);
            }
        });
        steps.add(this::claim);
    }

    /** Avance le chantier pendant {@code budget} nanosecondes au plus ; vrai quand tout est pose. */
    boolean work(long budget) {
        long end = System.nanoTime() + budget;
        while (!steps.isEmpty() && System.nanoTime() < end) {
            steps.poll().run();
        }
        return steps.isEmpty();
    }

    Result result() {
        return new Result(new BlockPos(cx, y0, cz), new BlockPos(cx - coreRadius, coreFloor, cz - coreRadius),
                new BlockPos(cx + coreRadius, coreFloor + coreHeight - 1, cz + coreRadius));
    }

    // -- L'enceinte, commune a tous les genres ---------------------------------------

    private void perimeter() {
        int moat = wall + 4;
        Supplier<BlockState> moatFluid = grade == 1 ? () -> Blocks.WATER.defaultBlockState()
                : () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState();
        int moatOuter = moat + (grade == 3 ? 2 : 1);
        steps.add(() -> trench(moat, moatOuter, grade == 3 ? 3 : 2, moatFluid));
        curtain(wall, wallHeight, outer, this::trim, this::merlon);
        int towerRadius = 2 + grade;
        int towerHeight = wallHeight + 4 + grade;
        for (int[] corner : corners(wall - 1)) {
            tower(corner[0], corner[1], towerRadius, towerHeight);
        }
        // Les tours de courtine : aucune au nord, c'est la place de la porterie.
        if (grade == 2) {
            tower(wall - 1, 0, 3, towerHeight - 2);
            tower(-(wall - 1), 0, 3, towerHeight - 2);
            tower(0, wall - 1, 3, towerHeight - 2);
        } else if (grade == 3) {
            for (int at : new int[]{-20, 20}) {
                tower(wall - 1, at, 4, towerHeight - 2);
                tower(-(wall - 1), at, 4, towerHeight - 2);
                tower(at, wall - 1, 4, towerHeight - 2);
            }
            tower(0, wall - 1, 4, towerHeight);
        }
        gate(wall, moatOuter, wallHeight);
        if (innerWall > 0) {
            curtain(innerWall, innerHeight, inner, this::trim, this::merlon);
            for (int[] corner : corners(innerWall - 1)) {
                tower(corner[0], corner[1], 2, innerHeight + 3);
            }
            // Le fosse entre la seconde enceinte et la salle forte, puis un rang de spikes.
            int ditch = keep + 2;
            steps.add(() -> {
                if (grade == 3) {
                    trench(ditch, ditch + 1, 2, () -> Blocks.LAVA.defaultBlockState());
                    ring(ditch + 4, y0, () -> PalaDefense.TITANE_SPIKE.get().defaultBlockState());
                } else {
                    trench(ditch, ditch, 1, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState());
                    ring(ditch + 2, y0, () -> PalaDefense.DIAMOND_SPIKE.get().defaultBlockState());
                }
            });
        }
    }

    /**
     * Une enceinte : une couche par anneau, de l'exterieur (rayon {@code r}) vers
     * l'interieur. Les liquides vont du sol a l'avant-derniere rangee, coiffes
     * par la couche exterieure ; sous l'enceinte, deux rangees pleines.
     */
    private void curtain(int r, int height, List<Layer> layers, Supplier<BlockState> band, Supplier<BlockState> merlon) {
        for (int y = y0 - 2; y <= y0 + height; y++) {
            int row = y;
            steps.add(() -> {
                if (row == y0 + height) {
                    ringMerlons(r, row, merlon);
                    return;
                }
                for (int i = 0; i < layers.size(); i++) {
                    Layer layer = layers.get(i);
                    Supplier<BlockState> block = layer.fluid() && (row < y0 || row == y0 + height - 1)
                            ? layers.get(0).block() : layer.block();
                    if (i == 0 && row >= y0 && (row - y0) % 4 == 3) {
                        block = band;
                    }
                    ring(r - i, row, block);
                }
            });
        }
    }

    /** Une tour pleine, a creneaux, lanterne et banniere aux couleurs du genre. */
    private void tower(int tx, int tz, int radius, int height) {
        Supplier<BlockState> skin = outer.get(0).block();
        steps.add(() -> {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean edge = Math.max(Math.abs(x), Math.abs(z)) == radius;
                    for (int y = y0 - 2; y <= y0 + height; y++) {
                        boolean band = edge && y >= y0 && (y - y0) % 4 == 3;
                        set(tx + x, y, tz + z, band ? trim() : edge || y == y0 + height ? skin.get()
                                : Blocks.COBBLESTONE.defaultBlockState());
                    }
                    if (edge && Math.floorMod(x + z, 2) == 0) {
                        set(tx + x, y0 + height + 1, tz + z, merlon());
                    }
                }
            }
            lantern(tx - radius + 1, y0 + height + 1, tz + radius - 1, false);
            set(tx + radius - 1, y0 + height + 1, tz - radius + 1, Blocks.BANNER.pick(banner()).defaultBlockState());
            guard(EntityTypes.PILLAGER, tx, y0 + height + 1, tz, false);
            if (grade >= 2 && radius >= 4) {
                guard(EntityTypes.PILLAGER, tx + 1, y0 + height + 1, tz - 1, false);
            }
        });
    }

    /**
     * La porterie, au nord : deux tourelles, une herse devant le mur plein, un
     * pont sur la douve, et derriere le mur un bastion qui coupe l'allee.
     */
    private void gate(int r, int moatOuter, int height) {
        Supplier<BlockState> stone = outer.get(0).block();
        steps.add(() -> {
            for (int side = -1; side <= 1; side += 2) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        for (int y = y0 - 2; y <= y0 + height + 4; y++) {
                            set(side * 6 + x, y, -r + z, stone.get());
                        }
                        if ((Math.abs(x) == 2 || Math.abs(z) == 2) && Math.floorMod(x + z, 2) == 0) {
                            set(side * 6 + x, y0 + height + 5, -r + z, merlon());
                        }
                    }
                }
                guard(EntityTypes.PILLAGER, side * 6, y0 + height + 5, -r, false);
            }
            for (int x = -3; x <= 3; x++) {
                for (int y = y0; y <= y0 + 6; y++) {
                    shape(x, y, -r - 1, y == y0 + 6 ? stone.get() : Blocks.IRON_BARS.defaultBlockState());
                }
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = r + 2; z <= moatOuter + 1; z++) {
                    set(x, y0 - 1, -z, Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            // Le bastion : un mur de deux couches en travers de l'allee, des spikes devant.
            int back = r - outer.size() - 4;
            for (int x = -3; x <= 3; x++) {
                for (int y = y0; y <= y0 + 4; y++) {
                    set(x, y, -back, stone.get());
                    set(x, y, -back + 1, hardened());
                }
                set(x, y0, -back - 1, spike());
            }
            guard(EntityTypes.VINDICATOR, -1, y0, -back + 3, false);
            guard(EntityTypes.VINDICATOR, 1, y0, -back + 3, false);
        });
    }

    // -- Les quartiers ---------------------------------------------------------------

    /** Les modules du genre, dans l'ordre ou ils sont distribues aux terrains tires au hasard. */
    private List<String> modules() {
        return switch (style) {
            case CITADEL -> List.of("barracks", "spikes", "armory", "arsenal", "forge", "barracks", "stable", "spikes");
            case FARM -> List.of("field", "greenhouse", "silo", "field", "stable", "field", "barracks", "field");
            case STORAGE -> List.of("warehouse", "silo", "warehouse", "armory", "arsenal", "warehouse", "barracks",
                    "warehouse");
            case FACTORY -> List.of("cobble", "crusher", "grinder", "golem", "forge", "alchemy", "warehouse",
                    "barracks");
            case SHOWCASE -> List.of("pyramid", "trophies", "spawners", "altar", "golem", "pyramid", "arsenal",
                    "barracks");
        };
    }

    private void districts() {
        List<Lot> lots = lots();
        Collections.shuffle(lots, new java.util.Random(random.nextLong()));
        List<String> modules = modules();
        for (int index = 0; index < lots.size(); index++) {
            Lot lot = lots.get(index);
            String module = modules.get(index % modules.size());
            steps.add(() -> build(module, lot));
        }
    }

    /**
     * Les terrains : quatre coins, et la bande de chaque cote decoupee en
     * terrains d'une quinzaine de blocs. L'allee de la porterie coupe la bande
     * nord ; celle du sud est coupee de meme quand il y a une seconde enceinte.
     */
    private List<Lot> lots() {
        List<Lot> lots = new ArrayList<>();
        int a = lotsIn + 1;
        int b = lotsOut;
        lots.add(new Lot(a, a, b, b, Direction.NORTH));
        lots.add(new Lot(-b, a, -a, b, Direction.NORTH));
        lots.add(new Lot(a, -b, b, -a, Direction.SOUTH));
        lots.add(new Lot(-b, -b, -a, -a, Direction.SOUTH));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean road = side == Direction.NORTH || (side == Direction.SOUTH && innerWall > 0);
            List<int[]> ranges = road ? List.of(new int[]{-lotsIn + 1, -3}, new int[]{3, lotsIn - 1})
                    : List.of(new int[]{-lotsIn + 1, lotsIn - 1});
            for (int[] range : ranges) {
                int length = range[1] - range[0] + 1;
                if (length < 7) {
                    continue;
                }
                int count = Math.max(1, (length + 2) / 15);
                int each = (length - 2 * (count - 1)) / count;
                for (int i = 0; i < count; i++) {
                    int from = range[0] + i * (each + 2);
                    int to = from + each - 1;
                    lots.add(switch (side) {
                        case NORTH -> new Lot(from, -b, to, -a, Direction.SOUTH);
                        case SOUTH -> new Lot(from, a, to, b, Direction.NORTH);
                        case EAST -> new Lot(a, from, b, to, Direction.WEST);
                        default -> new Lot(-b, from, -a, to, Direction.EAST);
                    });
                }
            }
        }
        return lots;
    }

    private void build(String module, Lot lot) {
        switch (module) {
            case "barracks" -> barracks(lot);
            case "spikes" -> spikeYard(lot);
            case "armory" -> armory(lot);
            case "arsenal" -> arsenal(lot);
            case "forge" -> forge(lot);
            case "stable" -> stable(lot);
            case "field" -> field(lot);
            case "greenhouse" -> greenhouse(lot);
            case "silo" -> silos(lot);
            case "warehouse" -> warehouse(lot);
            case "cobble" -> cobbleLine(lot);
            case "crusher" -> crusherHall(lot);
            case "grinder" -> {
                if (grade >= 2) {
                    grinderPlant(lot);
                } else {
                    crusherHall(lot);
                }
            }
            case "golem" -> {
                if (grade >= 2) {
                    golemYard(lot);
                } else {
                    forge(lot);
                }
            }
            case "alchemy" -> alchemy(lot);
            case "pyramid" -> pyramid(lot);
            case "trophies" -> trophies(lot);
            case "spawners" -> spawnerShow(lot);
            default -> altar(lot);
        }
    }

    /**
     * Un batiment sur le terrain, en retrait d'un bloc : murs, toit plat, une
     * porte du cote du centre. Rend l'interieur : {@code x0, z0, x1, z1}.
     */
    private int[] shell(Lot lot, int height, Supplier<BlockState> walls, Supplier<BlockState> roof, BlockState window) {
        int x0 = lot.x0() + 1;
        int x1 = lot.x1() - 1;
        int z0 = lot.z0() + 1;
        int z1 = lot.z1() - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edgeX = x == x0 || x == x1;
                boolean edgeZ = z == z0 || z == z1;
                for (int y = y0; y <= y0 + height; y++) {
                    BlockState state;
                    if (y == y0 + height) {
                        state = roof.get();
                    } else if (edgeX || edgeZ) {
                        boolean glazed = window != null && !(edgeX && edgeZ) && y >= y0 + 1 && y <= y0 + 2
                                && Math.floorMod(x + z, 3) == 0;
                        state = glazed ? window : walls.get();
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(x, y, z, state);
                }
            }
        }
        int[] door = door(lot);
        set(door[0], y0, door[1], Blocks.AIR.defaultBlockState());
        set(door[0], y0 + 1, door[1], Blocks.AIR.defaultBlockState());
        lantern((x0 + x1) / 2, y0 + height - 1, (z0 + z1) / 2);
        return new int[]{x0 + 1, z0 + 1, x1 - 1, z1 - 1};
    }

    /** La porte d'un batiment : au milieu de sa face tournee vers le centre. */
    private int[] door(Lot lot) {
        Direction in = lot.inward();
        if (in.getAxis() == Direction.Axis.X) {
            return new int[]{in.getStepX() > 0 ? lot.x1() - 1 : lot.x0() + 1, lot.mz()};
        }
        return new int[]{lot.mx(), in.getStepZ() > 0 ? lot.z1() - 1 : lot.z0() + 1};
    }

    /** Les cases le long du mur du fond (a l'oppose de la porte), a l'interieur. */
    private List<int[]> backRow(int[] in, Lot lot) {
        List<int[]> row = new ArrayList<>();
        Direction dir = lot.inward();
        if (dir.getAxis() == Direction.Axis.X) {
            int x = dir.getStepX() > 0 ? in[0] : in[2];
            for (int z = in[1]; z <= in[3]; z++) {
                row.add(new int[]{x, z});
            }
        } else {
            int z = dir.getStepZ() > 0 ? in[1] : in[3];
            for (int x = in[0]; x <= in[2]; x++) {
                row.add(new int[]{x, z});
            }
        }
        return row;
    }

    /** Une caserne : une halle sombre, un spawner au milieu, un garde a la porte. */
    private void barracks(Lot lot) {
        Supplier<BlockState> stone = grade >= 3 ? () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState() : this::stoneBricks;
        int[] in = shell(lot, 4, stone, stone, null);
        EntityType<?> mob = switch (random.nextInt(3)) {
            case 0 -> grade >= 3 ? EntityTypes.WITHER_SKELETON : EntityTypes.HUSK;
            case 1 -> grade >= 3 ? EntityTypes.CAVE_SPIDER : EntityTypes.SPIDER;
            default -> EntityTypes.HUSK;
        };
        BlockPos pos = new BlockPos(cx + (in[0] + in[2]) / 2, y0, cz + (in[1] + in[3]) / 2);
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), FLAGS);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(mob, random);
        }
        int[] door = door(lot);
        guard(EntityTypes.VINDICATOR, door[0] + lot.inward().getStepX() * 2, y0, door[1] + lot.inward().getStepZ() * 2,
                false);
    }

    /** Une cour a spikes, en damier, autour d'un lampadaire. */
    private void spikeYard(Lot lot) {
        for (int x = lot.x0() + 1; x <= lot.x1() - 1; x++) {
            for (int z = lot.z0() + 1; z <= lot.z1() - 1; z++) {
                if (Math.floorMod(x + z, 2) == 0) {
                    set(x, y0, z, spike());
                }
            }
        }
        post(lot.mx(), lot.mz());
    }

    /** Une armurerie : des coffres au fond, des enclumes, un garde. */
    private void armory(Lot lot) {
        int[] in = shell(lot, 4, this::stoneBricks, this::stoneBricks, Blocks.IRON_BARS.defaultBlockState());
        List<int[]> back = backRow(in, lot);
        int chests = 0;
        for (int i = 1; i < back.size() - 1 && chests < 2 + grade; i += 2) {
            chest(back.get(i)[0], y0, back.get(i)[1], lot.inward(), "chests/enemy_arsenal");
            chests++;
        }
        set(in[0], y0, in[1], anvil());
        set(in[2], y0, in[3], Blocks.SMITHING_TABLE.defaultBlockState());
        guard(EntityTypes.VINDICATOR, (in[0] + in[2]) / 2, y0, (in[1] + in[3]) / 2, false);
    }

    /** Une poudriere : des piles de TNT derriere de l'obsidienne. La percer a l'explosif se paie. */
    private void arsenal(Lot lot) {
        Supplier<BlockState> shellBlock = grade >= 2 ? this::hardened : () -> Blocks.OBSIDIAN.defaultBlockState();
        int[] in = shell(lot, 4, shellBlock, shellBlock, null);
        int mx = (in[0] + in[2]) / 2;
        int mz = (in[1] + in[3]) / 2;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y < 1 + grade; y++) {
                    int roll = random.nextInt(10);
                    set(mx + x, y0 + y, mz + z, grade >= 3 && roll == 0 ? state("endium_tnt", Blocks.TNT)
                            : grade >= 2 && roll <= 2 ? state("big_tnt", Blocks.TNT) : Blocks.TNT.defaultBlockState());
                }
            }
        }
        List<int[]> back = backRow(in, lot);
        chest(back.get(0)[0], y0, back.get(0)[1], lot.inward(), "chests/enemy_arsenal");
        chest(back.get(back.size() - 1)[0], y0, back.get(back.size() - 1)[1], lot.inward(), "chests/enemy_arsenal");
    }

    /** Une forge : palaforge, four en paladium, enclumes, un bassin de lave, une cheminee qui fume. */
    private void forge(Lot lot) {
        int[] in = shell(lot, 5, this::stoneBricks, () -> Blocks.BRICKS.defaultBlockState(), null);
        List<int[]> back = backRow(in, lot);
        int mid = back.size() / 2;
        machine(back.get(mid)[0], y0, back.get(mid)[1], "palaforge", Blocks.BLAST_FURNACE, lot.inward());
        machine(back.get(Math.max(0, mid - 2))[0], y0, back.get(Math.max(0, mid - 2))[1], "paladium_furnace",
                Blocks.FURNACE, lot.inward());
        set(back.get(Math.min(back.size() - 1, mid + 2))[0], y0, back.get(Math.min(back.size() - 1, mid + 2))[1],
                Blocks.LAVA_CAULDRON.defaultBlockState());
        set(in[0], y0, in[1], anvil());
        chest(in[2], y0, in[3], lot.inward(), "chests/enemy_atelier");
        chimney(lot.x0() + 1, lot.z0() + 1, 10);
        guard(EntityTypes.VINDICATOR, (in[0] + in[2]) / 2, y0, (in[1] + in[3]) / 2, false);
    }

    /** Une etable : du bois, du foin, deux vaches, les coffres du grenier. */
    private void stable(Lot lot) {
        int[] in = shell(lot, 5, () -> Blocks.SPRUCE_PLANKS.defaultBlockState(),
                () -> Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.GLASS.defaultBlockState());
        List<int[]> back = backRow(in, lot);
        for (int i = 0; i < back.size(); i++) {
            if (i == 1 || i == back.size() - 2) {
                chest(back.get(i)[0], y0, back.get(i)[1], lot.inward(), "chests/enemy_grenier");
            } else {
                set(back.get(i)[0], y0, back.get(i)[1], Blocks.HAY_BLOCK.defaultBlockState());
                if (i % 2 == 0) {
                    set(back.get(i)[0], y0 + 1, back.get(i)[1], Blocks.HAY_BLOCK.defaultBlockState());
                }
            }
        }
        animal(EntityTypes.COW, (in[0] + in[2]) / 2, (in[1] + in[3]) / 2);
        animal(EntityTypes.COW, (in[0] + in[2]) / 2 + 1, (in[1] + in[3]) / 2);
    }

    /**
     * Un champ : une culture de PalaMod a tous les ages, de la terre labouree
     * humide, un canal toutes les quatre rangees, une cloture, et a partir de
     * la base un totem de fertilite au milieu.
     */
    private void field(Lot lot) {
        int x0 = lot.x0() + 1;
        int x1 = lot.x1() - 1;
        int z0 = lot.z0() + 1;
        int z1 = lot.z1() - 1;
        String[] crops = {"kiwano_crop", "eggplant_crop", "chervil_crop", "orangeblue_crop"};
        Block crop = block(crops[random.nextInt(crops.length)], Blocks.WHEAT);
        int[] door = door(lot);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    if (x != door[0] || z != door[1]) {
                        shape(x, y0, z, Blocks.OAK_FENCE.defaultBlockState());
                    }
                    continue;
                }
                int lane = lot.alongX() ? z - z0 : x - x0;
                if (lane % 4 == 2) {
                    set(x, y0 - 1, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                set(x, y0 - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                set(x, y0, z, crop instanceof CropBlock grown
                        ? grown.getStateForAge(random.nextInt(grown.getMaxAge() + 1)) : crop.defaultBlockState());
            }
        }
        if (grade >= 2) {
            set(lot.mx(), y0 - 1, lot.mz(), Blocks.STONE_BRICKS.defaultBlockState());
            set(lot.mx(), y0, lot.mz(), state("totem_of_fertility", Blocks.HAY_BLOCK));
        }
        shape(x0 + 1, y0, z0 + 1, Blocks.OAK_FENCE.defaultBlockState());
        set(x0 + 1, y0 - 1, z0 + 1, Blocks.COARSE_DIRT.defaultBlockState());
        set(x0 + 1, y0 + 1, z0 + 1, Blocks.CARVED_PUMPKIN.defaultBlockState());
    }

    /** Une serre : du verre, les fleurs de l'alchimiste, des buissons a XP, des cuves au fond. */
    private void greenhouse(Lot lot) {
        int[] in = shell(lot, 5, () -> Blocks.GLASS.defaultBlockState(), () -> Blocks.GLASS.defaultBlockState(), null);
        String[] flowers = {"sauge_flower", "harpagophytum_flower", "clathrusarcheri_flower", "mineral_flower"};
        List<int[]> back = backRow(in, lot);
        for (int x = in[0]; x <= in[2]; x++) {
            for (int z = in[1]; z <= in[3]; z++) {
                set(x, y0 - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
                int roll = random.nextInt(8);
                if (roll < 3) {
                    set(x, y0, z, state(flowers[random.nextInt(flowers.length)], Blocks.POPPY));
                } else if (roll == 3) {
                    set(x, y0, z, state("xp_bush", Blocks.SWEET_BERRY_BUSH));
                }
            }
        }
        String tank = grade >= 3 ? "paladium_tank" : grade == 2 ? "titane_tank" : "gold_tank";
        for (int i = 1; i < back.size() - 1; i += 3) {
            set(back.get(i)[0], y0 - 1, back.get(i)[1], Blocks.STONE_BRICKS.defaultBlockState());
            set(back.get(i)[0], y0, back.get(i)[1], state(tank, Blocks.CAULDRON));
        }
    }

    /** Des silos : des tours de foin, un tonneau au pied avec le butin du grenier. */
    private void silos(Lot lot) {
        boolean alongX = lot.alongX();
        int length = alongX ? lot.x1() - lot.x0() + 1 : lot.z1() - lot.z0() + 1;
        int count = Math.max(1, length / 8);
        for (int i = 0; i < count; i++) {
            int along = (alongX ? lot.x0() : lot.z0()) + (2 * i + 1) * length / (2 * count);
            int sx = alongX ? along : lot.mx();
            int sz = alongX ? lot.mz() : along;
            int height = 8 + 2 * grade + random.nextInt(3);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                        continue;
                    }
                    boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                    for (int y = y0; y < y0 + height; y++) {
                        set(sx + x, y, sz + z, edge ? (y - y0) % 4 == 3 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                                : Blocks.CALCITE.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState());
                    }
                    set(sx + x, y0 + height, sz + z, Blocks.SPRUCE_SLAB.defaultBlockState());
                }
            }
            Direction in = lot.inward();
            BlockPos barrel = new BlockPos(cx + sx + in.getStepX() * 2, y0, cz + sz + in.getStepZ() * 2);
            level.setBlock(barrel, Blocks.BARREL.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, in), FLAGS);
            loot(barrel, "chests/enemy_grenier");
        }
    }

    /**
     * Une halle de stockage : des rangees de conteneurs sur deux niveaux. La
     * plupart tiennent du vrac ; quelques coffres — dont des coffres de
     * PalaMod — ont le butin de l'entrepot.
     */
    private void warehouse(Lot lot) {
        Supplier<BlockState> walls = grade >= 2 ? () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState() : this::stoneBricks;
        int[] in = shell(lot, 6, walls, () -> Blocks.SMOOTH_STONE.defaultBlockState(), null);
        boolean alongX = in[2] - in[0] >= in[3] - in[1];
        String fancy = grade >= 3 ? "paladium_chest" : grade == 2 ? "titane_chest" : "amethyst_chest";
        int rich = 3 + grade;
        int[] door = door(lot);
        for (int x = in[0]; x <= in[2]; x++) {
            for (int z = in[1]; z <= in[3]; z++) {
                int lane = alongX ? z - in[1] : x - in[0];
                int along = alongX ? x - in[0] : z - in[1];
                // Une allee sur trois, et une travee libre en face de la porte.
                if (lane % 3 == 1 || (alongX ? x == door[0] : z == door[1])) {
                    continue;
                }
                Direction face = lane % 3 == 0 ? (alongX ? Direction.SOUTH : Direction.EAST)
                        : (alongX ? Direction.NORTH : Direction.WEST);
                if (rich > 0 && along % 4 == 1 && random.nextInt(3) == 0) {
                    rich--;
                    if (random.nextBoolean()) {
                        BlockPos pos = new BlockPos(cx + x, y0, cz + z);
                        level.setBlock(pos, facing(state(fancy, Blocks.CHEST), face), FLAGS);
                        loot(pos, "chests/enemy_entrepot");
                    } else {
                        chest(x, y0, z, face, "chests/enemy_entrepot");
                    }
                } else {
                    barrel(x, y0, z, face, random.nextInt(3) == 0 ? "chests/enemy_vrac" : null);
                }
                barrel(x, y0 + 1, z, face, random.nextInt(5) == 0 ? "chests/enemy_vrac" : null);
            }
        }
        guard(EntityTypes.VINDICATOR, door[0] + lot.inward().getStepX() * 2, y0, door[1] + lot.inward().getStepZ() * 2,
                false);
    }

    /**
     * Une ligne de cobblebreakers sous un auvent : chaque machine sous son
     * entonnoir charge de cobblestone, un coffre a cote.
     */
    private void cobbleLine(Lot lot) {
        boolean alongX = lot.alongX();
        shed(lot, 5);
        int from = (alongX ? lot.x0() : lot.z0()) + 2;
        int to = (alongX ? lot.x1() : lot.z1()) - 2;
        Direction in = lot.inward();
        // Le coffre de chaque machine : du cote du centre, en travers de la ligne.
        int chestX = alongX ? 0 : in.getStepX() != 0 ? in.getStepX() : 1;
        int chestZ = alongX ? in.getStepZ() != 0 ? in.getStepZ() : 1 : 0;
        boolean first = true;
        for (int along = from; along <= to; along += 3) {
            int x = alongX ? along : lot.mx();
            int z = alongX ? lot.mz() : along;
            machine(x, y0, z, "cobblebreaker", Blocks.STONECUTTER, in);
            set(x, y0 + 1, z, state("paladium_hopper", Blocks.HOPPER));
            set(x, y0 + 2, z, Blocks.COBBLESTONE.defaultBlockState());
            chest(x + chestX, y0, z + chestZ, in, first ? "chests/enemy_atelier" : "chests/enemy_vrac");
            first = false;
        }
        guard(EntityTypes.PILLAGER, lot.mx() - chestX * 2, y0, lot.mz() - chestZ * 2, false);
    }

    /** Une halle de crushers : les machines au fond, leur charbon, un coffre de pieces. */
    private void crusherHall(Lot lot) {
        int[] in = shell(lot, 5, () -> Blocks.BRICKS.defaultBlockState(), () -> Blocks.BRICKS.defaultBlockState(),
                Blocks.GLASS.defaultBlockState());
        List<int[]> back = backRow(in, lot);
        int placed = 0;
        for (int i = 0; i < back.size() && placed < 2 + grade; i += 2) {
            machine(back.get(i)[0], y0, back.get(i)[1], "crusher", Blocks.BLAST_FURNACE, lot.inward());
            if (i + 1 < back.size()) {
                set(back.get(i + 1)[0], y0, back.get(i + 1)[1], Blocks.COAL_BLOCK.defaultBlockState());
            }
            placed++;
        }
        chest(in[0], y0, in[1], lot.inward(), "chests/enemy_atelier");
        chest(in[2], y0, in[3], lot.inward(), "chests/enemy_vrac");
        chimney(lot.x1() - 1, lot.z1() - 1, 9);
        guard(EntityTypes.VINDICATOR, (in[0] + in[2]) / 2, y0, (in[1] + in[3]) / 2, false);
    }

    /** Un grinder monte, sous un auvent, entre deux cheminees. */
    private void grinderPlant(Lot lot) {
        shed(lot, 7);
        grinder(lot.mx(), lot.mz(), lot.inward());
        chimney(lot.x0() + 2, lot.z0() + 2, 12);
        chimney(lot.x1() - 2, lot.z1() - 2, 12);
        set(lot.mx() + lot.inward().getStepX() * 3, y0, lot.mz() + lot.inward().getStepZ() * 3, anvil());
        guard(EntityTypes.VINDICATOR, lot.mx() + lot.inward().getStepX() * 4, y0,
                lot.mz() + lot.inward().getStepZ() * 4, false);
    }

    /**
     * Le grinder, comme {@code GrinderStructure} le veut : un cube de 3 x 3 x 3,
     * une source de lave au centre, un casing au milieu de chaque face, des
     * frames sur les aretes et les coins, le grinder au milieu de la face avant.
     */
    private void grinder(int gx, int gz, Direction front) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    int axes = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    BlockState state;
                    if (axes == 0) {
                        state = Blocks.LAVA.defaultBlockState();
                    } else if (axes == 1 && y == 0 && x == front.getStepX() && z == front.getStepZ()) {
                        state = facing(state("grinder", Blocks.BLAST_FURNACE), front);
                    } else if (axes == 1) {
                        state = state("grinder_casing", Blocks.IRON_BLOCK);
                    } else {
                        state = state("grinder_frame", Blocks.IRON_BARS);
                    }
                    set(gx + x, y0 + 1 + y, gz + z, state);
                }
            }
        }
    }

    /**
     * Une boite a golem montee, comme {@code GolemBoxStructure} la veut : un
     * cube de cadres autour d'une source d'eau angelique, la boite au milieu
     * d'une face. Deux blocs gardiens et une machine a croquettes a cote.
     */
    private void golemYard(Lot lot) {
        Direction front = lot.inward();
        for (int x = lot.x0() + 1; x <= lot.x1() - 1; x++) {
            for (int z = lot.z0() + 1; z <= lot.z1() - 1; z++) {
                set(x, y0 - 1, z, Blocks.QUARTZ_BRICKS.defaultBlockState());
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockState state;
                    if (x == 0 && y == 0 && z == 0) {
                        state = state("angelic_water", Blocks.WATER);
                    } else if (y == 0 && x == front.getStepX() && z == front.getStepZ()) {
                        state = facing(state("golem_box", Blocks.IRON_BLOCK), front);
                    } else {
                        state = state("golem_box_frame", Blocks.IRON_BLOCK);
                    }
                    set(lot.mx() + x, y0 + 1 + y, lot.mz() + z, state);
                }
            }
        }
        int sx = front.getStepZ() != 0 ? 3 : 0;
        int sz = front.getStepX() != 0 ? 3 : 0;
        set(lot.mx() + sx, y0, lot.mz() + sz, state("guardian_block", Blocks.CHISELED_QUARTZ_BLOCK));
        set(lot.mx() - sx, y0, lot.mz() - sz, state("guardian_block", Blocks.CHISELED_QUARTZ_BLOCK));
        machine(lot.mx() + front.getStepX() * 3, y0, lot.mz() + front.getStepZ() * 3, "food_machine", Blocks.SMOKER,
                front);
        post(lot.x0() + 1, lot.z0() + 1);
        post(lot.x1() - 1, lot.z1() - 1);
        guard(EntityTypes.VINDICATOR, lot.mx() + front.getStepX() * 4, y0, lot.mz() + front.getStepZ() * 4, false);
    }

    /** Un laboratoire : extracteurs, cuves, chaudron et son coeur, bibliotheques mystiques. */
    private void alchemy(Lot lot) {
        int[] in = shell(lot, 5, () -> Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                () -> Blocks.STAINED_GLASS.pick(DyeColor.PURPLE).defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState());
        List<int[]> back = backRow(in, lot);
        String tank = grade >= 3 ? "paladium_tank" : grade == 2 ? "titane_tank" : "amethyst_tank";
        for (int i = 0; i < back.size(); i++) {
            int x = back.get(i)[0];
            int z = back.get(i)[1];
            switch (i % 4) {
                case 0 -> set(x, y0, z, state("mystical_bookshelf", Blocks.BOOKSHELF));
                case 1 -> machine(x, y0, z, "extractor", Blocks.BREWING_STAND, lot.inward());
                case 2 -> set(x, y0, z, state(tank, Blocks.CAULDRON));
                default -> set(x, y0, z, Blocks.BREWING_STAND.defaultBlockState());
            }
        }
        int mx = (in[0] + in[2]) / 2;
        int mz = (in[1] + in[3]) / 2;
        set(mx, y0, mz, state("cauldron_core", Blocks.CAULDRON));
        chest(in[0], y0, in[3], lot.inward(), "chests/enemy_atelier");
        guard(EntityTypes.WITCH, mx + 1, y0, mz + 1, false);
    }

    /**
     * Une pyramide a balise : des blocs de minerai en gradins, de plus en plus
     * precieux vers le haut, la balise au sommet.
     */
    private void pyramid(Lot lot) {
        int levels = Math.min(1 + grade, (Math.min(lot.x1() - lot.x0(), lot.z1() - lot.z0()) - 1) / 2);
        for (int level = 0; level < levels; level++) {
            int half = levels - level;
            boolean top = level == levels - 1;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    BlockState state;
                    if (top && grade >= 3) {
                        state = state("paladium_block", Blocks.DIAMOND_BLOCK);
                    } else if (top && grade == 2) {
                        state = state("titane_block", Blocks.DIAMOND_BLOCK);
                    } else if (level == levels - 2 && grade >= 2) {
                        state = state("amethyst_block", Blocks.EMERALD_BLOCK);
                    } else {
                        state = Math.floorMod(x + z, 2) == 0 ? Blocks.IRON_BLOCK.defaultBlockState()
                                : Blocks.GOLD_BLOCK.defaultBlockState();
                    }
                    set(lot.mx() + x, y0 + level, lot.mz() + z, state);
                }
            }
        }
        set(lot.mx(), y0 + levels, lot.mz(), Blocks.BEACON.defaultBlockState());
        guard(EntityTypes.VINDICATOR, lot.mx() + lot.inward().getStepX() * (levels + 2), y0,
                lot.mz() + lot.inward().getStepZ() * (levels + 2), false);
    }

    /** Une galerie de trophees : une colonnade de quartz, des blocs de minerai sous verre blinde. */
    private void trophies(Lot lot) {
        boolean alongX = lot.alongX();
        for (int x = lot.x0() + 1; x <= lot.x1() - 1; x++) {
            for (int z = lot.z0() + 1; z <= lot.z1() - 1; z++) {
                boolean edge = x == lot.x0() + 1 || x == lot.x1() - 1 || z == lot.z0() + 1 || z == lot.z1() - 1;
                set(x, y0 - 1, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                if (edge && Math.floorMod(x + z, 3) == 0) {
                    for (int y = y0; y <= y0 + 4; y++) {
                        set(x, y, z, y == y0 + 4 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
                                : Blocks.QUARTZ_PILLAR.defaultBlockState());
                    }
                }
            }
        }
        String[] prizes = grade >= 3
                ? new String[]{"paladium_block", "green_paladium_block", "findium_block", "trixium_block", "titane_block"}
                : grade == 2 ? new String[]{"titane_block", "amethyst_block", "trixium_block", "paladium_block"}
                : new String[]{"amethyst_block", "titane_block"};
        int from = (alongX ? lot.x0() : lot.z0()) + 3;
        int to = (alongX ? lot.x1() : lot.z1()) - 3;
        int index = 0;
        for (int along = from; along <= to; along += 4) {
            int x = alongX ? along : lot.mx();
            int z = alongX ? lot.mz() : along;
            set(x, y0, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            set(x, y0 + 1, z, state(prizes[index++ % prizes.length], Blocks.GOLD_BLOCK));
            BlockState glass = state("vault_glass", Blocks.GLASS);
            set(x, y0 + 2, z, glass);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                set(x + side.getStepX(), y0 + 1, z + side.getStepZ(), glass);
            }
        }
        guard(EntityTypes.VINDICATOR, lot.mx() + (alongX ? 0 : 2), y0, lot.mz() + (alongX ? 2 : 0), false);
    }

    /** Une ferme a creatures en vitrine : un socle d'obsidienne infusee, un controleur, des spawners vides. */
    private void spawnerShow(Lot lot) {
        String infused = grade >= 3 ? "spawner_structure_three" : grade == 2 ? "spawner_structure_two"
                : "spawner_structure_one";
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(lot.mx() + x, y0, lot.mz() + z, state(infused, Blocks.OBSIDIAN));
                if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                    set(lot.mx() + x, y0 + 1, lot.mz() + z, state("empty_mob_spawner", Blocks.IRON_BARS));
                }
            }
        }
        set(lot.mx(), y0 + 1, lot.mz(), state("spawner_controller", Blocks.SPAWNER));
        guard(EntityTypes.HUSK, lot.mx() + 3, y0, lot.mz() + 3, false);
        guard(EntityTypes.HUSK, lot.mx() - 3, y0, lot.mz() - 3, false);
    }

    /** Un autel a wither : un receptacle sur un socle fletri, quatre piliers a crane. */
    private void altar(Lot lot) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(lot.mx() + x, y0, lot.mz() + z, Math.abs(x) == 2 && Math.abs(z) == 2
                        ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : state("withered_obsidian", Blocks.OBSIDIAN));
                if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                    set(lot.mx() + x, y0 + 1, lot.mz() + z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    set(lot.mx() + x, y0 + 2, lot.mz() + z, Blocks.SOUL_LANTERN.defaultBlockState());
                }
            }
        }
        set(lot.mx(), y0 + 1, lot.mz(), state("wither_receptacle", Blocks.SOUL_SAND));
        EntityType<? extends Mob> keeper = grade >= 2 ? EntityTypes.WITHER_SKELETON : EntityTypes.SKELETON;
        guard(keeper, lot.mx() + 3, y0, lot.mz(), false);
        guard(keeper, lot.mx() - 3, y0, lot.mz(), false);
    }

    /** Un auvent : quatre piliers et un toit de dalles, ouvert sur ses quatre cotes. */
    private void shed(Lot lot, int height) {
        for (int x = lot.x0() + 1; x <= lot.x1() - 1; x++) {
            for (int z = lot.z0() + 1; z <= lot.z1() - 1; z++) {
                boolean corner = (x == lot.x0() + 1 || x == lot.x1() - 1) && (z == lot.z0() + 1 || z == lot.z1() - 1);
                set(x, y0 - 1, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                if (corner) {
                    for (int y = y0; y < y0 + height; y++) {
                        set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
                set(x, y0 + height, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        }
        lantern(lot.mx(), y0 + height - 1, lot.mz());
    }

    /** Une cheminee de brique, un feu de camp qui fume au sommet. */
    private void chimney(int x, int z, int height) {
        for (int y = y0; y < y0 + height; y++) {
            set(x, y, z, Blocks.BRICKS.defaultBlockState());
        }
        set(x, y0 + height, z, Blocks.CAMPFIRE.defaultBlockState());
    }

    private void post(int x, int z) {
        set(x, y0, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        set(x, y0 + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        lantern(x, y0 + 2, z, false);
    }

    // -- La salle forte --------------------------------------------------------------

    private void vault() {
        int depth = vaultLayers.size();
        switch (style) {
            case CITADEL -> {
                cube(y0, y0 + room + depth + 3 + grade, keep);
                crown(y0 + room + depth + 3 + grade, true);
            }
            case STORAGE -> {
                cube(y0, y0 + room + depth, keep);
                crown(y0 + room + depth, false);
                steps.add(this::cells);
            }
            case FARM -> {
                cube(y0 - depth - room, y0 - 1, keep);
                steps.add(this::grange);
            }
            case FACTORY -> {
                cube(y0 - depth - room, y0 - 1, keep);
                steps.add(this::plant);
            }
            default -> {
                // La tour pleine : la salle forte perchee, son socle plein jusqu'au sol.
                int floor = y0 + 6 + 4 * grade;
                steps.add(() -> plinth(floor - depth - 1));
                cube(floor, floor + room + depth, keep);
                crown(floor + room + depth, true);
            }
        }
        steps.add(this::treasure);
    }

    /**
     * Le cube a couches sur ses six faces — la couche {@code d} est a {@code d}
     * blocs de la face la plus proche, dessous compris —, la salle des coffres
     * en son coeur, de {@code floor} sur {@link #room} rangees.
     *
     * @param floor le sol de la salle
     * @param top   la derniere rangee du cube
     */
    private void cube(int floor, int top, int radius) {
        int depth = vaultLayers.size();
        coreRadius = radius - depth;
        coreHeight = room;
        coreFloor = floor;
        int bottom = floor - 1 - depth;
        for (int y = bottom; y <= top; y++) {
            int row = y;
            steps.add(() -> {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        int side = radius - Math.max(Math.abs(x), Math.abs(z));
                        int d = Math.min(side, Math.min(top - row, row - bottom));
                        BlockState state;
                        if (d < depth) {
                            state = d == 0 && side == 0 && row >= floor && (row - floor) % 5 == 4
                                    ? trim() : vaultLayers.get(d).block().get();
                        } else if (row >= floor && row < floor + room) {
                            state = Blocks.AIR.defaultBlockState();
                        } else {
                            state = vaultLayers.get(depth - 1).block().get();
                        }
                        set(x, row, z, state);
                    }
                }
            });
        }
    }

    /** Le couronnement : creneaux, tourelles d'angle a banniere, et une balise ou un crane. */
    private void crown(int top, boolean beacon) {
        steps.add(() -> {
            ringMerlons(keep, top + 1, this::merlon);
            for (int[] corner : corners(keep - 1)) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        for (int y = top + 1; y <= top + 4; y++) {
                            set(corner[0] + x, y, corner[1] + z, vaultLayers.get(0).block().get());
                        }
                    }
                }
                set(corner[0], top + 5, corner[1], Blocks.BANNER.pick(banner()).defaultBlockState());
            }
            if (beacon && grade >= 2) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        set(x, top, z, Blocks.IRON_BLOCK.defaultBlockState());
                    }
                }
                set(0, top + 1, 0, Blocks.BEACON.defaultBlockState());
            } else {
                set(0, top + 1, 0, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
            }
            guard(EntityTypes.PILLAGER, 2, top + 1, 2, false);
            guard(EntityTypes.PILLAGER, -2, top + 1, -2, false);
        });
    }

    /** Le socle de la tour : plein, deux marches en empattement au pied, des bandes aux couleurs du genre. */
    private void plinth(int top) {
        for (int y = y0; y <= top; y++) {
            int spread = y == y0 ? 2 : y == y0 + 1 ? 1 : 0;
            int half = keep + spread;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean skin = Math.max(Math.abs(x), Math.abs(z)) >= keep;
                    set(x, y, z, skin ? (y - y0) % 4 == 3 ? trim() : vaultLayers.get(0).block().get()
                            : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            }
        }
    }

    /**
     * Le bunker a cellules de l'entrepot : la salle est cloisonnee en neuf.
     * Seule la cellule du milieu est la salle forte ; les huit autres ont des
     * coffres de l'entrepot, derriere une cloison de plus a percer.
     */
    private void cells() {
        int roomRadius = coreRadius;
        int wallAt = Math.max(2, roomRadius / 2);
        for (int x = -roomRadius; x <= roomRadius; x++) {
            for (int z = -roomRadius; z <= roomRadius; z++) {
                if (Math.abs(x) == wallAt || Math.abs(z) == wallAt) {
                    for (int y = coreFloor; y < coreFloor + room; y++) {
                        set(x, y, z, hardened());
                    }
                }
            }
        }
        int side = (wallAt + 1 + roomRadius) / 2;
        for (int[] cell : new int[][]{{side, 0}, {-side, 0}, {0, side}, {0, -side}, {side, side}, {-side, side},
                {side, -side}, {-side, -side}}) {
            chest(cell[0], coreFloor, cell[1], Direction.NORTH, "chests/enemy_entrepot");
        }
        coreRadius = wallAt - 1;
    }

    /** La grande grange au-dessus de la cave : du bois sur un soubassement de pierre, du foin, le grenier. */
    private void grange() {
        int half = keep;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                int ridge = Math.min(3, half - Math.abs(x));
                for (int y = y0; y <= y0 + 7 + ridge; y++) {
                    if (y >= y0 + 7) {
                        if (y == y0 + 7 + ridge || edge) {
                            set(x, y, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                        }
                    } else if (edge) {
                        boolean post = Math.abs(x) == half && Math.abs(z) == half || Math.floorMod(x + z, 5) == 0;
                        set(x, y, z, y < y0 + 2 ? stoneBricks() : post ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                                : Blocks.SPRUCE_PLANKS.defaultBlockState());
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = y0; y <= y0 + 3; y++) {
                set(x, y, -half, Blocks.AIR.defaultBlockState());
            }
        }
        for (int[] corner : corners(half - 2)) {
            for (int y = y0; y <= y0 + 2; y++) {
                set(corner[0], y, corner[1], Blocks.HAY_BLOCK.defaultBlockState());
                set(corner[0] + Integer.signum(-corner[0]), y0, corner[1], Blocks.HAY_BLOCK.defaultBlockState());
            }
        }
        chest(-2, y0, half - 1, Direction.NORTH, "chests/enemy_grenier");
        chest(2, y0, half - 1, Direction.NORTH, "chests/enemy_grenier");
        lantern(0, y0 + 6, 0);
        guard(EntityTypes.VINDICATOR, 0, y0, 2, false);
        guard(EntityTypes.VINDICATOR, 0, y0, -3, false);
    }

    /** La centrale au-dessus du bunker : une halle de brique, quatre cheminees, un grinder ou des crushers. */
    private void plant() {
        int half = keep;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                for (int y = y0; y <= y0 + 8; y++) {
                    if (y == y0 + 8) {
                        set(x, y, z, Blocks.SMOOTH_STONE.defaultBlockState());
                    } else if (edge) {
                        boolean glazed = y >= y0 + 3 && y <= y0 + 5 && Math.floorMod(x + z, 4) == 0;
                        set(x, y, z, glazed ? Blocks.GLASS.defaultBlockState() : Blocks.BRICKS.defaultBlockState());
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = y0; y <= y0 + 3; y++) {
                set(x, y, -half, Blocks.AIR.defaultBlockState());
            }
        }
        for (int[] corner : corners(half)) {
            for (int y = y0 + 8; y < y0 + 15 + 2 * grade; y++) {
                set(corner[0], y, corner[1], Blocks.BRICKS.defaultBlockState());
            }
            set(corner[0], y0 + 15 + 2 * grade, corner[1], Blocks.CAMPFIRE.defaultBlockState());
        }
        if (grade >= 2) {
            grinder(0, 2, Direction.NORTH);
        } else {
            machine(-2, y0, 2, "crusher", Blocks.BLAST_FURNACE, Direction.NORTH);
            machine(2, y0, 2, "crusher", Blocks.BLAST_FURNACE, Direction.NORTH);
        }
        machine(-(half - 2), y0, half - 2, "palamachine", Blocks.CRAFTER, Direction.NORTH);
        chest(half - 2, y0, half - 2, Direction.NORTH, "chests/enemy_atelier");
        lantern(0, y0 + 7, -3);
        guard(EntityTypes.VINDICATOR, -3, y0, -3, false);
        guard(EntityTypes.VINDICATOR, 3, y0, -3, false);
    }

    /** Les coffres de la salle forte, sur son pourtour, le coffre fort et le detecteur de la forteresse. */
    private void treasure() {
        int c = coreRadius;
        int wanted = grade == 1 ? 4 : grade == 2 ? 6 : 10;
        List<int[]> spots = new ArrayList<>();
        for (int i = -c + 1; i <= c - 1; i += 2) {
            spots.add(new int[]{i, -c, 0});
            spots.add(new int[]{i, c, 1});
        }
        for (int i = -c + 2; i <= c - 2; i += 2) {
            spots.add(new int[]{-c, i, 2});
            spots.add(new int[]{c, i, 3});
        }
        Direction[] faces = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
        for (int i = 0; i < spots.size() && i < wanted; i++) {
            int[] spot = spots.get(i);
            chest(spot[0], coreFloor, spot[1], faces[spot[2]], tier.lootTable());
        }
        if (grade >= 3) {
            chest(0, coreFloor, 0, Direction.NORTH, "chests/enemy_coffre_fort");
            set(1, coreFloor, 0, PalaPillage.ONLINE_DETECTOR.get().defaultBlockState());
        }
        lantern(0, coreFloor + room - 1, 0);
        if (c >= 2) {
            guard(EntityTypes.VINDICATOR, -1, coreFloor, 1, false);
            guard(grade >= 3 ? EntityTypes.EVOKER : EntityTypes.VINDICATOR, 1, coreFloor, -1, false);
        }
    }

    // -- La garnison -----------------------------------------------------------------

    /** Les gardes du chemin de ronde et des cours ; ceux des tours et des quartiers sont poses avec eux. */
    private void garrison() {
        int walk = wall - 1;
        for (int at = -walk + 8; at <= walk - 8; at += 16) {
            guard(EntityTypes.PILLAGER, at, y0 + wallHeight, walk, false);
            guard(EntityTypes.PILLAGER, walk, y0 + wallHeight, at, false);
            guard(EntityTypes.PILLAGER, -walk, y0 + wallHeight, at, false);
            if (Math.abs(at) > 8) {
                guard(EntityTypes.PILLAGER, at, y0 + wallHeight, -walk, false);
            }
        }
        int yard = lotsIn - 1;
        guard(EntityTypes.WITCH, 2, y0, yard, false);
        guard(EntityTypes.ZOMBIE, -2, y0, -yard, true);
        if (innerWall > 0) {
            // Entre le fosse et les spikes.
            int ditch = keep + (grade == 3 ? 5 : 3);
            for (int[] corner : corners(ditch)) {
                guard(EntityTypes.VINDICATOR, corner[0], y0, corner[1], false);
            }
            guard(EntityTypes.WITCH, -2, y0, yard + 2, false);
            for (int at = -innerWall + 6; at <= innerWall - 6; at += 12) {
                guard(EntityTypes.PILLAGER, at, y0 + innerHeight, innerWall - 1, false);
                guard(EntityTypes.PILLAGER, at, y0 + innerHeight, -(innerWall - 1), false);
            }
        }
        if (grade >= 3) {
            // Les ravageurs tournent dans la cour interieure, derriere les spikes.
            guard(EntityTypes.RAVAGER, -6, y0, keep + 7, false);
            guard(EntityTypes.RAVAGER, 6, y0, -(keep + 7), false);
            guard(EntityTypes.EVOKER, 0, y0, -(innerWall + 1), false);
        }
    }

    // -- Terrain ------------------------------------------------------------------

    /** Le sol : la mediane des hauteurs de l'emprise, au-dessus du niveau de la mer. */
    private int ground() {
        int half = tier.half();
        List<Integer> heights = new ArrayList<>();
        for (int x = cx - half; x <= cx + half; x += 4) {
            for (int z = cz - half; z <= cz + half; z += 4) {
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        heights.sort(Integer::compare);
        return Math.max(heights.get(heights.size() / 2), level.getSeaLevel() + 1);
    }

    /** Une colonne de l'emprise : degagee, pavee, posee sur des fondations de pierre. */
    private void flatten(int dx) {
        int half = tier.half();
        int x = cx + dx;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = -half; dz <= half; dz++) {
            int z = cz + dz;
            int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), y0 + tier.clearHeight());
            for (int y = y0; y <= top; y++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            level.setBlock(pos.set(x, y0 - 1, z), floor(dx, dz), FLAGS);
            for (int y = y0 - 2; y > y0 - 16; y--) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
                    break;
                }
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Dehors, l'herbe ; dans l'enceinte, une croix d'allees et le pavage du genre. */
    private BlockState floor(int dx, int dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) > wall - outer.size()) {
            return random.nextInt(8) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
            return Blocks.POLISHED_ANDESITE.defaultBlockState();
        }
        int roll = random.nextInt(10);
        return switch (style) {
            case FARM -> roll < 5 ? Blocks.GRASS_BLOCK.defaultBlockState() : roll < 8 ? Blocks.DIRT_PATH.defaultBlockState()
                    : Blocks.COARSE_DIRT.defaultBlockState();
            case STORAGE -> roll < 7 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            case FACTORY -> roll < 5 ? Blocks.STONE_BRICKS.defaultBlockState() : roll < 8 ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case SHOWCASE -> Math.floorMod(dx + dz, 2) == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                    : Blocks.SMOOTH_QUARTZ.defaultBlockState();
            default -> roll < 4 ? Blocks.COBBLESTONE.defaultBlockState() : roll < 6 ? Blocks.GRAVEL.defaultBlockState()
                    : roll < 8 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    /** Un anneau de liquide de {@code from} a {@code to}, profond de {@code depth}, sur un fond de pierre. */
    private void trench(int from, int to, int depth, Supplier<BlockState> fluid) {
        for (int r = from; r <= to; r++) {
            for (int y = y0 - depth; y <= y0 - 1; y++) {
                ring(r, y, fluid);
            }
            for (int y = y0 - depth - 2; y < y0 - depth; y++) {
                ring(r, y, () -> Blocks.STONE.defaultBlockState());
            }
        }
    }

    // -- Palettes -------------------------------------------------------------------

    /** La bande decorative des murs : la couleur du genre. */
    private BlockState trim() {
        return switch (style) {
            case FARM -> Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
            case STORAGE -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            case FACTORY -> Blocks.WAXED_CUT_COPPER.defaultBlockState();
            case SHOWCASE -> Blocks.PURPUR_BLOCK.defaultBlockState();
            default -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
        };
    }

    private BlockState merlon() {
        return switch (style) {
            case FARM -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case FACTORY -> Blocks.BRICKS.defaultBlockState();
            case SHOWCASE -> Blocks.QUARTZ_BRICKS.defaultBlockState();
            default -> grade >= 3 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : stoneBricks();
        };
    }

    private DyeColor banner() {
        return switch (style) {
            case FARM -> DyeColor.GREEN;
            case STORAGE -> DyeColor.GRAY;
            case FACTORY -> DyeColor.ORANGE;
            case SHOWCASE -> DyeColor.PURPLE;
            default -> DyeColor.BLACK;
        };
    }

    private BlockState stoneBricks() {
        int roll = random.nextInt(20);
        return roll < 12 ? Blocks.STONE_BRICKS.defaultBlockState()
                : roll < 15 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : roll < 18 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }

    private BlockState hardened() {
        return PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState();
    }

    private BlockState spike() {
        return grade >= 3 ? PalaDefense.TITANE_SPIKE.get().defaultBlockState()
                : PalaDefense.DIAMOND_SPIKE.get().defaultBlockState();
    }

    private BlockState anvil() {
        return grade >= 3 ? state("paladium_anvil", Blocks.ANVIL) : grade == 2 ? state("titane_anvil", Blocks.ANVIL)
                : Blocks.ANVIL.defaultBlockState();
    }

    /** Une obsidienne sur quatre piegee : poison, boom, knockback, gel. */
    private BlockState trappedObsidian() {
        return switch (random.nextInt(16)) {
            case 0 -> PalaDefense.POISON_OBSIDIAN.get().defaultBlockState();
            case 1 -> PalaDefense.BOOM_OBSIDIAN.get().defaultBlockState();
            case 2 -> PalaDefense.KNOCKBACK_OBSIDIAN.get().defaultBlockState();
            case 3 -> PalaDefense.FROZEN_OBSIDIAN.get().defaultBlockState();
            default -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    /** La couche piegee de la place forte : une sur trois, slime contre les withers compris. */
    private BlockState wallTrap() {
        return switch (random.nextInt(9)) {
            case 0 -> PalaDefense.POISON_OBSIDIAN.get().defaultBlockState();
            case 1 -> PalaDefense.MEGA_BOOM_OBSIDIAN.get().defaultBlockState();
            case 2 -> PalaDefense.SLIME_OBSIDIAN.get().defaultBlockState();
            default -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    private BlockState boomObsidian() {
        return random.nextInt(6) == 0 ? PalaDefense.BOOM_OBSIDIAN.get().defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
    }

    private BlockState megaBoomObsidian() {
        return random.nextInt(6) == 0 ? PalaDefense.MEGA_BOOM_OBSIDIAN.get().defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
    }

    /** Les enclumes en paladium encaissent deux coups de wither ; l'anti-aggro leur fait lacher prise. */
    private BlockState anvilLayer() {
        return random.nextBoolean() ? PalaDefense.PALADIUM_ANVIL.get().defaultBlockState()
                : PalaDefense.ANTI_AGGRO_OBSIDIAN.get().defaultBlockState();
    }

    /** L'obsidienne a wither retient les withers ; la compacte revient. */
    private BlockState witherLayer() {
        return random.nextInt(3) == 0 ? PalaDefense.BIG_OBSIDIAN.get().defaultBlockState()
                : PalaDefense.WITHER_OBSIDIAN.get().defaultBlockState();
    }

    // -- Primitives -----------------------------------------------------------------

    /** Un bloc de PalaMod par son nom ; {@code fallback} s'il n'est pas enregistre. */
    private Block block(String path, Block fallback) {
        return blocks.computeIfAbsent(path, key -> BuiltInRegistries.BLOCK
                .getOptional(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, key)).orElse(fallback));
    }

    private BlockState state(String path, Block fallback) {
        return block(path, fallback).defaultBlockState();
    }

    /** Tourne un bloc vers {@code face}, s'il a une orientation horizontale. */
    private static BlockState facing(BlockState state, Direction face) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, face) : state;
    }

    private void set(int dx, int y, int dz, BlockState state) {
        level.setBlock(new BlockPos(cx + dx, y, cz + dz), state, FLAGS);
    }

    /** Un bloc dont la forme depend de ses voisins (barreaux, clotures) : raccorde en fin de chantier. */
    private void shape(int dx, int y, int dz, BlockState state) {
        BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
        level.setBlock(pos, state, FLAGS);
        shaped.add(pos);
    }

    /** Une machine de PalaMod, tournee vers {@code face}. */
    private void machine(int dx, int y, int dz, String path, Block fallback, Direction face) {
        set(dx, y, dz, facing(state(path, fallback), face));
    }

    private static int[][] corners(int at) {
        return new int[][]{{-at, -at}, {at, -at}, {-at, at}, {at, at}};
    }

    /** Le pourtour du carre de demi-cote {@code r} a la hauteur {@code y}. */
    private void ring(int r, int y, Supplier<BlockState> state) {
        for (int i = -r; i <= r; i++) {
            set(i, y, -r, state.get());
            set(i, y, r, state.get());
            if (i != -r && i != r) {
                set(-r, y, i, state.get());
                set(r, y, i, state.get());
            }
        }
    }

    /** Des creneaux : un bloc sur deux du pourtour. */
    private void ringMerlons(int r, int y, Supplier<BlockState> state) {
        for (int i = -r; i <= r; i++) {
            if (Math.floorMod(i, 2) == 0) {
                set(i, y, -r, state.get());
                set(i, y, r, state.get());
                set(-r, y, i, state.get());
                set(r, y, i, state.get());
            }
        }
    }

    private void lantern(int dx, int y, int dz) {
        lantern(dx, y, dz, true);
    }

    private void lantern(int dx, int y, int dz, boolean hanging) {
        set(dx, y, dz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    /** Un coffre a butin : la table est tiree a l'ouverture, ou a l'explosion. */
    private void chest(int dx, int y, int dz, Direction face, String table) {
        BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
        level.setBlock(pos, facing(Blocks.CHEST.defaultBlockState(), face), FLAGS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table(table), random.nextLong());
        }
    }

    private void barrel(int dx, int y, int dz, Direction face, String table) {
        BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
        level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, face), FLAGS);
        if (table != null && level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table(table), random.nextLong());
        }
    }

    /**
     * Remplit un conteneur d'une table de butin. Un coffre vanilla la tire
     * tout seul a l'ouverture ; un coffre de PalaMod ne sait pas, on la tire
     * pour lui, maintenant.
     */
    private void loot(BlockPos pos, String path) {
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table(path), random.nextLong());
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(table(path));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            free.add(slot);
        }
        Collections.shuffle(free, new java.util.Random(random.nextLong()));
        table.getRandomItems(params, stack -> {
            if (!free.isEmpty() && !stack.isEmpty()) {
                container.setItem(free.remove(free.size() - 1), stack);
            }
        });
        container.setChanged();
    }

    /** Un garde de la faction : persistant, nomme. Le zombie a un casque et une epee. */
    private void guard(EntityType<? extends Mob> type, int dx, int y, int dz, boolean armed) {
        Mob mob = type.create(level, EntitySpawnReason.STRUCTURE);
        if (mob == null) {
            return;
        }
        BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
        mob.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, random.nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(false);
        }
        if (armed) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
        mob.setPersistenceRequired();
        mob.addTag(EnemyBases.GUARD_TAG);
        mob.setCustomName(Component.literal("Garde des " + name));
        level.addFreshEntity(mob);
    }

    /** Une bete de la ferme : elle reste, mais ce n'est pas un garde. */
    private void animal(EntityType<? extends Mob> type, int dx, int dz) {
        Mob mob = type.create(level, EntitySpawnReason.STRUCTURE);
        if (mob == null) {
            return;
        }
        mob.snapTo(cx + dx + 0.5d, y0, cz + dz + 0.5d, random.nextFloat() * 360.0f, 0.0f);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    /** Les claims de la faction ennemie, sur tout le carre de chunks. */
    private void claim() {
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        int radius = tier.claimRadius();
        ClaimOwner owner = new ClaimOwner(Optional.of(owner(name, cx, cz)), name, true, true);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                chunk.setData(Factions.CLAIM.get(), owner);
                chunk.markUnsaved();
            }
        }
    }
}
