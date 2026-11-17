package fr.atraxterra.dohy.arrakis;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.component.DataComponents;

import java.util.function.Consumer;

/**
 * La gourde : huit gorgees d'eau. On la boit comme une potion, une gorgee a la fois
 * (un cinquieme de la reserve d'eau) ; elle ne se vide pas pour rien : sans soif, on ne
 * boit pas. On la remplit a un piege a vent, a la borne EAU d'Arrakeen, ou a n'importe
 * quelle eau hors d'Arrakis (sur Arrakis, l'eau versee s'evapore).
 *
 * <p>Sa barre, sous l'icone, dit ce qu'il reste.
 */
public class CanteenItem extends Item {

    /** Les gorgees d'une gourde pleine. */
    public static final int CAPACITY = 8;
    /** Les gorgees de la gourde de sietch. */
    public static final int SIETCH_CAPACITY = 16;
    private static final int BAR = 0x3FA9F5;

    private final int capacity;

    public CanteenItem(Properties properties) {
        this(properties, CAPACITY);
    }

    public CanteenItem(Properties properties, int capacity) {
        super(properties.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
        this.capacity = capacity;
    }

    /** Les gorgees que tient cette gourde, pleine. */
    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof CanteenItem canteen ? canteen.capacity : CAPACITY;
    }

    /** Les gorgees qu'il reste dans cette gourde. */
    public static int water(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ArrakisContent.WATER.get(), 0), 0, capacity(stack));
    }

    public static void setWater(ItemStack stack, int sips) {
        stack.set(ArrakisContent.WATER.get(), Mth.clamp(sips, 0, capacity(stack)));
    }

    /** Une gourde pleine. */
    public static ItemStack filled(Item canteen) {
        ItemStack stack = new ItemStack(canteen);
        setWater(stack, capacity(stack));
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // a une eau libre (hors d'Arrakis), on la remplit
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && water(stack) < capacity(stack)
                && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            if (!level.isClientSide()) {
                setWater(stack, capacity(stack));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL,
                        SoundSource.PLAYERS, 1.0f, 0.8f);
                level.gameEvent(player, GameEvent.FLUID_PICKUP, hit.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        }
        if (water(stack) <= 0) {
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.literal("Gourde vide : remplis-la a un piege a vent.")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.FAIL;
        }
        if (!Hydration.thirsty(player)) {
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.literal("Tu n'as pas soif : garde ton eau.")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    /** Une gorgee : la gourde ne se consomme pas, elle se vide. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && water(stack) > 0) {
            setWater(stack, water(stack) - 1);
            Hydration.drink(player, Hydration.SIP);
            if (water(stack) == 0) {
                player.sendSystemMessage(Component.literal("Ta gourde est vide.").withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * water(stack) / capacity(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.dohy.canteen.water", water(stack), capacity(stack))
                .withStyle(ChatFormatting.AQUA));
    }
}
