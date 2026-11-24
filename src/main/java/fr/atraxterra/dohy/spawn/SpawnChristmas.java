package fr.atraxterra.dohy.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.noel.AdventCalendar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le spawn en habit de Noel, du 1er decembre au 6 janvier : un grand sapin sur le parvis, en
 * face de l'autel des offrandes (a droite de l'allee en arrivant), ses boules, sa guirlande de
 * lumieres, son etoile et ses cadeaux ; le Pere Noel et son calendrier de l'Avent
 * ({@link AdventCalendar}) ; des guirlandes au-dessus de l'allee, de petits sapins le long, et
 * la neige sur l'herbe et les epiceas du parvis.
 *
 * <p>Le decor se pose sur le spawn construit, et s'en va a la fin de la saison : tout ce qu'il
 * remplace est garde ({@link Decor#undo}) et remis tel quel. Il se pose en deux temps : le sapin
 * et les lumieres, puis, cinq secondes plus tard, la neige — seulement la ou la lumiere des
 * lampes la laisserait tenir (au-dessus de 11, la neige fond). Un spawn reconstruit (nouvelle
 * version du dessin) repart sans decor ; il revient a la verification suivante.
 */
public final class SpawnChristmas {

    /** A augmenter des que le decor change : un decor d'une autre version est retire puis repose. */
    static final int VERSION = 1;

    /** Le pied du sapin : le pendant de l'autel des offrandes, de l'autre cote de l'allee. */
    static final int TREE_X = -SpawnBuilder.ALTAR_X;
    static final int TREE_Z = SpawnBuilder.ALTAR_Z;
    /** Le Pere Noel, entre l'allee et le sapin, tourne vers l'allee. */
    static final BlockPos SANTA = new BlockPos(14, SpawnBuilder.FLOOR + 1, TREE_Z);
    /** Les degres du jeu : 90, il regarde l'ouest, vers l'allee. */
    static final float SANTA_FACING = 90.0f;

    private static final int FLOOR = SpawnBuilder.FLOOR;
    /** Le sapin : son tronc monte jusqu'ici, l'etoile au-dessus. */
    private static final int TREE_TOP = FLOOR + 20;
    /** Les etages d'aiguilles, du bas (FLOOR + 3) vers le haut : leur rayon. */
    private static final double[] RADII = {6.4, 5.6, 4.8, 5.3, 4.5, 3.8, 4.2, 3.5, 2.9, 3.2, 2.6, 2.0, 2.2, 1.6, 1.2,
            1.0, 0.6};
    /** Les allees : un arc de guirlande au-dessus de chaque paire de lampadaires. */
    private static final int[] ARCHES = {16, 22, 28, 34};
    /** La neige ne tient pas au-dessus de cette lumiere de bloc ({@code SnowLayerBlock.randomTick}). */
    private static final int SNOW_LIGHT = 11;

    /** Un bloc remplace par le decor : sa place, ce qu'il etait. */
    public record Saved(long pos, BlockState state) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(Saved::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Saved::state)
        ).apply(instance, Saved::new));
    }

    /**
     * Le decor pose, garde sur la dimension du spawn.
     *
     * @param season  la saison posee (l'annee de son decembre), 0 sans decor
     * @param version la version du decor pose
     * @param snowed  la neige est-elle tombee (le second temps)
     * @param undo    ce que le sapin, les lumieres et le Pere Noel ont remplace
     * @param snow    ou la neige est tombee : de l'air avant elle, la terre dessous sans neige
     */
    public record Decor(int season, int version, boolean snowed, List<Saved> undo, List<Long> snow) {
        public static final Decor NONE = new Decor(0, 0, false, List.of(), List.of());
        public static final Codec<Decor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("season").forGetter(Decor::season),
                Codec.INT.fieldOf("version").forGetter(Decor::version),
                Codec.BOOL.fieldOf("snowed").forGetter(Decor::snowed),
                Saved.CODEC.listOf().fieldOf("undo").forGetter(Decor::undo),
                Codec.LONG.listOf().fieldOf("snow").forGetter(Decor::snow)
        ).apply(instance, Decor::new));

        public boolean isNone() {
            return season == 0;
        }
    }

    /** Quand le decor est pose, en ticks du serveur (-1 : inconnu, apres un redemarrage). */
    static long placedAt = -1L;

    private SpawnChristmas() {
    }

    // -- Poser, retirer --------------------------------------------------------------------

    /** Toutes les cinq secondes, depuis {@link SpawnApi#tick} : la saison et le decor s'accordent. */
    static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(DoHySpawn.DIMENSION);
        if (level == null || SpawnApi.building() || level.getData(DoHySpawn.BUILT.get()) != SpawnApi.target()) {
            return;
        }
        Decor decor = level.getData(DoHySpawn.CHRISTMAS.get());
        int season = AdventCalendar.season(AdventCalendar.today());
        if (!decor.isNone() && (season != decor.season() || decor.version() != VERSION)) {
            remove(level, decor);
            decor = Decor.NONE;
        }
        if (season != 0 && decor.isNone()) {
            apply(level, season);
        } else if (season != 0 && !decor.snowed() && (placedAt < 0L || server.getTickCount() - placedAt >= 100L)) {
            snow(level, decor);
        }
    }

    /** Un spawn reconstruit n'a plus de decor : ce qu'il gardait n'a plus de sens. */
    static void forget(ServerLevel level) {
        level.setData(DoHySpawn.CHRISTMAS.get(), Decor.NONE);
    }

    private static void apply(ServerLevel level, int season) {
        Map<BlockPos, BlockState> design = design(level);
        List<Saved> undo = new ArrayList<>();
        int placed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : design.entrySet()) {
            if (replace(level, entry.getKey(), entry.getValue(), undo)) {
                placed++;
            }
        }
        // les enseignes, puis le Pere Noel : seulement dans l'air libre
        label(level, new BlockPos(SANTA.getX(), FLOOR + 6, SANTA.getZ()), "CALENDRIER DE L'AVENT", 0xD42A2A, 1.1f,
                undo);
        label(level, new BlockPos(SANTA.getX(), FLOOR + 5, SANTA.getZ()), "Une case par jour, du 1er au 24 decembre",
                0xFAC400, 0.6f, undo);
        label(level, new BlockPos(TREE_X, TREE_TOP + 7, TREE_Z), "JOYEUX NOEL", 0xD42A2A, 3.0f, undo);
        label(level, new BlockPos(0, FLOOR + 8, SpawnBuilder.PAD_Z - 9), "Joyeux Noel !", 0xD42A2A, 1.6f, undo);
        if (level.getBlockState(SANTA).isAir() && level.getBlockState(SANTA.above()).isAir()) {
            undo.add(new Saved(SANTA.asLong(), level.getBlockState(SANTA)));
            level.setBlock(SANTA, DoHySpawn.NPC_POST.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(SANTA) instanceof NpcPostBlockEntity post) {
                post.set(SpawnNpc.Role.ADVENT, SANTA_FACING);
            }
        } else {
            DoHy.LOGGER.warn("Spawn : pas de place pour le Pere Noel en {}", SANTA);
        }
        level.setData(DoHySpawn.CHRISTMAS.get(), new Decor(season, VERSION, false, List.copyOf(undo), List.of()));
        placedAt = level.getServer().getTickCount();
        DoHy.LOGGER.info("Spawn : decor de Noel pose ({} blocs), la neige suit", placed);
    }

    /** Remplace un bloc et garde l'ancien ; jamais un bloc qui porte des donnees (enseigne, poste). */
    private static boolean replace(ServerLevel level, BlockPos pos, BlockState state, List<Saved> undo) {
        BlockState old = level.getBlockState(pos);
        if (old.equals(state) || old.hasBlockEntity()) {
            return false;
        }
        undo.add(new Saved(pos.asLong(), old));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private static void label(ServerLevel level, BlockPos pos, String text, int color, float scale, List<Saved> undo) {
        if (!level.getBlockState(pos).isAir()) {
            DoHy.LOGGER.warn("Spawn : l'enseigne de Noel « {} » en {} gene un bloc, elle n'est pas posee", text, pos);
            return;
        }
        undo.add(new Saved(pos.asLong(), level.getBlockState(pos)));
        level.setBlock(pos, DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof HologramBlockEntity hologram) {
            hologram.set(text, color, scale);
        }
    }

    /** Le second temps : la neige, la ou l'air est libre et la lumiere assez basse. */
    private static void snow(ServerLevel level, Decor decor) {
        BlockState layer = Blocks.SNOW.defaultBlockState();
        List<Long> fallen = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -56; x <= 56; x++) {
            for (int z = -52; z <= 40; z++) {
                if (!inPlaza(x, z)) {
                    continue;
                }
                for (int y = FLOOR; y <= TREE_TOP + 1; y++) {
                    pos.set(x, y, z);
                    BlockState ground = level.getBlockState(pos);
                    if (!holdsSnow(ground)) {
                        continue;
                    }
                    BlockPos above = pos.above();
                    if (!level.getBlockState(above).isAir()
                            || level.getBrightness(LightLayer.BLOCK, above) > SNOW_LIGHT) {
                        continue;
                    }
                    level.setBlock(above, layer, Block.UPDATE_CLIENTS);
                    if (ground.hasProperty(BlockStateProperties.SNOWY)) {
                        level.setBlock(pos, ground.setValue(BlockStateProperties.SNOWY, true), Block.UPDATE_CLIENTS);
                    }
                    fallen.add(above.asLong());
                }
            }
        }
        level.setData(DoHySpawn.CHRISTMAS.get(), new Decor(decor.season(), decor.version(), true, decor.undo(),
                List.copyOf(fallen)));
        DoHy.LOGGER.info("Spawn : {} couche(s) de neige sur le parvis", fallen.size());
    }

    /** L'herbe, la mousse, la terre et les aiguilles des epiceas prennent la neige ; le dallage, non. */
    private static boolean holdsSnow(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.DIRT) || state.is(Blocks.SPRUCE_LEAVES);
    }

    private static void remove(ServerLevel level, Decor decor) {
        for (long packed : decor.snow()) {
            BlockPos pos = BlockPos.of(packed);
            if (level.getBlockState(pos).is(Blocks.SNOW)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockState ground = level.getBlockState(pos.below());
            if (ground.hasProperty(BlockStateProperties.SNOWY) && ground.getValue(BlockStateProperties.SNOWY)) {
                level.setBlock(pos.below(), ground.setValue(BlockStateProperties.SNOWY, false), Block.UPDATE_CLIENTS);
            }
        }
        List<Saved> undo = decor.undo();
        for (int index = undo.size() - 1; index >= 0; index--) {
            Saved saved = undo.get(index);
            level.setBlock(BlockPos.of(saved.pos()), saved.state(), Block.UPDATE_CLIENTS);
        }
        level.setData(DoHySpawn.CHRISTMAS.get(), Decor.NONE);
        DoHy.LOGGER.info("Spawn : decor de Noel retire ({} blocs remis, {} couches de neige)", undo.size(),
                decor.snow().size());
    }

    // -- Le dessin -------------------------------------------------------------------------

    private static boolean inPlaza(int x, int z) {
        double dx = x / 54.0d;
        double dz = (z + 6) / 44.0d;
        return dx * dx + dz * dz <= 1.0d;
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73_856_093 ^ y * 19_349_663 ^ z * 83_492_791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Ce qui pousse ou tient debout sur le parvis : ce que le sapin peut repousser. */
    private static boolean natural(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock || state.is(Blocks.SPRUCE_LOG)
                || state.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock
                || state.is(Blocks.MOSS_CARPET);
    }

    private static BlockState wool(DyeColor color) {
        return Blocks.WOOL.pick(color).defaultBlockState();
    }

    private static BlockState concrete(DyeColor color) {
        return Blocks.CONCRETE.pick(color).defaultBlockState();
    }

    private static BlockState carpet(DyeColor color) {
        return Blocks.CARPET.pick(color).defaultBlockState();
    }

    private static BlockState leaves() {
        return Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
    }

    /** Le decor, bloc par bloc, dans l'ordre de la pose. */
    static Map<BlockPos, BlockState> design(ServerLevel level) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        tree(level, blocks);
        santa(level, blocks);
        alley(level, blocks);
        return blocks;
    }

    /**
     * Le grand sapin : la place faite d'abord (les epiceas, buissons et le lampadaire qui s'y
     * trouvent s'effacent), un tronc, dix-sept etages d'aiguilles en cone a gradins, une guirlande
     * de lumieres en spirale, des boules, l'etoile, et les cadeaux dessous.
     */
    private static void tree(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // la place : rien de naturel ni de lampadaire dans le cylindre du sapin
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx * dx + dz * dz > 64) {
                    continue;
                }
                for (int y = FLOOR + 1; y <= TREE_TOP + 4; y++) {
                    BlockPos pos = new BlockPos(TREE_X + dx, y, TREE_Z + dz);
                    BlockState state = level.getBlockState(pos);
                    boolean inside = dx * dx + dz * dz <= 49;
                    if (!state.isAir() && (natural(state) || inside && !state.hasBlockEntity())) {
                        blocks.put(pos, air);
                    }
                }
            }
        }
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int y = FLOOR + 1; y < TREE_TOP; y++) {
            blocks.put(new BlockPos(TREE_X, y, TREE_Z), log);
        }
        BlockState[] baubles = {concrete(DyeColor.RED), Blocks.GOLD_BLOCK.defaultBlockState(),
                concrete(DyeColor.RED), concrete(DyeColor.LIGHT_BLUE),
                concrete(DyeColor.WHITE)};
        BlockState[] lights = {Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState()};
        BlockState garland = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
        for (int k = 0; k < RADII.length; k++) {
            int y = FLOOR + 3 + k;
            double radius = RADII[k];
            int reach = (int) Math.ceil(radius);
            // la guirlande : deux tours et demi de spirale, du bas vers le haut
            double angle = k * 0.92d;
            int gx = (int) Math.round(Math.cos(angle) * radius);
            int gz = (int) Math.round(Math.sin(angle) * radius);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > radius + 0.25d || dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(TREE_X + dx, y, TREE_Z + dz);
                    boolean shell = distance > radius - 1.15d;
                    int roll = hash(TREE_X + dx, y, TREE_Z + dz) % 100;
                    BlockState state = leaves();
                    if (shell && dx == gx && dz == gz) {
                        state = garland;
                    } else if (shell && roll < 9) {
                        state = baubles[roll % baubles.length];
                    } else if (shell && roll < 14) {
                        state = lights[roll % lights.length];
                    }
                    blocks.put(pos, state);
                }
            }
        }
        // la pointe et l'etoile
        blocks.put(new BlockPos(TREE_X, TREE_TOP, TREE_Z), leaves());
        blocks.put(new BlockPos(TREE_X, TREE_TOP + 1, TREE_Z), Blocks.GLOWSTONE.defaultBlockState());
        BlockPos heart = new BlockPos(TREE_X, TREE_TOP + 1, TREE_Z);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            blocks.put(heart.relative(side), Blocks.GOLD_BLOCK.defaultBlockState());
        }
        blocks.put(new BlockPos(TREE_X, TREE_TOP + 2, TREE_Z), Blocks.GOLD_BLOCK.defaultBlockState());
        blocks.put(new BlockPos(TREE_X, TREE_TOP + 3, TREE_Z),
                Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        // les cadeaux, sous les branches : une boite de laine, un ruban de tapis
        BlockState[][] gifts = {
                {wool(DyeColor.RED), carpet(DyeColor.YELLOW)},
                {wool(DyeColor.LIME), carpet(DyeColor.RED)},
                {wool(DyeColor.LIGHT_BLUE), carpet(DyeColor.WHITE)},
                {wool(DyeColor.YELLOW), carpet(DyeColor.RED)},
                {wool(DyeColor.PURPLE), carpet(DyeColor.YELLOW)},
                {wool(DyeColor.WHITE), carpet(DyeColor.GREEN)},
        };
        int[][] spots = {{-3, 2}, {-4, -1}, {-2, -4}, {1, -4}, {3, -3}, {4, 1}, {2, 4}, {-1, 4}, {-5, 2}, {5, -2},
                {0, -5}, {-3, -3}};
        for (int index = 0; index < spots.length; index++) {
            BlockState[] gift = gifts[index % gifts.length];
            BlockPos box = new BlockPos(TREE_X + spots[index][0], FLOOR + 1, TREE_Z + spots[index][1]);
            blocks.put(box, gift[0]);
            blocks.put(box.above(), gift[1]);
        }
    }

    /** Le Pere Noel : un tapis rouge borde de blanc sous ses pieds, deux petits sapins de part et d'autre. */
    private static void santa(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos ground = new BlockPos(SANTA.getX() + dx, FLOOR, SANTA.getZ() + dz);
                if (holdsSnow(level.getBlockState(ground))) {
                    boolean edge = Math.abs(dz) == 2 || Math.abs(dx) == 1;
                    blocks.put(ground, edge ? wool(DyeColor.WHITE) : wool(DyeColor.RED));
                }
                for (int up = 1; up <= 2; up++) {
                    BlockPos above = ground.above(up);
                    if (natural(level.getBlockState(above))) {
                        blocks.put(above, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        BlockState small = fr.atraxterra.dohy.palamod.lucky.SeasonItems.CHRISTMAS_TREE.get().defaultBlockState();
        for (int side = -1; side <= 1; side += 2) {
            BlockPos spot = SANTA.offset(0, 0, 3 * side);
            if (level.getBlockState(spot).isAir() || natural(level.getBlockState(spot))) {
                blocks.put(spot, small);
            }
        }
    }

    /**
     * L'allee : un arc de guirlande — des aiguilles et une lumiere sur deux — au-dessus de
     * chaque paire de lampadaires, qui ploie au milieu ; et de petits sapins le long.
     */
    private static void alley(ServerLevel level, Map<BlockPos, BlockState> blocks) {
        BlockState[] bulbs = {Blocks.OCHRE_FROGLIGHT.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
                Blocks.VERDANT_FROGLIGHT.defaultBlockState()};
        for (int z : ARCHES) {
            for (int x = -4; x <= 4; x++) {
                int y = Math.abs(x) >= 3 ? FLOOR + 5 : FLOOR + 4;
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                blocks.put(pos, Math.floorMod(x, 2) == 0 ? bulbs[Math.floorMod(x / 2 + z, bulbs.length)] : leaves());
            }
        }
        BlockState small = fr.atraxterra.dohy.palamod.lucky.SeasonItems.CHRISTMAS_TREE.get().defaultBlockState();
        for (int side = -1; side <= 1; side += 2) {
            for (int z = 19; z <= 31; z += 6) {
                BlockPos spot = new BlockPos(7 * side, FLOOR + 1, z);
                BlockState below = level.getBlockState(spot.below());
                if (level.getBlockState(spot).isAir() && below.isFaceSturdy(level, spot.below(), Direction.UP)) {
                    blocks.put(spot, small);
                }
            }
        }
    }
}
