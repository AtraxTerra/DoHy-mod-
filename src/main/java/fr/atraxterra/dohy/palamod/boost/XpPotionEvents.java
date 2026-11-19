package fr.atraxterra.dohy.palamod.boost;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Le boost de PalaMod etait une date de fin, pas un effet : ni le lait ni la
 * mort ne l'enlevaient. Ces deux evenements gardent cette promesse.
 */
@EventBusSubscriber
public final class XpPotionEvents {

    private XpPotionEvents() {
    }

    private static boolean isBoost(Holder<MobEffect> effect) {
        MobEffect value = effect.value();
        return value == PalaXpPotions.DOUBLE_JOB_XP.get() || value == PalaXpPotions.TRIPLE_JOB_XP.get();
    }

    /** Le lait (et tout « enlever les effets ») passe a cote du boost d'un joueur. */
    @SubscribeEvent
    public static void onRemove(MobEffectEvent.Remove event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (event.getEntity() instanceof ServerPlayer && instance != null && isBoost(event.getEffect())
                && instance.getDuration() > 0) {
            event.setCanceled(true);
        }
    }

    /** A la mort, le boost passe au joueur qui reapparait, avec le temps qui lui restait. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (int multiplier : new int[] {2, 3}) {
            MobEffectInstance kept = event.getOriginal().getEffect(PalaXpPotions.effect(multiplier));
            if (kept != null && kept.getDuration() > 0) {
                player.addEffect(PalaXpPotions.instance(multiplier, kept.getDuration()));
            }
        }
    }
}
