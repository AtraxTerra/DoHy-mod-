package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.faction.tech.TechConfig;
import fr.atraxterra.dohy.faction.tech.TechSync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'infobulle des machines de l'arbre (chapitre 23, lot 23d) : « Technologie : Energie II ·
 * Vapeur », en vert si la nation l'a, en rouge sinon, avec la commande qui ouvre les
 * technologies. Le client tient ce que le serveur lui envoie ({@link TechSync}) et retient, pour
 * chaque objet, le palier trouve : les motifs ne se relisent qu'apres un nouvel envoi.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TechTooltips {

    private static volatile TechSync sync;
    private static final Map<Item, Optional<TechSync.Entry>> CACHE = new ConcurrentHashMap<>();

    private TechTooltips() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TechSync.TYPE, (payload, context) -> {
            sync = payload;
            CACHE.clear();
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        sync = null;
        CACHE.clear();
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        TechSync current = sync;
        if (current == null || event.getItemStack().isEmpty()) {
            return;
        }
        Optional<TechSync.Entry> found = CACHE.computeIfAbsent(event.getItemStack().getItem(),
                item -> find(current, item));
        if (found.isEmpty()) {
            return;
        }
        TechSync.Entry entry = found.get();
        event.getToolTip().add(Component.literal("Technologie : " + entry.domain() + " "
                + TechConfig.roman(entry.tier()) + " · " + entry.name())
                .withStyle(entry.reached() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!entry.reached()) {
            event.getToolTip().add(Component.literal("Pose verrouillee : a rechercher (/f techno)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Le palier qui deverrouille cet objet, ou le bloc qu'il pose ; rien pour une machine libre. */
    private static Optional<TechSync.Entry> find(TechSync current, Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        String block = item instanceof BlockItem placed ? BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString()
                : id;
        for (String pattern : current.free()) {
            if (TechConfig.matches(pattern, id) || TechConfig.matches(pattern, block)) {
                return Optional.empty();
            }
        }
        for (TechSync.Entry entry : current.entries()) {
            for (String pattern : entry.patterns()) {
                if (TechConfig.matches(pattern, id) || TechConfig.matches(pattern, block)) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }
}
