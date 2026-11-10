package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Un menu generique cote serveur : il construit sa {@link MenuView} et traite
 * les clics sur ses cartes.
 *
 * <p>Apres une action, la vue est renvoyee telle quelle si le joueur est
 * toujours sur ce menu : les chiffres (solde, paliers) se mettent a jour sans
 * fermer l'ecran. Une action qui ouvre un autre ecran n'est donc pas ecrasee.
 */
public abstract class MenuController {

    private final Identifier id;

    protected MenuController(String path) {
        this.id = UiNet.id(path);
    }

    public final Identifier id() {
        return id;
    }

    public abstract MenuView view(ServerPlayer player);

    /** Un clic sur une carte : sa cle d'action, et le bouton (0 gauche, 1 droit). */
    protected abstract void card(ServerPlayer player, String key, int button);

    /** L'hexagone de gauche ; par defaut, le menu principal. */
    protected void back(ServerPlayer player) {
        HubController.INSTANCE.open(player);
    }

    public void open(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        UiNet.show(player, view(player));
    }

    public final void handle(ServerPlayer player, UiNet.Action action) {
        switch (action.verb()) {
            case "back" -> back(player);
            case "card" -> {
                card(player, action.argument(), action.amount());
                if (UiNet.isOpen(player, id)) {
                    UiNet.show(player, view(player));
                }
            }
            default -> {
            }
        }
    }

    // -- Retours au joueur -------------------------------------------------------

    protected static void success(ServerPlayer player) {
        play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
    }

    protected static void failure(ServerPlayer player) {
        play(player, SoundEvents.VILLAGER_NO, 1.0f);
    }

    protected static void play(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 0.4f, pitch);
    }

    /** Le refus en rouge, ou le message de reussite en vert, avec le son qui va. */
    protected static void report(ServerPlayer player, String refusal, String message) {
        if (refusal == null) {
            success(player);
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
        } else {
            failure(player);
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
    }

    /** Un pourcentage d'avancement, borne. */
    protected static String percent(long done, long total) {
        return total <= 0 ? "100 %" : Math.min(100, done * 100 / total) + " %";
    }
}
