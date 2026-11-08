package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Aimant : attire les objets au sol quand il est actif.
 *
 * <p>Releve dans {@code ItemMagnet} : un clic droit bascule l'etat
 * {@code miner_magnet} et l'objet brille quand il est actif. L'etat porte ici sur
 * le composant {@code enchantment_glint_override} — present quand l'aimant est
 * actif, absent sinon — ce qui donne la brillance sans rien de plus, et un
 * modele different par etat ({@code magnet_on.png}, comme dans le pack).
 *
 * <p>L'attraction est dans {@link MinerPocketEvents}.
 */
public class MagnetItem extends Item {

    public MagnetItem(Properties properties) {
        super(properties);
    }

    public static boolean isActive(ItemStack stack) {
        return stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean active = !isActive(stack);
        if (active) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal(active ? "Aimant active" : "Aimant desactive")
                            .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
        return InteractionResult.SUCCESS;
    }
}
