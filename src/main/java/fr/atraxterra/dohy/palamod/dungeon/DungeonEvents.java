package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
    public static void onStarted(ServerStartedEvent event) {
        DungeonManager.releaseChunks(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        DungeonManager.shutdown();
    }

    /**
     * Rien ne se jette au donjon : un objet au sol serait efface avec la
     * salle. Il revient dans l'inventaire.
     */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !DungeonManager.inDimension(player)
                || exempt(player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem().copy();
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            event.setCanceled(true);
        } else {
            event.getEntity().setItem(stack);
        }
        player.inventoryMenu.broadcastChanges();
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
     * Les degats, avant armure.
     * <ul>
     *   <li>ce que frappe un monstre de partie (sa fleche et sa boule de feu
     *       comprises, la source etant le tireur) : facteur du palier, puis la
     *       reduction de l'equipement de donjon porte par le joueur ;</li>
     *   <li>ce qu'encaisse un monstre de partie : la part du boss au plafond de
     *       vie, et le bonus de l'epee de donjon pour un coup porte a la main ;</li>
     *   <li>pas de combat entre monstres de partie.</li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!DungeonManager.inDimension(target)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        DungeonRun targetRun = DungeonManager.runOfMob(target.getUUID());
        DungeonRun sourceRun = source == null || source == target ? null : DungeonManager.runOfMob(source.getUUID());
        if (sourceRun != null) {
            if (targetRun != null || target.entityTags().contains(DungeonRun.TAG)) {
                event.setCanceled(true);
                return;
            }
            float amount = event.getAmount() * sourceRun.damageFactor();
            if (target instanceof ServerPlayer player) {
                // L'esquive evite tout le coup ; sinon defense RPG et armure de donjon.
                if (player.getRandom().nextFloat() < Rpg.stat(player, RpgStat.DODGE)) {
                    event.setCanceled(true);
                    ServerLevel level = (ServerLevel) player.level();
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0d, player.getZ(),
                            8, 0.3d, 0.4d, 0.3d, 0.02d);
                    player.sendSystemMessage(Component.translatable("message.palamod.rpg.dodge")
                            .withStyle(RpgStat.DODGE.color()), true);
                    return;
                }
                amount *= 1.0f - DungeonGearItem.reduction(player, sourceRun.dungeon);
                amount *= 1.0f - Rpg.stat(player, RpgStat.DEFENSE);
            }
            event.setAmount(amount);
        }
        if (targetRun != null) {
            float amount = event.getAmount() * DungeonManager.takenFactor(target.getUUID());
            if (source instanceof ServerPlayer player) {
                if (event.getSource().getDirectEntity() == player) {
                    amount *= 1.0f + DungeonGearItem.damageBonus(player, targetRun.dungeon);
                }
                amount *= 1.0f + Rpg.stat(player, RpgStat.ATTACK);
                if (player.getRandom().nextFloat() < Rpg.stat(player, RpgStat.CRIT)) {
                    amount *= 1.5f;
                    ServerLevel level = (ServerLevel) target.level();
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2,
                            target.getZ(), 12, 0.3d, 0.3d, 0.3d, 0.2d);
                    level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                            SoundSource.PLAYERS, 0.8f, 1.0f);
                }
            }
            event.setAmount(amount);
        }
        if (target.entityTags().contains(DungeonRun.MERCHANT_TAG)) {
            event.setCanceled(true);
        }
    }

    /** La note : ce que chacun inflige et perd, une fois l'armure passee. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!DungeonManager.inDimension(target) || event.getInflictedDamage() <= 0.0f) {
            return;
        }
        if (target instanceof ServerPlayer player) {
            DungeonRun run = DungeonManager.runOf(player);
            if (run != null) {
                run.recordTaken(player, event.getInflictedDamage());
            }
            return;
        }
        DungeonRun run = DungeonManager.runOfMob(target.getUUID());
        if (run != null && event.getSource().getEntity() instanceof ServerPlayer player) {
            run.recordDealt(player, event.getInflictedDamage());
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
        DungeonRun owner = DungeonManager.runOfMob(entity.getUUID());
        if (owner != null && event.getSource().getEntity() instanceof ServerPlayer killer) {
            owner.recordKill(killer);
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
        Entity target = event.getTarget();
        boolean prop = DungeonProps.is(target, DungeonProps.TAG);
        if (!prop && !target.entityTags().contains(DungeonRun.MERCHANT_TAG)) {
            return;
        }
        // Rien d'autre ne se fait sur un objet de decor : ni laisse, ni selle, ni nourriture.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        DungeonRun run = DungeonManager.runOf(player);
        if (run == null) {
            return;
        }
        if (prop) {
            if (DungeonProps.is(target, DungeonProps.CHEST_TAG)) {
                run.openChest(player, target);
            } else if (DungeonProps.is(target, DungeonProps.JAR_TAG)) {
                run.breakJar(player, target);
            }
        } else if (run.isMerchant(target)) {
            run.greet(target);
            DungeonMenus.openMerchant(player, run);
        }
    }

    /** Une jarre se casse d'un coup : elle est invulnerable, le coup n'irait pas plus loin. */
    @SubscribeEvent
    public static void onAttackProp(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!DungeonProps.is(target, DungeonProps.TAG)) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player && DungeonProps.is(target, DungeonProps.JAR_TAG)) {
            DungeonRun run = DungeonManager.runOf(player);
            if (run != null) {
                run.breakJar(player, target);
            }
        }
    }

    /**
     * L'amelioration d'une rune (wiki) : deux runes de meme rarete et de meme
     * tier, la seconde sacrifiee ; 10, 15 puis 20 niveaux.
     */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!(left.getItem() instanceof RuneItem) || !left.is(right.getItem())) {
            return;
        }
        RuneData target = left.get(PalaDungeon.RUNE.get());
        RuneData sacrificed = right.get(PalaDungeon.RUNE.get());
        if (target == null || sacrificed == null || !target.canUpgradeWith(sacrificed)) {
            return;
        }
        ItemStack output = left.copy();
        output.set(PalaDungeon.RUNE.get(), target.upgradedWith(sacrificed));
        event.setOutput(output);
        event.setXpCost(target.upgradeCost());
        event.setMaterialCost(1);
    }

    // -- Commandes ----------------------------------------------------------------

    /**
     * {@code /donjon quitter} : abandonner le palier ; {@code /donjon sac} : voir
     * son sac ; {@code /profil} : le profil RPG, aussi au menu principal.
     */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("profil").executes(context -> {
            RpgMenu.INSTANCE.open(context.getSource().getPlayerOrException());
            return 1;
        }));
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
                }))
                // Essais : un palier sans cle ni progression, puis la salle de son choix.
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("lancer")
                                .then(Commands.argument("donjon", StringArgumentType.word())
                                        .then(Commands.argument("palier", IntegerArgumentType.integer(1,
                                                        DungeonManager.MAX_PALIER))
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource()
                                                                    .getPlayerOrException();
                                                            Dungeon dungeon = Dungeon.byKey(StringArgumentType
                                                                    .getString(context, "donjon"));
                                                            DungeonMode mode = DungeonMode.byName(StringArgumentType
                                                                    .getString(context, "mode"));
                                                            if (dungeon == null || mode == null) {
                                                                return 0;
                                                            }
                                                            return DungeonManager.start(player, dungeon,
                                                                    IntegerArgumentType.getInteger(context, "palier"),
                                                                    InteractionHand.MAIN_HAND, mode, true) ? 1 : 0;
                                                        })))))
                        .then(Commands.literal("salle").then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    DungeonRun run = DungeonManager.runOf(player);
                                    RoomType type;
                                    try {
                                        type = RoomType.valueOf(StringArgumentType.getString(context, "type")
                                                .toUpperCase(java.util.Locale.ROOT));
                                    } catch (IllegalArgumentException unknown) {
                                        return 0;
                                    }
                                    if (run == null || type == RoomType.EXIT) {
                                        return 0;
                                    }
                                    run.forceNext(player, type);
                                    return 1;
                                })))));
    }
}
