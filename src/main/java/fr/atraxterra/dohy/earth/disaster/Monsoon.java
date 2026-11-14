package fr.atraxterra.dohy.earth.disaster;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Une averse de mousson : pendant la saison des pluies d'une region de
 * mousson, la pluie s'installe autour d'un joueur (160 blocs), tombe huit a
 * douze minutes, puis se calme.
 *
 * <p>Les rivieres, les lacs et la mer montent d'un bloc sur leurs berges, de
 * deux dans la seconde moitie de l'averse (2 500 blocs d'eau au plus). Quand
 * l'averse se calme, l'eau se retire en deux minutes et laisse du limon sur
 * les berges — de la boue et de l'argile — jusqu'au retour du terrain.
 */
final class Monsoon extends Disaster {

    static final double RADIUS = 160.0d;
    private static final int BUILD = 60 * 20;
    private static final int RECEDE = 2 * 60 * 20;
    /** L'eau posee au plus : 1 500 blocs pendant la premiere moitie de l'averse, 2 500 en tout. */
    private static final int LIMIT = 2500;
    private static final int FIRST = 1500;

    final DisasterConfig.PlacedMonsoon region;
    private final int rain;
    /** L'eau posee, dans l'ordre, et le niveau de la riviere d'ou elle est partie. */
    private final List<BlockPos> flood = new ArrayList<>();
    private final Map<BlockPos, Integer> base = new HashMap<>();
    private int silt;
    private int receded;
    /** Le plafond du moment (voir LIMIT). */
    private int cap = FIRST;

    Monsoon(int id, DisasterConfig.PlacedMonsoon region, double x, double z, RandomSource random) {
        super(id, DisasterKind.MONSOON, "Pluies de mousson", region.monsoon().name(), x, z, random);
        this.region = region;
        this.rain = (8 + random.nextInt(5)) * 60 * 20;
    }

    private boolean raining() {
        return ticks < BUILD + rain;
    }

    @Override
    double radius() {
        return RADIUS;
    }

    @Override
    DisasterNet.Entry entry() {
        int left = Math.max(0, (BUILD + rain + RECEDE - ticks) / 20);
        return new DisasterNet.Entry(id, DisasterNet.MONSOON, name, place, (int) x, (int) z, (int) RADIUS, 0, 0,
                raining() ? 1 : 2, left, new int[0]);
    }

    @Override
    float[] weather(ServerPlayer player) {
        double distance = distance(player);
        if (distance > RADIUS) {
            return null;
        }
        float edge = (float) Math.max(0.0d, Math.min(1.0d, (RADIUS - distance) / 24.0d));
        float strength;
        if (ticks < BUILD) {
            strength = ticks / (float) BUILD;
        } else if (raining()) {
            strength = 1.0f;
        } else {
            strength = Math.max(0.0f, 1.0f - (ticks - BUILD - rain) / (float) (RECEDE / 2));
        }
        float level = strength * edge;
        return new float[] {level, level * 0.45f};
    }

    void begin(ServerLevel level) {
        for (ServerPlayer player : near(level, x, z, RADIUS + 64.0d)) {
            tell(player, "Pluies de mousson (" + place + ") : le ciel s'assombrit, les rivieres vont monter.",
                    ChatFormatting.AQUA);
        }
    }

    @Override
    void tick(ServerLevel level) {
        ticks++;
        if (ticks == BUILD + rain) {
            for (ServerPlayer player : near(level, x, z, RADIUS + 64.0d)) {
                tell(player, "La mousson se calme : l'eau se retire et laisse du limon sur les berges.",
                        ChatFormatting.AQUA);
            }
        }
        if (ticks >= BUILD + rain + RECEDE) {
            while (!flood.isEmpty()) {
                recedeOne(level);
            }
            if (ledger != null) {
                DoHy.LOGGER.info("Mousson ({}) finie : {} blocs d'eau montes, {} retires, {} de limon, {} blocs "
                        + "changes", place, base.size(), receded, silt, ledger.changed);
            }
            finish(level.getServer());
            return;
        }
        if (raining()) {
            if (ticks > BUILD / 2 && ticks % 10 == 0) {
                for (ServerPlayer player : near(level, x, z, RADIUS)) {
                    rise(level, player);
                }
            }
            if (ticks > BUILD && random.nextInt(260) == 0) {
                for (ServerPlayer player : near(level, x, z, RADIUS)) {
                    lightning(level, player);
                }
            }
            return;
        }
        // l'eau se retire, a peu pres egalement sur les deux minutes
        int per = Math.max(1, (int) Math.ceil(flood.size() / (double) Math.max(1, BUILD + rain + RECEDE - ticks)));
        for (int i = 0; i < per && !flood.isEmpty(); i++) {
            recedeOne(level);
        }
    }

    /**
     * L'eau monte autour du joueur : la riviere (ou le lac, ou la mer) monte pres
     * de ses berges, avec un bord en pente vers le milieu, et deborde sur la terre
     * de proche en proche. Jamais de nappe au milieu d'un lac : l'eau va ou on la voit.
     */
    private void rise(ServerLevel level, ServerPlayer player) {
        int rise = ticks < BUILD + rain / 2 ? 1 : 2;
        cap = rise == 1 ? FIRST : LIMIT;
        if (base.size() >= cap) {
            return;
        }
        DisasterTerrain.Ledger ledger = ledger();
        ArrayDeque<BlockPos> grown = new ArrayDeque<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 32 && base.size() < cap; attempt++) {
            int bx = (int) Math.floor(player.getX() + random.nextInt(81) - 40);
            int bz = (int) Math.floor(player.getZ() + random.nextInt(81) - 40);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
            pos.set(bx, top, bz);
            if (Floods.source(level, pos) && level.getBlockState(pos.above()).isAir()) {
                grow(level, ledger, pos.immutable(), rise, grown);
            }
        }
        // l'eau gagne de proche en proche autour de ce qui vient de monter
        for (int step = 0; step < 48 && !grown.isEmpty() && base.size() < cap; step++) {
            BlockPos water = grown.poll();
            if (Math.abs(water.getX() - player.getX()) <= 56.0d && Math.abs(water.getZ() - player.getZ()) <= 56.0d
                    && Floods.source(level, water) && level.getBlockState(water.above()).isAir()) {
                grow(level, ledger, water, rise, grown);
            }
        }
    }

    /** Autour d'une eau : elle monte (pres des berges), et deborde sur ses cotes. */
    private void grow(ServerLevel level, DisasterTerrain.Ledger ledger, BlockPos water, int rise,
            ArrayDeque<BlockPos> grown) {
        // le niveau d'origine de cette eau : le sien, ou celui de la riviere d'ou elle est partie
        int surface = base.getOrDefault(water, water.getY());
        BlockPos up = water.above();
        place(level, ledger, up, Floods.over(level, up, surface, rise), surface, rise, grown);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = water.relative(direction);
            if (!Floods.free(level, side)) {
                continue;
            }
            if (Floods.overWater(level, side, surface)) {
                place(level, ledger, side, Floods.over(level, side, surface, rise), surface, rise, grown);
                continue;
            }
            // un creux d'un bloc au bord se remplit d'abord
            BlockPos dip = side.below();
            place(level, ledger, dip, Floods.beside(level, dip), surface, rise, grown);
            place(level, ledger, side, Floods.beside(level, side), surface, rise, grown);
        }
    }

    private void place(ServerLevel level, DisasterTerrain.Ledger ledger, BlockPos pos, BlockState water, int surface,
            int rise, ArrayDeque<BlockPos> grown) {
        if (water == null || pos.getY() > surface + rise || base.size() >= cap || base.containsKey(pos)) {
            return;
        }
        if (DisasterTerrain.set(level, ledger, pos, water, DisasterTerrain.FLAGS)) {
            flood.add(pos);
            base.put(pos, surface);
            if (water.getFluidState().isSource()) {
                grown.add(pos);
            }
        }
    }

    /** Un bloc d'eau se retire ; sur la terre qu'il couvrait, parfois du limon. */
    private void recedeOne(ServerLevel level) {
        BlockPos pos = flood.remove(flood.size() - 1);
        if (!level.isLoaded(pos) || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return;
        }
        BlockState before = DisasterTerrain.original(ledger, pos);
        level.setBlock(pos, before != null && before.getFluidState().isEmpty() ? before
                : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        receded++;
        BlockPos ground = pos.below();
        BlockState soil = level.getBlockState(ground);
        if ((soil.is(Blocks.GRASS_BLOCK) || soil.is(BlockTags.DIRT) || soil.is(BlockTags.SAND))
                && random.nextInt(100) < 18) {
            BlockState deposit = random.nextInt(4) == 0 ? Blocks.CLAY.defaultBlockState()
                    : Blocks.MUD.defaultBlockState();
            if (DisasterTerrain.set(level, ledger, ground, deposit, Block.UPDATE_CLIENTS)) {
                silt++;
            }
        }
    }

    private void lightning(ServerLevel level, ServerPlayer player) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }
        double a = random.nextDouble() * Math.PI * 2.0d;
        double r = 20.0d + random.nextDouble() * 40.0d;
        int bx = (int) Math.floor(player.getX() + Math.cos(a) * r);
        int bz = (int) Math.floor(player.getZ() + Math.sin(a) * r);
        bolt.snapTo(bx + 0.5d, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz + 0.5d, 0.0f, 0.0f);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }
}
