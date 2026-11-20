package fr.atraxterra.dohy.client.ui.vanilla;

import fr.atraxterra.dohy.client.season.ClientSeasons;
import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.ui.TabInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La liste des joueurs (touche Tab) au style du kit : chaque joueur avec sa tete, son
 * grade, sa faction et son niveau global, son ping en barres ; en tete l'heure du serveur, en
 * pied la prochaine remise a neuf de la Mine et la saison.
 *
 * <p>Elle remplace le calque du jeu. La faction et le niveau viennent du serveur
 * ({@link TabInfo}) ; le reste, le client le connait. Contrairement au jeu, la liste
 * s'affiche aussi en solo : l'heure et la Mine y servent autant.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class KitTabList implements GuiLayer {

    private static final Map<UUID, TabInfo.Entry> INFO = new ConcurrentHashMap<>();

    private static final float WIDTH = 200.0f;
    private static final float TOP = 15.0f;
    private static final int ROW_H = 14;
    private static final int ROW_PITCH = 16;

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TabInfo.TYPE, (payload, context) -> accept(payload));
    }

    private static void accept(TabInfo info) {
        INFO.clear();
        for (TabInfo.Entry entry : info.entries()) {
            INFO.put(entry.id(), entry);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        INFO.clear();
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.TAB_LIST, new KitTabList());
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (minecraft.player == null || connection == null || !minecraft.options.keyPlayerList.isDown()) {
            return;
        }
        List<PlayerInfo> players = new ArrayList<>(connection.getListedOnlinePlayers());
        players.sort(Comparator.<PlayerInfo>comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
                .thenComparing(info -> faction(info).isEmpty() ? "\uFFFF" : faction(info).toLowerCase(Locale.ROOT))
                .thenComparing(info -> info.getProfile().name().toLowerCase(Locale.ROOT)));
        draw(graphics, minecraft, players);
    }

    private static String faction(PlayerInfo info) {
        TabInfo.Entry entry = INFO.get(info.getProfile().id());
        return entry == null ? "" : entry.faction();
    }

    private static void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, List<PlayerInfo> players) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        UiGraphics g = ContainerStyle.begin(graphics, minecraft.font, -1, -1);
        Paint p = g.paint();
        float width = Math.min(WIDTH, screenWidth - 16.0f);
        float x = Math.round((screenWidth - width) / 2.0f);
        float y = TOP;
        int fit = Math.max(1, (int) ((screenHeight - TOP - 30 - 16) / ROW_PITCH));
        int shown = Math.min(players.size(), fit);
        float rowsTop = y + 30.0f;
        float height = 30.0f + shown * ROW_PITCH + 12.0f;

        p.shadow(x + 4, y + 8, width - 8, height - 6, 14, Paint.alpha(Ui.Theme.SHADOW, 0.5f));
        p.framed(x, y, width, height, 5.0f, 0xF017181B, 0xFF2C2E33, 1);

        // En tete : le logo, le nombre de joueurs, l'heure du serveur.
        float logo = 10.5f;
        MenuStyle.logo(g, x + 7.0f, y + 11.5f, logo);
        String count = (minecraft.isLocalServer() ? "SOLO" : "SERVEUR") + "  \u00B7  " + players.size()
                + (players.size() > 1 ? " JOUEURS" : " JOUEUR");
        g.text(Ui.PIXEL, count, x + 11.5f + MenuStyle.logoWidth(g, logo), y + 15.0f, 3.3f, Ui.Theme.DIM);
        g.right(Ui.WIDE, MenuStyle.city() + "  " + MenuStyle.clock(), x + width - 7.5f, y + 14.0f, 4.0f,
                Ui.Theme.TEXT2);

        // Les joueurs.
        float rowX = x + 7.5f;
        float rowW = width - 15.0f;
        for (int index = 0; index < shown; index++) {
            PlayerInfo info = players.get(index);
            int rowY = Math.round(rowsTop) + index * ROW_PITCH;
            TabInfo.Entry entry = INFO.get(info.getProfile().id());
            int color = entry == null || entry.faction().isEmpty() ? 0xFF5D6068 : entry.color();
            p.framed(rowX, rowY, rowW, ROW_H, 3.0f, 0xFF1C1D21, 0xFF2A2C31, 1);
            p.round(rowX, rowY + 2.0f, 1.0f, ROW_H - 4.0f, 0.5f, color);
            PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), Math.round(rowX + 6), rowY + 4, 6);
            String name = info.getProfile().name();
            boolean spectator = info.getGameMode() == GameType.SPECTATOR;
            boolean graded = entry != null && !entry.grade().isEmpty();
            // Le pseudo prend la couleur du grade, et le grade sa pastille, avant celle de la faction.
            g.text(Ui.BOLD, name, rowX + 16.0f, rowY + 4.4f, 4.6f, spectator ? Ui.Theme.MUTED
                    : graded && entry.gradeColor() != 0 ? entry.gradeColor() : Ui.Theme.TEXT);
            float chipX = rowX + 16.0f + g.width(Ui.BOLD, name, 4.6f) + 4.0f;
            if (graded) {
                int gradeColor = entry.gradeColor() != 0 ? entry.gradeColor() : Ui.Theme.TEXT2;
                float gradeW = g.width(Ui.PIXEL, entry.grade(), 3.0f) + 6.0f;
                p.framed(chipX, rowY + 3.5f, gradeW, 7.0f, 3.5f, Paint.mix(0xFF1C1D21, gradeColor, 0.22f),
                        Paint.mix(0xFF2A2C31, gradeColor, 0.6f), 1);
                g.text(Ui.PIXEL, entry.grade(), chipX + 3.0f, rowY + 5.6f, 3.0f, gradeColor);
                chipX += gradeW + 3.0f;
            }
            if (entry != null && !entry.faction().isEmpty()) {
                String tag = ContainerStyle.plain(entry.faction()).toUpperCase(Locale.ROOT);
                float tagW = g.width(Ui.PIXEL, tag, 3.0f) + 6.0f;
                p.framed(chipX, rowY + 3.5f, tagW, 7.0f, 3.5f, Paint.mix(0xFF1C1D21, color, 0.16f),
                        Paint.mix(0xFF2A2C31, color, 0.45f), 1);
                g.text(Ui.PIXEL, tag, chipX + 3.0f, rowY + 5.6f, 3.0f, color);
            } else {
                p.framed(chipX, rowY + 3.5f, 7.0f, 7.0f, 3.5f, 0xFF24262A, 0xFF33363C, 1);
                p.round(chipX + 2.4f, rowY + 6.75f, 2.2f, 0.5f, 0.25f, Ui.Theme.DIM);
            }
            if (entry != null) {
                g.right(Ui.WIDE, "NIV. " + entry.level(), rowX + rowW - 17.0f, rowY + 5.0f, 4.0f, Ui.Theme.TEXT2);
            }
            ping(p, rowX + rowW - 13.5f, rowY + 10.5f, info.getLatency());
        }

        // Pied : la Mine, les joueurs qui ne tiennent pas, la saison.
        float footY = rowsTop + shown * ROW_PITCH + 1.5f;
        String foot = "MINE : " + MenuStyle.mineReset();
        if (players.size() > shown) {
            foot += "   \u00B7   +" + (players.size() - shown) + " JOUEURS";
        }
        g.text(Ui.PIXEL, foot, rowX, footY, 3.3f, Ui.Theme.DIM);
        ClientSeasons.Now season = ClientSeasons.now(minecraft.player.blockPosition());
        if (season != null) {
            String text = ContainerStyle.plain(season.name()).toUpperCase(Locale.ROOT)
                    + (season.day() > 0 ? "  \u00B7  J" + season.day() : "");
            g.right(Ui.PIXEL, text, rowX + rowW, footY, 3.3f, season.color());
        }
    }

    /** Le ping en cinq barres, comme le jeu : de 5 (moins de 150 ms) a 1 (plus d'une seconde). */
    private static void ping(Paint p, float x, float bottom, int latency) {
        int bars;
        if (latency < 0) {
            bars = 0;
        } else if (latency < 150) {
            bars = 5;
        } else if (latency < 300) {
            bars = 4;
        } else if (latency < 600) {
            bars = 3;
        } else if (latency < 1000) {
            bars = 2;
        } else {
            bars = 1;
        }
        int color = bars >= 4 ? Ui.Theme.GREEN_L : bars == 3 ? Ui.Theme.YELLOW : Ui.Theme.RED;
        for (int index = 0; index < 5; index++) {
            float height = 2.0f + index * 1.2f;
            p.round(x + index * 2.0f, bottom - height, 1.2f, height, 0.4f, index < bars ? color : 0xFF3A3C42);
        }
    }
}
