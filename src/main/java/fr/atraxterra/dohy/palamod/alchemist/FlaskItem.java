package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.LevelReader;

import java.util.List;
import java.util.function.Consumer;

/**
 * Fiole ({@code ItemFlask}) : vide, ou remplie de un a six sixiemes d'une seve.
 * Par seize, comme l'original. L'icone suit le remplissage, par la donnee de
 * modele que {@link #setSap} tient a jour.
 */
public class FlaskItem extends Item {

    public FlaskItem(Properties properties) {
        super(properties);
    }

    /** Pose le contenu (ou le retire, {@code null}) et l'icone qui va avec. */
    public static ItemStack setSap(ItemStack stack, FlaskSap sap) {
        if (sap == null) {
            stack.remove(PalaAlchemist.FLASK_SAP.get());
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        } else {
            stack.set(PalaAlchemist.FLASK_SAP.get(), sap);
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of((float) sap.level()), List.of(), List.of(), List.of()));
        }
        return stack;
    }

    /**
     * Retire {@code used} objets de la pile en main et donne {@code result} : la
     * main le recoit si la pile est epuisee, l'inventaire sinon, le sol a defaut.
     */
    public static void replace(Player player, InteractionHand hand, ItemStack stack, int used, ItemStack result) {
        if (used >= stack.getCount()) {
            player.setItemInHand(hand, result);
            return;
        }
        stack.shrink(used);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }

    /** Accroupi, le clic droit doit encore atteindre le tank et l'extracteur. */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).getBlock() instanceof TankBlock
                || level.getBlockState(pos).getBlock() instanceof ExtractorBlock;
    }

    @Override
    public Component getName(ItemStack stack) {
        return stack.has(PalaAlchemist.FLASK_SAP.get())
                ? Component.translatable("item.palamod.flask.filled") : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        FlaskSap sap = stack.get(PalaAlchemist.FLASK_SAP.get());
        int percent = sap == null ? 0 : (int) (sap.level() / 6.0d * 100.0d);
        tooltip.accept(Component.translatable("item.palamod.flask.level", percent).withStyle(ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable("item.palamod.flask.sap",
                sap == null ? Component.translatable("item.palamod.flask.no_sap") : sap.type().displayName())
                .withStyle(ChatFormatting.YELLOW));
    }
}
