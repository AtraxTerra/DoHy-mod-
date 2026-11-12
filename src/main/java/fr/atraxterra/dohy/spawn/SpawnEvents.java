package fr.atraxterra.dohy.spawn;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.stats.Stats;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Les regles du spawn : on n'y casse rien, on n'y pose rien, on n'y prend
 * aucun degat, et qui tombe dans le vide revient sur la plateforme. Le mode
 * creatif echappe aux deux premieres, pour qui veut retoucher le lieu.
 */
@EventBusSubscriber
public final class SpawnEvents {

    /** En dessous d'une minute de jeu, un joueur est un nouveau venu. */
    private static final int NEWCOMER_TICKS = 1200;

    private SpawnEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spawn").executes(context -> {
            SpawnApi.toggle(context.getSource().getPlayerOrException());
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SpawnApi.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SpawnApi.stop();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && SpawnApi.inSpawn(player) && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SpawnApi.inSpawn(player) && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == DoHySpawn.DIMENSION) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SpawnApi.inSpawn(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Le vide : qui tombe sous le spawn est remis sur la plateforme d'arrivee. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && SpawnApi.inSpawn(player)
                && player.getY() < SpawnBuilder.VOID_Y && player.level() instanceof ServerLevel level) {
            SpawnApi.toPad(player, level);
        }
    }

    /**
     * La premiere connexion : un joueur qui n'a encore rien fait dans ce monde
     * est envoye au spawn. Celui qui jouait deja avant que le spawn existe est
     * seulement marque : on ne l'arrache pas a sa base.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getData(DoHySpawn.WELCOMED.get())) {
            return;
        }
        player.setData(DoHySpawn.WELCOMED.get(), true);
        boolean fresh = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) < NEWCOMER_TICKS;
        if (fresh && SpawnConfig.config().firstJoin()) {
            SpawnApi.arriveSoon(player);
        }
    }

    /** Apres une mort, sans lit ni ancre, on reapparait au spawn. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()
                && SpawnConfig.config().respawn() && player.getRespawnConfig() == null) {
            SpawnApi.arriveSoon(player);
        }
    }

    /** Les tonneaux du spawn sont un decor : ils ne s'ouvrent pas. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && SpawnApi.inSpawn(player) && !player.isCreative()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BarrelBlock) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    /** Un texte flottant revenu d'une sauvegarde est un double : son bloc en a deja refait un. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().entityTags().contains(HologramBlockEntity.TAG)) {
            event.setCanceled(true);
        }
    }
}
