package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Une graine minerale ({@code ItemBotanistMineralSeed}) : elle pose son plant sur
 * de la terre labouree et lui passe ses caracteres ({@link SeedTraits}), que le
 * plant recopie depuis les composants de l'objet.
 *
 * <p>L'info-bulle reprend celle de JobsReborn ({@code BotanistMineralSeedTooltipNode}) :
 * la vitesse, la pousse hors ligne, le temps de pousse et ou elle se plante. Les
 * tirages par recolte, qui dependent du niveau, sont ecrits sur le plant (fiche de
 * l'encyclopedie et {@code PALAMOD.md}).
 */
public class MineralSeedItem extends BlockItem {

    private final MineralSeed seed;

    public MineralSeedItem(Block crop, Properties properties, MineralSeed seed) {
        super(crop, properties);
        this.seed = seed;
    }

    public MineralSeed seed() {
        return seed;
    }

    /**
     * Une pile de graines portant ces caracteres. Les caracteres par defaut ne sont
     * pas ecrits : une graine moyenne recoltee s'empile avec celles des recompenses.
     */
    public static ItemStack create(ItemLike item, SeedTraits traits, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (!SeedTraits.DEFAULT.equals(traits)) {
            stack.set(PalaBotanist.TRAITS.get(), traits);
        }
        return stack;
    }

    public static SeedTraits traits(ItemStack stack) {
        return stack.getOrDefault(PalaBotanist.TRAITS.get(), SeedTraits.DEFAULT);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        SeedTraits traits = traits(stack);
        tooltip.accept(Component.translatable("tooltip.palamod.mineral_seed.speed",
                Component.translatable(traits.speed().translationKey()).withStyle(traits.speed().color()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mineral_seed.offline",
                Component.translatable(traits.offline() ? "tooltip.palamod.mineral_seed.yes"
                        : "tooltip.palamod.mineral_seed.no")
                        .withStyle(traits.offline() ? ChatFormatting.GREEN : ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mineral_seed.growth",
                Component.literal(duration(seed.growthTicks(traits.speed()))).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mineral_seed.soil")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** {@code 45 min}, {@code 1 h}, {@code 1 h 30} : la duree d'une pousse, en temps de jeu charge. */
    static String duration(long ticks) {
        long minutes = Math.max(1L, Math.round(ticks / 1200.0d));
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + (rest < 10 ? "0" + rest : String.valueOf(rest));
    }
}
