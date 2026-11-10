package fr.atraxterra.dohy.palamod.trixium;

import fr.atraxterra.dohy.faction.event.FactionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * La fabrique a nuage ({@code ItemCloud}), objet exclusif du concours Trixium.
 *
 * <ul>
 *   <li>Elle pose un nuage qui disparait au bout de la duree choisie ; elle
 *       n'est <b>jamais consommee</b> ({@code onItemUse} pose le bloc sans
 *       toucher a la pile).</li>
 *   <li><b>Maj + clic droit</b> choisit la duree : 15 s, 30 s, 1 min, 5 min
 *       ({@code onItemRightClick}, et le wiki « Objets exclusifs »).</li>
 *   <li>Desactivee quand on porte l'oeuf de l'Egg Hunt, comme l'original.</li>
 * </ul>
 */
public class CloudItem extends BlockItem {

    /** Les durees proposees, en secondes, dans l'ordre du cycle. */
    public static final int[] DURATIONS = {15, 30, 60, 300};

    public CloudItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** La duree choisie, en secondes ; 15 par defaut ({@code getDuration}). */
    public static int duration(ItemStack stack) {
        return stack.getOrDefault(PalaTrixium.CLOUD_DURATION.get(), DURATIONS[0]);
    }

    /** « 15 s », « 1 min ». */
    public static String label(int seconds) {
        return seconds < 60 ? seconds + " s" : seconds / 60 + " min";
    }

    private static void cycle(ItemStack stack, Player player) {
        int current = duration(stack);
        int next = DURATIONS[0];
        for (int i = 0; i < DURATIONS.length; i++) {
            if (DURATIONS[i] == current) {
                next = DURATIONS[(i + 1) % DURATIONS.length];
                break;
            }
        }
        stack.set(PalaTrixium.CLOUD_DURATION.get(), next);
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal("Duree du nuage : " + label(next)).withStyle(ChatFormatting.AQUA)));
            server.level().playSound(null, server.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS,
                    0.6f, 1.4f);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                cycle(player.getItemInHand(hand), player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide()) {
                cycle(stack, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server && FactionEvents.carriesEgg(server)) {
            server.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal("La fabrique a nuage est desactivee quand tu portes l'oeuf.")
                            .withStyle(ChatFormatting.RED)));
            return InteractionResult.FAIL;
        }
        int count = stack.getCount();
        InteractionResult result = super.useOn(context);
        if (stack.getCount() < count) {
            stack.setCount(count);
        }
        return result;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.palamod.cloud.named", super.getName(stack), label(duration(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.cloud.duration", label(duration(stack)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.cloud.cycle").withStyle(ChatFormatting.DARK_GRAY));
    }
}
