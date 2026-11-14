package fr.atraxterra.dohy.pass;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Le battement du pass : ouvrir le mois et le jour a la connexion et chaque
 * minute, et l'effet de la relique tenue en main secondaire.
 */
@EventBusSubscriber
public final class PassEvents {

    private static final int MINUTE = 20 * 60;
    /** L'effet d'une relique dure vingt secondes, renouvele toutes les deux : il ne clignote jamais. */
    private static final int EFFECT_TICKS = 400;
    private static final int REFRESH_TICKS = 40;

    private static int counter;

    private PassEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PassApi.rollIfDue(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++counter < MINUTE) {
            return;
        }
        counter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PassApi.rollIfDue(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % REFRESH_TICKS != 3) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RelicItem relic) {
            player.addEffect(new MobEffectInstance(relic.relic().effect(), EFFECT_TICKS, 0, true, false, true));
        }
    }
}
