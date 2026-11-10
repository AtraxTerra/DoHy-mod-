package fr.atraxterra.dohy.palamod.trixium;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le « combat » des objets exclusifs, que Paladium lisait dans son plugin
 * CombatTag : ici, avoir frappe ou ete frappe par une creature dans les
 * dernieres <b>10 secondes</b>.
 */
@EventBusSubscriber
public final class CombatTag {

    /** Duree du marquage, en ticks. */
    public static final long TICKS = 200L;

    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    private CombatTag() {
    }

    public static boolean inCombat(ServerPlayer player) {
        Long until = UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    private static void mark(ServerPlayer player) {
        UNTIL.put(player.getUUID(), player.level().getGameTime() + TICKS);
    }

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer player && attacker instanceof LivingEntity && attacker != player) {
            mark(player);
        }
        if (attacker instanceof ServerPlayer player && event.getEntity() != player) {
            mark(player);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity) {
            mark(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UNTIL.remove(event.getEntity().getUUID());
    }
}
