package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.cryo.CryoChamberBlock;
import fr.atraxterra.dohy.cryo.CryoChamberBlockEntity;
import fr.atraxterra.dohy.cryo.DoHyCryo;
import fr.atraxterra.dohy.cryo.Dna;
import fr.atraxterra.dohy.spawn.SpawnApi;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Temporaire : la chambre cryogenique avec la vache, le mouton, le cochon et la poule. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static BlockPos origin;
    private static final List<EntityType<?>> MOBS = List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG,
            EntityTypes.CHICKEN);

    private ShotSamples() {
    }

    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
                }
            });
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void tp(ServerPlayer player, double dx, double dy, double dz, float yaw, float pitch) {
        command(player, String.format(java.util.Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f",
                player.getName().getString(), origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw,
                pitch));
    }

    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [cryo] " + format, args);
    }

    private static BlockPos base(int index) {
        return origin.offset(index * 4, 0, 0);
    }

    /** Pose une chambre comme le fait son objet : 45 cases, la console vers le sud. */
    private static void place(ServerLevel level, BlockPos base) {
        BlockState part = DoHyCryo.CHAMBER.get().defaultBlockState().setValue(CryoChamberBlock.FACING, Direction.SOUTH);
        for (int y = 0; y < 5; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(base.offset(x, y, z), part.setValue(CryoChamberBlock.PX, x + 1)
                            .setValue(CryoChamberBlock.PY, y).setValue(CryoChamberBlock.PZ, z + 1), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static String outputs(CryoChamberBlockEntity chamber) {
        List<String> out = new ArrayList<>();
        for (int slot = CryoChamberBlockEntity.FIRST_OUTPUT;
             slot < CryoChamberBlockEntity.FIRST_OUTPUT + CryoChamberBlockEntity.OUTPUTS; slot++) {
            ItemStack stack = chamber.getItem(slot);
            if (!stack.isEmpty()) {
                out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + " x" + stack.getCount());
            }
        }
        return out.isEmpty() ? "(rien)" : String.join(", ", out);
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 80, player -> {
            ShotHarness.until = () -> !SpawnApi.building();
            command(player, "time set noon");
            command(player, "weather clear");
            command(player, "gamerule doDaylightCycle false");
            command(player, "gamerule doMobSpawning false");
            player.setGameMode(GameType.CREATIVE);
            origin = new BlockPos(300, -60, 300);
            command(player, String.format("fill %d %d %d %d %d %d air", origin.getX() - 4, origin.getY(),
                    origin.getZ() - 4, origin.getX() + 18, origin.getY() + 8, origin.getZ() + 14));
            tp(player, 6.5, 0, 10.5, 180.0f, 0.0f);
        }));
        ShotHarness.SHOTS.add(server("a1_chambres", 40, player -> {
            ServerLevel level = (ServerLevel) player.level();
            for (int index = 0; index < MOBS.size(); index++) {
                place(level, base(index));
                if (level.getBlockEntity(base(index)) instanceof CryoChamberBlockEntity chamber) {
                    chamber.setItem(CryoChamberBlockEntity.SLOT_DNA, Dna.stack(MOBS.get(index), Dna.MAX_GRADE));
                    chamber.setItem(CryoChamberBlockEntity.SLOT_COLD, new ItemStack(Items.BLUE_ICE, 64));
                    log("{} : chambre posee", BuiltInRegistries.ENTITY_TYPE.getKey(MOBS.get(index)));
                } else {
                    log("{} : PAS DE CHAMBRE", BuiltInRegistries.ENTITY_TYPE.getKey(MOBS.get(index)));
                }
            }
        }));
        ShotHarness.SHOTS.add(server("a2_cycles", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            for (int index = 0; index < MOBS.size(); index++) {
                if (level.getBlockEntity(base(index)) instanceof CryoChamberBlockEntity chamber) {
                    // trois cycles de grade V (600 ticks chacun)
                    for (int tick = 0; tick < 1850; tick++) {
                        chamber.serverTick();
                    }
                    log("{} : sterile {} ; butin : {}", BuiltInRegistries.ENTITY_TYPE.getKey(MOBS.get(index)),
                            !Dna.yields(level, MOBS.get(index)), outputs(chamber));
                }
            }
        }));
        ShotHarness.SHOTS.add(server("a3_seringue", 20, player -> {
            ServerLevel level = (ServerLevel) player.level();
            ItemStack syringe = new ItemStack(DoHyCryo.SYRINGE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, syringe);
            int index = 0;
            for (EntityType<?> type : MOBS) {
                var mob = type.create(level, EntitySpawnReason.COMMAND);
                if (!(mob instanceof LivingEntity living)) {
                    continue;
                }
                living.snapTo(origin.getX() + 2.5 + index * 2, origin.getY(), origin.getZ() + 6.5, 0.0f, 0.0f);
                level.addFreshEntity(living);
                int before = player.getInventory().countItem(DoHyCryo.DNA.get());
                var mobFirst = living.interact(player, InteractionHand.MAIN_HAND);
                var result = mobFirst.consumesAction() ? mobFirst
                        : player.getMainHandItem().interactLivingEntity(player, living, InteractionHand.MAIN_HAND);
                int after = player.getInventory().countItem(DoHyCryo.DNA.get());
                log("seringue sur {} : la bete repond {}, la seringue {}, fioles {} -> {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(type), mobFirst, result, before, after);
                index++;
            }
        }));
        ShotHarness.SHOTS.add(server("b1_cuves", 40, player -> tp(player, 6.5, 2.5, 9.5, 180.0f, 8.0f)));
        ShotHarness.SHOTS.add(server("b2_mouton_cochon", 30, player -> tp(player, 6.0, 2.2, 5.6, 180.0f, 8.0f)));
        ShotHarness.SHOTS.add(server("b3_poule", 30, player -> tp(player, 12.0, 2.2, 4.6, 180.0f, 8.0f)));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> LOG.info("dohy-shots: [fin] {}",
                player.getName().getString())));
    }
}
