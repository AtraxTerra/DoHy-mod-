package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Un sac de donjon (« Dungeon Leather Backpack » et les deux sacs « obtenables
 * uniquement dans le donjon »).
 *
 * <p>Le wiki ne dit ni sa contenance ni s'il se porte : il suffit ici de
 * l'avoir sur soi en entrant. Sans sac, le butin tient en 9 piles ; le
 * meilleur sac de l'inventaire fixe la contenance pour tout le palier.
 */
public class DungeonBackpackItem extends Item {

    /** Sans sac. */
    public static final int BASE_CAPACITY = 9;

    private final int capacity;

    public DungeonBackpackItem(int capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    /** La contenance du meilleur sac de l'inventaire. */
    public static int capacityOf(ServerPlayer player) {
        int best = BASE_CAPACITY;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof DungeonBackpackItem backpack) {
                best = Math.max(best, backpack.capacity);
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.dungeon_backpack", capacity)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.dungeon_backpack.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
