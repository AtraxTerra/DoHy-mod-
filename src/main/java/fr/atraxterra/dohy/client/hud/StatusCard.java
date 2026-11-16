package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.disaster.DisasterClient;
import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.faction.FactionLeague;
import fr.atraxterra.dohy.ui.HudStatus;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * La carte de faction, en haut a droite : le solde (et ce qu'on vient de gagner ou de
 * perdre), la faction avec sa couleur et sa ligue, ses claims et son ELO, puis les alertes
 * regroupees — le siege en cours, la nation qui menace ou attaque et son echeance, la
 * catastrophe ou l'on se trouve. Elle remplace l'ancien solde au milieu du bord droit.
 *
 * <p>{@code /money hud} la masque, comme il masquait le solde.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class StatusCard {

    static final int MARGIN = 6;
    private static final float WIDTH = 108.0f;
    private static final float PAD = 6.0f;
    private static final float RADIUS = 6.0f;
    private static final long DELTA_MILLIS = 1800L;

    private static volatile HudStatus status;
    /** L'horloge du client moins celle du serveur, a la reception. */
    private static volatile long offset;

    private static long knownBalance = Long.MIN_VALUE;
    private static long delta;
    private static long deltaAt;
    /** Le bas de la carte a la derniere image : les effets se rangent dessous. */
    private static int bottom = MARGIN - 4;

    private StatusCard() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(HudStatus.TYPE, (payload, context) -> {
            offset = System.currentTimeMillis() - payload.now();
            status = payload;
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        status = null;
        knownBalance = Long.MIN_VALUE;
        delta = 0L;
    }

    static int bottom() {
        return bottom;
    }

    static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || HudKit.hidden(minecraft)) {
            bottom = MARGIN - 4;
            return;
        }
        PlayerEconomy economy = Economy.of(player);
        track(economy.balance());
        if (economy.hudHidden()) {
            bottom = MARGIN - 4;
            return;
        }
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        UiGraphics ui = new UiGraphics(graphics, minecraft.font, 0, 0);
        HudStatus info = status;
        List<Alert> alerts = alerts(info, player);

        float height = PAD + 7.5f;
        boolean faction = info != null && info.hasFaction();
        height += faction ? 16.0f : 8.0f;
        if (!alerts.isEmpty()) {
            height += 4.5f + alerts.size() * 8.0f;
        }
        height += 3.0f;
        float x = graphics.guiWidth() - MARGIN - WIDTH;
        float y = MARGIN;
        HudKit.card(paint, x, y, WIDTH, height, RADIUS);

        // Le solde : le billet, le montant en or, le dernier gain ou la derniere perte qui s'efface.
        float row = y + PAD - 0.5f;
        ui.texture(Ui.MONEY, Math.round(x + PAD), Math.round(row + 0.5f), 10, 6, 56, 34);
        String balance = MoneyFormat.exact(economy.balance());
        float room = WIDTH - PAD * 2 - 13.0f;
        String change = deltaText();
        if (change != null) {
            room -= ui.width(Ui.BOLD, change, 4.4f) + 3.0f;
        }
        if (ui.width(Ui.BOLD, balance, 6.0f) > room) {
            balance = MoneyFormat.compact(economy.balance());
        }
        ui.textShaded(Ui.BOLD, balance, x + PAD + 13.0f, row, 6.0f, Ui.Theme.YELLOW, 0x99000000, 0.6f);
        if (change != null) {
            float fade = 1.0f - Math.min(1.0f, (System.currentTimeMillis() - deltaAt) / (float) DELTA_MILLIS);
            int color = delta > 0 ? Ui.Theme.GREEN_L : Ui.Theme.RED_LIGHT;
            ui.right(Ui.BOLD, change, x + WIDTH - PAD, row + 1.0f, 4.4f, Paint.alpha(color, fade));
        }
        row += 9.5f;

        if (faction) {
            FactionLeague league = FactionLeague.of(info.league());
            String tag = league.label().toUpperCase(java.util.Locale.ROOT);
            float tagWidth = ui.width(Ui.BOLD, tag, 3.4f) + 5.0f;
            paint.dot(x + PAD + 2.0f, row + 2.6f, 2.0f, info.color());
            String name = ui.fit(Ui.BOLD, info.faction(), 4.8f, WIDTH - PAD * 2 - 7.0f - tagWidth - 3.0f);
            ui.text(Ui.BOLD, name, x + PAD + 7.0f, row, 4.8f, Ui.Theme.TEXT);
            float tagX = x + WIDTH - PAD - tagWidth;
            paint.framed(tagX, row - 0.4f, tagWidth, 6.0f, 3.0f, Ui.Theme.BG1, Paint.mix(Ui.Theme.EDGE,
                    league.color(), 0.8f), 1);
            ui.text(Ui.BOLD, tag, tagX + 2.5f, row + 0.9f, 3.4f, league.color());
            row += 7.5f;
            String stats = info.claims() + " / " + info.claimCap() + " claims  ·  "
                    + MoneyFormat.exact(info.elo()) + " ELO";
            ui.text(Ui.BODY, ui.fit(Ui.BODY, stats, 4.1f, WIDTH - PAD * 2), x + PAD, row, 4.1f, Ui.Theme.MUTED);
            row += 8.5f;
        } else {
            ui.text(Ui.BODY, "Sans faction", x + PAD, row, 4.4f, Ui.Theme.MUTED);
            row += 8.0f;
        }

        if (!alerts.isEmpty()) {
            paint.rect(x + PAD, row - 0.5f, WIDTH - PAD * 2, 0.5f, Ui.Theme.LINE);
            row += 3.0f;
            for (Alert alert : alerts) {
                float pulse = alert.urgent() ? 0.45f + 0.55f * Anim.pulse(1.1d) : 1.0f;
                paint.dot(x + PAD + 1.6f, row + 2.3f, 1.6f, Paint.alpha(alert.color(), pulse));
                float textX = x + PAD + 5.5f;
                float space = WIDTH - PAD * 2 - 5.5f;
                if (alert.right() != null) {
                    float width = ui.width(Ui.BOLD, alert.right(), 4.0f);
                    ui.right(Ui.BOLD, alert.right(), x + WIDTH - PAD, row + 0.2f, 4.0f, Ui.Theme.TEXT);
                    space -= width + 3.0f;
                }
                ui.text(Ui.BOLD, ui.fit(Ui.BOLD, alert.text(), 4.2f, space), textX, row, 4.2f, alert.color());
                row += 8.0f;
            }
        }
        bottom = Math.round(y + height);
    }

    /** Une ligne d'alerte : le texte et sa couleur, une valeur a droite, et si elle pulse. */
    private record Alert(String text, int color, String right, boolean urgent) {
    }

    private static List<Alert> alerts(HudStatus info, LocalPlayer player) {
        List<Alert> alerts = new ArrayList<>();
        if (info != null && !info.siege().isEmpty()) {
            alerts.add(new Alert(info.siege(), Ui.Theme.RED_LIGHT, null, true));
        }
        if (info != null && info.stance() > 0 && !info.nation().isEmpty()) {
            boolean war = info.stance() == 2;
            String text = (war ? "Guerre · " : "Ultimatum · ") + info.nation();
            String right = info.deadline() > 0L ? countdown(info.deadline() + offset - System.currentTimeMillis())
                    : war ? "heure ?" : null;
            alerts.add(new Alert(text, war ? Ui.Theme.RED_LIGHT : Ui.Theme.ORANGE, right, war));
        }
        DisasterClient.Alert disaster = DisasterClient.alert(player.getX(), player.getZ());
        if (disaster != null && alerts.size() < 3) {
            alerts.add(new Alert(capitalize(disaster.text()), disaster.color(), null, true));
        }
        return alerts;
    }

    private static String countdown(long millis) {
        if (millis <= 0L) {
            return "maintenant";
        }
        long minutes = Math.max(1L, millis / 60_000L);
        if (minutes < 60L) {
            return minutes + " min";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return hours + " h " + String.format(java.util.Locale.ROOT, "%02d", minutes % 60L);
        }
        return hours / 24L + " j";
    }

    /** Les alertes des catastrophes arrivent en capitales ; la carte les ecrit comme une phrase. */
    private static String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static void track(long balance) {
        if (knownBalance == Long.MIN_VALUE) {
            knownBalance = balance;
            return;
        }
        if (balance != knownBalance) {
            long now = System.currentTimeMillis();
            // Des gains rapproches s'additionnent dans la meme ligne au lieu de se remplacer.
            delta = now - deltaAt < DELTA_MILLIS ? delta + (balance - knownBalance) : balance - knownBalance;
            deltaAt = now;
            knownBalance = balance;
        }
    }

    private static String deltaText() {
        if (delta == 0L || System.currentTimeMillis() - deltaAt >= DELTA_MILLIS) {
            return null;
        }
        return MoneyFormat.delta(delta);
    }
}
