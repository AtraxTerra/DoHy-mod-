package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * L'application des verrous de technologie (chapitre 23, lot 23d) : <b>la pose</b> d'une machine
 * d'un palier que la nation n'a pas est refusee, avec le palier qui la debloque.
 *
 * <ul>
 *   <li>Les machines deja posees continuent de tourner, et leur ecran s'ouvre : seul un nouveau
 *   placement est verrouille. Une machine cassee puis reposee demande sa technologie.</li>
 *   <li>Un objet qui pose une machine sans etre un bloc (le tracteur, le bot de farming) est
 *   verrouille a l'usage, puisque c'est son usage qui la pose.</li>
 *   <li>Pas de verrou sur les recettes : la fabrication de NTM passe par ses machines, hors de
 *   portee, et une machine qu'on ne peut pas poser l'est pour de bon. Elle se garde dans un
 *   coffre en attendant la recherche.</li>
 * </ul>
 *
 * <p><b>Le creatif passe</b>, comme pour les verrous de metier, et l'exemption est ici plutot que
 * dans {@link TechGate#allows} : celle-ci dit toujours la verite sur la recherche, l'infobulle et
 * les ecrans s'en servent. Tout gestionnaire de cette classe commence par {@link #exempt} ;
 * {@code tools/check_job_gates.py} le verifie.
 */
@EventBusSubscriber
public final class TechGateEvents {

    /** Anti-spam des refus : deux secondes par joueur, sur le temps du monde. */
    private static final Map<UUID, Long> lastRefusal = new HashMap<>();
    private static final long REFUSAL_COOLDOWN_TICKS = 40L;

    private TechGateEvents() {
    }

    /** Les joueurs que les verrous ne concernent pas : le creatif sert a construire et a tester. */
    private static boolean exempt(ServerPlayer player) {
        return player.isCreative();
    }

    /**
     * Pose d'une machine verrouillee. L'evenement couvre aussi les machines de NTM en plusieurs
     * blocs : NeoForge note tout ce que pose l'objet, et l'annulation remet tout en place.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || exempt(player)) {
            return;
        }
        TechGate.Lock lock = TechGate.of(event.getPlacedBlock().getBlock());
        if (TechGate.allows(player, lock)) {
            return;
        }
        event.setCanceled(true);
        refuse(player, lock);
    }

    /** Clic droit sur un bloc avec un objet qui pose une machine sans etre un bloc (tracteur, bot). */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || exempt(player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || held.getItem() instanceof BlockItem) {
            return;
        }
        TechGate.Lock lock = TechGate.of(held.getItem());
        if (TechGate.allows(player, lock)) {
            return;
        }
        event.setCanceled(true);
        refuse(player, lock);
    }

    /** Clic droit dans le vide avec un tel objet. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || exempt(player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || held.getItem() instanceof BlockItem) {
            return;
        }
        TechGate.Lock lock = TechGate.of(held.getItem());
        if (TechGate.allows(player, lock)) {
            return;
        }
        event.setCanceled(true);
        refuse(player, lock);
    }

    /** Le refus, une fois toutes les deux secondes au plus : « Technologie requise : Energie II · Vapeur ». */
    public static Component refusal(ServerPlayer player, TechGate.Lock lock) {
        String hint = FactionApi.has(player) ? " Recherche-la dans /f techno."
                : " Il te faut d'abord une nation (/f create).";
        return FactionApi.prefix().append(Component.literal("Technologie requise : " + lock.label() + "." + hint)
                .withStyle(ChatFormatting.RED));
    }

    private static void refuse(ServerPlayer player, TechGate.Lock lock) {
        if (lock == null) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = lastRefusal.get(player.getUUID());
        if (previous != null && now - previous < REFUSAL_COOLDOWN_TICKS) {
            return;
        }
        if (lastRefusal.size() > 64) {
            lastRefusal.clear();
        }
        lastRefusal.put(player.getUUID(), now);
        player.sendSystemMessage(refusal(player, lock));
    }
}
