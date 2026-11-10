package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.defense.PalaDefense;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bati une base ennemie (lot 22g) dans des chunks deja charges : le terrain
 * aplani, l'enceinte et le bunker de sa taille, les coffres (tables de butin
 * {@code palamod:chests/enemy_*}, deroulees a l'ouverture ou a l'explosion),
 * les gardes, et les claims de la faction ennemie.
 *
 * <p>Tout est pose sans mise a jour des voisins (sauf leurs formes) : la base
 * apparait d'un bloc, sans que l'eau ou le sable ne coulent dans le chantier.
 */
final class EnemyBaseBuilder {

    record Result(BlockPos center, BlockPos coreMin, BlockPos coreMax) {
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final ServerLevel level;
    private final RandomSource random;
    private final EnemyBaseTier tier;
    private final String name;
    private final int cx;
    private final int cz;
    private int y0;
    private final List<BlockPos> shaped = new ArrayList<>();

    EnemyBaseBuilder(ServerLevel level, RandomSource random, EnemyBaseTier tier, String name, int cx, int cz) {
        this.level = level;
        this.random = random;
        this.tier = tier;
        this.name = name;
        this.cx = cx;
        this.cz = cz;
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

    Result build() {
        y0 = ground();
        flatten();
        int core;
        switch (tier) {
            case OUTPOST -> core = outpost();
            case BASE -> core = base();
            default -> core = fortress();
        }
        for (BlockPos pos : shaped) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), FLAGS);
        }
        claim();
        int height = tier == EnemyBaseTier.FORTRESS ? 5 : 4;
        return new Result(new BlockPos(cx, y0, cz), new BlockPos(cx - core, y0, cz - core),
                new BlockPos(cx + core, y0 + height, cz + core));
    }

    // -- Terrain ------------------------------------------------------------------

    /** Le sol : la mediane des hauteurs de l'emprise, au-dessus du niveau de la mer. */
    private int ground() {
        int half = tier.half();
        List<Integer> heights = new ArrayList<>();
        for (int x = cx - half; x <= cx + half; x += 3) {
            for (int z = cz - half; z <= cz + half; z += 3) {
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        heights.sort(Integer::compare);
        int median = heights.get(heights.size() / 2);
        return Math.max(median, level.getSeaLevel() + 1);
    }

    /** Degage l'emprise et la pose sur des fondations de pierre. */
    private void flatten() {
        int half = tier.half();
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), y0 + tier.clearHeight());
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y0, z);
                for (int y = y0; y <= top; y++) {
                    pos.setY(y);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                set(x, y0 - 1, z, floor(x, z));
                for (int y = y0 - 2; y > y0 - 16; y--) {
                    pos.setY(y);
                    BlockState state = level.getBlockState(pos);
                    if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    private BlockState floor(int x, int z) {
        if (tier == EnemyBaseTier.OUTPOST) {
            return random.nextInt(6) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        int roll = random.nextInt(10);
        return roll < 4 ? Blocks.COBBLESTONE.defaultBlockState() : roll < 7 ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.COARSE_DIRT.defaultBlockState();
    }

    // -- Les trois plans ------------------------------------------------------------

    /** Un bunker de pierre de 9 x 9 dans une palissade : la TNT suffit. */
    private int outpost() {
        ring(6, y0, () -> Blocks.OAK_FENCE.defaultBlockState(), true);
        fill(4, y0 - 1, () -> Blocks.STONE_BRICKS.defaultBlockState());
        shell(4, y0, y0 + 4, this::stoneBricks);
        fill(4, y0 + 5, this::stoneBricks);
        chest(-3, 3, Direction.NORTH, tier.lootTable());
        chest(3, 3, Direction.NORTH, tier.lootTable());
        lantern(0, y0 + 4, 0);
        set(cx, y0 + 6, cz, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
        guard(EntityTypes.ZOMBIE, -1, y0, -1, Items.IRON_HELMET, Items.IRON_SWORD);
        guard(EntityTypes.SKELETON, 1, y0, 0, Items.LEATHER_HELMET, null);
        guard(EntityTypes.ZOMBIE, 0, y0, 1, Items.CHAINMAIL_HELMET, Items.STONE_SWORD);
        return 3;
    }

    /** Une enceinte d'obsidienne, une douve de Fake Water, un bunker obsidienne et pierre. */
    private int base() {
        shell(12, y0, y0 + 3, () -> Blocks.OBSIDIAN.defaultBlockState());
        merlons(12, y0 + 4, this::stoneBricks);
        ring(8, y0 - 1, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState(), false);
        fill(5, y0 - 1, () -> Blocks.OBSIDIAN.defaultBlockState());
        shell(5, y0, y0 + 5, () -> random.nextInt(10) == 0 ? PalaDefense.POISON_OBSIDIAN.get().defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState());
        fill(5, y0 + 6, () -> Blocks.OBSIDIAN.defaultBlockState());
        shell(4, y0, y0 + 4, this::stoneBricks);
        fill(4, y0 + 5, this::stoneBricks);
        for (int[] corner : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            chest(corner[0], corner[1], corner[1] < 0 ? Direction.SOUTH : Direction.NORTH, tier.lootTable());
        }
        lantern(0, y0 + 4, 0);
        cobwebs(3, 4);
        set(cx, y0 + 7, cz, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
        guard(EntityTypes.ZOMBIE, -1, y0, -1, Items.IRON_HELMET, Items.IRON_SWORD);
        guard(EntityTypes.ZOMBIE, 1, y0, 1, Items.IRON_HELMET, Items.IRON_AXE);
        guard(EntityTypes.SKELETON, 1, y0, -1, Items.CHAINMAIL_HELMET, null);
        guard(EntityTypes.SKELETON, -1, y0, 1, Items.CHAINMAIL_HELMET, null);
        guard(EntityTypes.SKELETON, 10, y0, 0, Items.IRON_HELMET, null);
        guard(EntityTypes.SKELETON, -10, y0, 0, Items.IRON_HELMET, null);
        return 3;
    }

    /** Quatre tours, un champ de spikes, une double douve, un bunker a trois couches. */
    private int fortress() {
        shell(20, y0, y0 + 5, this::trappedObsidian);
        merlons(20, y0 + 6, () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                tower(sx * 20, sz * 20);
            }
        }
        ring(16, y0, () -> PalaDefense.DIAMOND_SPIKE.get().defaultBlockState(), false);
        ring(13, y0 - 1, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState(), false);
        ring(12, y0 - 1, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState(), false);
        fill(7, y0 - 2, () -> Blocks.OBSIDIAN.defaultBlockState());
        fill(7, y0 - 1, () -> PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState());
        // Du dehors vers le dedans : renforcee, a wither, piegee.
        shell(7, y0, y0 + 7, () -> PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState());
        fill(7, y0 + 8, () -> PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState());
        Supplier<BlockState> wither = () -> random.nextBoolean() ? PalaDefense.WITHER_OBSIDIAN.get().defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
        shell(6, y0, y0 + 6, wither);
        fill(6, y0 + 7, wither);
        Supplier<BlockState> trapped = () -> random.nextInt(10) == 0
                ? PalaDefense.MEGA_BOOM_OBSIDIAN.get().defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        shell(5, y0, y0 + 5, trapped);
        fill(5, y0 + 6, trapped);
        for (int[] spot : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {0, -4}, {0, 4}}) {
            chest(spot[0], spot[1], spot[1] < 0 ? Direction.SOUTH : Direction.NORTH, tier.lootTable());
        }
        chest(0, 0, Direction.NORTH, "chests/enemy_coffre_fort");
        set(cx + 4, y0, cz, PalaPillage.ONLINE_DETECTOR.get().defaultBlockState());
        for (int[] spot : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            lantern(spot[0], y0 + 5, spot[1]);
        }
        set(cx, y0 + 9, cz, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
        guard(EntityTypes.WITHER_SKELETON, -2, y0, -2, null, null);
        guard(EntityTypes.WITHER_SKELETON, 2, y0, -2, null, null);
        guard(EntityTypes.WITHER_SKELETON, -2, y0, 2, null, null);
        guard(EntityTypes.WITHER_SKELETON, 2, y0, 2, null, null);
        guard(EntityTypes.ZOMBIE, -1, y0, 0, Items.DIAMOND_HELMET, Items.DIAMOND_SWORD);
        guard(EntityTypes.ZOMBIE, 1, y0, 0, Items.DIAMOND_HELMET, Items.DIAMOND_AXE);
        for (int[] spot : new int[][]{{-9, -9}, {9, -9}, {-9, 9}, {9, 9}}) {
            guard(spot[0] < 0 ? EntityTypes.ZOMBIE : EntityTypes.SKELETON, spot[0], y0, spot[1], Items.IRON_HELMET,
                    spot[0] < 0 ? Items.IRON_SWORD : null);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                guard(EntityTypes.SKELETON, sx * 20, y0 + 10, sz * 20, Items.IRON_HELMET, null);
            }
        }
        return 4;
    }

    /** Une tour pleine de 5 x 5, haute de 10, et ses creneaux. */
    private void tower(int dx, int dz) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = y0; y <= y0 + 9; y++) {
                    set(cx + dx + x, y, cz + dz + z, y == y0 + 9 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                }
                if ((Math.abs(x) == 2 || Math.abs(z) == 2) && (x + z) % 2 == 0) {
                    set(cx + dx + x, y0 + 10, cz + dz + z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                }
            }
        }
    }

    // -- Palettes -------------------------------------------------------------------

    private BlockState stoneBricks() {
        int roll = random.nextInt(20);
        return roll < 12 ? Blocks.STONE_BRICKS.defaultBlockState()
                : roll < 15 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : roll < 18 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** L'obsidienne de l'enceinte, piegee une fois sur sept. */
    private BlockState trappedObsidian() {
        if (random.nextInt(7) != 0) {
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        List<Supplier<? extends Block>> traps = Arrays.asList(PalaDefense.POISON_OBSIDIAN, PalaDefense.BOOM_OBSIDIAN,
                PalaDefense.KNOCKBACK_OBSIDIAN, PalaDefense.FROZEN_OBSIDIAN);
        return traps.get(random.nextInt(traps.size())).get().defaultBlockState();
    }

    // -- Primitives -----------------------------------------------------------------

    private void set(int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }

    /** Le carre plein de demi-cote {@code r} a la hauteur {@code y}. */
    private void fill(int r, int y, Supplier<BlockState> state) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                set(cx + x, y, cz + z, state.get());
            }
        }
    }

    /** Le pourtour du carre de demi-cote {@code r} a la hauteur {@code y}. */
    private void ring(int r, int y, Supplier<BlockState> state, boolean shape) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == r) {
                    set(cx + x, y, cz + z, state.get());
                    if (shape) {
                        shaped.add(new BlockPos(cx + x, y, cz + z));
                    }
                }
            }
        }
    }

    private void shell(int r, int from, int to, Supplier<BlockState> state) {
        for (int y = from; y <= to; y++) {
            ring(r, y, state, false);
        }
    }

    /** Des creneaux : un bloc sur deux du pourtour. */
    private void merlons(int r, int y, Supplier<BlockState> state) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == r && (x + z) % 2 == 0) {
                    set(cx + x, y, cz + z, state.get());
                }
            }
        }
    }

    private void lantern(int dx, int y, int dz) {
        set(cx + dx, y, cz + dz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** Quelques toiles dans la salle, loin des coffres. */
    private void cobwebs(int r, int count) {
        for (int placed = 0, tries = 0; placed < count && tries < 40; tries++) {
            int x = random.nextInt(2 * r - 1) - (r - 1);
            int z = random.nextInt(2 * r - 1) - (r - 1);
            BlockPos pos = new BlockPos(cx + x, y0 + random.nextInt(3), cz + z);
            if (level.getBlockState(pos).isAir() && (x != 0 || z != 0)) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), FLAGS);
                placed++;
            }
        }
    }

    private void chest(int dx, int dz, Direction facing, String table) {
        BlockPos pos = new BlockPos(cx + dx, y0, cz + dz);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table(table), random.nextLong());
        }
    }

    /** Un garde de la faction : persistant, casque contre le soleil, nomme. */
    private void guard(EntityType<? extends Mob> type, int dx, int y, int dz, Item helmet, Item weapon) {
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
        if (helmet != null) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        }
        if (weapon != null) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        }
        mob.setPersistenceRequired();
        mob.addTag(EnemyBases.GUARD_TAG);
        mob.setCustomName(Component.literal("Garde des " + name));
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

    static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path));
    }
}
