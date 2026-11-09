package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.function.Consumer;

/**
 * La gemme de monture ({@code ItemMountGemstone}).
 *
 * <p>Grise et « sauvage » au sortir d'une capture, elle s'apprivoise dans le
 * gestionnaire d'enclos et prend alors l'icone doree. L'icone se lit sur le
 * premier drapeau de {@link CustomModelData}, pose par {@link #stackOf}.
 */
public class MountGemstoneItem extends Item {

    public MountGemstoneItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stackOf(MountGem gem) {
        ItemStack stack = new ItemStack(PalaMount.MOUNT_GEMSTONE.get());
        write(stack, gem);
        return stack;
    }

    public static void write(ItemStack stack, MountGem gem) {
        stack.set(PalaMount.MOUNT_GEM.get(), gem);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(!gem.isWild()), List.of(), List.of()));
    }

    public static MountGem read(ItemStack stack) {
        return stack.is(PalaMount.MOUNT_GEMSTONE.get()) ? stack.get(PalaMount.MOUNT_GEM.get()) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        MountGem gem = stack.get(PalaMount.MOUNT_GEM.get());
        if (gem == null || gem.isWild()) {
            return Component.translatable("item.palamod.mount_gemstone.of",
                            Component.literal(gem == null ? MountGem.DEFAULT_NAME : gem.name()))
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("item.palamod.mount_gemstone.wild").withStyle(ChatFormatting.RED));
        }
        return Component.translatable("item.palamod.mount_gemstone.of",
                Component.literal(MountGem.DEFAULT_NAME).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        MountGem gem = stack.get(PalaMount.MOUNT_GEM.get());
        if (gem == null) {
            // Une vignette d'ecran : sa description est deja ecrite.
            return;
        }
        if (gem.isWild()) {
            tooltip.accept(Component.translatable("item.palamod.mount_gemstone.tame_hint")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        tooltip.accept(Component.translatable("item.palamod.mount_gemstone.name",
                Component.literal(gem.name()).withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable("item.palamod.mount_gemstone.level",
                Component.literal(String.valueOf(gem.level())).withStyle(ChatFormatting.GREEN),
                (int) gem.xp(), Component.literal(gem.sharedXp() + "%").withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable("item.palamod.mount_gemstone.health",
                MountTexts.health(gem.maxHealth() - gem.damage(), gem.maxHealth())).withStyle(ChatFormatting.YELLOW));
        long now = System.currentTimeMillis();
        if (gem.inCare()) {
            tooltip.accept(Component.translatable("item.palamod.mount_gemstone.care",
                    MountTexts.duration(gem.healEnd() - now)).withStyle(ChatFormatting.GREEN));
        }
        if (gem.expireTime() != -1L) {
            tooltip.accept(gem.isExpired(now)
                    ? Component.translatable("item.palamod.mount_gemstone.expired").withStyle(ChatFormatting.RED)
                    : Component.translatable("item.palamod.mount_gemstone.expires",
                    MountTexts.duration(gem.expireTime() - now)).withStyle(ChatFormatting.GREEN));
        }
    }

    /** {@code onItemUse} : poser une gemme sauvage ne fait rien, mais le dit. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        MountGem gem = context.getItemInHand().get(PalaMount.MOUNT_GEM.get());
        if (gem != null && gem.isWild() && context.getPlayer() != null) {
            if (!context.getLevel().isClientSide()) {
                context.getPlayer().sendSystemMessage(
                        Component.translatable("message.palamod.mount.tame_in_barn").withStyle(ChatFormatting.GREEN));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
