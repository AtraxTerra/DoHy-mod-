package fr.atraxterra.dohy.faction;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que protege un claim, en solo : la base contre le monde.
 *
 * <ul>
 *   <li>les explosions qui ne viennent pas du proprietaire (creepers, ghasts,
 *       withers sauvages) ne cassent aucun bloc du claim ;</li>
 *   <li>les monstres n'y degradent rien (endermen, ravageurs, zombies qui
 *       cassent les portes, champs pietines) ;</li>
 *   <li>les autres joueurs, sur un serveur, n'y cassent, ne posent et n'ouvrent
 *       rien ;</li>
 *   <li>un claim peut refuser l'apparition naturelle des monstres ;</li>
 *   <li>la foudre naturelle n'y tombe pas : la base, qui tourne aussi en
 *       l'absence du joueur (lot 22k), ne prend pas feu sous l'orage.</li>
 * </ul>
 *
 * <p>Les claims des bases ennemies generees (lot 22g) suivent la regle de
 * Paladium : rien a la main, mais explosions et withers y cassent tout.
 */
@EventBusSubscriber
public final class ClaimProtection {

    /** Dernier message de refus par joueur : un par seconde au plus. */
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private ClaimProtection() {
    }

    /** Un operateur en creatif passe partout : administrer un serveur ne demande pas de claim. */
    private static boolean exempt(Player player) {
        return player instanceof ServerPlayer server && server.isCreative()
                && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(server.createCommandSourceStack());
    }

    /**
     * Vrai si ce joueur n'a rien a faire dans ce claim. Un op en creatif passe
     * partout, sauf dans une base ennemie : celle-la se pille, meme en creatif.
     */
    private static boolean foreign(Player player, ClaimOwner owner) {
        return owner.claimed() && !owner.ownedBy(player.getUUID()) && (owner.enemy() || !exempt(player));
    }

    private static void warn(ServerPlayer player, ClaimOwner owner) {
        long now = System.currentTimeMillis();
        Long last = LAST_WARNING.get(player.getUUID());
        if (last == null || now - last > 1000L) {
            LAST_WARNING.put(player.getUUID(), now);
            FactionApi.tell(player, owner.enemy()
                    ? "Base ennemie des " + owner.faction() + " : rien ne se casse ni ne se pose ici. Dynamites, canon a TNT ou wither."
                    : "Ce territoire appartient a la faction " + owner.faction() + ".", ChatFormatting.RED);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            ClaimOwner owner = FactionApi.claimAt(event.getLevel(), event.getPos());
            if (foreign(player, owner)) {
                event.setCanceled(true);
                warn(player, owner);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClaimOwner owner = FactionApi.claimAt(event.getLevel(), event.getPos());
            if (foreign(player, owner)) {
                event.setCanceled(true);
                warn(player, owner);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClaimOwner owner = FactionApi.claimAt(event.getLevel(), event.getPos());
            if (foreign(player, owner)) {
                event.setCanceled(true);
                warn(player, owner);
            }
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        ClaimOwner owner = FactionApi.claimAt(event.getLevel(), event.getPos());
        if (owner.claimed() && !(event.getEntity() instanceof Player player && owner.ownedBy(player.getUUID()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity instanceof Player) {
            return;
        }
        ClaimOwner owner = FactionApi.claimAt(entity.level(), entity.blockPosition());
        if (owner.claimed() && !owner.raidable()) {
            event.setCanGrief(false);
        }
    }

    /**
     * Une explosion du proprietaire (sa TNT, sa dynamite) casse son claim ; les
     * autres, non. Une base ennemie (lot 22g) se pille a l'explosif : tout y casse.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity source = event.getExplosion().getIndirectSourceEntity();
        UUID cause = source instanceof Player player ? player.getUUID() : null;
        event.getAffectedBlocks().removeIf(pos -> {
            ClaimOwner owner = FactionApi.claimAt(level, pos);
            return owner.claimed() && !owner.raidable() && (cause == null || !owner.ownedBy(cause));
        });
    }

    /**
     * La foudre naturelle (sans joueur a l'origine, comme un trident a
     * canalisation) ne tombe pas dans un claim : depuis que la base tourne sans
     * son joueur, un orage la nuit y mettrait le feu sans personne pour
     * l'eteindre. Les bases ennemies, elles, la recoivent.
     */
    @SubscribeEvent
    public static void onLightning(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt
                && event.getLevel() instanceof ServerLevel level && bolt.getCause() == null) {
            ClaimOwner owner = FactionApi.claimAt(level, bolt.blockPosition());
            if (owner.claimed() && !owner.raidable()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
                && reason != EntitySpawnReason.PATROL && reason != EntitySpawnReason.REINFORCEMENT) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        ClaimOwner owner = FactionApi.claimAt(event.getLevel(), pos);
        if (owner.claimed() && !owner.monsters()) {
            event.setSpawnCancelled(true);
        }
    }
}
