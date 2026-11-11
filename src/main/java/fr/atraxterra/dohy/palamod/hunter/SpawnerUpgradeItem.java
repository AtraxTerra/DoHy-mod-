package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

/** Une amelioration de controleur de spawners ({@code AItemSpawnerUpgrade}). */
public class SpawnerUpgradeItem extends Item {

    private final SpawnerUpgrade upgrade;

    public SpawnerUpgradeItem(SpawnerUpgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    public SpawnerUpgrade upgrade() {
        return upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.palamod.spawner_upgrade."
                + upgrade.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.palamod.spawner_upgrade.where")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
