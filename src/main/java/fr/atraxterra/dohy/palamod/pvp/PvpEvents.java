package fr.atraxterra.dohy.palamod.pvp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Map;

/**
 * Le liant des objets de PvP : la jauge de recharge des sticks, la physique du
 * deltaplane, et l'infobulle de la capacite de reparation.
 */
@EventBusSubscriber
public final class PvpEvents {

    private PvpEvents() {
    }

    /**
     * La physique du deltaplane, sur le joueur dont ce client calcule le
     * mouvement — le joueur local. Avant le deplacement du tick, comme
     * {@code onUpdate} en 1.7.10, qui passait avant {@code moveEntityWithHeading}.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() && player.isLocalInstanceAuthoritative()) {
            HangGliderItem.glide(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreCooldowns(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreCooldowns(player);
        }
    }

    /**
     * Repose la jauge de recharge de chaque stick encore en recharge.
     *
     * <p>Les recharges du jeu vivent sur l'objet joueur, que la deconnexion et
     * la mort remplacent ; les echeances de {@link PalaPvp#STICK_COOLDOWNS},
     * elles, sont sauvegardees et suivent la mort.
     */
    private static void restoreCooldowns(ServerPlayer player) {
        Map<String, Long> timers = player.getData(PalaPvp.STICK_COOLDOWNS);
        if (timers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        timers.values().removeIf(ready -> ready <= now);
        for (DeferredItem<StickItem> stick : PalaPvp.STICKS) {
            Long ready = timers.get(stick.get().type().key());
            if (ready != null) {
                int ticks = (int) Math.min(Integer.MAX_VALUE, (ready - now + 49L) / 50L);
                player.getCooldowns().addCooldown(new ItemStack(stick.get()), ticks);
            }
        }
    }

    /**
     * La capacite de reparation par anneau qui reste a une piece, une fois
     * entamee. L'original ne l'affichait pas ; sans elle, un outil en paladium
     * a sa troisieme remise a neuf ne le dit pas.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.has(PalaPvp.RING_REPAIR.get())) {
            return;
        }
        int left = RingRepair.describe(stack);
        if (left >= 0) {
            event.getToolTip().add(Component.translatable("tooltip.palamod.ring_repair",
                    left, RingRepair.capacity(stack)));
        }
    }
}
