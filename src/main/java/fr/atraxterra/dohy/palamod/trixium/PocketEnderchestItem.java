package fr.atraxterra.dohy.palamod.trixium;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * L'enderchest de poche ({@code ItemPocketEnderchest}), objet exclusif du
 * concours Trixium : un clic droit ouvre l'ender chest du joueur, ou qu'il soit,
 * sauf en combat ({@link CombatTag}).
 */
public class PocketEnderchestItem extends Item {

    public PocketEnderchestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (CombatTag.inCombat(server)) {
            server.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal("Cet objet n'est pas utilisable en combat.").withStyle(ChatFormatting.RED)));
            return InteractionResult.FAIL;
        }
        server.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> ChestMenu.threeRows(containerId, inventory,
                        server.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
        server.level().playSound(null, server.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS,
                0.5f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.pocket_enderchest").withStyle(ChatFormatting.GRAY));
    }
}
