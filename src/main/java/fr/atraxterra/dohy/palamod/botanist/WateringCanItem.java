package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

/**
 * L'arrosoir ({@code ItemBotanistWateringCan}, un {@code ABotanistChargedTool} de
 * dix charges) : il se remplit a une source d'eau et repond au besoin « eau »
 * d'une plante en pot. Chaque arrosage prend une charge et use l'arrosoir
 * (32 utilisations, sans reparation).
 */
public class WateringCanItem extends Item {

    public static final int CAPACITY = 10;

    public WateringCanItem(Properties properties) {
        super(properties);
    }

    public static int charges(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(PalaBotanist.CHARGES.get(), 0), 0, CAPACITY);
    }

    public static void setCharges(ItemStack stack, int charges) {
        stack.set(PalaBotanist.CHARGES.get(), Mth.clamp(charges, 0, CAPACITY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return InteractionResult.PASS;
        }
        if (charges(stack) >= CAPACITY) {
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.literal("L'arrosoir est deja plein.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            setCharges(stack, CAPACITY);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL,
                    SoundSource.PLAYERS, 1.0f, 1.1f);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, hit.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int charges = charges(stack);
        tooltip.accept(Component.translatable("tooltip.palamod.watering_can.charges",
                Component.literal(charges + "/" + CAPACITY)
                        .withStyle(charges > 0 ? ChatFormatting.AQUA : ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.watering_can.fill").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.botanist.tool.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
