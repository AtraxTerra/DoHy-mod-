package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * {@code ItemGuardianStone} : vide, elle eveille un golem neuf ; habitee, le
 * golem qu'elle garde. On la pose, clic droit, sur un bloc gardien coiffant un
 * T de blocs de paladium.
 */
public class GuardianStoneItem extends Item {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(RealClock.zone());

    public GuardianStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GolemData data = stack.get(PalaGolem.GOLEM_DATA.get());
        if (data == null) {
            tooltip.accept(Component.translatable("item.palamod.guardian_stone.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        describe(data, tooltip);
        data.death().ifPresent(death -> {
            tooltip.accept(Component.translatable("item.palamod.guardian_stone.death",
                    death.source().isEmpty() ? death.cause() : death.source(),
                    DATE.format(Instant.ofEpochSecond(death.date())),
                    death.pos().getX() + " / " + death.pos().getY() + " / " + death.pos().getZ())
                    .withStyle(ChatFormatting.DARK_RED));
        });
    }

    static void describe(GolemData data, Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("item.palamod.guardian_stone.level", data.level())
                .withStyle(ChatFormatting.GOLD));
        if (!data.ownerName().isEmpty()) {
            tooltip.accept(Component.translatable("item.palamod.guardian_stone.owner", data.ownerName())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("item.palamod.guardian_stone.points", data.points(),
                data.upgrades().size()).withStyle(ChatFormatting.GRAY));
    }
}
