package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que le deuxieme lot d'evenements laisse derriere lui, cote serveur
 * ({@code EventsManager} de PalaMod) : les pousses de #TeamTree, les monstres de
 * « Boom les mobs », les explosions de « Explosif », le terrain mine, le bol
 * sans fond et l'inventaire de terre de « La dirt, c'est bien ».
 */
@EventBusSubscriber
public final class LuckyEffectEvents {

    /** {@code nextInt(30) + 30} : la hauteur ou « Boom les mobs » envoie un monstre frappe. */
    private static final int BOOM_HEIGHT = 30;
    /** {@code createExplosion(player, ..., 5.0f, true)} : la puissance de « Explosif ». */
    private static final float EXPLOSE_POWER = 5.0f;
    /** {@code getTime() - now >= 1} : une explosion par seconde au plus. */
    private static final int EXPLOSE_COOLDOWN = 20;
    /** {@code createExplosion(null, ..., 15.0f, true)} : le terrain mine. */
    private static final float MINE_POWER = 15.0f;
    /** {@code DirtIsGood} : les 40 cases (sac et armure) passent a la terre pour dix secondes. */
    static final int DIRT_SLOTS = 40;
    static final int DIRT_TICKS = 200;

    /** Les blocs de « Terrain mine » ({@code MinedTerrain.blockList}), en memoire comme chez PalaMod. */
    private static final Map<GlobalPos, Block> MINES = new ConcurrentHashMap<>();
    /** Les joueurs de « On voit pas le fond du bol » ({@code UsersManager.getBol}) : le prochain repas les coiffe. */
    private static final Set<UUID> BOWLS = ConcurrentHashMap.newKeySet();
    /** Les joueurs qui gardent la vue de la citrouille jusqu'a leur prochaine connexion ({@code getKeepPumpkinBlur}). */
    private static final Set<UUID> BLURRED = ConcurrentHashMap.newKeySet();
    /** Les inventaires mis de cote par « La dirt, c'est bien ». */
    private static final Map<UUID, ItemStack[]> DIRT = new ConcurrentHashMap<>();
    /** Accroupi au tic precedent, et la derniere explosion de chacun. */
    private static final Set<UUID> SNEAKING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> EXPLODED = new ConcurrentHashMap<>();

    private LuckyEffectEvents() {
    }

    // -- Ce que posent les evenements --------------------------------------------

    static void mine(ServerLevel level, BlockPos pos, Block block) {
        MINES.put(GlobalPos.of(level.dimension(), pos.immutable()), block);
    }

    static void bowl(ServerPlayer player) {
        BOWLS.add(player.getUUID());
    }

    /** Met l'inventaire de cote et le remplit de terre ; il revient au bout de dix secondes. */
    static void dirt(ServerPlayer player) {
        if (DIRT.containsKey(player.getUUID())) {
            return;
        }
        ItemStack[] saved = new ItemStack[DIRT_SLOTS];
        for (int slot = 0; slot < DIRT_SLOTS; slot++) {
            saved[slot] = player.getInventory().getItem(slot).copy();
            player.getInventory().setItem(slot, new ItemStack(Blocks.DIRT));
        }
        DIRT.put(player.getUUID(), saved);
        LuckyWorld.tell(player, "Ca sent mauvais ici nan ?", net.minecraft.ChatFormatting.RED);
        LuckyTasks.later(player, DIRT_TICKS, () -> restore(player));
    }

    /**
     * Rend l'inventaire mis de cote. PalaMod ecrasait chaque case ; ici, ce qui
     * a ete ramasse entre-temps (autre chose que la terre posee) est rendu aussi.
     */
    private static void restore(ServerPlayer player) {
        // Mort entre-temps : on attend la reapparition, l'inventaire du mort ne suivrait pas.
        if (!player.isAlive()) {
            return;
        }
        ItemStack[] saved = DIRT.remove(player.getUUID());
        if (saved == null) {
            return;
        }
        for (int slot = 0; slot < DIRT_SLOTS; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            player.getInventory().setItem(slot, saved[slot]);
            boolean placeholder = current.is(Items.DIRT) && current.getCount() == 1;
            if (!current.isEmpty() && !placeholder && !player.getInventory().add(current)) {
                player.drop(current, false);
            }
        }
        LuckyWorld.tell(player, "Ah c'est mieux", net.minecraft.ChatFormatting.GREEN);
    }

    // -- #TeamTree, Explosif -----------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.hasEffect(PalaLucky.TREE)) {
            plant(player, level);
        }
        boolean sneaking = player.isShiftKeyDown();
        boolean started = sneaking && SNEAKING.add(player.getUUID());
        if (!sneaking) {
            SNEAKING.remove(player.getUUID());
        }
        if (started && player.hasEffect(PalaLucky.EXPLOSE)) {
            long now = level.getGameTime();
            Long last = EXPLODED.get(player.getUUID());
            if (last == null || now - last >= EXPLOSE_COOLDOWN) {
                EXPLODED.put(player.getUUID(), now);
                // Le joueur est la source : l'explosion l'epargne, et ne casse rien la ou il n'a pas le droit.
                level.explode(player, player.getX(), player.getY(), player.getZ(), EXPLOSE_POWER,
                        Level.ExplosionInteraction.TNT);
            }
        }
    }

    /**
     * Une pousse de chene en haut de la colonne du joueur ({@code onTreeMove} :
     * {@code getHeightValue}, puis {@code setBlock(sapling)}). Ici, seulement la
     * ou une pousse tient : PalaMod la posait meme sur la pierre.
     */
    private static void plant(ServerPlayer player, ServerLevel level) {
        int x = net.minecraft.util.Mth.floor(player.getX());
        int z = net.minecraft.util.Mth.floor(player.getZ());
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState();
        if (level.getBlockState(pos).canBeReplaced() && sapling.canSurvive(level, pos)
                && !level.getBlockState(pos).is(Blocks.OAK_SAPLING)) {
            LuckyWorld.place(player, level, pos, sapling);
        }
    }

    // -- Boom les mobs -----------------------------------------------------------

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        // {@code getSourceOfDamage} : le coup direct, pas la fleche.
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getDirectEntity();
        if (victim instanceof Player
                || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(victim.getType()).is(Tags.EntityTypes.BOSSES)
                || !(attacker instanceof ServerPlayer player) || !player.hasEffect(PalaLucky.BOOM_MOBS)) {
            return;
        }
        int height = BOOM_HEIGHT + player.getRandom().nextInt(BOOM_HEIGHT);
        victim.move(MoverType.SELF, new Vec3(0.0d, height, 0.0d));
    }

    // -- Terrain mine ------------------------------------------------------------

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        GlobalPos at = GlobalPos.of(level.dimension(), event.getPos());
        Block mined = MINES.get(at);
        if (mined == null || !event.getState().is(mined)) {
            return;
        }
        MINES.remove(at);
        Player player = event.getPlayer();
        var silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        if (player != null && EnchantmentHelper.getItemEnchantmentLevel(silk, player.getMainHandItem()) > 0) {
            return;
        }
        BlockPos pos = event.getPos();
        level.explode(null, pos.getX(), pos.getY(), pos.getZ(), MINE_POWER, Level.ExplosionInteraction.TNT);
    }

    // -- On voit pas le fond du bol ----------------------------------------------

    /**
     * Le premier repas apres le bol ({@code onEatSoup}, quoi qu'on mange) : une
     * citrouille sur la tete, et sa vue qui reste. PalaMod ecrasait le casque ;
     * ici il revient dans l'inventaire.
     */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BOWLS.remove(player.getUUID())) {
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Blocks.CARVED_PUMPKIN));
        if (!helmet.isEmpty() && !player.getInventory().add(helmet)) {
            player.drop(helmet, false);
        }
        BLURRED.add(player.getUUID());
        player.addEffect(new MobEffectInstance(PalaLucky.PUMPKIN_BLUR, MobEffectInstance.INFINITE_DURATION));
    }

    // -- Connexions, morts, arret ------------------------------------------------

    /** La vue de la citrouille s'en va a la connexion suivante ({@code onLogin}). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BLURRED.remove(player.getUUID());
            player.removeEffect(PalaLucky.PUMPKIN_BLUR);
            restore(player);
        }
    }

    /** Mort, on garde la vue de la citrouille ({@code entityJoinWorld}) et on retrouve son inventaire. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (BLURRED.contains(player.getUUID())) {
            player.addEffect(new MobEffectInstance(PalaLucky.PUMPKIN_BLUR, MobEffectInstance.INFINITE_DURATION));
        }
        restore(player);
    }

    /** Parti avant les dix secondes : l'inventaire revient avant la sauvegarde du joueur. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restore(player);
            SNEAKING.remove(player.getUUID());
            BOWLS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            restore(player);
        }
        MINES.clear();
        BOWLS.clear();
        BLURRED.clear();
        SNEAKING.clear();
        EXPLODED.clear();
    }
}
