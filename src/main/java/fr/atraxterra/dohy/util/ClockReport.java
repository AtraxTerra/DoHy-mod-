package fr.atraxterra.dohy.util;

import fr.atraxterra.dohy.DoHy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dit au demarrage quelle heure le mod croit qu'il est.
 *
 * <p>C'est la premiere chose a regarder si un event ou une bascule tombe a la
 * mauvaise heure : la ligne donne le fuseau retenu et celui de la machine, cote
 * a cote. A part de {@link RealClock}, qui doit rester compilable sans
 * Minecraft pour son test.
 */
@EventBusSubscriber
public final class ClockReport {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private ClockReport() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String unknown = RealClock.unknownOption();
        if (unknown != null) {
            DoHy.LOGGER.warn("Fuseau '{}' inconnu (option dohy.fuseau) : heure de Paris a la place.", unknown);
        }
        ZoneId zone = RealClock.zone();
        DoHy.LOGGER.info("Horloge DoHy : {} ({}), machine en {}", zone,
                ZonedDateTime.now(zone).format(TIME), ZoneId.systemDefault());
    }
}
