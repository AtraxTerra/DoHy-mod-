package fr.atraxterra.dohy.palamod.storage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.fml.ModList;

/**
 * La cle en paladium : elle deverrouille un tiroir ferme par une cle
 * personnelle, et part a l'usage.
 *
 * <p>Portee de {@code ItemPaladiumKey} de PalaMod, qui visait les tiroirs de
 * Storage Drawers ; ici ceux de Vault Drawers, son portage en 26.2
 * ({@link VaultDrawersCompat}). Une par pile, comme l'original
 * ({@code setMaxStackSize(1)}), et le Chasseur la debloque au niveau 85
 * ({@code jobs.json}).
 *
 * <p>Le deverrouillage se fait dans {@link PaladiumKeyEvents}, avant que le
 * tiroir ne recoive le clic : un tiroir ferme refuse tout a qui n'en est pas le
 * proprietaire, la cle comprise. Il ne reste ici que le cas ou le joueur vise
 * autre chose qu'un tiroir.
 */
public class PaladiumKeyItem extends Item {
    static final String NOT_A_DRAWER = "Veuillez utiliser cet item sur un drawer verrouille.";

    public PaladiumKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !context.getLevel().isClientSide()) {
            player.sendSystemMessage(Component.literal(NOT_A_DRAWER).withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.paladium_key").withStyle(ChatFormatting.GRAY));
        if (!ModList.get().isLoaded(VaultDrawersCompat.MOD_ID)) {
            tooltip.accept(Component.translatable("tooltip.palamod.paladium_key.mod")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
