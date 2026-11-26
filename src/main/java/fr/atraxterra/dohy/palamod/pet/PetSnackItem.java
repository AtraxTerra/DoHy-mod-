package fr.atraxterra.dohy.palamod.pet;

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
 * La friandise pour familier ({@code pet_snack}) : un clic droit la donne au
 * familier, ce qui compte pour la quete « Nourrir ton familier » et lui rend un
 * peu de bonheur. Une par seconde.
 */
public class PetSnackItem extends Item {

    /** Le bonheur d'une friandise. */
    public static final int HAPPINESS = 5;

    public PetSnackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.getData(PalaPets.LOOK.get()).familiar() == null) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("[Familier] Tu n'as pas de familier a nourrir.")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, 20);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        PetApi.addHappiness(server, HAPPINESS);
        PetData data = PetApi.data(server);
        server.sendSystemMessage(Component.literal("[Familier] " + data.familiar().label() + " croque la friandise (+"
                + HAPPINESS + " bonheur, " + data.happiness() + "/" + PetApi.maxHappiness(data.level()) + ").")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        PetApi.progress(server, PetMission.FEED, 1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.pet_snack", HAPPINESS).withStyle(ChatFormatting.GRAY));
    }
}
