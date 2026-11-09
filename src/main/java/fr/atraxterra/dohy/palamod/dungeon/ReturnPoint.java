package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** La position d'un joueur avant son entree au donjon. */
public record ReturnPoint(String dimension, double x, double y, double z, float yaw) {

    public static final ReturnPoint NONE = new ReturnPoint("", 0.0d, 0.0d, 0.0d, 0.0f);

    public static final MapCodec<ReturnPoint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(ReturnPoint::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
            Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
            Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yaw)
    ).apply(instance, ReturnPoint::new));

    public static ReturnPoint of(ServerPlayer player) {
        return new ReturnPoint(player.level().dimension().identifier().toString(), player.getX(), player.getY(),
                player.getZ(), player.getYRot());
    }

    public boolean isNone() {
        return dimension.isEmpty();
    }

    /**
     * Ramene le joueur. Si sa dimension n'existe plus, au point
     * d'apparition de l'overworld.
     */
    public void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = null;
        Identifier id = Identifier.tryParse(dimension);
        if (id != null) {
            level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        Vec3 target = new Vec3(x, y, z);
        float facing = yaw;
        if (level == null || level.dimension() == PalaDungeon.DIMENSION) {
            LevelData.RespawnData spawn = server.overworld().getRespawnData();
            level = server.getLevel(spawn.dimension());
            if (level == null || level.dimension() == PalaDungeon.DIMENSION) {
                level = server.overworld();
            }
            target = Vec3.atBottomCenterOf(spawn.pos());
            facing = spawn.yaw();
        }
        player.teleport(new TeleportTransition(level, target, Vec3.ZERO, facing, 0.0f, TeleportTransition.DO_NOTHING));
    }
}
