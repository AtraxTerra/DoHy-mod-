package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Le « bonbon a deballer » : recompense de metier (niveau 95) et butin des
 * boss de donjon (des le palier 50). Deballe, il donne un bonbon de metier :
 * le multicolore une fois sur dix, sinon l'un des quatre de couleur (22,5 %
 * chacun) (le wiki ne dit pas les chances : elles sont
 * de DoHy).
 */
public class WrappedCandyItem extends Item {

    public WrappedCandyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        Item inside = player.getRandom().nextInt(10) == 0 ? PalaDungeon.CANDY_RAINBOW.get()
                : switch (player.getRandom().nextInt(4)) {
                    case 0 -> PalaDungeon.CANDY_GREEN.get();
                    case 1 -> PalaDungeon.CANDY_YELLOW.get();
                    case 2 -> PalaDungeon.CANDY_BLUE.get();
                    default -> PalaDungeon.CANDY_PURPLE.get();
                };
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        ItemStack candy = new ItemStack(inside);
        Component name = candy.getHoverName();
        if (!player.getInventory().add(candy)) {
            player.drop(candy, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.COMPOSTER_FILL, SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        serverPlayer.sendSystemMessage(Component.translatable("message.palamod.candy_wrapped", name)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.candy_wrapped").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.candy_wrapped.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
