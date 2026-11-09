package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * La cle d'un donjon (« Cle de Talikus » et suivantes).
 *
 * <p>Sur Paladium, on entrait par un hub dedie. En solo, la cle fait le hub :
 * clic droit pour choisir le palier, et le groupe est fait des joueurs
 * proches. Seul le chef a besoin d'une cle, consommee a l'entree.
 */
public class DungeonKeyItem extends Item {

    private final Dungeon dungeon;

    public DungeonKeyItem(Dungeon dungeon, Properties properties) {
        super(properties);
        this.dungeon = dungeon;
    }

    public Dungeon dungeon() {
        return dungeon;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (DungeonManager.inDimension(serverPlayer) || DungeonManager.runOf(serverPlayer) != null) {
                DungeonManager.message(serverPlayer, ChatFormatting.RED, "already");
                return InteractionResult.FAIL;
            }
            DungeonMenus.openPaliers(serverPlayer, dungeon, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.dungeon_key", dungeon.job().display())
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.dungeon_key.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
