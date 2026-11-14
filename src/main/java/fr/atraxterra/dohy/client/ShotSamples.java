package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.palamod.dungeon.RpgSkin;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/** Temporaire : galerie des modeles candidats pour les nouveaux boss. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int FLOOR = 200;
    private static final double X = 0.5d;
    private static final double Y = FLOOR + 31.0d;
    private static final double Z = 60.5d;

    /** Modele, largeur, hauteur, echelle montree. */
    private static final Object[][] GALLERY = {
            {"talikus_tree", 7.88d, 4.31d, 1.0d},
            {"talikus_miniboss", 2.97d, 3.84d, 1.0d},
            {"talikus_grandma", 4.94d, 4.12d, 1.0d},
            {"nimbria_grolem", 6.75d, 6.0d, 1.0d},
            {"vitalys_cerberus", 8.6d, 2.5d, 1.0d},
            {"vitalys_chimera", 13.86d, 3.09d, 0.8d},
            {"vitalys_marmite", 1.65d, 3.74d, 1.0d},
            {"nimbria_snake", 18.38d, 3.28d, 0.6d},
            {"talikus_distance_bow", 1.25d, 1.81d, 1.6d},
            {"talikus_assassin", 1.26d, 1.63d, 1.6d},
            {"talikus_flying", 2.44d, 1.69d, 1.6d},
            {"talikus_tank", 1.4d, 1.89d, 1.6d},
            {"talikus_mage", 1.62d, 1.25d, 1.6d},
            {"talikus_melee", 1.56d, 1.62d, 1.6d},
            {"nimbria_assassin", 1.5d, 2.12d, 1.6d},
            {"nimbria_tank", 2.58d, 2.14d, 1.6d},
            {"nimbria_melee", 2.12d, 2.12d, 1.6d},
            {"nimbria_distance", 2.05d, 2.4d, 1.6d},
            {"nimbria_flying", 1.56d, 1.03d, 1.6d},
            {"nimbria_mage", 1.62d, 1.91d, 1.6d},
            {"vitalys_distance", 1.5d, 1.94d, 1.6d},
            {"vitalys_assassin", 1.5d, 2.36d, 1.6d},
            {"vitalys_flying", 0.81d, 1.25d, 1.6d},
            {"vitalys_mage", 1.12d, 1.93d, 1.6d},
            {"vitalys_melee", 1.12d, 1.93d, 1.6d},
            {"vitalys_tank", 1.47d, 2.25d, 1.6d},
            {"manelios_assassin", 3.06d, 1.5d, 1.6d},
            {"manelios_flying", 2.51d, 1.25d, 1.6d},
            {"manelios_distance", 0.97d, 1.75d, 1.6d},
            {"manelios_melee", 1.41d, 1.75d, 1.6d},
            {"manelios_tank", 1.41d, 2.12d, 1.6d},
            {"manelios_totem", 2.51d, 1.69d, 1.6d},
    };

    private ShotSamples() {
    }

    private static ServerPlayer first(IntegratedServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            Minecraft.getInstance().gui.setScreen(null);
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(first(server));
                } catch (Throwable t) {
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
                }
            });
            return null;
        });
    }

    private static void raw(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player)
                .withPosition(player.position()), command);
    }

    /** Vole en (x, y, z), les yeux sur (lx, ly, lz). */
    private static void view(ServerPlayer player, ServerLevel level, double x, double y, double z,
                             double lx, double ly, double lz) {
        double dx = lx - x;
        double dy = ly - (y + 1.62d);
        double dz = lz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    static void register() {
        for (int i = 0; i < GALLERY.length; i++) {
            Object[] entry = GALLERY[i];
            String model = (String) entry[0];
            double width = (Double) entry[1];
            double height = (Double) entry[2];
            float scale = ((Double) entry[3]).floatValue();
            boolean firstShot = i == 0;
            ShotHarness.SHOTS.add(server(String.format("%02d_%s", i + 1, model), firstShot ? 80 : 25, player -> {
                ServerLevel level = player.level().getServer().getLevel(DoHySpawn.DIMENSION);
                if (firstShot) {
                    raw(player, "time set noon");
                    raw(player, "gamemode creative");
                    raw(player, "weather clear");
                    BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
                    for (int dx = -12; dx <= 12; dx++) {
                        for (int dz = -12; dz <= 12; dz++) {
                            level.setBlock(at.set((int) Math.floor(X) + dx, (int) Y - 1, (int) Math.floor(Z) + dz),
                                    (dx + dz) % 2 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState()
                                            : Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
                        }
                    }
                }
                for (Mob old : level.getEntitiesOfClass(Mob.class, new AABB(X - 30, Y - 10, Z - 30, X + 30, Y + 30,
                        Z + 30), mob -> mob.entityTags().contains("dohy.gallery"))) {
                    old.discard();
                }
                double size = Math.max(width, height) * scale;
                double distance = size * 1.15d + 3.5d;
                view(player, level, X, Y + height * scale * 0.45d, Z + distance, X, Y + height * scale * 0.45d, Z);
                Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                if (husk == null) {
                    return;
                }
                husk.snapTo(X, Y, Z, 0.0f, 0.0f);
                husk.setYHeadRot(0.0f);
                husk.setYBodyRot(0.0f);
                husk.setNoAi(true);
                husk.setSilent(true);
                husk.setInvulnerable(true);
                husk.setPersistenceRequired();
                husk.addTag("dohy.gallery");
                husk.setCustomName(Component.literal(model));
                husk.setData(PalaDungeon.SKIN.get(), new RpgSkin(model, "default", scale));
                level.addFreshEntity(husk);
                LOG.info("dohy-shots: galerie {} ({} x {}, echelle {})", model, width, height, scale);
            }));
        }
    }
}
