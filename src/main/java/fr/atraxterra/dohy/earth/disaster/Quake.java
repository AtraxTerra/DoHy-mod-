package fr.atraxterra.dohy.earth.disaster;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthDeposits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un seisme : une premiere secousse, le choc principal, puis trois repliques.
 *
 * <p>Il se ressent jusqu'a 40 blocs pour une magnitude 5, deux fois plus loin a
 * chaque degre (320 blocs pour une magnitude 8) ; il abime le terrain dans un
 * rayon de 8 blocs plus 8 par degre au-dessus de 5. Au choc principal une
 * faille s'ouvre depuis l'epicentre, de plus en plus longue, large et profonde
 * avec la magnitude ; ses parois mettent a nu les filons du pays (ceux de ses
 * gisements, sinon du charbon, du fer, du cuivre, de l'or et de la redstone).
 * Les pentes s'eboulent, les arbres perdent des feuilles, tout ce qui est
 * debout est bouscule. La faille ne s'ouvre jamais sous les pieds d'un joueur.
 */
final class Quake extends Disaster {

    private static final int FORESHOCK = 40;

    final double magnitude;
    final String zone;
    private final double felt;
    private final double damage;
    final double angle;
    private final int length;
    private final int width;
    private final int depth;
    private final int main;
    private final int[] aftershocks;
    private final int aftershockLength = 80;
    private final List<BlockState[]> ores = new ArrayList<>();
    private boolean physical;
    private boolean prepared;
    private int held;
    private int opened = -1;
    private int revealed;
    private int falls;
    private int end;

    Quake(int id, double magnitude, String zone, String country, double x, double z, double angle,
          RandomSource random) {
        super(id, DisasterKind.QUAKE, "Seisme de magnitude " + magnitude(magnitude), country.isEmpty() ? zone
                : country, x, z, random);
        this.magnitude = magnitude;
        this.zone = zone;
        this.felt = 40.0d * Math.pow(2.0d, magnitude - 5.0d);
        this.damage = Math.min(40.0d, 8.0d + 8.0d * (magnitude - 5.0d));
        this.angle = angle;
        this.length = (int) Math.round(damage);
        this.width = magnitude < 6.0d ? 1 : magnitude < 7.5d ? 2 : 3;
        this.depth = (int) Math.round(5.0d + 2.5d * (magnitude - 5.0d)) + random.nextInt(3);
        this.main = (int) Math.round(200 + 160 * (magnitude - 5.0d));
        int after = FORESHOCK + main;
        this.aftershocks = new int[] {after + 600 + random.nextInt(200), after + 1500 + random.nextInt(300),
                after + 2600 + random.nextInt(400)};
        this.end = aftershocks[2] + aftershockLength + 20;
    }

    /** « 6,4 ». */
    static String magnitude(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }

    @Override
    double radius() {
        return felt;
    }

    /** La force a l'epicentre : 0,2 pour une magnitude 5, 1 a partir de 8,6. */
    private double strength() {
        return Math.max(0.15d, Math.min(1.0d, (magnitude - 4.0d) / 4.6d));
    }

    /** La part de la secousse en ce moment : 0 au calme, 1 au plus fort du choc principal. */
    private double phase() {
        if (ticks < FORESHOCK) {
            return 0.3d;
        }
        if (ticks < FORESHOCK + main) {
            int t = ticks - FORESHOCK;
            double up = Math.min(1.0d, t / 20.0d);
            double down = Math.min(1.0d, (main - t) / 40.0d);
            return Math.max(0.3d, Math.min(up, down));
        }
        for (int start : aftershocks) {
            if (ticks >= start && ticks < start + aftershockLength) {
                int t = ticks - start;
                return 0.45d * Math.min(1.0d, Math.min(t / 10.0d, (aftershockLength - t) / 20.0d));
            }
        }
        return 0.0d;
    }

    private boolean mainShock() {
        return ticks >= FORESHOCK && ticks < FORESHOCK + main;
    }

    @Override
    float shake(ServerPlayer player) {
        double falloff = Math.max(0.0d, 1.0d - distance(player) / felt);
        return (float) (strength() * Math.pow(falloff, 0.8d) * phase());
    }

    @Override
    DisasterNet.Entry entry() {
        int left = Math.max(0, (end - ticks) / 20);
        return new DisasterNet.Entry(id, DisasterNet.QUAKE, name, place, (int) x, (int) z, (int) felt, 0,
                (int) Math.round(magnitude * 10.0d), ticks < FORESHOCK + main ? 1 : 2, left, new int[0]);
    }

    /** Commence : annonce, et le terrain se releve si un joueur la ressent. */
    void begin(ServerLevel level) {
        List<ServerPlayer> close = near(level, x, z, felt);
        physical = !close.isEmpty();
        EarthData earth = EarthData.get();
        if (physical && earth != null) {
            for (EarthDeposits.Ore ore : EarthDeposits.oresAt(earth, (int) x, (int) z)) {
                ores.add(new BlockState[] {ore.ore(), ore.deepslate()});
            }
            if (ores.isEmpty()) {
                ores.add(new BlockState[] {Blocks.COAL_ORE.defaultBlockState(),
                        Blocks.DEEPSLATE_COAL_ORE.defaultBlockState()});
                ores.add(new BlockState[] {Blocks.IRON_ORE.defaultBlockState(),
                        Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()});
                ores.add(new BlockState[] {Blocks.COPPER_ORE.defaultBlockState(),
                        Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState()});
                if (magnitude >= 6.5d) {
                    ores.add(new BlockState[] {Blocks.GOLD_ORE.defaultBlockState(),
                            Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState()});
                    ores.add(new BlockState[] {Blocks.REDSTONE_ORE.defaultBlockState(),
                            Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState()});
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            double distance = distance(player);
            if (distance <= felt) {
                title(player, "Seisme !", ChatFormatting.RED, "Magnitude " + magnitude(magnitude) + " - " + place);
                tell(player, name + " (" + place + "), epicentre a " + (int) distance + " blocs "
                        + Disasters.direction(player.getX(), player.getZ(), x, z) + ".", ChatFormatting.RED);
            } else if (magnitude >= 6.0d) {
                tell(player, name + " : " + place + " (" + Disasters.blocks((int) distance) + " blocs "
                        + Disasters.direction(player.getX(), player.getZ(), x, z) + ").", ChatFormatting.GRAY);
            }
        }
    }

    @Override
    void tick(ServerLevel level) {
        if (physical && !prepared) {
            prepared = DisasterTerrain.prepare(level, ledger(), x, z, damage + 24.0d);
            if (!prepared && ticks == FORESHOCK - 1 && held < 100) {
                // le releve n'est pas fini : la premiere secousse dure un peu plus
                held++;
                return;
            }
        }
        ticks++;
        if (ticks >= end) {
            finishWith(level);
            return;
        }
        double force = phase();
        if (force <= 0.0d) {
            return;
        }
        List<ServerPlayer> feeling = near(level, x, z, felt);
        if (ticks % 30 == 1) {
            for (ServerPlayer player : feeling) {
                sound(player, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), x, ground(level, (int) x, (int) z), z,
                        (float) (0.8d + force), 0.5f + random.nextFloat() * 0.2f);
            }
        }
        if (ticks == FORESHOCK) {
            for (ServerPlayer player : feeling) {
                sound(player, SoundEvents.GENERIC_EXPLODE.value(), x, ground(level, (int) x, (int) z), z, 0.6f,
                        0.4f);
            }
        }
        if (!physical || !prepared) {
            return;
        }
        if (mainShock()) {
            openFissure(level);
            jolt(level, force);
        }
        slides(level, force);
        leaves(level, force);
    }

    private void finishWith(ServerLevel level) {
        if (physical) {
            for (ServerPlayer player : near(level, x, z, felt)) {
                tell(player, "Les secousses ont cesse." + (revealed > 0
                        ? " La faille met des filons a nu : ils resteront jusqu'au retour du terrain, dans "
                        + Disasters.hours() + "." : ""), ChatFormatting.GOLD);
            }
            fr.atraxterra.dohy.DoHy.LOGGER.info("Seisme M{} ({}) fini : faille de {} colonnes, {} blocs de minerai, "
                    + "{} eboulis, {} blocs changes", magnitude(magnitude), place, opened + 1, revealed, falls,
                    ledger == null ? 0 : ledger.changed);
        }
        finish(level.getServer());
    }

    /** La faille s'ouvre de l'epicentre vers ses deux bouts, sur les deux premiers tiers du choc. */
    private void openFissure(ServerLevel level) {
        int t = ticks - FORESHOCK;
        int target = (int) Math.min(length, Math.floor(length * t / (main * 0.65d)));
        while (opened < target) {
            opened++;
            column(level, opened);
            if (opened > 0) {
                column(level, -opened);
            }
        }
    }

    private void column(ServerLevel level, int step) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        double taper = 1.0d - Math.pow(Math.abs(step) / (double) Math.max(1, length), 2.0d);
        int deep = Math.max(2, (int) Math.round(depth * taper));
        int w = Math.abs(step) > length * 0.8d ? 1 : width;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int across = 0; across < w; across++) {
            double offset = across - (w - 1) / 2.0d;
            double cx = x + dx * step - dz * offset;
            double cz = z + dz * step + dx * offset;
            int bx = (int) Math.floor(cx);
            int bz = (int) Math.floor(cz);
            if (underFoot(level, bx, bz)) {
                continue;
            }
            int top = ground(level, bx, bz);
            if (level.getBlockState(pos.set(bx, top + 1, bz)).is(BlockTags.LOGS)
                    || level.getBlockState(pos.set(bx, top, bz)).is(BlockTags.LOGS)) {
                // pas d'arbre suspendu au-dessus de la faille
                continue;
            }
            // ce qui pousse dessus part avec
            BlockState above = level.getBlockState(pos.set(bx, top + 1, bz));
            if (soft(above) && !above.isAir()) {
                DisasterTerrain.set(level, ledger, pos.immutable(), Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
            for (int y = top; y > top - deep; y--) {
                BlockState state = level.getBlockState(pos.set(bx, y, bz));
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                if (natural(state)) {
                    if (DisasterTerrain.set(level, ledger, pos.immutable(), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS) && y == top && random.nextInt(3) == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), bx + 0.5d,
                                y + 0.8d, bz + 0.5d, 12, 0.4d, 0.6d, 0.4d, 0.05d);
                    }
                }
            }
            if (magnitude >= 7.0d && taper > 0.7d) {
                DisasterTerrain.set(level, ledger, new BlockPos(bx, top - deep, bz),
                        Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (Math.abs(step) % 3 == 0 && across == 0) {
                for (ServerPlayer player : near(level, cx, cz, 40.0d)) {
                    sound(player, random.nextBoolean() ? SoundEvents.STONE_BREAK : SoundEvents.GRAVEL_BREAK, cx, top,
                            cz, 1.2f, 0.6f);
                }
            }
            if (across == 0 || across == w - 1) {
                walls(level, cx, cz, dx, dz, across == 0 ? -1 : 1, top, deep, w);
            }
        }
    }

    /** Les parois : des filons du pays dans la roche mise a nu. */
    private void walls(ServerLevel level, double cx, double cz, double dx, double dz, int side, int top, int deep,
                       int w) {
        double push = side * (w == 1 ? 1.0d : 1.0d);
        int wx = (int) Math.floor(cx - dz * push);
        int wz = (int) Math.floor(cz + dx * push);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top - 2; y > top - deep; y--) {
            if (random.nextFloat() >= 0.16f) {
                continue;
            }
            BlockState state = level.getBlockState(pos.set(wx, y, wz));
            boolean deepslate = state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF);
            if (!state.is(BlockTags.BASE_STONE_OVERWORLD) && !deepslate) {
                continue;
            }
            BlockState[] ore = ores.get(random.nextInt(ores.size()));
            if (DisasterTerrain.set(level, ledger, pos.immutable(), deepslate ? ore[1] : ore[0],
                    Block.UPDATE_CLIENTS)) {
                revealed++;
            }
        }
    }

    private boolean underFoot(ServerLevel level, int bx, int bz) {
        for (ServerPlayer player : level.players()) {
            if (Math.abs(player.getX() - (bx + 0.5d)) < 1.6d && Math.abs(player.getZ() - (bz + 0.5d)) < 1.6d) {
                return true;
            }
        }
        return false;
    }

    /** Les pentes s'eboulent : un bloc au bord d'une pente tombe vers le bas. */
    private void slides(ServerLevel level, double force) {
        int attempts = (int) Math.round(force * strength() * 4.0d);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            double r = damage * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0d;
            int bx = (int) Math.floor(x + Math.cos(a) * r);
            int bz = (int) Math.floor(z + Math.sin(a) * r);
            if (!DisasterTerrain.surrounded(ledger, bx, bz) || underFoot(level, bx, bz)) {
                continue;
            }
            int top = ground(level, bx, bz);
            pos.set(bx, top, bz);
            BlockState state = level.getBlockState(pos);
            if (!natural(state) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                    || !DisasterTerrain.editable(level, ledger, pos)) {
                continue;
            }
            int[] lower = null;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (ground(level, bx + d[0], bz + d[1]) <= top - 2) {
                    lower = d;
                    break;
                }
            }
            if (lower == null || level.getBlockState(pos.set(bx, top + 1, bz)).is(BlockTags.LOGS)) {
                continue;
            }
            FallingBlockEntity falling = FallingBlockEntity.fall(level, new BlockPos(bx, top, bz), state);
            falling.dropItem = false;
            falling.setHurtsEntities(1.0f, 8);
            falling.setDeltaMovement(lower[0] * 0.18d, 0.12d, lower[1] * 0.18d);
            falls++;
        }
    }

    /** Les arbres perdent des feuilles. */
    private void leaves(ServerLevel level, double force) {
        int attempts = (int) Math.round(force * 3.0d);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            double r = damage * 1.5d * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2.0d;
            int bx = (int) Math.floor(x + Math.cos(a) * r);
            int bz = (int) Math.floor(z + Math.sin(a) * r);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            BlockState state = level.getBlockState(pos.set(bx, top, bz));
            if (state.is(BlockTags.LEAVES) && DisasterTerrain.set(level, ledger, pos.immutable(),
                    Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), bx + 0.5d, top + 0.5d,
                        bz + 0.5d, 10, 0.5d, 0.3d, 0.5d, 0.02d);
            }
        }
    }

    /** Ce qui est debout est bouscule. */
    private void jolt(ServerLevel level, double force) {
        if (ticks % 4 != 0) {
            return;
        }
        double reach = damage * 1.5d;
        AABB box = new AABB(x - reach, level.getMinY(), z - reach, x + reach, level.getMaxY(), z + reach);
        double push = 0.07d * force * strength();
        for (Entity entity : level.getEntities((Entity) null, box, found -> found instanceof LivingEntity
                && found.onGround())) {
            if (entity instanceof ServerPlayer player && (player.isSpectator() || player.getAbilities().flying)) {
                continue;
            }
            entity.push((random.nextDouble() - 0.5d) * push, 0.02d * force, (random.nextDouble() - 0.5d) * push);
            entity.hurtMarked = true;
        }
    }
}
