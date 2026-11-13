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
 * L'apparition des Dancaroks sauvages, en plus de la regle vanilla.
 *
 * <p>La regle vanilla (biome_modifier/dancarok.json) ne suffit pas : les
 * creatures paisibles apparaissent surtout a la generation des chunks, et sur
 * une carte deja generee (la Terre de WorldPainter) il n'y en a aucune ; reste
 * le tirage des creatures toutes les 20 secondes, plafonne a une dizaine de
 * betes autour du joueur, que les moutons, cochons, vaches et poules
 * remplissent : un Dancarok n'y passait presque jamais.
 *
 * <p>Ici, une fois par minute, une chance sur huit par joueur dans le monde
 * normal : un Dancarok sauvage apparait a 32-64 blocs, hors de vue, dans une
 * plaine ou une savane ({@link #BIOMES}), sur de l'herbe au jour, s'il n'y en
 * a aucun autre sauvage a 128 blocs. Il ne disparait jamais : on en croise un
 * par coin de plaine ou l'on passe un peu de temps (huit minutes en moyenne).
 * La regle du jeu {@code spawn_mobs} l'arrete comme les autres.
 */
@EventBusSubscriber
public final class DancarokSpawner {
    /** Les biomes ou il vit : plaines, prairie, savanes. */
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "dancarok_spawns"));

    static final int PERIOD = 1200;
    static final int CHANCE = 8;
    static final int ALONE = 128;
    private static final int TRIES = 12;

    private DancarokSpawner() {
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

    /** Un essai pres de ce joueur ; rend le Dancarok pose, ou {@code null}. */
    public static Dancarok near(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (!level.getEntitiesOfClass(Dancarok.class, player.getBoundingBox().inflate(ALONE),
                dancarok -> !dancarok.isTamed()).isEmpty()) {
            return null;
        }
        EntityType<Dancarok> type = PalaMount.DANCAROK.get();
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            int distance = 32 + random.nextInt(33);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getBiome(pos).is(BIOMES)
                    || !Dancarok.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)
                    || !level.noCollision(type.getSpawnAABB(x + 0.5d, pos.getY(), z + 0.5d))
                    || level.getNearestPlayer(x + 0.5d, pos.getY(), z + 0.5d, 24.0d, false) != null) {
                continue;
            }
            Dancarok dancarok = type.create(level, EntitySpawnReason.NATURAL);
            if (dancarok == null) {
                return null;
            }
            dancarok.snapTo(x + 0.5d, pos.getY(), z + 0.5d, random.nextFloat() * 360.0f, 0.0f);
            dancarok.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            level.addFreshEntity(dancarok);
            return dancarok;
        }
        return null;
    }
}
