package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * L'apparition des montures sauvages, en plus de la regle vanilla.
 *
 * <p>La regle vanilla (biome_modifier/dancarok.json, ravirok.json,
 * tedarok.json) ne suffit pas : les creatures paisibles apparaissent surtout a
 * la generation des chunks, et sur une carte deja generee (la Terre de
 * WorldPainter) il n'y en a aucune ; reste le tirage des creatures toutes les
 * 20 secondes, plafonne a une dizaine de betes autour du joueur, que les
 * moutons, cochons, vaches et poules remplissent : une monture n'y passait
 * presque jamais.
 *
 * <p>Ici, une fois par minute, une chance sur huit par joueur dans le monde
 * normal : une monture sauvage apparait a 32-64 blocs, hors de vue, dans un
 * biome de son espece — le Dancarok dans les plaines et les savanes
 * ({@link #BIOMES}), le Ravirok dans les montagnes, le Tedarok dans les forets
 * et les taigas —, sur de l'herbe au jour, s'il n'y en a aucune autre de la
 * meme espece a 128 blocs. Elle ne disparait jamais. La regle du jeu
 * {@code spawn_mobs} l'arrete comme les autres.
 *
 * <p>Chez Paladium, les sauvages vivaient dans la Farmland, posees a la main :
 * aucun code d'apparition dans les jars.
 */
@EventBusSubscriber
public final class DancarokSpawner {
    /** Les biomes ou vit le Dancarok : plaines, prairie, savanes. */
    public static final TagKey<Biome> BIOMES = tag("dancarok_spawns");
    /** Le Ravirok : collines battues des vents, pics, pentes. */
    public static final TagKey<Biome> RAVIROK_BIOMES = tag("ravirok_spawns");
    /** Le Tedarok : forets et taigas. */
    public static final TagKey<Biome> TEDAROK_BIOMES = tag("tedarok_spawns");

    static final int PERIOD = 1200;
    static final int CHANCE = 8;
    static final int ALONE = 128;
    private static final int TRIES = 12;

    private DancarokSpawner() {
    }

    private static TagKey<Biome> tag(String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path));
    }

    /** Les biomes de l'espece. */
    public static TagKey<Biome> biomes(MountSpecies species) {
        return switch (species) {
            case DANCAROK -> BIOMES;
            case RAVIROK -> RAVIROK_BIOMES;
            case TEDAROK -> TEDAROK_BIOMES;
        };
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % PERIOD != 0 || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && level.getRandom().nextInt(CHANCE) == 0) {
                near(level, player, level.getRandom());
            }
        }
    }

    /** Un essai pres de ce joueur ; rend la monture posee, ou {@code null}. */
    public static Dancarok near(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            int distance = 32 + random.nextInt(33);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            MountSpecies species = null;
            for (MountSpecies candidate : MountSpecies.values()) {
                if (level.getBiome(pos).is(biomes(candidate))) {
                    species = candidate;
                    break;
                }
            }
            if (species == null || !alone(level, player, species)) {
                continue;
            }
            EntityType<? extends Dancarok> type = species.entityType();
            if (!Dancarok.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)
                    || !level.noCollision(type.getSpawnAABB(x + 0.5d, pos.getY(), z + 0.5d))
                    || level.getNearestPlayer(x + 0.5d, pos.getY(), z + 0.5d, 24.0d, false) != null) {
                continue;
            }
            Dancarok mount = type.create(level, EntitySpawnReason.NATURAL);
            if (mount == null) {
                return null;
            }
            mount.snapTo(x + 0.5d, pos.getY(), z + 0.5d, random.nextFloat() * 360.0f, 0.0f);
            mount.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            level.addFreshEntity(mount);
            return mount;
        }
        return null;
    }

    /** Aucune autre sauvage de cette espece a 128 blocs. */
    private static boolean alone(ServerLevel level, ServerPlayer player, MountSpecies species) {
        return level.getEntitiesOfClass(Dancarok.class, player.getBoundingBox().inflate(ALONE),
                mount -> !mount.isTamed() && mount.species() == species).isEmpty();
    }
}
