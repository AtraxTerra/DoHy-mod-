package fr.atraxterra.dohy.farmland;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les regles de la farmland : on n'y casse rien, on n'y pose rien (le mode creatif
 * excepte, pour qui veut retoucher la carte), les explosions et les creatures n'y
 * abiment rien ; pas de PvP ; la faim n'y baisse pas ; les commandes de faction n'y
 * marchent pas ; le jeu n'y fait apparaitre aucune creature de lui-meme, ni foudre ;
 * qui tombe ou passe les falaises revient au camp.
 */
@EventBusSubscriber
public final class FarmlandEvents {

    /** Les commandes de faction, sous tous leurs noms. */
    static final Set<String> FACTION = Set.of("f", "faction", "nation");
    /** La faim et la saturation gardees, par joueur present. */
    private static final Map<UUID, float[]> FOOD = new HashMap<>();

    private FarmlandEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Farmland.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Farmland.DIMENSION) {
            FarmlandSpawner.tick(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Farmland.stop();
        FarmlandSpawner.reset();
        FOOD.clear();
    }

    /** Un joueur que les regles de la carte retiennent : dans la farmland, hors du mode creatif. */
    private static boolean bound(Player player) {
        return player != null && Farmland.inFarmland(player) && !player.isCreative();
    }

    // -- Rien ne s'abime ----------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && bound(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && bound(player)) {
            event.setCanceled(true);
        }
    }

    /** Hache, houe, pelle : ni ecorcer, ni labourer, ni tracer de chemin. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (bound(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /** Seaux, briquet, boule de feu, poudre d'os : ce qui changerait la carte. */
    static boolean changesWorld(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BucketItem || item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE
                || item == Items.BONE_MEAL;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && bound(player) && changesWorld(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && bound(player)
                && event.getItemStack().getItem() instanceof BucketItem) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == Farmland.DIMENSION) {
            event.getAffectedBlocks().clear();
        }
    }

    /** Endermen qui emportent un bloc, moutons qui broutent l'herbe : pas ici. */
    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && Farmland.inFarmland(entity)) {
            event.setCanGrief(false);
        }
    }

    // -- Les creatures ------------------------------------------------------------------------------

    /**
     * Le jeu ne fait rien apparaitre ici de lui-meme (pas de creatures d'un biome peint, pas de
     * foudre qui mettrait le feu a la foret) : seulement les creatures du Chasseur.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().dimension() != Farmland.DIMENSION || event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof LightningBolt) {
            event.setCanceled(true);
            return;
        }
        if (entity instanceof Mob mob && !mob.entityTags().contains(FarmlandSpawner.TAG)) {
            EntitySpawnReason reason = mob.getSpawnType();
            if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
                    || reason == EntitySpawnReason.PATROL || reason == EntitySpawnReason.STRUCTURE) {
                event.setCanceled(true);
            }
        }
    }

    /** Pas de PvP ; une creature qu'un joueur frappe ne s'en va plus au bout de dix minutes. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!Farmland.inFarmland(victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (victim instanceof Player && source instanceof Player && source != victim) {
            event.setCanceled(true);
            return;
        }
        if (source instanceof Player && victim.entityTags().contains(FarmlandSpawner.TAG)) {
            victim.addTag(FarmlandSpawner.HIT);
        }
    }

    /** Nourrie, apprivoisee, tenue en laisse, montee : la bete reste. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (Farmland.inFarmland(target) && target.entityTags().contains(FarmlandSpawner.TAG)) {
            target.addTag(FarmlandSpawner.HIT);
        }
    }

    // -- Les joueurs --------------------------------------------------------------------------------

    /** Vrai pour une commande de faction, qui ne marche pas dans la farmland. */
    public static boolean factionCommand(String input) {
        String text = input.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int space = text.indexOf(' ');
        String root = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        return FACTION.contains(root);
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player
                && Farmland.inFarmland(player) && factionCommand(event.getParseResults().getReader().getString())) {
            player.sendSystemMessage(Component.literal("Les commandes de faction ne marchent pas dans la farmland.")
                    .withStyle(ChatFormatting.RED));
            event.setCanceled(true);
        }
    }

    /** La faim gardee telle qu'a l'arrivee (manger la remonte) ; hors des falaises, retour au camp. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!Farmland.inFarmland(player)) {
            FOOD.remove(player.getUUID());
            return;
        }
        if (Farmland.outOfBounds(player) && player.level() instanceof ServerLevel level) {
            Farmland.toHub(player, level);
            player.sendSystemMessage(Component.literal("On ne quitte pas la farmland par la : te revoila au camp.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        FoodData food = player.getFoodData();
        float[] kept = FOOD.computeIfAbsent(player.getUUID(),
                id -> new float[] {food.getFoodLevel(), food.getSaturationLevel()});
        if (food.getFoodLevel() < kept[0]) {
            food.setFoodLevel((int) kept[0]);
        } else {
            kept[0] = food.getFoodLevel();
        }
        if (food.getSaturationLevel() < kept[1]) {
            food.setSaturation(kept[1]);
        } else {
            kept[1] = food.getSaturationLevel();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FOOD.remove(event.getEntity().getUUID());
    }
}
