package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les sieges en cours, et ce qui les relie au monde : le tick, les explosions
 * des charges, les morts, les deconnexions, l'arret du serveur.
 *
 * <p><b>Les charges.</b> Une explosion partie d'une charge de sapeur ne suit
 * pas la protection des claims : elle souffle les blocs de ta base que la
 * puissance atteint, sauf conteneurs et machines, et sauf les claims d'autrui.
 * Chaque bloc souffle passe par le registre du terrain ({@link SiegeTerrain}) :
 * aucun objet ne tombe, tout reviendra.
 *
 * <p><b>Jamais en ton absence.</b> Te deconnecter coupe le siege : l'argent en
 * route revient, l'armee rentre, le terrain revient. La guerre, elle,
 * continue : il faudra fixer une nouvelle heure.
 */
@EventBusSubscriber
public final class Sieges {

    static final String CHARGE_TAG = "dohy_siege_charge";

    private static final Map<UUID, Siege> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, Siege> CHARGES = new HashMap<>();
    private static final Map<ResourceKey<Level>, SiegeTerrain.Ledger> LEDGERS = new HashMap<>();
    private static final java.util.Set<UUID> REPORTED = new java.util.HashSet<>();

    private Sieges() {
    }

    static Siege get(UUID id) {
        return ACTIVE.get(id);
    }

    /** Le siege en cours contre ce joueur (le terrain qui revient compris). */
    static Siege of(UUID player) {
        for (Siege siege : ACTIVE.values()) {
            if (siege.player.equals(player) && siege.phase != Siege.Phase.DONE) {
                return siege;
            }
        }
        return null;
    }

    /** Ta banque est-elle sous scelle ? Du camp a la fuite du tresorier, rien n'en sort. */
    public static boolean besieged(UUID player) {
        Siege siege = of(player);
        return siege != null && siege.sealing();
    }

    /** Pour le menu : une ligne sur le siege en cours, ou {@code null}. */
    public static String status(UUID player) {
        Siege siege = of(player);
        return siege == null || siege.finished() ? null : siege.status();
    }

    static List<Siege> all() {
        return List.copyOf(ACTIVE.values());
    }

    static SiegeTerrain.Ledger terrain(ServerLevel level) {
        return LEDGERS.computeIfAbsent(level.dimension(), key -> new SiegeTerrain.Ledger(level));
    }

    static Siege start(ServerPlayer target, WarData.Relation relation, ServerLevel level, BlockPos vault, int force,
                       long now) {
        NationTraits traits = relation.traits();
        double intel = 0.35d + 0.6d * traits.cunning();
        boolean heavy = relation.treasury >= 20_000L || traits.doctrine() == NationTraits.Doctrine.SIEGE;
        boolean endium = traits.cunning() >= 0.6d || traits.doctrine() == NationTraits.Doctrine.SIEGE
                && relation.treasury >= 40_000L;
        double morale = Math.max(40.0d, Math.min(95.0d, 60.0d + 25.0d * traits.aggression()
                - 10.0d * traits.caution() + Math.min(15.0d, relation.grievance(now) / 4.0d)));
        Siege siege = new Siege(level, target, relation.nation, vault, force, intel,
                new SiegePlanner.Capabilities(heavy, endium), morale);
        ACTIVE.put(siege.id, siege);
        DoHy.LOGGER.info("Guerre : {} assiege {} ({} soldats, coffre {} {} {}, moral {})", traits.name(),
                target.getGameProfile().name(), force, vault.getX(), vault.getY(), vault.getZ(), Math.round(morale));
        return siege;
    }

    static void charge(UUID tnt, Siege siege) {
        CHARGES.put(tnt, siege);
    }

    static void forget(UUID tnt) {
        if (tnt != null) {
            CHARGES.remove(tnt);
        }
    }

    /** La fin d'un siege, une fois : la nation en tire ses lecons. */
    static void concluded(Siege siege) {
        if (REPORTED.add(siege.id)) {
            WarStrategy.concluded(siege);
        }
    }

    // -- Le monde ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!ACTIVE.isEmpty()) {
            for (Siege siege : List.copyOf(ACTIVE.values())) {
                try {
                    siege.tick();
                } catch (RuntimeException failure) {
                    DoHy.LOGGER.error("Guerre : le siege de {} a echoue, il est leve", siege.traits.name(), failure);
                    siege.suspend(true);
                }
                if (siege.phase == Siege.Phase.DONE) {
                    ACTIVE.remove(siege.id);
                    REPORTED.remove(siege.id);
                }
            }
        }
        if (server.getTickCount() % 20 == 0) {
            long now = System.currentTimeMillis();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                WarStrategy.tick(player, now);
            }
        }
    }

    /** Une charge de sapeur : elle souffle ce qu'elle atteint, et tout est note pour revenir. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source == null || !source.entityTags().contains(CHARGE_TAG)) {
            return;
        }
        Siege siege = CHARGES.get(source.getUUID());
        List<BlockPos> blocks = event.getAffectedBlocks();
        if (siege == null) {
            // Une charge orpheline (un siege coupe) : elle ne touche a rien.
            blocks.clear();
            return;
        }
        for (BlockPos pos : new ArrayList<>(blocks)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0f) {
                continue;
            }
            ClaimOwner owner = FactionApi.claimAt(level, pos);
            if (owner.claimed() && (owner.enemy() || !owner.ownedBy(siege.player))) {
                continue;
            }
            if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }
            siege.terrain.remove(pos, false);
        }
        blocks.clear();
    }

    /** Une charge qui revient d'une sauvegarde sans son siege n'explosera pas. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof PrimedTnt tnt && tnt.entityTags().contains(CHARGE_TAG)
                && !CHARGES.containsKey(tnt.getUUID()) && event.loadedFromDisk()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Siege siege = of(player.getUUID());
            if (siege != null && !siege.finished()) {
                siege.onPlayerDeath();
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WarStrategy.login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Siege siege = of(player.getUUID());
            if (siege != null && !siege.finished()) {
                siege.tell("Tu fuis le combat ? Nous reviendrons.", net.minecraft.ChatFormatting.RED);
                siege.suspend(false);
            }
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SiegeTerrain.recover(level);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Siege siege : List.copyOf(ACTIVE.values())) {
            siege.suspend(true);
        }
        for (SiegeTerrain.Ledger ledger : LEDGERS.values()) {
            ledger.restore(-1);
            ledger.save();
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        CHARGES.clear();
        LEDGERS.clear();
        REPORTED.clear();
    }
}
