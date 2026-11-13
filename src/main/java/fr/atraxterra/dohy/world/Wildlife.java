package fr.atraxterra.dohy.world;

import fr.atraxterra.dohy.palamod.mount.PalaMount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * La faune de l'overworld : quand il y a trop peu d'animaux autour d'un joueur,
 * de nouveaux groupes arrivent, hors de vue.
 *
 * <p><b>Pourquoi.</b> Les animaux paisibles apparaissent surtout a la generation
 * des chunks ; ensuite, le jeu n'en ajoute que toutes les 20 secondes et jamais
 * au-dela d'une dizaine autour du joueur, et ceux qu'on chasse ne reviennent
 * guere. Sur une carte faite d'avance (la Terre de WorldPainter), la
 * generation n'en a pas pose du tout : des regions entieres restaient vides.
 *
 * <p><b>Comment.</b> Toutes les 20 secondes, pour chaque joueur de l'overworld :
 * s'il y a moins de {@value #TARGET} animaux a {@value #RADIUS} blocs, deux
 * groupes au plus arrivent a 32-80 blocs, hors de vue, tires dans la liste du
 * biome comme le fait le jeu — les animaux des mods compris (les pigeons de NTM,
 * par exemple), sauf le Dancarok, qui a son propre tirage
 * ({@code DancarokSpawner}). Chaque bete passe par la regle d'apparition de son
 * espece (le bon sol, la lumiere) : pas de mouton sur la neige ni dans un
 * desert. Groupes de quatre au plus. La regle du jeu {@code spawn_mobs}
 * l'arrete.
 */
@EventBusSubscriber
public final class Wildlife {
    static final int PERIOD = 400;
    /** Decale du tirage vanilla des creatures (toutes les 400 ticks, a 0). */
    static final int OFFSET = 200;
    static final int TARGET = 24;
    static final int RADIUS = 96;
    static final int GROUPS = 2;
    static final int MAX_GROUP = 4;
    private static final int TRIES = 10;

    private Wildlife() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % PERIOD != OFFSET || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                restock(level, player, level.getRandom());
            }
        }
    }

    /** Les animaux a {@value #RADIUS} blocs de ce joueur. */
    public static int around(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS, 64, RADIUS),
                entity -> entity.getType().getCategory() == MobCategory.CREATURE).size();
    }

    /** Un passage pour ce joueur ; rend le nombre d'animaux poses. */
    public static int restock(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (around(level, player) >= TARGET) {
            return 0;
        }
        int placed = 0;
        for (int group = 0; group < GROUPS; group++) {
            placed += group(level, player, random);
        }
        return placed;
    }

    private static int group(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            int distance = 32 + random.nextInt(49);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos pos = surface(level, x, z);
            if (pos == null || level.getNearestPlayer(x + 0.5d, pos.getY(), z + 0.5d, 24.0d, false) != null) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(pos);
            MobSpawnSettings.SpawnerData data = biome.value().getMobSettings().getMobs(MobCategory.CREATURE)
                    .getRandom(random).orElse(null);
            if (data == null || data.type() == PalaMount.DANCAROK.get() || !fits(level, data.type(), pos, random)) {
                continue;
            }
            int size = Math.min(MAX_GROUP, data.minCount() + random.nextInt(Math.max(1,
                    data.maxCount() - data.minCount() + 1)));
            int placed = 0;
            SpawnGroupData shared = null;
            for (int member = 0; member < size * 3 && placed < size; member++) {
                BlockPos at = member == 0 ? pos
                        : surface(level, x + random.nextInt(9) - 4, z + random.nextInt(9) - 4);
                if (at == null || !fits(level, data.type(), at, random)) {
                    continue;
                }
                Entity entity = data.type().create(level, EntitySpawnReason.NATURAL);
                if (!(entity instanceof Mob mob)) {
                    break;
                }
                mob.snapTo(at.getX() + 0.5d, at.getY(), at.getZ() + 0.5d, random.nextFloat() * 360.0f, 0.0f);
                shared = mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, shared);
                level.addFreshEntity(mob);
                placed++;
            }
            if (placed > 0) {
                return placed;
            }
        }
        return 0;
    }

    /** Le premier bloc libre au-dessus du sol, dans un chunk deja charge ; {@code null} sinon. */
    private static BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** La regle d'apparition de l'espece (sol, lumiere, place) en ce point. */
    private static boolean fits(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random) {
        return SpawnPlacements.isSpawnPositionOk(type, level, pos)
                && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)
                && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d));
    }
}
