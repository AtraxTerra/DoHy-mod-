package fr.atraxterra.dohy.palamod.wither;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * La selle de wither ({@code wither_saddle}) : clic droit sur un wither pour
 * s'y asseoir. Sans la canne a peche de wither en main, on ne le dirige pas.
 * Elle ne sert qu'une fois : descendu, ou trempe dans l'eau, il ne se remonte
 * plus. Ni l'assoiffe, ni le predateur, ni le supreme ne se montent.
 */
public class WitherSaddleItem extends Item {

    public WitherSaddleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof PalaWither wither)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!wither.saddle(server)) {
            FactionApi.tell(server, wither.kind().saddleable() ? "Ce wither ne se monte plus."
                    : "Ce wither ne se monte pas.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
