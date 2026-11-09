package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Les messages de la monture.
 *
 * <p>Mount passait par les notifications de PaladiumUI, en quatre couleurs :
 * erreur, succes, information, avertissement. Ici, un message dans le chat de
 * la meme couleur.
 */
public final class MountTexts {

    private MountTexts() {
    }

    public static void error(ServerPlayer player, String key, Object... args) {
        send(player, ChatFormatting.RED, key, args);
    }

    public static void success(ServerPlayer player, String key, Object... args) {
        send(player, ChatFormatting.GREEN, key, args);
    }

    public static void info(ServerPlayer player, String key, Object... args) {
        send(player, ChatFormatting.YELLOW, key, args);
    }

    public static void warning(ServerPlayer player, String key, Object... args) {
        send(player, ChatFormatting.GOLD, key, args);
    }

    private static void send(ServerPlayer player, ChatFormatting color, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.palamod.mount." + key, args).withStyle(color));
    }

    /** « 12 / 40 », en demi-coeurs comme partout dans Mount. */
    public static MutableComponent health(float current, float max) {
        return Component.literal(Math.round(Math.max(0.0f, current)) + " / " + Math.round(max));
    }

    /**
     * « 1h30mn », « 3j 4h12mn ». <b>Ecart</b> : l'original affichait les
     * heures modulo 24 pour un soin, d'ou un soin de 25 heures annonce a 1 h.
     */
    public static MutableComponent duration(long millis) {
        long minutes = Math.max(0L, millis) / 60_000L;
        long days = minutes / (24 * 60);
        long hours = minutes / 60 % 24;
        long rest = minutes % 60;
        return Component.literal((days > 0 ? days + "j " : "") + hours + "h" + rest + "mn");
    }
}
