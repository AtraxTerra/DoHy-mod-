package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Les regles de la dimension des donjons.
 *
 * <ul>
 *   <li>on n'y casse ni n'y pose rien, les explosions n'y cassent rien, les
 *       monstres n'y degradent rien ;</li>
 *   <li>les monstres n'y lachent ni objet ni experience — le butin est dans
 *       les coffres — et ne se battent pas entre eux ;</li>
 *   <li>leurs degats croissent avec le palier ;</li>
 *   <li>la mort fait echouer le palier : le joueur est soigne et ramene,
 *       inventaire garde, sac perdu ;</li>
 *   <li>coffres, boutons de l'enigme, autel et marchande passent par la
 *       partie.</li>
 * </ul>
 * Le createur y echappe, pour pouvoir construire et reparer.
 */
@EventBusSubscriber
public final class DungeonEvents {

    private DungeonEvents() {
    }

    private static boolean exempt(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DungeonManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        DungeonManager.shutdown();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonManager.settle(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonManager.leave(player, DungeonRun.Leave.LOGOUT);
        }
    }

    // -- Monstres -----------------------------------------------------------------

    /**
     * Un monstre ne d'un autre (vex d'evocateur, cavalier d'araignee, petit
     * cube) rejoint la partie dont il est dans la salle : il compte pour
     * finir la salle et frappe au facteur du palier.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !DungeonManager.levelIs(level)
                || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)
                || mob.entityTags().contains(DungeonRun.TAG)) {
            return;
        }
        DungeonRun run = DungeonManager.runAt(level, mob.getX(), mob.getZ());
        if (run == null || run.phase != DungeonRun.Phase.FIGHT) {
            event.setCanceled(true);
            return;
        }
        mob.addTag(DungeonRun.TAG);
        mob.setPersistenceRequired();
        run.track(mob, MobKind.MELEE, 1.0f);
    }

    @SubscribeEvent
    public static void onSplit(MobSplitEvent event) {
        if (DungeonManager.inDimension(event.getParent())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player) && DungeonManager.inDimension(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Player) && DungeonManager.inDimension(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() != null && DungeonManager.inDimension(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (DungeonManager.levelIs(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    /**
     * Les degats : facteur du palier pour ce que frappe un monstre de partie
     * (sa fleche et sa boule de feu comprises, la source etant le tireur) ;
     * part encaissee pour un boss au plafond de vie ; et pas de combat entre
     * monstres de partie.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!DungeonManager.inDimension(target)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        boolean targetTracked = DungeonManager.runOfMob(target.getUUID()) != null;
        if (source != null && source != target && DungeonManager.runOfMob(source.getUUID()) != null) {
            if (targetTracked || target.entityTags().contains(DungeonRun.TAG)) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(event.getAmount() * DungeonManager.runOfMob(source.getUUID()).damageFactor());
        }
        if (targetTracked) {
            event.setAmount(event.getAmount() * DungeonManager.takenFactor(target.getUUID()));
        }
        if (target.entityTags().contains(DungeonRun.MERCHANT_TAG)) {
            event.setCanceled(true);
        }
    }

    /**
     * Avant la penalite de mort de DoHy : une mort au donjon ne coute que le
     * palier. A Manelios, le guerrier « se demultiplie a sa mort » : deux
     * petits noyes.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!DungeonManager.inDimension(entity)) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            DungeonRun run = DungeonManager.runOf(player);
            if (run == null || exempt(player)) {
                return;
            }
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            player.clearFire();
            player.getFoodData().setFoodLevel(20);
            DungeonManager.leave(player, DungeonRun.Leave.DEATH);
            return;
        }
        if (entity instanceof Mob mob && mob.entityTags().contains(DungeonRun.SPLIT_TAG)) {
            DungeonRun run = DungeonManager.runOfMob(mob.getUUID());
            if (run != null && run.phase == DungeonRun.Phase.FIGHT) {
                for (int i = 0; i < 2; i++) {
                    BlockPos pos = mob.blockPosition();
                    run.spawnMob(pos, MobKind.MELEE, true);
                }
            }
        }
    }

    // -- Blocs --------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && DungeonManager.inDimension(player) && !exempt(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DungeonManager.inDimension(player)
                && !exempt(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Coffre, bouton, autel : la partie decide. Tout autre bloc a interface
     * de la salle (la table d'enchantement de l'autel en est un) est refuse.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DungeonManager.inDimension(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        DungeonRun run = DungeonManager.runOf(player);
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        if (state.is(Blocks.CHEST)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (main && run != null) {
                run.openChest(player, pos);
            }
            return;
        }
        if (state.is(Blocks.ENCHANTING_TABLE)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (main && run != null && run.room != null && run.room.type == RoomType.BOOST
                    && pos.equals(run.room.center)) {
                DungeonMenus.openBoost(player, run);
            }
            return;
        }
        if (state.getBlock() instanceof ButtonBlock) {
            if (main && run != null) {
                run.pressButton(pos.immutable());
            }
            return;
        }
        if (!exempt(player) && event.getLevel().getBlockEntity(pos) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getTarget().entityTags().contains(DungeonRun.MERCHANT_TAG)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        DungeonRun run = DungeonManager.runOf(player);
        if (event.getHand() == InteractionHand.MAIN_HAND && run != null && run.isMerchant(event.getTarget())) {
            DungeonMenus.openMerchant(player, run);
        }
    }

    // -- Commandes ----------------------------------------------------------------

    /** {@code /donjon quitter} : abandonner le palier ; {@code /donjon sac} : voir son sac. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("donjon")
                .then(Commands.literal("quitter").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (DungeonManager.runOf(player) == null) {
                        DungeonManager.message(player, ChatFormatting.RED, "not_inside");
                        return 0;
                    }
                    DungeonManager.leave(player, DungeonRun.Leave.QUIT);
                    return 1;
                }))
                .then(Commands.literal("sac").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    DungeonRun run = DungeonManager.runOf(player);
                    if (run == null) {
                        DungeonManager.message(player, ChatFormatting.RED, "not_inside");
                        return 0;
                    }
                    DungeonMenus.openBag(player, run);
                    return 1;
                })));
    }
}
