package fr.atraxterra.dohy.guide.plan;

import fr.atraxterra.dohy.guide.DoHyGuide;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Le plan de construction : l'hologramme d'une structure, pose la ou l'on veut
 * la batir (le Blueprint de Paladium).
 *
 * <ul>
 *   <li>clic droit sur un bloc : le plan se pose devant toi, sur ce bloc, dans
 *       le sens de ton regard ; un nouveau clic le deplace ;</li>
 *   <li>accroupi, clic droit : un quart de tour ;</li>
 *   <li>clic droit en l'air : le menu du plan (structure, taille, materiel,
 *       retirer le plan).</li>
 * </ul>
 * L'hologramme se dessine tant que le plan est dans l'inventaire : bleu pour
 * un bloc a poser, rouge pour un bloc a retirer, jaune pour un bloc a finir
 * (une obsidienne a charger, de l'eau qui coule), vert pour un bloc en place.
 */
public class PlanItem extends Item {

    public PlanItem(Properties properties) {
        super(properties);
    }

    public static PlanData data(ItemStack stack) {
        PlanData data = stack.get(DoHyGuide.PLAN_DATA.get());
        return data != null ? data : PlanData.DEFAULT;
    }

    /** Un plan neuf, sur cette structure et cette taille. */
    public static ItemStack of(String blueprint, int variant) {
        ItemStack stack = new ItemStack(DoHyGuide.PLAN.get());
        stack.set(DoHyGuide.PLAN_DATA.get(), new PlanData(blueprint, variant, java.util.Optional.empty()));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        PlanData data = data(stack);
        Blueprint blueprint = data.resolve();
        return Component.translatable("item.dohy.plan.of", blueprint.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        PlanData data = data(stack);
        Blueprint blueprint = data.resolve();
        Blueprint.Variant variant = blueprint.variant(data.variant());
        tooltip.accept(Component.literal(variant.label() + " · " + variant.required() + " blocs")
                .withStyle(ChatFormatting.GRAY));
        data.anchor().ifPresentOrElse(
                anchor -> tooltip.accept(Component.literal("Pose en " + anchor.pos().getX() + " " + anchor.pos().getY()
                        + " " + anchor.pos().getZ()).withStyle(ChatFormatting.AQUA)),
                () -> tooltip.accept(Component.literal("Pas encore pose").withStyle(ChatFormatting.DARK_GRAY)));
        tooltip.accept(Component.translatable("item.dohy.plan.help.place").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.dohy.plan.help.turn").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.dohy.plan.help.menu").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        PlanData data = data(stack);
        if (player.isShiftKeyDown()) {
            turn(server, stack, data);
            return InteractionResult.SUCCESS;
        }
        BlockPos anchor = context.getClickedPos().relative(context.getClickedFace());
        int quarter = player.getDirection().get2DDataValue();
        String dimension = context.getLevel().dimension().identifier().toString();
        stack.set(DoHyGuide.PLAN_DATA.get(), data.withAnchor(new PlanData.Anchor(dimension, anchor, quarter)));
        Blueprint blueprint = data.resolve();
        Blueprint.Variant variant = blueprint.variant(data.variant());
        server.sendSystemMessage(Component.literal("Plan pose : " + blueprint.name() + " (" + variant.label() + "), "
                + variant.required() + " blocs. Accroupi + clic droit pour tourner, clic droit en l'air pour le menu.")
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            turn(server, stack, data(stack));
        } else {
            PlanMenu.open(server, hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static void turn(ServerPlayer player, ItemStack stack, PlanData data) {
        if (data.anchor().isEmpty()) {
            player.sendSystemMessage(Component.literal("Pose d'abord le plan : clic droit sur un bloc.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        stack.set(DoHyGuide.PLAN_DATA.get(), data.withAnchor(data.anchor().get().turned()));
        player.sendSystemMessage(Component.literal("Plan tourne d'un quart.").withStyle(ChatFormatting.AQUA), true);
    }
}
