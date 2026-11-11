package fr.atraxterra.dohy.palamod.box;

import fr.atraxterra.dohy.boost.Crates;
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
 * Une cle de caisse. Jusqu'ici un compteur attache au joueur ; maintenant un
 * objet, qui se trouve en butin, se range et se pose sur une box.
 *
 * <p>Clic droit dans le vide : ouvre la caisse de son palier a la roulette.
 * Clic droit sur une box posee : c'est la box qui la prend
 * ({@link BoxBlockEntity}), avec son animation.
 */
public class CrateKeyItem extends Item {

    private final String tier;

    public CrateKeyItem(String tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** Le palier de caisse que cette cle ouvre : {@code common}, {@code rare}, {@code legendary}. */
    public String tier() {
        return tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            String refusal = Crates.open(server, tier);
            if (refusal != null) {
                server.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.palamod.crate_key.use").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.palamod.crate_key.box").withStyle(ChatFormatting.DARK_GRAY));
    }
}
