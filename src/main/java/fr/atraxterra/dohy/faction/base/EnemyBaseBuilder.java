package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.defense.PalaDefense;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import fr.atraxterra.dohy.palette.DoHyPalette;
import fr.atraxterra.dohy.palette.PaletteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
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
 * Bati une base ennemie (lot 22g ; refonte militaire du 2026-11-13) dans des
 * chunks deja charges, par etapes : le chantier tourne quelques millisecondes
 * par tick ({@link #work(long)}), une forteresse de 129 x 129 ne fige donc pas
 * le serveur.
 *
 * <p><b>Ce que fixe la taille</b> ({@link EnemyBaseTier}) ne change pas avec la
 * refonte : c'est le systeme de raid. De l'exterieur vers le centre : un fosse
 * en eau et un pont ; une <b>enceinte</b> a couches — dont de l'eau prise entre
 * deux murs : une TNT qui la perce explose ensuite dans l'eau, il faut la
 * vider — avec ses miradors d'angle et de courtine et sa porterie ; un anneau
 * de <b>quartiers</b> ; pour les deux grandes tailles, une <b>seconde
 * enceinte</b>, puis un fosse ; et au centre la <b>salle forte</b>, un cube a
 * couches sur ses six faces. Le beton des parements, des parapets et des
 * batiments est du beton du jeu : il se casse a la TNT comme la pierre qu'il
 * remplace, les couches dures restent les memes.
 *
 * <p><b>Ce que fixe le genre</b> ({@link EnemyBaseStyle}) : la couleur du beton,
 * les quartiers — puits de missiles sous leur trappe, cuves de carburant,
 * casemates, tranchees, miradors, garage de blindes, place d'armes, radomes,
 * mat d'antennes, heliport — et la salle forte : enterree sous la grande
 * trappe du silo ou sous le blockhaus de commandement, dans un donjon de beton
 * a l'etoile rouge, ou sous le grand radar. Les quartiers sont tires au
 * hasard : deux bases du meme genre n'ont pas le meme plan.
 *
 * <p><b>Avec NTM NEXT</b> ({@link NtmDefenses}), les defenses sont celles du
 * mod : des tourelles armees (mitrailleuses « Mister Friendly » et « Chekhov »,
 * MASER « Maxwell » a la forteresse : elles portent a 32 blocs, le Maxwell a
 * 64, moins que le glacis — on peut s'installer hors de leur portee), des
 * mines dans le glacis, du barbele sur les parapets et devant les tranchees,
 * des projecteurs halogenes, une porte blindee, des trappes de silo, le grand
 * radar. Une tourelle de NTM ne vise qu'a 30 degres sous l'horizon (35 pour le
 * Maxwell) : posee haut, elle ne voit personne au sol. Elles sont donc basses —
 * sur des balcons de tir au pied des miradors et de la porterie, dans des nids
 * de mitrailleuse et des casemates, au ras du tablier du silo, aux coins du toit
 * du blockhaus, sur des balcons du donjon et de la station radar — sauf les
 * MASER de la forteresse, en surplomb aux coins du toit. Sans le mod, la base
 * garde ses gardes, ses spikes et ses lanternes.
 *
 * <p>Tout ce qui est pose est vrai : les tourelles tirent, les coffres et les
 * palettes ont leur butin, les machines marchent. La base pillee, ses claims
 * tombent, ses tourelles s'eteignent et tout cela se demonte.
 */
final class EnemyBaseBuilder {

    record Result(BlockPos center, BlockPos coreMin, BlockPos coreMax, int turrets, int mines, int guards) {
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

    /** Le bloc d'une case d'un anneau, selon sa position. */
    private interface Cell {
        BlockState at(int x, int z);
    }

    /** Le bloc d'une case d'un parement, selon sa position et sa hauteur. */
    private interface Facade {
        BlockState at(int x, int y, int z);
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** Les munitions des palettes : des obus et des cartouches ordinaires, rien de nucleaire. */
    private static final String[] SHELLS = {"ammo_shell", "ammo_shell_explosive", "ammo_arty", "ammo_arty_he"};
    private static final String[] ROUNDS = {"ammo_standard_r762_fmj", "ammo_standard_bmg50_fmj",
            "ammo_standard_r556_fmj", "ammo_standard_g12", "ammo_standard_p9_fmj"};

    private static Block rodBlock;

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
    private int turrets;
    private int mines;
    private int guards;
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
                outer = List.of(Layer.solid(this::skin), Layer.fluid(water), Layer.solid(this::skin));
                innerWall = 0;
                innerHeight = 0;
                inner = List.of();
                keep = 7;
                vaultLayers = List.of(Layer.solid(this::skin), Layer.fluid(water), Layer.solid(obsidian));
                room = 4;
                lotsIn = keep + 2;
            }
            case BASE -> {
                wallHeight = 11;
                outer = List.of(Layer.solid(obsidian), Layer.fluid(water), Layer.solid(this::trappedObsidian),
                        Layer.solid(this::hardened));
                innerWall = 18;
                innerHeight = 8;
                inner = List.of(Layer.solid(this::skin), Layer.fluid(water), Layer.solid(this::hardened));
                keep = 11;
                vaultLayers = List.of(Layer.solid(this::hardened), Layer.fluid(water), Layer.solid(obsidian),
                        Layer.solid(this::boomObsidian), Layer.solid(this::skin));
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
        // Avant le premier bloc, le releve du terrain : il reviendra apres le raid.
        EnemyBaseRegen.Snapshot terrain = EnemyBaseRegen.Snapshot.begin(level, name, cx, cz, tier.claimRadius(), y0,
                tier.clearHeight());
        for (int i = 0; i < terrain.chunks(); i++) {
            int index = i;
            steps.add(() -> terrain.capture(level, index));
        }
        for (int x = -tier.half(); x <= tier.half(); x++) {
            int column = x;
            steps.add(() -> flatten(column));
        }
        perimeter();
        districts();
        vault();
        steps.add(this::garrison);
        steps.add(this::minefield);
        steps.add(() -> {
            for (BlockPos pos : shaped) {
                level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), FLAGS);
            }
        });
        steps.add(this::claim);
        steps.add(() -> EnemyBaseRegen.store(level, terrain));
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
                new BlockPos(cx + coreRadius, coreFloor + coreHeight - 1, cz + coreRadius), turrets, mines, guards);
    }

    // -- L'enceinte, commune a tous les genres ---------------------------------------

    private void perimeter() {
        int moat = wall + 4;
        Supplier<BlockState> moatFluid = grade == 1 ? () -> Blocks.WATER.defaultBlockState()
                : () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState();
        int moatOuter = moat + (grade == 3 ? 2 : 1);
        steps.add(() -> ditch(moat, moatOuter, grade == 3 ? 3 : 2, moatFluid));
        // Les grandes tailles ont un parement de beton devant et derriere leurs couches d'obsidienne.
        boolean veneer = grade >= 2;
        curtain(wall, wallHeight, outer, veneer, veneer);
        int towerRadius = 2 + grade;
        int towerHeight = wallHeight + 4 + grade;
        int[][] corners = corners(wall - 1);
        Direction[] outs = {Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH};
        for (int i = 0; i < corners.length; i++) {
            String turret = switch (grade) {
                case 1 -> i == 0 || i == 3 ? "turret_friendly" : null;
                default -> "turret_chekhov";
            };
            tower(corners[i][0], corners[i][1], towerRadius, towerHeight, turret, outs[i]);
        }
        // Les miradors de courtine : aucun au nord, c'est la place de la porterie.
        if (grade == 2) {
            tower(wall - 1, 0, 3, towerHeight - 2, null, Direction.EAST);
            tower(-(wall - 1), 0, 3, towerHeight - 2, null, Direction.WEST);
            tower(0, wall - 1, 3, towerHeight - 2, null, Direction.SOUTH);
        } else if (grade == 3) {
            for (int at : new int[]{-20, 20}) {
                tower(wall - 1, at, 4, towerHeight - 2, null, Direction.EAST);
                tower(-(wall - 1), at, 4, towerHeight - 2, null, Direction.WEST);
                tower(at, wall - 1, 4, towerHeight - 2, null, Direction.SOUTH);
            }
            tower(0, wall - 1, 4, towerHeight, "turret_friendly", Direction.SOUTH);
        }
        gate(wall, moatOuter, wallHeight);
        if (innerWall > 0) {
            curtain(innerWall, innerHeight, inner, true, true);
            int[][] innerCorners = corners(innerWall - 1);
            for (int i = 0; i < innerCorners.length; i++) {
                tower(innerCorners[i][0], innerCorners[i][1], 2, innerHeight + 3, null, outs[i]);
            }
            // Le fosse entre la seconde enceinte et la salle forte, puis un rang de barbele.
            int ditch = keep + 2;
            steps.add(() -> {
                if (grade == 3) {
                    ditch(ditch, ditch + 1, 2, () -> Blocks.LAVA.defaultBlockState());
                    wireRing(ditch + 4, y0, true);
                } else {
                    ditch(ditch, ditch, 1, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState());
                    wireRing(ditch + 1, y0, false);
                }
            });
        }
    }

    /**
     * Une enceinte : une couche par anneau, de l'exterieur (rayon {@code r}) vers
     * l'interieur. Les liquides vont du sol a l'avant-derniere rangee, coiffes
     * par la couche exterieure ; sous l'enceinte, deux rangees pleines. Au-dessus,
     * un chemin de ronde de beton sur toute l'epaisseur, puis un parapet coiffe
     * de barbele.
     *
     * @param front un parement de beton devant les couches (rayon {@code r + 1})
     * @param back  un parement derriere elles, cote cour
     */
    private void curtain(int r, int height, List<Layer> layers, boolean front, boolean back) {
        int outside = front ? r + 1 : r;
        int inside = r - layers.size();
        for (int y = y0 - 2; y <= y0 + height + 2; y++) {
            int row = y;
            steps.add(() -> {
                if (row == y0 + height + 2) {
                    wireRing(outside, row, false);
                    return;
                }
                if (row == y0 + height + 1) {
                    ringAt(outside, row, (x, z) -> skin());
                    return;
                }
                if (row == y0 + height) {
                    // Le chemin de ronde, et des lanternes marines dans sa dalle : la nuit, le mur reste eclaire.
                    int walk = back ? inside : inside + 1;
                    for (int ring = walk; ring <= outside; ring++) {
                        int at = ring;
                        ringAt(ring, row, (x, z) -> at == walk && Math.floorMod(Math.abs(z) == at ? x : z, 8) == 4
                                ? Blocks.SEA_LANTERN.defaultBlockState() : accent());
                    }
                    return;
                }
                for (int i = 0; i < layers.size(); i++) {
                    Layer layer = layers.get(i);
                    Supplier<BlockState> block = layer.fluid() && (row < y0 || row == y0 + height - 1)
                            ? layers.get(0).block() : layer.block();
                    if (i == 0 && !front && row >= y0 && (row - y0) % 4 == 3) {
                        ringAt(r, row, (x, z) -> band(x, row, z));
                    } else {
                        ring(r - i, row, block);
                    }
                }
                if (front && row >= y0 - 1) {
                    ringAt(r + 1, row, (x, z) -> row >= y0 && (row - y0) % 4 == 3 ? band(x, row, z) : skin());
                }
                if (back && row >= y0) {
                    ring(inside, row, this::skin);
                }
            });
        }
    }

    /**
     * Un mirador : un fut de beton plein a bandes aux couleurs du genre, un
     * toit-terrasse a garde-corps grillage, un garde, un projecteur sur le bord
     * tourne vers {@code out}, le drapeau du genre. La tourelle de NTM que la
     * taille lui donne n'est pas en haut — elle ne peut viser qu'a 30 degres
     * sous l'horizon, d'en haut elle ne verrait personne au sol — mais sur un
     * balcon de tir au pied du mirador, a trois blocs du sol, cote exterieur.
     */
    private void tower(int tx, int tz, int radius, int height, String turret, Direction out) {
        steps.add(() -> {
            int top = y0 + height;
            Direction side = out.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            int lateral = radius - 1;
            // Le projecteur au bord du toit, son socle derriere lui, son levier a cote.
            int spotX = tx + out.getStepX() * radius + side.getStepX() * lateral;
            int spotZ = tz + out.getStepZ() * radius + side.getStepZ() * lateral;
            int leverX = spotX - side.getStepX();
            int leverZ = spotZ - side.getStepZ();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean edge = Math.max(Math.abs(x), Math.abs(z)) == radius;
                    for (int y = y0 - 2; y <= top; y++) {
                        boolean band = edge && y >= y0 && (y - y0) % 4 == 3;
                        set(tx + x, y, tz + z, band ? band(tx + x, y, tz + z) : edge || y == top ? skin()
                                : Blocks.COBBLESTONE.defaultBlockState());
                    }
                    boolean light = tx + x == spotX && tz + z == spotZ || tx + x == leverX && tz + z == leverZ;
                    if (edge && !light) {
                        shape(tx + x, top + 1, tz + z, railing());
                    }
                }
            }
            floodlight(spotX, top + 1, spotZ, out, side.getOpposite());
            set(tx, top + 1, tz + lateral, Blocks.BANNER.pick(banner()).defaultBlockState());
            guard(EntityTypes.PILLAGER, radius >= 3 ? tx - lateral : tx, top + 1, radius >= 3 ? tz - lateral : tz,
                    false);
            balcony(tx + out.getStepX() * radius, tz + out.getStepZ() * radius, out, y0 + 2, turret);
        });
    }

    /**
     * Un balcon de tir accroche a une facade : une dalle de beton qui deborde de
     * deux cases, des garde-corps sur les cotes, la tourelle de NTM dessus.
     * {@code (fx, fz)} est une case de la facade, {@code out} la direction de
     * l'exterieur ; la dalle est en {@code y}, la tourelle en {@code y + 1}. Rien
     * sans tourelle, ou si la place est prise. Rend vrai si la tourelle est posee.
     */
    private boolean balcony(int fx, int fz, Direction out, int y, String id) {
        if (id == null) {
            return false;
        }
        Direction side = out.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        int coreX = switch (out) {
            case WEST -> fx - 1;
            case EAST -> fx + 2;
            default -> fx;
        };
        int coreZ = switch (out) {
            case NORTH -> fz - 1;
            case SOUTH -> fz + 2;
            default -> fz;
        };
        if (!turret(coreX, y + 1, coreZ, id)) {
            return false;
        }
        for (int depth = 1; depth <= 2; depth++) {
            for (int across = -2; across <= 1; across++) {
                int x = fx + out.getStepX() * depth + side.getStepX() * across;
                int z = fz + out.getStepZ() * depth + side.getStepZ() * across;
                set(x, y, z, accent());
                if (across == -2 || across == 1) {
                    shape(x, y + 1, z, railing());
                }
            }
        }
        return true;
    }

    /**
     * La porterie, au nord : deux miradors, entre eux une porte blindee de NTM
     * (une herse sans le mod) devant le mur plein, un linteau, un seuil raye, un
     * pont sur le fosse ; derriere le mur, un bastion qui coupe l'allee.
     */
    private void gate(int r, int moatOuter, int height) {
        String turret = grade == 1 ? null : grade == 2 ? "turret_friendly" : "turret_chekhov";
        int face = r + 2;
        for (int side = -1; side <= 1; side += 2) {
            tower(side * 6, -r, 2, height + 4, turret, Direction.NORTH);
        }
        steps.add(() -> {
            for (int x = -3; x <= 3; x++) {
                for (int y = y0; y <= y0 + height; y++) {
                    set(x, y, -(r + 1), skin());
                }
            }
            if (!NtmDefenses.multiblock(level, at(0, y0, -face), "sliding_blast_door", Direction.NORTH)) {
                for (int x = -3; x <= 3; x++) {
                    for (int y = y0; y <= y0 + 3; y++) {
                        shape(x, y, -face, Blocks.IRON_BARS.defaultBlockState());
                    }
                }
            }
            for (int x = -3; x <= 3; x++) {
                for (int y = y0 + 4; y <= y0 + height + 1; y++) {
                    set(x, y, -face, y == y0 + 4 ? band(x, y, -face) : skin());
                }
                set(x, y0 - 1, -(face + 1), hazard(x, face));
            }
            for (int z = face + 2; z <= moatOuter + 1; z++) {
                for (int x = -2; x <= 2; x++) {
                    set(x, y0 - 1, -z, road(x, -z));
                }
                set(-3, y0 - 1, -z, accent());
                set(3, y0 - 1, -z, accent());
                shape(-3, y0, -z, railing());
                shape(3, y0, -z, railing());
            }
            // Le bastion : un mur de deux couches en travers de l'allee, du barbele devant.
            int behind = r - outer.size() - 4;
            for (int x = -3; x <= 3; x++) {
                for (int y = y0; y <= y0 + 4; y++) {
                    set(x, y, -behind, y == y0 + 4 ? band(x, y, -behind) : skin());
                    set(x, y, -behind + 1, hardened());
                }
                wire(x, y0, -behind - 1, Direction.Axis.X, false);
            }
            guard(EntityTypes.VINDICATOR, -1, y0, -behind + 3, false);
            guard(EntityTypes.VINDICATOR, 1, y0, -behind + 3, false);
        });
    }

    /**
     * Le champ de mines du glacis, avec NTM : des mines posees sur le terrain
     * naturel, en couronne autour de l'emprise, armees. La route de la porterie
     * reste libre. Antipersonnel ; plus des mines a eclats a partir de la base,
     * des mines antichar a la forteresse. Le terrain decide : une plaine en prend
     * autant qu'il en faut, une foret dense (troncs, champignons geants) moins.
     */
    private void minefield() {
        if (NtmDefenses.block("mine_ap") == null) {
            return;
        }
        int from = tier.half() + 3;
        int to = Math.min(from + 10 + 4 * grade, tier.claimRadius() * 16 + 4);
        int wanted = grade == 1 ? 40 : grade == 2 ? 90 : 160;
        String[] kinds = grade == 1 ? new String[]{"mine_ap"}
                : grade == 2 ? new String[]{"mine_ap", "mine_ap", "mine_shrap"}
                : new String[]{"mine_ap", "mine_shrap", "mine_shrap", "mine_he"};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < wanted * 6 && mines < wanted; attempt++) {
            int x = random.nextInt(2 * to + 1) - to;
            int z = random.nextInt(2 * to + 1) - to;
            int ring = Math.max(Math.abs(x), Math.abs(z));
            if (ring < from || ring > to || z < 0 && Math.abs(x) <= 3) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + x, cz + z);
            if (y < y0 - 40 || y > y0 + 40) {
                continue;
            }
            pos.set(cx + x, y - 1, cz + z);
            BlockState ground = level.getBlockState(pos);
            if (!naturalGround(ground) || !ground.isFaceSturdy(level, pos, Direction.UP)) {
                continue;
            }
            pos.set(cx + x, y, cz + z);
            BlockState here = level.getBlockState(pos);
            // L'herbe, les fleurs, les buissons, la litiere de feuilles cedent la place ; pas les feuillages.
            boolean free = here.isAir() || here.canBeReplaced()
                    || here.is(BlockTags.REPLACEABLE_BY_TREES) && !here.is(BlockTags.LEAVES);
            if (!free || !here.getFluidState().isEmpty()) {
                continue;
            }
            if (here.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                // Pas de haut d'herbe haute flottant au-dessus de la mine.
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (NtmDefenses.mine(level, pos.immutable(), kinds[random.nextInt(kinds.length)])) {
                mines++;
            }
        }
    }

    /**
     * Le sol naturel du glacis. En 26.2, le tag {@code minecraft:dirt} ne compte plus
     * que la terre, la terre sterile et la terre racineuse : l'herbe, le podzol, le
     * mycelium, la mousse et la boue sont nommes un par un.
     */
    private static boolean naturalGround(BlockState ground) {
        return ground.is(BlockTags.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL)
                || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.MOSS_BLOCK) || ground.is(Blocks.MUD)
                || ground.is(Blocks.CLAY) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL)
                || ground.is(Blocks.SANDSTONE) || ground.is(Blocks.RED_SANDSTONE)
                || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(BlockTags.TERRACOTTA)
                || ground.is(Blocks.SNOW_BLOCK);
    }

    // -- Les quartiers ---------------------------------------------------------------

    /**
     * Les modules du genre, dans l'ordre ou ils sont distribues aux terrains tires
     * au hasard (7, 12 ou 16 terrains : la liste reprend au debut). Chaque genre a
     * ses nids de mitrailleuse (les casemates, au bunker) : ce sont eux qui tiennent
     * l'anneau des quartiers sous leur feu.
     */
    private List<String> modules() {
        return switch (style) {
            case SILO -> List.of("launch", "fuel", "nest", "arsenal", "barracks", "launch", "control", "armory",
                    "generator");
            case BUNKER -> List.of("pillbox", "trench", "arsenal", "barracks", "pillbox", "armory", "generator",
                    "depot");
            case SOVIET -> List.of("mirador", "barracks", "nest", "garage", "arsenal", "parade", "mirador", "armory",
                    "depot");
            case RADAR -> List.of("radome", "antenna", "nest", "dish", "arsenal", "barracks", "radome", "control",
                    "helipad", "generator", "depot");
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
            case "launch" -> launch(lot);
            case "fuel" -> fuel(lot);
            case "control" -> control(lot);
            case "pillbox" -> pillbox(lot);
            case "nest" -> nest(lot);
            case "trench" -> trench(lot);
            case "mirador" -> mirador(lot);
            case "garage" -> garage(lot);
            case "parade" -> parade(lot);
            case "radome" -> radome(lot);
            case "antenna" -> antenna(lot);
            case "dish" -> dish(lot);
            case "helipad" -> helipad(lot);
            case "armory" -> armory(lot);
            case "arsenal" -> arsenal(lot);
            case "generator" -> generator(lot);
            case "depot" -> depot(lot);
            default -> barracks(lot);
        }
    }

    /**
     * Un batiment de beton sur le terrain, en retrait d'un bloc : murs, toit
     * plat, une porte du cote du centre ; avec {@code terrace}, un acrotere et
     * une antenne sur le toit. Rend l'interieur : {@code x0, z0, x1, z1}.
     */
    private int[] shell(Lot lot, int height, Supplier<BlockState> walls, Supplier<BlockState> roof, BlockState window,
                        boolean terrace) {
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
                if (terrace && (edgeX || edgeZ)) {
                    set(x, y0 + height + 1, z, accent());
                }
            }
        }
        if (terrace) {
            set(x0 + 1, y0 + height + 1, z1 - 1, rod(Direction.UP));
            set(x0 + 1, y0 + height + 2, z1 - 1, rod(Direction.UP));
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

    /** Le terrain en retrait d'un bloc : {@code x0, z0, x1, z1}. */
    private static int[] inset(Lot lot) {
        return new int[]{lot.x0() + 1, lot.z0() + 1, lot.x1() - 1, lot.z1() - 1};
    }

    /** Les cases le long du mur du fond (a l'oppose de la porte), a l'interieur. */
    private List<int[]> backRow(int[] in, Lot lot) {
        return row(in, lot, true);
    }

    /** Les cases d'une rangee interieure : le long du mur du fond, ou du mur de la porte. */
    private List<int[]> row(int[] in, Lot lot, boolean back) {
        List<int[]> row = new ArrayList<>();
        Direction dir = lot.inward();
        if (dir.getAxis() == Direction.Axis.X) {
            int x = dir.getStepX() > 0 == back ? in[0] : in[2];
            for (int z = in[1]; z <= in[3]; z++) {
                row.add(new int[]{x, z});
            }
        } else {
            int z = dir.getStepZ() > 0 == back ? in[1] : in[3];
            for (int x = in[0]; x <= in[2]; x++) {
                row.add(new int[]{x, z});
            }
        }
        return row;
    }

    /** Un grillage sur le pourtour du terrain, ouvert sur trois cases du cote de la cour. */
    private void fence(Lot lot) {
        int[] door = door(lot);
        Direction in = lot.inward();
        for (int x = lot.x0(); x <= lot.x1(); x++) {
            for (int z = lot.z0(); z <= lot.z1(); z++) {
                if (x != lot.x0() && x != lot.x1() && z != lot.z0() && z != lot.z1()) {
                    continue;
                }
                boolean gate = in.getAxis() == Direction.Axis.X
                        ? x == (in.getStepX() > 0 ? lot.x1() : lot.x0()) && Math.abs(z - door[1]) <= 1
                        : z == (in.getStepZ() > 0 ? lot.z1() : lot.z0()) && Math.abs(x - door[0]) <= 1;
                if (!gate) {
                    shape(x, y0, z, railing());
                }
            }
        }
    }

    // -- Les quartiers communs -------------------------------------------------------

    /** Une caserne : une halle de beton, des lits et des casiers au fond, un spawner au milieu, un garde. */
    private void barracks(Lot lot) {
        Supplier<BlockState> walls = grade >= 3 ? () -> concrete(DyeColor.GRAY) : this::skin;
        int[] in = shell(lot, 4, walls, this::accent, window(), true);
        List<int[]> back = backRow(in, lot);
        Direction toBack = lot.inward().getOpposite();
        for (int i = 0; i < back.size(); i++) {
            int[] cell = back.get(i);
            if (i % 3 == 1) {
                bed(cell[0], cell[1], toBack);
            } else if (i % 3 == 0) {
                barrel(cell[0], y0, cell[1], lot.inward(), random.nextInt(3) == 0 ? "chests/enemy_vrac" : null);
            }
        }
        EntityType<?> mob = switch (random.nextInt(3)) {
            case 0 -> grade >= 3 ? EntityTypes.WITHER_SKELETON : EntityTypes.HUSK;
            case 1 -> grade >= 3 ? EntityTypes.CAVE_SPIDER : EntityTypes.SPIDER;
            default -> EntityTypes.HUSK;
        };
        BlockPos pos = at((in[0] + in[2]) / 2, y0, (in[1] + in[3]) / 2);
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), FLAGS);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(mob, random);
        }
        int[] door = door(lot);
        guard(EntityTypes.VINDICATOR, door[0] + lot.inward().getStepX() * 2, y0, door[1] + lot.inward().getStepZ() * 2,
                false);
    }

    /**
     * Une armurerie : des coffres au fond, des caisses de munitions sur palettes
     * au milieu — obus et cartouches de NTM, sinon fleches et fusees —, une
     * enclume, des caisses d'acier, un garde.
     */
    private void armory(Lot lot) {
        int[] in = shell(lot, 4, this::skin, this::accent, window(), true);
        List<int[]> back = backRow(in, lot);
        int chests = 0;
        for (int i = 1; i < back.size() - 1 && chests < 2 + grade; i += 2) {
            chest(back.get(i)[0], y0, back.get(i)[1], lot.inward(), "chests/enemy_arsenal");
            chests++;
        }
        int mx = (in[0] + in[2]) / 2;
        int mz = (in[1] + in[3]) / 2;
        boolean alongX = in[2] - in[0] >= in[3] - in[1];
        pallet(mx, y0, mz, lot.inward(), ROUNDS, Items.ARROW, 4 + 2 * grade);
        pallet(mx + (alongX ? 1 : 0), y0, mz + (alongX ? 0 : 1), lot.inward(), SHELLS, Items.FIREWORK_ROCKET,
                2 + grade);
        set(in[0], y0, in[1], anvil());
        set(in[2], y0, in[3], Blocks.SMITHING_TABLE.defaultBlockState());
        BlockState crate = NtmDefenses.state("crate_steel", Blocks.BARREL.defaultBlockState());
        set(in[2], y0, in[1], crate);
        set(in[2], y0 + 1, in[1], crate);
        set(in[0], y0, in[3], crate);
        int[] door = door(lot);
        guard(EntityTypes.VINDICATOR, door[0] + lot.inward().getStepX() * 2, y0, door[1] + lot.inward().getStepZ() * 2,
                false);
    }

    /**
     * Un depot de munitions enterre : un bunker d'obsidienne sous un toit de
     * terre, des piles de TNT au milieu. Le percer a l'explosif se paie.
     */
    private void arsenal(Lot lot) {
        Supplier<BlockState> shellBlock = grade >= 2 ? this::hardened : () -> Blocks.OBSIDIAN.defaultBlockState();
        int[] in = shell(lot, 4, shellBlock, shellBlock, null, false);
        for (int x = lot.x0() + 1; x <= lot.x1() - 1; x++) {
            for (int z = lot.z0() + 1; z <= lot.z1() - 1; z++) {
                set(x, y0 + 5, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
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
        int[] door = door(lot);
        set(door[0], y0 - 1, door[1], hazard(door[0], door[1]));
        set(door[0] + lot.inward().getStepX(), y0 - 1, door[1] + lot.inward().getStepZ(),
                hazard(door[0] + 1, door[1]));
    }

    /**
     * Un entrepot : des rangees de conteneurs sur deux niveaux. La plupart tiennent
     * du vrac ; quelques coffres — dont des coffres de PalaMod — ont le butin de
     * l'entrepot ; des caisses d'acier de NTM, vides, completent les rangs.
     */
    private void depot(Lot lot) {
        Supplier<BlockState> walls = grade >= 2 ? () -> concrete(DyeColor.GRAY) : this::skin;
        int[] in = shell(lot, 6, walls, this::accent, null, true);
        boolean alongX = in[2] - in[0] >= in[3] - in[1];
        String fancy = grade >= 3 ? "paladium_chest" : grade == 2 ? "titane_chest" : "amethyst_chest";
        int rich = 3 + grade;
        int[] door = door(lot);
        BlockState crate = NtmDefenses.state("crate_steel", null);
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
                        BlockPos pos = at(x, y0, z);
                        level.setBlock(pos, facing(state(fancy, Blocks.CHEST), face), FLAGS);
                        loot(pos, "chests/enemy_entrepot");
                    } else {
                        chest(x, y0, z, face, "chests/enemy_entrepot");
                    }
                } else if (crate != null && random.nextInt(4) == 0) {
                    set(x, y0, z, crate);
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
     * Un groupe electrogene : des moteurs (bloc de fer, haut fourneau) sur un sol
     * raye, leurs echappements qui traversent le toit et fument, des futs de
     * carburant, un coffre d'atelier.
     */
    private void generator(Lot lot) {
        int[] in = shell(lot, 5, this::skin, this::accent, window(), true);
        List<int[]> back = backRow(in, lot);
        for (int i = 0; i + 1 < back.size(); i += 3) {
            int x = back.get(i)[0];
            int z = back.get(i)[1];
            int ex = back.get(i + 1)[0];
            int ez = back.get(i + 1)[1];
            set(x, y0 - 1, z, hazard(x, z));
            set(ex, y0 - 1, ez, hazard(ex, ez));
            set(x, y0, z, Blocks.IRON_BLOCK.defaultBlockState());
            machine(ex, y0, ez, "", Blocks.BLAST_FURNACE, lot.inward());
            for (int y = y0 + 1; y <= y0 + 7; y++) {
                set(x, y, z, Blocks.POLISHED_BASALT.defaultBlockState());
            }
            set(x, y0 + 8, z, Blocks.CAMPFIRE.defaultBlockState());
        }
        List<int[]> front = row(in, lot, false);
        BlockState drum = NtmDefenses.state("barrel_steel", Blocks.BARREL.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        int[] first = front.get(0);
        int[] last = front.get(front.size() - 1);
        set(first[0], y0, first[1], drum);
        set(first[0], y0 + 1, first[1], drum);
        set(last[0], y0, last[1], drum);
        chest(front.get(1)[0], y0, front.get(1)[1], lot.inward().getOpposite(), "chests/enemy_atelier");
        guard(EntityTypes.PILLAGER, (in[0] + in[2]) / 2, y0, (in[1] + in[3]) / 2, false);
    }

    // -- Le silo de missiles ----------------------------------------------------------

    /**
     * Un puits de lancement : un fut de beton creuse dans le sol, eclaire de
     * lanternes marines ; au fond, un missile — fut blanc a bandes noires,
     * ailerons, coiffe rouge — ; au ras du sol, la trappe de silo de NTM sur son
     * bord raye (une grille de trappes de fer sans le mod).
     */
    private void launch(Lot lot) {
        int mx = lot.mx();
        int mz = lot.mz();
        int depth = 14 + 2 * grade;
        int bottom = y0 - depth - 1;
        for (int y = bottom; y <= y0 - 1; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean edge = Math.max(Math.abs(x), Math.abs(z)) == 3;
                    BlockState state = edge || y == bottom ? skin() : Blocks.AIR.defaultBlockState();
                    if (edge && y < y0 - 1 && (y - bottom) % 4 == 2 && (x == 0 || z == 0)) {
                        state = Blocks.SEA_LANTERN.defaultBlockState();
                    }
                    set(mx + x, y, mz + z, state);
                }
            }
        }
        int nose = y0 - 4;
        for (int y = bottom + 1; y <= nose; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    set(mx + x, y, mz + z, (y - bottom) % 5 == 0 ? concrete(DyeColor.BLACK) : concrete(DyeColor.WHITE));
                }
            }
        }
        for (int y = bottom + 1; y <= bottom + 3; y++) {
            for (Direction fin : Direction.Plane.HORIZONTAL) {
                set(mx + 2 * fin.getStepX(), y, mz + 2 * fin.getStepZ(), concrete(DyeColor.BLACK));
            }
        }
        set(mx, nose + 1, mz, concrete(DyeColor.WHITE));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            set(mx + side.getStepX(), nose + 1, mz + side.getStepZ(), concrete(DyeColor.WHITE));
        }
        set(mx, nose + 2, mz, concrete(DyeColor.RED));
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) >= 3) {
                    set(mx + x, y0 - 1, mz + z, hazard(mx + x, mz + z));
                }
            }
        }
        if (!NtmDefenses.multiblock(level, at(mx, y0 - 1, mz), "silo_hatch", Direction.SOUTH)) {
            BlockState hatch = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    set(mx + x, y0 - 1, mz + z, hatch);
                }
            }
        }
        post(mx - 4, mz - 4);
        post(mx + 4, mz + 4);
    }

    /**
     * Un depot de carburant : des cuves blanches a bande de danger, leurs
     * echelles et leurs events, des canalisations entre elles, des futs, un
     * grillage autour.
     */
    private void fuel(Lot lot) {
        int[] a = inset(lot);
        boolean alongX = lot.alongX();
        int start = alongX ? a[0] : a[1];
        int length = alongX ? a[2] - a[0] + 1 : a[3] - a[1] + 1;
        int count = Math.max(1, Math.min(3, (length - 1) / 6));
        int h = 6 + grade;
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int along = start + (2 * i + 1) * length / (2 * count);
            int tx = alongX ? along : lot.mx();
            int tz = alongX ? lot.mz() : along;
            tank(tx, tz, h, alongX ? Direction.SOUTH : Direction.EAST);
            if (previous != Integer.MIN_VALUE) {
                Direction pipe = alongX ? Direction.EAST : Direction.SOUTH;
                for (int k = previous + 3; k <= along - 3; k++) {
                    set(alongX ? k : tx, y0 + 2, alongX ? tz : k, rod(pipe));
                }
            }
            previous = along;
        }
        BlockState drum = NtmDefenses.state("barrel_steel", Blocks.BARREL.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        int dx = alongX ? a[2] : a[0];
        int dz = alongX ? a[1] : a[3];
        set(dx, y0, dz, drum);
        set(dx, y0 + 1, dz, drum);
        fence(lot);
        guard(EntityTypes.PILLAGER, lot.mx() + lot.inward().getStepX() * 3, y0, lot.mz() + lot.inward().getStepZ() * 3,
                false);
    }

    /** Une cuve : un cylindre plein de beton blanc, une bande rayee, une echelle du cote {@code ladder}, un event. */
    private void tank(int tx, int tz, int h, Direction ladder) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                    continue;
                }
                set(tx + x, y0 - 1, tz + z, accent());
                for (int y = y0; y <= y0 + h; y++) {
                    set(tx + x, y, tz + z, y == y0 + h / 2 ? hazard(tx + x + y, tz + z) : concrete(DyeColor.WHITE));
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(tx + x, y0 + h + 1, tz + z, concrete(DyeColor.WHITE));
            }
        }
        set(tx, y0 + h + 2, tz, rod(Direction.UP));
        BlockState rungs = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, ladder);
        for (int y = y0; y <= y0 + h; y++) {
            set(tx + ladder.getStepX() * 3, y, tz + ladder.getStepZ() * 3, rungs);
        }
    }

    /**
     * Un poste de controle : des pupitres sombres au fond, les ecrans radar de
     * NTM dessus, une table des cartes, des classeurs, un coffre, un garde.
     */
    private void control(Lot lot) {
        int[] in = shell(lot, 4, this::skin, this::accent, window(), true);
        Direction face = lot.inward();
        for (int[] cell : backRow(in, lot)) {
            set(cell[0], y0, cell[1], Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(cell[0], y0 + 1, cell[1], screen(face));
        }
        int mx = (in[0] + in[2]) / 2;
        int mz = (in[1] + in[3]) / 2;
        set(mx, y0, mz, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        List<int[]> front = row(in, lot, false);
        BlockState cabinet = NtmDefenses.facing(NtmDefenses.state("filing_cabinet_steel",
                Blocks.BOOKSHELF.defaultBlockState()), face.getOpposite());
        set(front.get(0)[0], y0, front.get(0)[1], cabinet);
        set(front.get(front.size() - 1)[0], y0, front.get(front.size() - 1)[1], cabinet);
        chest(front.get(1)[0], y0, front.get(1)[1], face.getOpposite(), "chests/enemy_atelier");
        guard(EntityTypes.VINDICATOR, mx + face.getStepZ(), y0, mz + face.getStepX(), false);
    }

    /**
     * Un nid de mitrailleuse : la tourelle de NTM au ras du sol, sur une dalle a
     * bord raye, derriere un muret de sacs de sable ouvert du cote de la cour ;
     * une palette de cartouches, un servant, un lampadaire. Sans NTM, deux
     * tireurs. Au ras du sol, la tourelle voit tout l'anneau des quartiers.
     */
    private void nest(Lot lot) {
        int mx = lot.mx();
        int mz = lot.mz();
        Direction in = lot.inward();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                set(mx + x, y0 - 1, mz + z, ring == 3 ? hazard(mx + x, mz + z) : accent());
                boolean opening = in.getAxis() == Direction.Axis.X
                        ? x == in.getStepX() * 2 && Math.abs(z) <= 1
                        : z == in.getStepZ() * 2 && Math.abs(x) <= 1;
                if (ring == 2 && !opening) {
                    shape(mx + x, y0, mz + z, sandbag());
                }
            }
        }
        String id = grade == 1 ? "turret_friendly" : "turret_chekhov";
        boolean armed = turret(mx + 1, y0, mz + 1, id);
        pallet(mx - 1, y0, mz - 1, in, ROUNDS, Items.ARROW, 3 + grade);
        guard(EntityTypes.PILLAGER, mx - 1, y0, mz + 1, false);
        if (!armed) {
            guard(EntityTypes.PILLAGER, mx + 1, y0, mz, false);
        }
        post(lot.x0() + 1, lot.z0() + 1);
    }

    // -- Le bunker --------------------------------------------------------------------

    /**
     * Une casemate : un blockhaus bas de beton a meurtrieres dans un talus de
     * terre, l'entree a l'arriere ; un toit borde de sacs de sable, la tourelle
     * de NTM dessus (un tireur sans le mod) ; dedans, le servant et ses
     * munitions.
     */
    private void pillbox(Lot lot) {
        int mx = lot.mx();
        int mz = lot.mz();
        Direction in = lot.inward();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                if (ring == 4) {
                    set(mx + x, y0, mz + z, Blocks.GRASS_BLOCK.defaultBlockState());
                    continue;
                }
                boolean edge = ring == 3;
                boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
                for (int y = y0; y <= y0 + 3; y++) {
                    BlockState state;
                    if (y == y0 + 3) {
                        state = skin();
                    } else if (edge) {
                        state = y == y0 + 1 && !corner && Math.floorMod(x + z, 2) == 0
                                ? Blocks.TINTED_GLASS.defaultBlockState() : skin();
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(mx + x, y, mz + z, state);
                }
                if (edge) {
                    // Au niveau du toit, pas au-dessus : un muret plus haut boucherait la vue de la tourelle.
                    shape(mx + x, y0 + 3, mz + z, sandbag());
                }
            }
        }
        for (int step = 3; step <= 4; step++) {
            set(mx + in.getStepX() * step, y0, mz + in.getStepZ() * step, Blocks.AIR.defaultBlockState());
            set(mx + in.getStepX() * step, y0 + 1, mz + in.getStepZ() * step, Blocks.AIR.defaultBlockState());
        }
        String id = grade == 1 ? "turret_friendly" : "turret_chekhov";
        if (!turret(mx, y0 + 4, mz, id)) {
            guard(EntityTypes.PILLAGER, mx, y0 + 4, mz, false);
        }
        chest(mx - in.getStepX() * 2, y0, mz - in.getStepZ() * 2, in, "chests/enemy_arsenal");
        lantern(mx, y0 + 2, mz);
        guard(EntityTypes.PILLAGER, mx + in.getStepZ(), y0, mz + in.getStepX(), false);
    }

    /**
     * Une tranchee le long du terrain : deux blocs de profondeur, des parois et
     * un caillebotis de sapin, des sacs de sable sur les bords, une sape couverte
     * au bout ; devant, cote mur, un rang de barbele.
     */
    private void trench(Lot lot) {
        boolean alongX = lot.alongX();
        Direction in = lot.inward();
        int from = (alongX ? lot.x0() : lot.z0()) + 1;
        int to = (alongX ? lot.x1() : lot.z1()) - 1;
        int c0 = alongX ? lot.mz() : lot.mx();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int along = from; along <= to; along++) {
            for (int k = -1; k <= 2; k++) {
                int x = alongX ? along : c0 + k;
                int z = alongX ? c0 + k : along;
                boolean inside = (k == 0 || k == 1) && along != from && along != to;
                if (inside) {
                    set(x, y0 - 3, z, planks);
                    set(x, y0 - 2, z, Blocks.AIR.defaultBlockState());
                    set(x, y0 - 1, z, Blocks.AIR.defaultBlockState());
                    if (along >= to - 3) {
                        set(x, y0, z, planks);
                    }
                } else {
                    set(x, y0 - 2, z, planks);
                    set(x, y0 - 1, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                    if (k == -1 || k == 2) {
                        shape(x, y0, z, sandbag());
                    }
                }
            }
        }
        // La sape : couverte de planches et de sacs, un coffre et une lampe.
        for (int along = to - 3; along <= to - 1; along++) {
            for (int k = 0; k <= 1; k++) {
                shape(alongX ? along : c0 + k, y0 + 1, alongX ? c0 + k : along, sandbag());
            }
        }
        Direction toEnd = alongX ? Direction.WEST : Direction.NORTH;
        chest(alongX ? to - 1 : c0, y0 - 2, alongX ? c0 : to - 1, toEnd, "chests/enemy_vrac");
        lantern(alongX ? to - 2 : c0 + 1, y0 - 1, alongX ? c0 + 1 : to - 2);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                alongX ? Direction.EAST : Direction.SOUTH);
        for (int y = y0 - 2; y <= y0 - 1; y++) {
            set(alongX ? from + 1 : c0, y, alongX ? c0 : from + 1, ladder);
        }
        // Le barbele, du cote oppose a la cour.
        int outward = alongX ? -in.getStepZ() : -in.getStepX();
        int wireAt = outward > 0 ? c0 + 4 : c0 - 3;
        for (int along = from; along <= to; along++) {
            wire(alongX ? along : wireAt, y0, alongX ? wireAt : along, alongX ? Direction.Axis.X : Direction.Axis.Z,
                    grade == 3);
        }
        int middle = (from + to) / 2;
        guard(EntityTypes.VINDICATOR, alongX ? middle : c0, y0 - 2, alongX ? c0 : middle, false);
    }

    // -- La forteresse sovietique -------------------------------------------------------

    /**
     * Un mirador de bois dans le coin du terrain : quatre pieds de chene noir,
     * un poteau d'echelle, une cabine de sapin a mi-hauteur de murs sous un toit,
     * un projecteur tourne vers le mur, une sentinelle.
     */
    private void mirador(Lot lot) {
        int[] a = inset(lot);
        Direction in = lot.inward();
        Direction up = in.getOpposite();
        int bx = up.getStepX() > 0 ? a[2] - 2 : up.getStepX() < 0 ? a[0] + 2 : (random.nextBoolean() ? a[0] + 2 : a[2] - 2);
        int bz = up.getStepZ() > 0 ? a[3] - 2 : up.getStepZ() < 0 ? a[1] + 2 : (random.nextBoolean() ? a[1] + 2 : a[3] - 2);
        int h = 6 + grade;
        BlockState leg = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int y = y0; y < y0 + h; y++) {
            for (int x = -1; x <= 1; x += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    set(bx + x, y, bz + z, leg);
                }
            }
            set(bx, y, bz, planks);
            set(bx + in.getStepX(), y, bz + in.getStepZ(), Blocks.LADDER.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, in));
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean edge = Math.max(Math.abs(x), Math.abs(z)) == 2;
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                set(bx + x, y0 + h, bz + z, planks);
                if (corner) {
                    set(bx + x, y0 + h + 1, bz + z, leg);
                    set(bx + x, y0 + h + 2, bz + z, leg);
                } else if (edge) {
                    set(bx + x, y0 + h + 1, bz + z, planks);
                }
                set(bx + x, y0 + h + 3, bz + z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        set(bx + in.getStepX(), y0 + h, bz + in.getStepZ(), Blocks.LADDER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, in));
        Direction side = up.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        floodlight(bx + up.getStepX() * 2, y0 + h + 1, bz + up.getStepZ() * 2, up, side);
        lantern(bx - side.getStepX(), y0 + h + 2, bz - side.getStepZ());
        guard(EntityTypes.PILLAGER, bx + side.getStepX(), y0 + h + 1, bz + side.getStepZ(), false);
    }

    /**
     * Un garage de blindes : un hangar de beton grand ouvert sur la cour, un sol
     * tache d'huile, un char d'assaut en blocs — chenilles, caisse et tourelle
     * vert armee, canon, etoile rouge — et l'atelier au fond : palaforge,
     * enclume, futs, coffre.
     */
    private void garage(Lot lot) {
        int[] in = shell(lot, 6, this::skin, this::accent, null, true);
        Direction forward = lot.inward();
        int bx0 = lot.x0() + 1;
        int bx1 = lot.x1() - 1;
        int bz0 = lot.z0() + 1;
        int bz1 = lot.z1() - 1;
        for (int y = y0; y <= y0 + 3; y++) {
            if (forward.getAxis() == Direction.Axis.X) {
                int x = forward.getStepX() > 0 ? bx1 : bx0;
                for (int z = bz0 + 1; z <= bz1 - 1; z++) {
                    set(x, y, z, Blocks.AIR.defaultBlockState());
                }
            } else {
                int z = forward.getStepZ() > 0 ? bz1 : bz0;
                for (int x = bx0 + 1; x <= bx1 - 1; x++) {
                    set(x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = in[0]; x <= in[2]; x++) {
            for (int z = in[1]; z <= in[3]; z++) {
                set(x, y0 - 1, z, random.nextInt(10) == 0 ? concrete(DyeColor.BLACK) : concrete(DyeColor.GRAY));
            }
        }
        vehicle(in, lot);
        List<int[]> back = backRow(in, lot);
        machine(back.get(0)[0], y0, back.get(0)[1], "palaforge", Blocks.BLAST_FURNACE, forward);
        set(back.get(1)[0], y0, back.get(1)[1], anvil());
        int[] end = back.get(back.size() - 1);
        chest(end[0], y0, end[1], forward, "chests/enemy_atelier");
        BlockState drum = NtmDefenses.state("barrel_steel", Blocks.BARREL.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        int[] before = back.get(back.size() - 2);
        set(before[0], y0, before[1], drum);
        List<int[]> front = row(in, lot, false);
        int[] corner = front.get(front.size() - 1);
        guard(EntityTypes.VINDICATOR, corner[0], y0, corner[1], false);
    }

    /**
     * Le char, juste devant l'atelier : chenilles noires, caisse et tourelle vert
     * armee, etoile rouge, trappe, canon tourne vers la cour.
     */
    private void vehicle(int[] in, Lot lot) {
        Direction forward = lot.inward();
        Direction side = forward.getClockWise();
        boolean alongX = forward.getAxis() == Direction.Axis.X;
        int depth = alongX ? in[2] - in[0] + 1 : in[3] - in[1] + 1;
        int length = Math.min(7, depth - 1);
        int[] rear = backRow(in, lot).get(0);
        int ox = alongX ? rear[0] + forward.getStepX() : (in[0] + in[2]) / 2;
        int oz = alongX ? (in[1] + in[3]) / 2 : rear[1] + forward.getStepZ();
        BlockState green = terracotta(DyeColor.GREEN);
        for (int along = 0; along < length; along++) {
            for (int across = -2; across <= 2; across++) {
                int x = ox + forward.getStepX() * along + side.getStepX() * across;
                int z = oz + forward.getStepZ() * along + side.getStepZ() * across;
                boolean track = Math.abs(across) == 2;
                set(x, y0, z, track ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : green);
                set(x, y0 + 1, z, green);
            }
        }
        for (int along = 1; along <= 3; along++) {
            for (int across = -1; across <= 1; across++) {
                int x = ox + forward.getStepX() * along + side.getStepX() * across;
                int z = oz + forward.getStepZ() * along + side.getStepZ() * across;
                boolean star = Math.abs(across) == 1 && along == 2;
                set(x, y0 + 2, z, star ? concrete(DyeColor.RED) : green);
            }
        }
        set(ox + forward.getStepX() * 2, y0 + 3, oz + forward.getStepZ() * 2, Blocks.IRON_TRAPDOOR.defaultBlockState());
        for (int along = 4; along <= length; along++) {
            set(ox + forward.getStepX() * along, y0 + 2, oz + forward.getStepZ() * along, rod(forward));
        }
    }

    /**
     * Une place d'armes : un dallage de beton clair, une grande etoile rouge au
     * sol, deux mats a drapeau rouge, et pour les grands terrains une tribune
     * tapissee de rouge ou se tiennent deux gardes.
     */
    private void parade(Lot lot) {
        int[] a = inset(lot);
        for (int x = a[0]; x <= a[2]; x++) {
            for (int z = a[1]; z <= a[3]; z++) {
                boolean edge = x == a[0] || x == a[2] || z == a[1] || z == a[3];
                set(x, y0 - 1, z, edge ? concrete(DyeColor.GRAY) : concrete(DyeColor.LIGHT_GRAY));
            }
        }
        int shortSide = Math.min(a[2] - a[0], a[3] - a[1]) + 1;
        boolean stand = shortSide >= 11;
        int size = odd(Math.min(15, shortSide - (stand ? 4 : 1)));
        boolean[][] star = star(size);
        int c = size / 2;
        Direction forward = lot.inward();
        Direction up = forward.getOpposite();
        Direction side = forward.getClockWise();
        int sx = lot.mx() + (stand ? forward.getStepX() : 0);
        int sz = lot.mz() + (stand ? forward.getStepZ() : 0);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (star[row][col]) {
                    int x = sx + up.getStepX() * (c - row) + side.getStepX() * (col - c);
                    int z = sz + up.getStepZ() * (c - row) + side.getStepZ() * (col - c);
                    set(x, y0 - 1, z, concrete(DyeColor.RED));
                }
            }
        }
        List<int[]> back = backRow(new int[]{a[0], a[1], a[2], a[3]}, lot);
        for (int[] corner : List.of(back.get(0), back.get(back.size() - 1))) {
            for (int y = y0; y <= y0 + 3; y++) {
                set(corner[0], y, corner[1], Blocks.POLISHED_BASALT.defaultBlockState());
            }
            set(corner[0], y0 + 4, corner[1], Blocks.BANNER.pick(DyeColor.RED).defaultBlockState());
        }
        if (stand) {
            int[] middle = back.get(back.size() / 2);
            for (int across = -2; across <= 2; across++) {
                for (int depth = 0; depth <= 1; depth++) {
                    int x = middle[0] + side.getStepX() * across + forward.getStepX() * depth;
                    int z = middle[1] + side.getStepZ() * across + forward.getStepZ() * depth;
                    set(x, y0, z, concrete(DyeColor.GRAY));
                    set(x, y0 + 1, z, carpet(DyeColor.RED));
                }
            }
            guard(EntityTypes.VINDICATOR, middle[0] - side.getStepX(), y0 + 1, middle[1] - side.getStepZ(), false);
            guard(EntityTypes.VINDICATOR, middle[0] + side.getStepX(), y0 + 1, middle[1] + side.getStepZ(), false);
        } else {
            guard(EntityTypes.VINDICATOR, lot.mx(), y0, lot.mz(), false);
        }
    }

    // -- La base radar ------------------------------------------------------------------

    /** Un radome : une coupole de panneaux blancs sur un socle, une porte du cote de la cour, un veilleur. */
    private void radome(Lot lot) {
        int[] a = inset(lot);
        int r = Math.min(5, Math.min(a[2] - a[0], a[3] - a[1]) / 2);
        int mx = lot.mx();
        int mz = lot.mz();
        double outerEdge = r + 0.5d;
        double innerEdge = r - 0.5d;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > outerEdge) {
                    continue;
                }
                set(mx + x, y0 - 1, mz + z, accent());
                if (d > innerEdge) {
                    set(mx + x, y0, mz + z, concrete(DyeColor.WHITE));
                    set(mx + x, y0 + 1, mz + z, concrete(DyeColor.WHITE));
                }
            }
        }
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= r; y++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= outerEdge && d > innerEdge) {
                        set(mx + x, y0 + 2 + y, mz + z, Math.floorMod(x + y + z, 3) == 0
                                ? Blocks.CALCITE.defaultBlockState() : concrete(DyeColor.WHITE));
                    }
                }
            }
        }
        Direction in = lot.inward();
        set(mx + in.getStepX() * r, y0, mz + in.getStepZ() * r, Blocks.AIR.defaultBlockState());
        set(mx + in.getStepX() * r, y0 + 1, mz + in.getStepZ() * r, Blocks.AIR.defaultBlockState());
        lantern(mx, y0 + 1 + r, mz);
        guard(EntityTypes.PILLAGER, mx, y0, mz, false);
    }

    /**
     * Un mat d'antennes : un treillis de barreaux, une plate-forme au sommet, un
     * paratonnerre et un feu d'obstacle ; au pied, un abri de beton et un
     * grillage.
     */
    private void antenna(Lot lot) {
        int mx = lot.mx();
        int mz = lot.mz();
        int h = Math.min(18 + 4 * grade, tier.clearHeight() + 12);
        for (int y = y0; y <= y0 + h; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
                    boolean brace = (y - y0) % 4 == 3 && (x != 0 || z != 0);
                    if (corner || brace) {
                        shape(mx + x, y, mz + z, Blocks.IRON_BARS.defaultBlockState());
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(mx + x, y0 + h + 1, mz + z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        for (int y = y0 + h + 2; y <= y0 + h + 4; y++) {
            set(mx, y, mz, rod(Direction.UP));
        }
        set(mx, y0 + h + 5, mz, Blocks.SHROOMLIGHT.defaultBlockState());
        int hx = lot.x1() - 3;
        int hz = lot.z1() - 3;
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = y0; y <= y0 + 3; y++) {
                    boolean edge = x != 1 || z != 1;
                    set(hx + x, y, hz + z, y == y0 + 3 ? accent() : edge ? skin() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        set(hx, y0, hz + 1, Blocks.AIR.defaultBlockState());
        set(hx, y0 + 1, hz + 1, Blocks.AIR.defaultBlockState());
        fence(lot);
        guard(EntityTypes.PILLAGER, mx + 2, y0, mz, false);
    }

    /** Le grand radar de NTM sur son socle raye ; sans NTM, une parabole en blocs. Un grillage autour. */
    private void dish(Lot lot) {
        int mx = lot.mx();
        int mz = lot.mz();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(mx + x, y0 - 1, mz + z, Math.max(Math.abs(x), Math.abs(z)) == 2 ? hazard(x, z) : accent());
            }
        }
        if (!NtmDefenses.multiblock(level, at(mx, y0, mz), "machine_radar_large", Direction.SOUTH)) {
            parabola(mx, y0, mz);
        }
        fence(lot);
        guard(EntityTypes.PILLAGER, mx + lot.inward().getStepX() * 3, y0, mz + lot.inward().getStepZ() * 3, false);
    }

    /** Une parabole : un pylone, une coupe de beton tournee vers le ciel, son cornet. */
    private void parabola(int mx, int y, int mz) {
        for (int dy = 0; dy < 3; dy++) {
            set(mx, y + dy, mz, Blocks.POLISHED_BASALT.defaultBlockState());
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int d2 = x * x + z * z;
                if (d2 <= 10) {
                    set(mx + x, y + 3 + (d2 >= 5 ? 1 : 0) + (d2 >= 9 ? 1 : 0), mz + z, concrete(DyeColor.LIGHT_GRAY));
                }
            }
        }
        set(mx, y + 4, mz, rod(Direction.UP));
        set(mx, y + 5, mz, rod(Direction.UP));
    }

    /** Un heliport : une aire grise, un cercle et un H blancs, des balises lumineuses, une manche a air. */
    private void helipad(Lot lot) {
        int[] a = inset(lot);
        int mx = lot.mx();
        int mz = lot.mz();
        int half = Math.min(a[2] - a[0], a[3] - a[1]) / 2;
        for (int x = a[0]; x <= a[2]; x++) {
            for (int z = a[1]; z <= a[3]; z++) {
                set(x, y0 - 1, z, concrete(DyeColor.GRAY));
            }
        }
        double r = half - 0.5d;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (Math.abs(d - r) < 0.5d) {
                    set(mx + x, y0 - 1, mz + z, concrete(DyeColor.WHITE));
                }
            }
        }
        int arm = Math.max(1, half - 2);
        for (int z = -arm; z <= arm; z++) {
            set(mx - arm, y0 - 1, mz + z, concrete(DyeColor.WHITE));
            set(mx + arm, y0 - 1, mz + z, concrete(DyeColor.WHITE));
        }
        for (int x = -arm; x <= arm; x++) {
            set(mx + x, y0 - 1, mz, concrete(DyeColor.WHITE));
        }
        for (int[] corner : new int[][]{{a[0], a[1]}, {a[2], a[1]}, {a[0], a[3]}, {a[2], a[3]}}) {
            set(corner[0], y0 - 1, corner[1], Blocks.SEA_LANTERN.defaultBlockState());
        }
        for (int y = y0; y <= y0 + 2; y++) {
            set(a[0], y, a[1], Blocks.POLISHED_BASALT.defaultBlockState());
        }
        set(a[0], y0 + 3, a[1], Blocks.BANNER.pick(DyeColor.ORANGE).defaultBlockState());
        guard(EntityTypes.PILLAGER, a[2], y0, a[3] - 1, false);
    }

    // -- La salle forte --------------------------------------------------------------

    private void vault() {
        int depth = vaultLayers.size();
        switch (style) {
            case SILO -> {
                cube(y0 - depth - room, y0 - 1, keep);
                steps.add(this::launchPad);
            }
            case BUNKER -> {
                cube(y0 - depth - room, y0 - 1, keep);
                steps.add(this::commandPost);
            }
            case SOVIET -> {
                int top = y0 + room + depth + 3 + grade;
                cube(y0, top, keep);
                sovietKeep(top);
            }
            case RADAR -> {
                int top = y0 + room + depth;
                cube(y0, top, keep);
                radarStation(top);
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
                            state = vaultLayers.get(d).block().get();
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

    /** Un parement de beton autour du cube de la salle forte, de {@code from} a {@code to}, rangee par rangee. */
    private void facade(int from, int to, Facade facade) {
        int r = keep + 1;
        for (int y = from; y <= to; y++) {
            int row = y;
            steps.add(() -> ringAt(r, row, (x, z) -> facade.at(x, row, z)));
        }
    }

    /** La tourelle de la salle forte : mitrailleuse legere, lourde, MASER a la forteresse. */
    private String vaultTurret() {
        return grade == 1 ? "turret_friendly" : grade == 2 ? "turret_chekhov" : "turret_maxwell";
    }

    /** Des tourelles de la salle forte, leurs coeurs en {@code cores} ; un garde la ou l'une ne passe pas. */
    private void turretsAt(int y, int[][] cores) {
        for (int[] core : cores) {
            if (!turret(core[0], y, core[1], vaultTurret())) {
                guard(EntityTypes.PILLAGER, core[0], y, core[1], false);
            }
        }
    }

    /** Les tourelles du tablier du silo, au ras du sol : deux aux coins opposes, quatre a la forteresse. */
    private void vaultTurrets(int y) {
        int at = keep - 2;
        turretsAt(y, grade == 3 ? corners(at) : new int[][]{{-at, -at}, {at, at}});
    }

    /**
     * Les coeurs des tourelles posees a cheval sur le bord du toit, aux coins
     * (deux, quatre a la forteresse) : au bord, rien ne leur cache le sol.
     *
     * @param outer la tourelle deborde du cube d'une case (sur le parement)
     */
    private int[][] cornerCores(boolean outer) {
        int far = outer ? keep + 1 : keep;
        int near = outer ? -keep : -keep + 1;
        int[][] all = {{far, far}, {near, near}, {far, near}, {near, far}};
        return grade == 3 ? all : new int[][]{all[0], all[1]};
    }

    /** Vrai si la case est sous l'une des tourelles de {@code cores} (une tourelle tient 2 x 2). */
    private static boolean under(int x, int z, int[][] cores) {
        for (int[] core : cores) {
            if (x >= core[0] - 1 && x <= core[0] && z >= core[1] - 1 && z <= core[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Les tourelles d'une salle forte hors sol (donjon, station radar). A la
     * forteresse, quatre MASER aux coins du toit, en surplomb : ils portent a
     * 64 blocs, assez pour voir les quartiers d'en haut. Aux autres tailles, le
     * toit est trop haut pour une mitrailleuse : deux balcons de tir bas, au nord
     * et au sud, a cote de la porte.
     */
    private void keepTurrets(int top) {
        if (grade == 3) {
            turretsAt(top + 2, cornerCores(true));
            return;
        }
        int r = keep + 1;
        int off = keep - 3;
        if (!balcony(-off, -r, Direction.NORTH, y0 + 1, vaultTurret())) {
            guard(EntityTypes.PILLAGER, -off, y0, -r - 2, false);
        }
        if (!balcony(off, r, Direction.SOUTH, y0 + 1, vaultTurret())) {
            guard(EntityTypes.PILLAGER, off, y0, r + 2, false);
        }
    }

    /** L'acrotere du toit, sauf aux coins ou les MASER de la forteresse se posent en surplomb. */
    private boolean parapetGap(int x, int z) {
        return grade == 3 && Math.abs(x) >= keep && Math.abs(z) >= keep;
    }

    /**
     * Le silo : sur la salle forte enterree, un tablier de beton a bordure
     * rayee, la grande trappe de silo de NTM au milieu (une grille de trappes de
     * fer sans le mod), des tourelles aux coins, un periscope, des feux.
     */
    private void launchPad() {
        for (int x = -keep; x <= keep; x++) {
            for (int z = -keep; z <= keep; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                BlockState state = ring == keep || ring == 4 ? hazard(x, z) : accent();
                if (ring == keep - 1 && (x == 0 || z == 0)) {
                    state = Blocks.SEA_LANTERN.defaultBlockState();
                }
                set(x, y0, z, state);
            }
        }
        if (!NtmDefenses.multiblock(level, at(0, y0 + 1, 0), "silo_hatch_large", Direction.SOUTH)) {
            BlockState hatch = Blocks.IRON_TRAPDOOR.defaultBlockState();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    set(x, y0 + 1, z, hatch);
                }
            }
        }
        vaultTurrets(y0 + 1);
        for (int y = y0 + 1; y <= y0 + 5; y++) {
            set(0, y, keep - 2, rod(Direction.UP));
        }
        guard(EntityTypes.PILLAGER, keep - 2, y0 + 1, 0, false);
        guard(EntityTypes.PILLAGER, -(keep - 2), y0 + 1, 0, false);
    }

    /**
     * Le bunker : sur la salle forte enterree, un blockhaus de beton a
     * meurtrieres dans un talus de terre, sa porte blindee au nord ; dedans, le
     * poste de commandement (ecrans radar, table des cartes, classeurs,
     * coffres) ; sur le toit, borde de sacs de sable, les tourelles aux coins,
     * un periscope.
     */
    private void commandPost() {
        int half = keep;
        int[][] cores = cornerCores(false);
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                if (ring == half + 1) {
                    if (z != -(half + 1) || Math.abs(x) > 1) {
                        set(x, y0, z, Blocks.DIRT.defaultBlockState());
                        set(x, y0 + 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    }
                    continue;
                }
                boolean edge = ring == half;
                boolean corner = Math.abs(x) == half && Math.abs(z) == half;
                for (int y = y0; y <= y0 + 4; y++) {
                    if (y == y0 + 4) {
                        set(x, y, z, skin());
                    } else if (edge) {
                        set(x, y, z, y == y0 + 2 && !corner && Math.floorMod(x + z, 3) == 0
                                ? Blocks.TINTED_GLASS.defaultBlockState() : skin());
                    } else if (y == y0) {
                        set(x, y, z, carpet(DyeColor.GRAY));
                    }
                }
                if (edge && !under(x, z, cores)) {
                    // Au niveau du toit : plus haut, les sacs boucheraient la vue des tourelles.
                    shape(x, y0 + 4, z, sandbag());
                } else if (!edge && Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2) {
                    set(x, y0 + 4, z, Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
        doorway(0, y0, -half, Direction.NORTH);
        for (int x = -half + 1; x <= half - 1; x++) {
            set(x, y0, half - 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(x, y0 + 1, half - 1, screen(Direction.NORTH));
        }
        set(0, y0, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        BlockState cabinet = NtmDefenses.facing(NtmDefenses.state("filing_cabinet_green",
                Blocks.BOOKSHELF.defaultBlockState()), Direction.EAST);
        for (int z = -2; z <= 2; z++) {
            set(-(half - 1), y0, z, cabinet);
        }
        chest(half - 1, y0, -1, Direction.WEST, "chests/enemy_atelier");
        chest(half - 1, y0, 1, Direction.WEST, "chests/enemy_atelier");
        turretsAt(y0 + 5, cores);
        for (int y = y0 + 5; y <= y0 + 7; y++) {
            set(0, y, 0, rod(Direction.UP));
        }
        guard(EntityTypes.VINDICATOR, -2, y0, -half + 2, false);
        guard(EntityTypes.VINDICATOR, 2, y0, 2, false);
    }

    /**
     * Le donjon sovietique : un parement de beton clair autour du cube, des
     * bandes rouges, de fausses fenetres, l'etoile rouge au-dessus de la porte
     * blindee ; sur le toit, l'acrotere, le mat au drapeau rouge ; les tourelles
     * ({@link #keepTurrets}).
     */
    private void sovietKeep(int top) {
        int r = keep + 1;
        facade(y0 - 1, top + 2, (x, y, z) -> {
            if (y < y0) {
                return accent();
            }
            if (y > top) {
                return y == top + 2 && parapetGap(x, z) ? Blocks.AIR.defaultBlockState() : skin();
            }
            int floor = Math.floorMod(y - y0, 5);
            if (floor == 4) {
                return concrete(DyeColor.RED);
            }
            boolean corner = Math.abs(x) == r && Math.abs(z) == r;
            int along = Math.abs(z) == r ? x : z;
            if (!corner && (floor == 1 || floor == 2) && Math.floorMod(along, 3) == 1) {
                return Blocks.STAINED_GLASS.pick(DyeColor.GRAY).defaultBlockState();
            }
            return skin();
        });
        steps.add(() -> {
            int height = top - y0;
            int size = odd(Math.max(5, Math.min(15, Math.min(2 * r - 3, height - 4))));
            boolean[][] star = star(size);
            int c = size / 2;
            int center = y0 + 3 + c;
            for (int x = -c - 1; x <= c + 1; x++) {
                for (int y = center - c - 1; y <= center + c + 1; y++) {
                    set(x, y, -r, skin());
                }
            }
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (star[row][col]) {
                        set(col - c, center + c - row, -r, concrete(DyeColor.RED));
                    }
                }
            }
            doorway(0, y0, -r, Direction.NORTH);
            for (int x = -keep; x <= keep; x++) {
                for (int z = -keep; z <= keep; z++) {
                    set(x, top + 1, z, accent());
                }
            }
            for (int y = top + 2; y <= top + 4; y++) {
                set(0, y, 0, Blocks.POLISHED_BASALT.defaultBlockState());
            }
            set(0, top + 5, 0, Blocks.BANNER.pick(DyeColor.RED).defaultBlockState());
            keepTurrets(top);
            guard(EntityTypes.PILLAGER, 2, top + 2, -2, false);
            guard(EntityTypes.PILLAGER, -2, top + 2, 2, false);
        });
    }

    /**
     * La station radar : un parement de beton autour du cube, des bandes aux
     * couleurs du genre, des baies vitrees ; sur le toit, le grand radar de NTM
     * (une parabole sans le mod), des antennes ; les tourelles
     * ({@link #keepTurrets}).
     */
    private void radarStation(int top) {
        int r = keep + 1;
        facade(y0 - 1, top + 2, (x, y, z) -> {
            if (y == top + 2 && parapetGap(x, z)) {
                return Blocks.AIR.defaultBlockState();
            }
            if (y < y0 || y > top) {
                return accent();
            }
            int floor = Math.floorMod(y - y0, 4);
            if (floor == 3) {
                return band(x, y, z);
            }
            boolean corner = Math.abs(x) == r && Math.abs(z) == r;
            int along = Math.abs(z) == r ? x : z;
            if (!corner && floor == 1 && Math.floorMod(along, 2) == 0) {
                return Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
            }
            return skin();
        });
        steps.add(() -> {
            for (int x = -keep; x <= keep; x++) {
                for (int z = -keep; z <= keep; z++) {
                    set(x, top + 1, z, accent());
                }
            }
            if (!NtmDefenses.multiblock(level, at(0, top + 2, 0), "machine_radar_large", Direction.SOUTH)) {
                parabola(0, top + 2, 0);
            }
            keepTurrets(top);
            for (int[] spot : new int[][]{{keep - 1, 0}, {-(keep - 1), 0}, {0, keep - 1}}) {
                for (int y = top + 2; y <= top + 4; y++) {
                    set(spot[0], y, spot[1], rod(Direction.UP));
                }
            }
            doorway(0, y0, -r, Direction.NORTH);
            guard(EntityTypes.PILLAGER, 3, top + 2, -3, false);
            guard(EntityTypes.PILLAGER, -3, top + 2, 3, false);
        });
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

    /** Les gardes du chemin de ronde et des cours ; ceux des miradors et des quartiers sont poses avec eux. */
    private void garrison() {
        int walk = wall - 1;
        for (int at = -walk + 8; at <= walk - 8; at += 16) {
            guard(EntityTypes.PILLAGER, at, y0 + wallHeight + 1, walk, false);
            guard(EntityTypes.PILLAGER, walk, y0 + wallHeight + 1, at, false);
            guard(EntityTypes.PILLAGER, -walk, y0 + wallHeight + 1, at, false);
            if (Math.abs(at) > 8) {
                guard(EntityTypes.PILLAGER, at, y0 + wallHeight + 1, -walk, false);
            }
        }
        int yard = lotsIn;
        guard(EntityTypes.WITCH, 2, y0, yard, false);
        guard(EntityTypes.ZOMBIE, -2, y0, -yard, true);
        if (innerWall > 0) {
            if (grade == 3) {
                // Entre la lave et le barbele.
                for (int[] corner : corners(keep + 5)) {
                    guard(EntityTypes.VINDICATOR, corner[0], y0, corner[1], false);
                }
            }
            guard(EntityTypes.WITCH, -2, y0, yard, false);
            for (int at = -innerWall + 6; at <= innerWall - 6; at += 12) {
                guard(EntityTypes.PILLAGER, at, y0 + innerHeight + 1, innerWall - 1, false);
                guard(EntityTypes.PILLAGER, at, y0 + innerHeight + 1, -(innerWall - 1), false);
            }
        }
        if (grade >= 3) {
            // Les ravageurs tournent dans la cour interieure, derriere le barbele.
            guard(EntityTypes.RAVAGER, -6, y0, keep + 7, false);
            guard(EntityTypes.RAVAGER, 6, y0, -(keep + 7), false);
            guard(EntityTypes.EVOKER, 0, y0, -(innerWall + 2), false);
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

    /** Dehors, l'herbe ; dans l'enceinte, une croix de routes et le sol du genre. */
    private BlockState floor(int dx, int dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) > wall - outer.size()) {
            return random.nextInt(8) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
            return road(dx, dz);
        }
        int roll = random.nextInt(10);
        return switch (style) {
            case SILO -> roll < 8 ? concrete(DyeColor.LIGHT_GRAY) : roll < 9 ? concrete(DyeColor.GRAY)
                    : Blocks.POLISHED_ANDESITE.defaultBlockState();
            case BUNKER -> roll < 3 ? Blocks.GRASS_BLOCK.defaultBlockState() : roll < 6
                    ? Blocks.COARSE_DIRT.defaultBlockState() : roll < 8 ? Blocks.PACKED_MUD.defaultBlockState()
                    : Blocks.GRAVEL.defaultBlockState();
            case SOVIET -> Math.floorMod(dx, 4) == 0 || Math.floorMod(dz, 4) == 0 ? concrete(DyeColor.GRAY)
                    : concrete(DyeColor.LIGHT_GRAY);
            case RADAR -> roll < 5 ? Blocks.GRASS_BLOCK.defaultBlockState() : roll < 7
                    ? Blocks.GRAVEL.defaultBlockState() : roll < 9 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : Blocks.ANDESITE.defaultBlockState();
        };
    }

    /** Une route : l'asphalte de NTM (du beton noir sans le mod), une ligne jaune pointillee au milieu. */
    private BlockState road(int dx, int dz) {
        boolean line = dx == 0 && Math.abs(dz) > 2 && Math.floorMod(dz, 4) < 2
                || dz == 0 && Math.abs(dx) > 2 && Math.floorMod(dx, 4) < 2;
        return line ? concrete(DyeColor.YELLOW) : NtmDefenses.state("asphalt", concrete(DyeColor.BLACK));
    }

    /** Un anneau de liquide de {@code from} a {@code to}, profond de {@code depth}, sur un fond de pierre. */
    private void ditch(int from, int to, int depth, Supplier<BlockState> fluid) {
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

    /** Le beton des murs et des batiments : blanc, gris, gris clair, ou gres pour la base radar. */
    private BlockState skin() {
        return switch (style) {
            case SILO -> concrete(DyeColor.WHITE);
            case BUNKER -> concrete(DyeColor.GRAY);
            case SOVIET -> concrete(DyeColor.LIGHT_GRAY);
            case RADAR -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        };
    }

    /** La bande des murs, un rang sur quatre : rayee jaune et noir, vert armee, rouge, bleue. */
    private BlockState band(int x, int y, int z) {
        return switch (style) {
            case SILO -> hazard(x + y, z);
            case BUNKER -> terracotta(DyeColor.GREEN);
            case SOVIET -> concrete(DyeColor.RED);
            case RADAR -> terracotta(DyeColor.BLUE);
        };
    }

    /** Les toits, les acroteres et les socles. */
    private BlockState accent() {
        return switch (style) {
            case SILO -> concrete(DyeColor.LIGHT_GRAY);
            case BUNKER -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case SOVIET -> concrete(DyeColor.GRAY);
            case RADAR -> concrete(DyeColor.WHITE);
        };
    }

    private DyeColor banner() {
        return switch (style) {
            case SILO -> DyeColor.YELLOW;
            case BUNKER -> DyeColor.GREEN;
            case SOVIET -> DyeColor.RED;
            case RADAR -> DyeColor.BLUE;
        };
    }

    private BlockState window() {
        return style == EnemyBaseStyle.BUNKER ? Blocks.TINTED_GLASS.defaultBlockState()
                : Blocks.STAINED_GLASS.pick(DyeColor.GRAY).defaultBlockState();
    }

    /** Le garde-corps : le grillage de NTM, des barreaux sans le mod. A poser par {@link #shape}. */
    private static BlockState railing() {
        return NtmDefenses.state("fence_metal", Blocks.IRON_BARS.defaultBlockState());
    }

    /** Des sacs de sable de NTM, un muret de gres sans le mod. A poser par {@link #shape}. */
    private static BlockState sandbag() {
        return NtmDefenses.state("sandbags", Blocks.SANDSTONE_WALL.defaultBlockState());
    }

    /** Le beton raye jaune et noir de NTM, sinon des cases jaunes et noires alternees. */
    private static BlockState hazard(int x, int z) {
        BlockState stripes = NtmDefenses.state("concrete_ext_hazard", null);
        if (stripes != null) {
            return stripes;
        }
        return Math.floorMod(x + z, 2) == 0 ? concrete(DyeColor.YELLOW) : concrete(DyeColor.BLACK);
    }

    /** Un ecran radar de NTM tourne vers la piece, du verre noir sans le mod. */
    private static BlockState screen(Direction face) {
        BlockState screen = NtmDefenses.state("radar_screen", null);
        return screen == null ? Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState()
                : NtmDefenses.facing(screen, face);
    }

    private static BlockState concrete(DyeColor color) {
        return Blocks.CONCRETE.pick(color).defaultBlockState();
    }

    private static BlockState terracotta(DyeColor color) {
        return Blocks.DYED_TERRACOTTA.pick(color).defaultBlockState();
    }

    private static BlockState carpet(DyeColor color) {
        return Blocks.CARPET.pick(color).defaultBlockState();
    }

    /** Un paratonnerre cire (antennes, canalisations, canon) tourne vers {@code facing}. */
    private static BlockState rod(Direction facing) {
        if (rodBlock == null) {
            rodBlock = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("minecraft", "waxed_lightning_rod"))
                    .or(() -> BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("minecraft",
                            "lightning_rod")))
                    .orElse(Blocks.END_ROD);
        }
        BlockState state = rodBlock.defaultBlockState();
        return state.hasProperty(BlockStateProperties.FACING) ? state.setValue(BlockStateProperties.FACING, facing)
                : state;
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

    private BlockPos at(int dx, int y, int dz) {
        return new BlockPos(cx + dx, y, cz + dz);
    }

    private void set(int dx, int y, int dz, BlockState state) {
        level.setBlock(at(dx, y, dz), state, FLAGS);
    }

    /** Un bloc dont la forme depend de ses voisins (barreaux, grillages, sacs) : raccorde en fin de chantier. */
    private void shape(int dx, int y, int dz, BlockState state) {
        BlockPos pos = at(dx, y, dz);
        level.setBlock(pos, state, FLAGS);
        shaped.add(pos);
    }

    /** Une machine de PalaMod, tournee vers {@code face} ; {@code fallback} si elle manque (ou sans nom). */
    private void machine(int dx, int y, int dz, String path, Block fallback, Direction face) {
        set(dx, y, dz, facing(path.isEmpty() ? fallback.defaultBlockState() : state(path, fallback), face));
    }

    private static int[][] corners(int at) {
        return new int[][]{{-at, -at}, {at, -at}, {-at, at}, {at, at}};
    }

    /** Le pourtour du carre de demi-cote {@code r} a la hauteur {@code y}. */
    private void ring(int r, int y, Supplier<BlockState> state) {
        ringAt(r, y, (x, z) -> state.get());
    }

    /** Le pourtour du carre de demi-cote {@code r} a la hauteur {@code y}, case par case. */
    private void ringAt(int r, int y, Cell cell) {
        for (int i = -r; i <= r; i++) {
            set(i, y, -r, cell.at(i, -r));
            set(i, y, r, cell.at(i, r));
            if (i != -r && i != r) {
                set(-r, y, i, cell.at(-r, i));
                set(r, y, i, cell.at(r, i));
            }
        }
    }

    /** Un anneau de barbele, couche le long de chaque cote. */
    private void wireRing(int r, int y, boolean poison) {
        for (int i = -r; i <= r; i++) {
            wire(i, y, -r, Direction.Axis.X, poison);
            wire(i, y, r, Direction.Axis.X, poison);
            if (i != -r && i != r) {
                wire(-r, y, i, Direction.Axis.Z, poison);
                wire(r, y, i, Direction.Axis.Z, poison);
            }
        }
    }

    /** Du barbele de NTM (empoisonne si {@code poison}) ; sans le mod, des barreaux en hauteur, des spikes au sol. */
    private void wire(int dx, int y, int dz, Direction.Axis axis, boolean poison) {
        BlockState wire = NtmDefenses.wire(poison ? "barbed_wire_poison" : "barbed_wire", axis);
        if (wire != null) {
            set(dx, y, dz, wire);
        } else if (y > y0) {
            shape(dx, y, dz, Blocks.IRON_BARS.defaultBlockState());
        } else {
            set(dx, y, dz, spike());
        }
    }

    /** Une tourelle de NTM, armee, son coeur en ({@code dx}, {@code y}, {@code dz}) ; vrai si elle est posee. */
    private boolean turret(int dx, int y, int dz, String id) {
        if (id == null) {
            return false;
        }
        boolean placed = NtmDefenses.turret(level, at(dx, y, dz), id, Direction.SOUTH);
        if (placed) {
            turrets++;
        }
        return placed;
    }

    /**
     * Un projecteur halogene de NTM pose sur un toit, tourne vers {@code out} :
     * son socle d'acier derriere lui, et a cote, du cote {@code side}, un levier
     * abaisse qui l'allume — il le voit en se posant. Sans NTM, une lanterne.
     */
    private void floodlight(int dx, int y, int dz, Direction out, Direction side) {
        Block spot = NtmDefenses.block("spotlight_halogen");
        if (spot == null) {
            lantern(dx, y, dz, false);
            return;
        }
        set(dx - out.getStepX(), y, dz - out.getStepZ(), Blocks.IRON_BLOCK.defaultBlockState());
        set(dx + side.getStepX(), y, dz + side.getStepZ(), Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, out)
                .setValue(BlockStateProperties.POWERED, true));
        set(dx, y, dz, NtmDefenses.facing(spot.defaultBlockState(), out));
    }

    /** Une porte sur deux rangs, fermee : la porte de bunker de NTM, une porte de fer sans le mod. */
    private void doorway(int dx, int y, int dz, Direction facing) {
        Block door = NtmDefenses.block("door_bunker");
        BlockState state = NtmDefenses.facing((door == null ? Blocks.IRON_DOOR : door).defaultBlockState(), facing);
        set(dx, y, dz, NtmDefenses.with(state, "half", "lower"));
        set(dx, y + 1, dz, NtmDefenses.with(state, "half", "upper"));
    }

    /** Un lit vert armee : la tete en ({@code dx}, {@code dz}), le pied du cote oppose a {@code toHead}. */
    private void bed(int dx, int dz, Direction toHead) {
        BlockState bed = Blocks.BED.pick(DyeColor.GREEN).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, toHead);
        set(dx, y0, dz, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
        set(dx - toHead.getStepX(), y0, dz - toHead.getStepZ(), bed.setValue(BlockStateProperties.BED_PART,
                BedPart.FOOT));
    }

    /**
     * Une palette d'armement chargee de {@code stacks} piles tirees de
     * {@code kinds} (des objets de NTM), ou de {@code fallback} sans le mod. Ses
     * caisses s'empilent des que le chunk tourne.
     */
    private void pallet(int dx, int y, int dz, Direction face, String[] kinds, Item fallback, int stacks) {
        BlockPos pos = at(dx, y, dz);
        level.setBlock(pos, DoHyPalette.PALETTE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, face), FLAGS);
        if (!(level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet)) {
            return;
        }
        for (int i = 0; i < stacks; i++) {
            Item item = fallback;
            if (NtmDefenses.present()) {
                item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(NtmDefenses.MOD,
                        kinds[random.nextInt(kinds.length)])).orElse(fallback);
            }
            ItemStack stack = new ItemStack(item);
            stack.setCount(stack.getMaxStackSize());
            pallet.insert(stack);
        }
    }

    /** Un lampadaire : un fut de basalte, une lanterne marine. */
    private void post(int x, int z) {
        set(x, y0, z, Blocks.POLISHED_BASALT.defaultBlockState());
        set(x, y0 + 1, z, Blocks.POLISHED_BASALT.defaultBlockState());
        set(x, y0 + 2, z, Blocks.POLISHED_BASALT.defaultBlockState());
        set(x, y0 + 3, z, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static int odd(int value) {
        return value % 2 == 0 ? value - 1 : value;
    }

    /** Une etoile a cinq branches dans un carre de cote {@code size}, la pointe en haut (rangee 0). */
    private static boolean[][] star(int size) {
        boolean[][] cells = new boolean[size][size];
        double c = (size - 1) / 2.0d;
        double outerRadius = size / 2.0d;
        double innerRadius = outerRadius * (size <= 9 ? 0.48d : 0.42d);
        double[] xs = new double[10];
        double[] ys = new double[10];
        for (int k = 0; k < 10; k++) {
            double angle = Math.toRadians(-90.0d + 36.0d * k);
            double radius = k % 2 == 0 ? outerRadius : innerRadius;
            xs[k] = c + Math.cos(angle) * radius;
            ys[k] = c + Math.sin(angle) * radius;
        }
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                boolean in = false;
                for (int i = 0, j = 9; i < 10; j = i++) {
                    if ((ys[i] > row) != (ys[j] > row)
                            && col < (xs[j] - xs[i]) * (row - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                        in = !in;
                    }
                }
                cells[row][col] = in;
            }
        }
        return cells;
    }

    private void lantern(int dx, int y, int dz) {
        lantern(dx, y, dz, true);
    }

    private void lantern(int dx, int y, int dz, boolean hanging) {
        set(dx, y, dz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging));
    }

    /** Un coffre a butin : la table est tiree a l'ouverture, ou a l'explosion. */
    private void chest(int dx, int y, int dz, Direction face, String table) {
        BlockPos pos = at(dx, y, dz);
        level.setBlock(pos, facing(Blocks.CHEST.defaultBlockState(), face), FLAGS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table(table), random.nextLong());
        }
    }

    private void barrel(int dx, int y, int dz, Direction face, String table) {
        BlockPos pos = at(dx, y, dz);
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
        BlockPos pos = at(dx, y, dz);
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
        if (level.addFreshEntity(mob)) {
            guards++;
        }
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
