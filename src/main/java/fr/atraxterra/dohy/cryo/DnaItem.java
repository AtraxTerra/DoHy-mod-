package fr.atraxterra.dohy.cryo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Une fiole d'ADN : la bete dont elle vient et son grade, portes par deux
 * composants ({@link DoHyCryo#DNA_MOB}, {@link DoHyCryo#DNA_GRADE}). Le nom
 * de la bete est celui du jeu, traduit chez le joueur.
 */
public class DnaItem extends Item {

    public DnaItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<EntityType<?>> type = Dna.type(stack);
        if (type.isEmpty()) {
            return super.getName(stack);
        }
        return Component.translatable("item.dohy.dna.of", type.get().getDescription(),
                Dna.roman(Dna.grade(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Optional<EntityType<?>> type = Dna.type(stack);
        if (type.isEmpty()) {
            tooltip.accept(Component.translatable("item.dohy.dna.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int grade = Dna.grade(stack);
        tooltip.accept(Component.translatable("item.dohy.dna.grade", Dna.roman(grade), Dna.MAX_GRADE)
                .withColor(Dna.gradeColor(grade) & 0xFFFFFF));
        tooltip.accept(Component.translatable("item.dohy.dna.cycle", Dna.cycle(grade) / 20, Dna.rolls(grade))
                .withStyle(ChatFormatting.GRAY));
        if (grade < Dna.MAX_GRADE) {
            tooltip.accept(Component.translatable("item.dohy.dna.fusion", Dna.FUSION, Dna.roman(grade + 1))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
