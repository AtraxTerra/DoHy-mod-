package fr.atraxterra.dohy.client.season;

import fr.atraxterra.dohy.mixin.BiomeAccessor;
import fr.atraxterra.dohy.season.SeasonClimate;
import fr.atraxterra.dohy.season.SeasonMath;
import fr.atraxterra.dohy.season.SeasonSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Les saisons cote client : le calendrier recu du serveur, la table de
 * temperature (pour que la pluie devienne neige a l'ecran la ou le serveur fait
 * neiger), les couleurs de saison et la ligne de l'atlas.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientSeasons {

    private static int ticks;
    private static int colorStep = Integer.MIN_VALUE;
    private static boolean onMap;

    private ClientSeasons() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(SeasonSync.TYPE, (payload, context) -> accept(payload));
    }

    private static void accept(SeasonSync sync) {
        SeasonClimate.CLIENT.configure(sync.frame(), sync.clock(), sync.serverNow() - System.currentTimeMillis());
        colorStep = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SeasonClimate.CLIENT.clear();
        SeasonColors.clear();
        colorStep = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        SeasonClimate climate = SeasonClimate.CLIENT;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (!climate.active() || level == null) {
            return;
        }
        boolean here = level.dimension().identifier().toString().equals(climate.frame().dimension());
        climate.dimension(here);
        if (++ticks % 100 == 0) {
            climate.refresh();
        }
        int step = SeasonColors.step(climate.phase());
        if (step != colorStep || here != onMap) {
            colorStep = step;
            onMap = here;
            SeasonColors.update(climate.phase());
            // un chunk garde les couleurs de sa construction : on les fait toutes reconstruire
            level.clearTintCaches();
            minecraft.levelRenderer.invalidateCompiledGeometry(level, minecraft.options,
                    minecraft.gameRenderer.mainCamera(), minecraft.getBlockColors());
        }
    }

    /** La saison sous les pieds du joueur, en une ligne courte : « Hiver  j3/7  -3 °C ». */
    public static Line line(ClientLevel level, BlockPos pos) {
        SeasonClimate climate = SeasonClimate.CLIENT;
        if (!climate.active() || !onMap) {
            return null;
        }
        double phase = climate.phase();
        double latitude = climate.frame().latitude(pos.getZ() + 0.5d);
        float temperature = ((BiomeAccessor) (Object) level.getBiome(pos).value())
                .dohy$invokeTemperature(pos, level.getSeaLevel());
        String degrees = SeasonMath.celsius(temperature) + " °C";
        if (SeasonMath.tropical(latitude)) {
            return new Line(SeasonMath.name(latitude, phase) + "  " + degrees, TROPICS);
        }
        double local = SeasonMath.local(phase, latitude);
        int season = SeasonMath.season(local);
        return new Line(SeasonMath.name(season) + "  j" + SeasonMath.day(local, 7) + "/7  " + degrees,
                COLORS[season]);
    }

    /** Une ligne a afficher et sa couleur (ARGB). */
    public record Line(String text, int color) {
    }

    private static final int TROPICS = 0xFF7FD8C8;
    private static final int[] COLORS = {0xFFA9D8FF, 0xFF9BE07A, 0xFFFFD45C, 0xFFE8873A};
}
