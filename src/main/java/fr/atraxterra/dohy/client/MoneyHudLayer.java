package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Affichage du solde, au centre du bord droit, avec l'animation de gain.
 *
 * <p>Ecrit directement pour le rendu en mode retenu de 26.2 : une couche de HUD
 * n'affiche pas, elle ecrit son etat dans un {@link GuiGraphicsExtractor} que le
 * moteur rend ensuite. {@code GuiGraphics} n'existe plus.
 */
public final class MoneyHudLayer implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int COLOR_BALANCE = 0xFFFFD24A;
    private static final int COLOR_GAIN = 0xFF63D865;
    private static final int COLOR_LOSS = 0xFFE06C6C;
    private static final int COLOR_BACKDROP = 0x66000000;

    /** Duree d'affichage du delta, en millisecondes. */
    private static final long DELTA_DURATION = 1500L;

    private long lastKnownBalance = Long.MIN_VALUE;
    private long pendingDelta;
    private long deltaShownAt;
    private boolean loggedFailure;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            renderInner(graphics);
        } catch (Throwable error) {
            // Une exception dans une couche de HUD fait tomber le jeu. On
            // degrade vers "rien affiche", avec une seule ligne de log.
            if (!loggedFailure) {
                loggedFailure = true;
                DoHy.LOGGER.error("Le HUD du solde a echoue, il est desactive pour cette session", error);
            }
        }
    }

    private Component balanceLabel;
    private long labelBalance;
    private int labelWidth;

    private void renderInner(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        PlayerEconomy data = Economy.of(player);
        if (data.hudHidden()) {
            // On continue de suivre le solde pour ne pas afficher un delta
            // enorme au moment ou le HUD est reaffiche.
            lastKnownBalance = data.balance();
            return;
        }

        trackDelta(data.balance());

        Font font = minecraft.font;
        // Le texte ne change qu'avec le solde : on ne le reformate pas a chaque image.
        if (balanceLabel == null || data.balance() != labelBalance) {
            labelBalance = data.balance();
            balanceLabel = Component.literal(MoneyFormat.shortForm(labelBalance));
            labelWidth = font.width(balanceLabel);
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int textWidth = labelWidth;
        int x = screenWidth - MARGIN - textWidth;
        // Centre vertical du bord droit : hors du chemin de la barre d'effets en
        // haut a droite, et loin de la hotbar et du chat en bas.
        int y = (screenHeight - font.lineHeight) / 2;

        graphics.fill(x - 4, y - 3, screenWidth - MARGIN + 2, y + font.lineHeight + 1, COLOR_BACKDROP);
        graphics.text(font, balanceLabel, x, y, COLOR_BALANCE);

        renderDelta(graphics, font, screenWidth, y + font.lineHeight + 4);
    }

    private void trackDelta(long balance) {
        if (lastKnownBalance == Long.MIN_VALUE) {
            lastKnownBalance = balance;
            return;
        }
        if (balance != lastKnownBalance) {
            pendingDelta = balance - lastKnownBalance;
            deltaShownAt = System.currentTimeMillis();
            lastKnownBalance = balance;
        }
    }

    private void renderDelta(GuiGraphicsExtractor graphics, Font font, int screenWidth, int y) {
        if (pendingDelta == 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - deltaShownAt;
        if (elapsed >= DELTA_DURATION) {
            pendingDelta = 0L;
            return;
        }

        float progress = 1f - (float) elapsed / DELTA_DURATION;
        int alpha = Math.max(0, Math.min(255, (int) (progress * 255f)));
        int baseColor = pendingDelta > 0 ? COLOR_GAIN : COLOR_LOSS;
        int color = (alpha << 24) | (baseColor & 0x00FFFFFF);

        String text = MoneyFormat.delta(pendingDelta);
        int x = screenWidth - MARGIN - font.width(text);
        graphics.text(font, Component.literal(text), x, y, color);
    }
}
