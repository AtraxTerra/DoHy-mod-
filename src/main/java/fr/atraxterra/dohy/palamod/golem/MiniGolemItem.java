package fr.atraxterra.dohy.palamod.golem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/**
 * {@code ItemMiniGolem} : le golem en petit, pour la boite a golem. Pose au sol,
 * il redevient un golem — a celui qui le pose, en pleine vie, apres son
 * chargement. Sa barre dit les productions qu'il lui reste en boite.
 */
public class MiniGolemItem extends Item {

    private static final int BAR_WIDTH = 13;

    public MiniGolemItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        GolemData data = stack.get(PalaGolem.GOLEM_DATA.get());
        Player player = context.getPlayer();
        if (data == null || player == null || context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.BEDROCK)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().above();
        GuardianGolem golem = PalaGolem.GUARDIAN_GOLEM.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (golem == null) {
            return InteractionResult.FAIL;
        }
        golem.load(data);
        golem.setOwner(player);
        golem.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, player.getYRot() + 180.0f, 0.0f);
        golem.startLoading();
        level.addFreshEntity(golem);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(PalaGolem.GOLEM_DATA.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(BAR_WIDTH * durability(stack) / (float) GolemData.MINI_GOLEM_DURABILITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = durability(stack) / (float) GolemData.MINI_GOLEM_DURABILITY;
        return ((int) (255 * Math.min(1.0f, 2.0f - 2.0f * ratio)) << 16) | ((int) (255 * Math.min(1.0f, 2.0f * ratio)) << 8);
    }

    static int durability(ItemStack stack) {
        return stack.getOrDefault(PalaGolem.GOLEM_DATA.get(), GolemData.EMPTY).durability();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GolemData data = stack.get(PalaGolem.GOLEM_DATA.get());
        if (data == null) {
            return;
        }
        GuardianStoneItem.describe(data, tooltip);
        tooltip.accept(Component.translatable("item.palamod.mini_golem.durability", data.durability(),
                GolemData.MINI_GOLEM_DURABILITY).withStyle(ChatFormatting.GRAY));
        if (!data.has(GolemUpgrade.INBOX)) {
            tooltip.accept(Component.translatable("item.palamod.mini_golem.no_inbox").withStyle(ChatFormatting.RED));
        }
    }
}
