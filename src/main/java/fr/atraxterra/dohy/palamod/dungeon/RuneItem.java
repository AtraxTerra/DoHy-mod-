package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
 * Une rune de donjon (wiki : « Les runes »).
 *
 * <ul>
 *   <li>tiree des coffres ou fabriquee a la palamachine, avec une ou deux
 *       statistiques au hasard ; une rune sans statistiques (donnee par
 *       commande) se revele d'un clic droit ;</li>
 *   <li>amelioree a l'enclume en sacrifiant une rune de meme rarete et de
 *       meme tier (10, 15, 20 niveaux) ;</li>
 *   <li>fusionnee au grinder dans une piece d'armure de donjon, une par
 *       piece, pour toujours (casque 5 paladium, plastron 8, jambieres 7,
 *       bottes 4).</li>
 * </ul>
 */
public class RuneItem extends Item {

    private final RuneRarity rarity;

    public RuneItem(RuneRarity rarity, Properties properties) {
        super(properties);
        this.rarity = rarity;
    }

    public RuneRarity rarity() {
        return rarity;
    }

    /** Les statistiques de la rune, tirees maintenant si elle n'en a pas. */
    public static RuneData ensure(ItemStack stack, RandomSource random) {
        RuneData data = stack.get(PalaDungeon.RUNE.get());
        if (data == null && stack.getItem() instanceof RuneItem rune) {
            data = RuneData.roll(rune.rarity, random);
            stack.set(PalaDungeon.RUNE.get(), data);
        }
        return data;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.has(PalaDungeon.RUNE.get())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ensure(stack, level.getRandom());
            serverPlayer.sendSystemMessage(Component.translatable("message.palamod.rune.revealed")
                    .withStyle(rarity.color()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        RuneData data = stack.get(PalaDungeon.RUNE.get());
        if (data == null) {
            tooltip.accept(Component.translatable("tooltip.palamod.rune.unrevealed").withStyle(ChatFormatting.GRAY));
            return;
        }
        describe(data, tooltip);
        tooltip.accept(Component.translatable("tooltip.palamod.rune.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Les lignes d'une rune, pour une rune ou une piece qui la porte. */
    static void describe(RuneData data, Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.palamod.rune.tier",
                Component.translatable("rarity.palamod." + data.rarity().getSerializedName()), data.tier())
                .withStyle(data.rarity().color()));
        for (RuneData.Line line : data.lines()) {
            tooltip.accept(Component.literal(" +" + RpgStat.percent(data.value(line)) + " ")
                    .append(Component.translatable("rpg.palamod." + line.stat().key()))
                    .withStyle(line.stat().color()));
        }
    }
}
