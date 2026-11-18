package fr.atraxterra.dohy.palamod.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * La cle en paladium sur un tiroir de Vault Drawers.
 *
 * <p>Le clic est pris <b>avant</b> le tiroir : ferme a cle, il ignore tout
 * joueur qui n'en est pas le proprietaire, et la cle n'arriverait jamais
 * jusqu'a {@code Item#useOn}. L'evenement est annule des deux cotes pour que le
 * client n'essaie pas non plus de ranger la cle dans le tiroir.
 *
 * <p>Priorite basse : le verrou de metier ({@code JobGateEvents}, priorite
 * normale) passe d'abord. Il annule l'evenement sans le niveau 85 de Chasseur,
 * et un evenement annule n'arrive pas ici.
 *
 * <p>Les messages sont ceux de PalaMod, sans les accents ni la faute de
 * « verrouille ».
 */
@EventBusSubscriber
public final class PaladiumKeyEvents {
    static final String UNLOCKED = "Drawer deverrouille.";
    static final String NOT_LOCKED = "Ce drawer n'est pas verrouille.";

    private PaladiumKeyEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(PalaStorage.PALADIUM_KEY.get())) {
            return;
        }
        BlockEntity drawer = event.getLevel().getBlockEntity(event.getPos());
        if (!VaultDrawersCompat.isLockable(drawer)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!VaultDrawersCompat.isLocked(drawer)) {
            player.sendSystemMessage(Component.literal(NOT_LOCKED).withStyle(ChatFormatting.RED));
            return;
        }
        if (VaultDrawersCompat.unlock(drawer)) {
            // Le creatif garde sa cle, comme dans PalaMod.
            stack.consume(1, player);
            player.sendSystemMessage(Component.literal(UNLOCKED).withStyle(ChatFormatting.GREEN));
        }
    }
}
