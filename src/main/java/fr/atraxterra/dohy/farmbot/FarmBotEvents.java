package fr.atraxterra.dohy.farmbot;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;

/** Ce que les bots de farming changent aux evenements du jeu. */
@EventBusSubscriber
public final class FarmBotEvents {

    /** Le contour de la zone : un point par bloc, au plus tant par cote. */
    private static final int OUTLINE_STEP_MAX = 48;

    private FarmBotEvents() {
    }

    /**
     * La recolte va dans le sac du bot. En dernier : l'XP de Fermier, les contrats et les
     * collections l'ont lue avant. Ce qui ne tient pas dans le sac tombe par terre, comme d'habitude
     * (le bot s'arrete avant d'en arriver la).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        FarmBot bot = FarmBot.harvesting;
        if (bot == null || event.isCanceled()) {
            return;
        }
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack stack = drops.next().getItem();
            int left = bot.collect(stack);
            if (left <= 0) {
                drops.remove();
            } else {
                stack.setCount(left);
            }
        }
    }

    /** Il marche dans les champs sans les pietiner. */
    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof FarmBot) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bots").executes(context -> {
            FarmBots.list(context.getSource().getPlayerOrException());
            return 1;
        }));
    }

    /** Telecommande en main : la zone de son bot, ses coffres et le coin en cours scintillent. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ItemStack remote = player.getMainHandItem().getItem() instanceof BotRemoteItem ? player.getMainHandItem()
                    : player.getOffhandItem().getItem() instanceof BotRemoteItem ? player.getOffhandItem() : null;
            if (remote == null || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos corner = remote.get(DoHyFarmBots.REMOTE_CORNER.get());
            if (corner != null) {
                point(level, player, ParticleTypes.END_ROD, corner.getX() + 0.5d, corner.getY() + 1.2d,
                        corner.getZ() + 0.5d);
            }
            FarmBot bot = BotRemoteItem.bot(player, remote);
            if (bot == null) {
                continue;
            }
            BotZone zone = bot.zone();
            if (zone != null && zone.distance(player.getX(), player.getZ()) < 96.0d) {
                outline(level, player, zone);
            }
            for (BlockPos chest : bot.chests()) {
                point(level, player, ParticleTypes.HAPPY_VILLAGER, chest.getX() + 0.5d, chest.getY() + 1.15d,
                        chest.getZ() + 0.5d);
            }
        }
    }

    private static void outline(ServerLevel level, ServerPlayer player, BotZone zone) {
        double y = zone.groundY();
        double x0 = zone.min().getX();
        double z0 = zone.min().getZ();
        double x1 = zone.max().getX() + 1.0d;
        double z1 = zone.max().getZ() + 1.0d;
        int stepX = Math.max(1, zone.width() / OUTLINE_STEP_MAX);
        int stepZ = Math.max(1, zone.length() / OUTLINE_STEP_MAX);
        for (double x = x0; x <= x1; x += stepX) {
            point(level, player, ParticleTypes.ELECTRIC_SPARK, x, y, z0);
            point(level, player, ParticleTypes.ELECTRIC_SPARK, x, y, z1);
        }
        for (double z = z0; z <= z1; z += stepZ) {
            point(level, player, ParticleTypes.ELECTRIC_SPARK, x0, y, z);
            point(level, player, ParticleTypes.ELECTRIC_SPARK, x1, y, z);
        }
    }

    private static void point(ServerLevel level, ServerPlayer player, ParticleOptions particle, double x, double y,
                              double z) {
        level.sendParticles(player, particle, true, true, x, y, z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
    }
}
