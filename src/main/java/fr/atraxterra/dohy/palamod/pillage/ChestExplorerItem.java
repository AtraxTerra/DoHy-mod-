package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * L'explorateur de coffre ({@code ItemChestExplorer}) : accroupi, clic droit
 * sur un conteneur, et son contenu s'affiche — en lecture seule, sans
 * l'ouvrir. Un coffre piege ne se declenche pas, un coffre de structure
 * encore scelle tire son butin comme a l'ouverture.
 *
 * <p>Sans limite d'usage, comme dans PalaMod ; l'arbre de faction en
 * debloque l'usage (Pillage, palier 4).
 */
public class ChestExplorerItem extends Item {

    public ChestExplorerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        BlockEntity entity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(entity instanceof Container container)) {
            return InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!DefenseGate.check(player, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        if (container instanceof RandomizableContainer sealed) {
            sealed.unpackLootTable(player);
        }
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < Math.min(container.getContainerSize(), ChestExplorerMenu.MAX_SLOTS); slot++) {
            contents.add(container.getItem(slot).copy());
        }
        ChestExplorerMenu.INSTANCE.show(player, entity.getBlockState().getBlock().getName(), contents);
        player.level().playSound(null, context.getClickedPos(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS,
                1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
