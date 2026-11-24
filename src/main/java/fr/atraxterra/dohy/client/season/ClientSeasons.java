package fr.atraxterra.dohy.client.season;

import fr.atraxterra.dohy.mixin.BiomeAccessor;
import fr.atraxterra.dohy.season.SeasonClimate;
import fr.atraxterra.dohy.season.SeasonMath;
import fr.atraxterra.dohy.season.SeasonSync;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Les saisons cote client : le calendrier recu du serveur, la table de
 * temperature (pour que la pluie devienne neige a l'ecran la ou le serveur fait
 * neiger), les couleurs de saison (bouleaux compris) et la ligne de l'atlas.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientSeasons {

    private static int ticks;
    private static int colorStep = Integer.MIN_VALUE;
    private static boolean onMap;

    private ClientSeasons() {
    }

    /**
     * Les feuilles de bouleau : le jeu leur donne une couleur fixe, qui ne passe
     * pas par le biome. On la remplace par celle de la saison ({@link SeasonColors#birch}) ;
     * hors du monde (inventaire, main), la couleur du jeu.
     */
    private static final BlockTintSource BIRCH = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return FoliageColor.FOLIAGE_BIRCH;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return SeasonColors.birch(pos);
        }
    };

    @SubscribeEvent
    public static void onRegisterTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(BIRCH), Blocks.BIRCH_LEAVES);
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
            redraw(minecraft, level);
        }
    }

    /**
     * Un chunk garde les couleurs de sa construction : on fait reconstruire
     * tout ce qui est a portee de vue. Comme pour un bloc pose, l'ancien rendu
     * reste affiche jusqu'a ce que le nouveau soit pret : rien ne clignote.
     */
    private static void redraw(Minecraft minecraft, ClientLevel level) {
        level.clearTintCaches();
        if (minecraft.player == null) {
            return;
        }
        int radius = minecraft.options.getEffectiveRenderDistance() + 1;
        int x = minecraft.player.getBlockX() >> 4;
        int z = minecraft.player.getBlockZ() >> 4;
        level.setSectionRangeDirty(x - radius, level.getMinY() >> 4, z - radius, x + radius, level.getMaxY() >> 4,
                z + radius);
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

    /**
     * La saison sous les pieds du joueur : son nom, le jour (1 a 7, 0 sous les
     * tropiques) et sa couleur ; {@code null} hors de la carte des saisons.
     */
    public static Now now(BlockPos pos) {
        SeasonClimate climate = SeasonClimate.CLIENT;
        if (!climate.active() || !onMap) {
            return null;
        }
        double phase = climate.phase();
        double latitude = climate.frame().latitude(pos.getZ() + 0.5d);
        if (SeasonMath.tropical(latitude)) {
            return new Now(SeasonMath.name(latitude, phase), 0, TROPICS);
        }
        double local = SeasonMath.local(phase, latitude);
        int season = SeasonMath.season(local);
        return new Now(SeasonMath.name(season), SeasonMath.day(local, 7), COLORS[season]);
    }

    /** La saison du moment, pour les ecrans (pause, liste des joueurs). */
    public record Now(String name, int day, int color) {
    }

    private static final int TROPICS = 0xFF7FD8C8;
    private static final int[] COLORS = {0xFFA9D8FF, 0xFF9BE07A, 0xFFFFD45C, 0xFFE8873A};
}
