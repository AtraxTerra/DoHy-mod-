package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber
public final class DungeonSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static void log(String line) {
        LOG.info("palamod-test: " + line);
    }

    private static Object call(DungeonRun run, String name) throws Exception {
        Method method = DungeonRun.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(run);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("donjonflow")
                .then(Commands.argument("palier", IntegerArgumentType.integer(1, 100)).executes(context -> {
                    int palier = IntegerArgumentType.getInteger(context, "palier");
                    ServerLevel level = context.getSource().getServer().getLevel(PalaDungeon.DIMENSION);
                    try {
                        flow(level, palier);
                    } catch (Throwable t) {
                        LOG.error("palamod-test: FLOW FAILED", t);
                    }
                    context.getSource().sendSuccess(() -> Component.literal("ok"), false);
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal("donjontest")
                .then(Commands.argument("palier", IntegerArgumentType.integer(1, 100)).executes(context -> {
                    int palier = IntegerArgumentType.getInteger(context, "palier");
                    ServerLevel level = context.getSource().getServer().getLevel(PalaDungeon.DIMENSION);
                    if (level == null) {
                        log("NO DIMENSION");
                        return 0;
                    }
                    try {
                        run(level, palier);
                    } catch (Throwable t) {
                        LOG.error("palamod-test: FAILED", t);
                    }
                    context.getSource().sendSuccess(() -> Component.literal("ok"), false);
                    return 1;
                })));
    }

    private static void run(ServerLevel level, int palier) throws Exception {
        int x = 900_000 + palier * 10_000;
        for (Dungeon dungeon : Dungeon.values()) {
            DungeonRun run = new DungeonRun(90_000 + dungeon.ordinal(), level, dungeon, palier, new BlockPos(x, 64, 0));
            log(dungeon + " p" + palier + " ----");
            for (RoomType type : RoomType.values()) {
                if (type == RoomType.EXIT) {
                    continue;
                }
                DungeonRoom room = DungeonRoom.build(level, dungeon, type, new BlockPos(x, 64, type.ordinal() * 80),
                        run.random);
                run.room = room;
                boolean floor = !level.getBlockState(room.center.below()).isAir();
                boolean wall = !level.getBlockState(new BlockPos(room.center.getX(), room.center.getY() + 1,
                        room.farWall())).isAir();
                call(run, "enter");
                StringBuilder line = new StringBuilder(type + " floor=" + floor + " wall=" + wall
                        + " phase=" + run.phase + " buttons=" + room.buttons.size()
                        + " boosts=" + run.boostOptions + " enemies=" + run.enemies.size()
                        + " bosses=" + run.bosses.size());
                if (type == RoomType.COMBAT) {
                    for (MobKind kind : MobKind.values()) {
                        Mob mob = run.spawnMob(room.spawnPoint(level, run.random), kind, false);
                        line.append(" | ").append(kind).append('=').append(mob == null ? "NULL"
                                : mob.getType().toShortString() + " hp=" + (int) mob.getMaxHealth()
                                + " scale=" + mob.getAttributeValue(Attributes.SCALE)
                                + " name=" + mob.getCustomName().getString());
                    }
                    Mob baby = run.spawnMob(room.spawnPoint(level, run.random), MobKind.MELEE, true);
                    line.append(" | baby=").append(baby == null ? "NULL" : baby.isBaby() + " hp=" + (int) baby.getMaxHealth());
                }
                if (type == RoomType.BOSS) {
                    for (UUID uuid : run.bosses) {
                        Entity entity = level.getEntity(uuid);
                        if (entity instanceof LivingEntity boss) {
                            line.append(" | boss=").append(boss.getType().toShortString()).append(" hp=")
                                    .append((int) boss.getMaxHealth()).append(" scale=")
                                    .append(boss.getAttributeValue(Attributes.SCALE)).append(" taken=")
                                    .append(DungeonManager.takenFactor(uuid));
                        }
                    }
                    call(run, "roomCleared");
                    line.append(" | chest=").append(level.getBlockState(room.center).is(Blocks.CHEST))
                            .append(" phase=").append(run.phase);
                }
                if (type == RoomType.PUZZLE) {
                    for (BlockPos button : new ArrayList<>(room.buttons)) {
                        run.pressButton(button);
                    }
                    line.append(" | afterPress phase=").append(run.phase)
                            .append(" chest=").append(level.getBlockState(room.center).is(Blocks.CHEST));
                }
                log(line.toString());
            }
            for (BossSpec spec : concat(dungeon.miniBosses(), dungeon.bosses())) {
                Mob boss = spec.type().create(level, EntitySpawnReason.MOB_SUMMONED);
                log("spec " + spec.name() + " -> " + (boss == null ? "NULL" : boss.getType().toShortString()));
            }
            for (int q : new int[]{1, 10, 30, 50, 60, 90, 100}) {
                List<ItemStack> loot = DungeonRewards.chest(level, run.random, dungeon, q, q % 5 == 0);
                StringBuilder line = new StringBuilder("loot p" + q + ": ");
                for (ItemStack stack : loot) {
                    line.append(stack.getCount()).append('x').append(stack.getItem()).append(stack.isEnchanted() ? "*" : "")
                            .append(stack.getComponentsPatch().isEmpty() ? "" : "+").append(", ");
                }
                log(line.toString());
            }
            log("unique40=" + DungeonRewards.unique(dungeon, 40) + " unique60=" + DungeonRewards.unique(dungeon, 60));
            run.cleanup();
            boolean erased = level.getBlockState(new BlockPos(x, 63, 0)).isAir();
            int left = level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(x - 40, 0, -40, x + 40, 255, 600)).size();
            log("cleanup erased=" + erased + " entitiesLeft=" + left);
            x += 1024;
        }
        // Tous les paliers de boss : pas d'erreur de tirage.
        int draws = 0;
        for (Dungeon dungeon : Dungeon.values()) {
            for (int q = 1; q <= 100; q++) {
                draws += DungeonRewards.chest(level, net.minecraft.util.RandomSource.create(), dungeon, q, q % 5 == 0).size();
                if (q % 5 == 0 && dungeon.bossFor(q) == null) {
                    log("NO BOSS for " + dungeon + " " + q);
                }
            }
        }
        log("draws=" + draws);
    }

    private static void killAll(ServerLevel level, DungeonRun run) {
        for (UUID uuid : new ArrayList<>(run.enemies.keySet())) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                living.kill(level);
            }
        }
    }

    private static void ticks(DungeonRun run, int count) {
        for (int i = 0; i < count; i++) {
            run.tick();
        }
    }

    private static void choose(DungeonRun run, net.minecraft.server.level.ServerPlayer fake, RoomType type)
            throws Exception {
        Method method = DungeonRun.class.getDeclaredMethod("choose", net.minecraft.server.level.ServerPlayer.class,
                RoomType.class);
        method.setAccessible(true);
        method.invoke(run, fake, type);
    }

    private static void flow(ServerLevel level, int palier) throws Exception {
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        log("forced before=" + level.getForceLoadedChunks().size());
        DungeonRun run = new DungeonRun(91_000, level, Dungeon.MANELIOS, palier, new BlockPos(990_000, 64, 0));
        run.begin(List.of());
        log("begin room=" + run.room.type + " phase=" + run.phase + " forced=" + level.getForceLoadedChunks().size());
        ticks(run, 31);
        log("after 31 ticks enemies=" + run.enemies.size());
        for (int round = 0; round < 6 && run.phase == DungeonRun.Phase.FIGHT; round++) {
            killAll(level, run);
            ticks(run, 10);
            log("round " + round + " enemies=" + run.enemies.size() + " phase=" + run.phase);
            ticks(run, 41);
        }
        log("room1 phase=" + run.phase + " chest=" + (run.chest != null && level.getBlockState(run.chest).is(Blocks.CHEST)));
        BlockPos first = run.room.center;
        choose(run, fake, RoomType.MERCHANT);
        log("room2=" + run.room.type + " phase=" + run.phase + " oldErased=" + level.getBlockState(first.below()).isAir()
                + " forced=" + level.getForceLoadedChunks().size());
        long merchants = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                run.room.outer()).stream().filter(run::isMerchant).count();
        log("merchant=" + merchants);
        choose(run, fake, RoomType.BOSS);
        log("room3=" + run.room.type + " phase=" + run.phase + " bosses=" + run.bosses.size());
        for (int round = 0; round < 4 && run.phase == DungeonRun.Phase.FIGHT; round++) {
            killAll(level, run);
            ticks(run, 10);
        }
        log("boss done phase=" + run.phase + " chest=" + (run.chest != null && level.getBlockState(run.chest).is(Blocks.CHEST)));
        BlockPos last = run.room.center;
        choose(run, fake, RoomType.EXIT);
        log("exit phase=" + run.phase + " erased=" + level.getBlockState(last.below()).isAir()
                + " forced=" + level.getForceLoadedChunks().size());
    }

    private static List<BossSpec> concat(List<BossSpec> a, List<BossSpec> b) {
        List<BossSpec> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
