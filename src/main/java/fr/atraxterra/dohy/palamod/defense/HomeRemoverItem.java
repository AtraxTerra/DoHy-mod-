package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Le home remover.
 *
 * <p>Sur Paladium, il efface les homes d'une faction ennemie dans un chunk.
 * <b>En solo, il efface les repaires des monstres</b> : a l'usage, les monstres
 * du chunk ou tu te tiens disparaissent, et aucun n'y apparait naturellement
 * pendant une heure de jeu (72 000 ticks), claim ou pas. Un objet, un usage.
 */
public class HomeRemoverItem extends Item {

    public static final long WARD_TICKS = 72_000L;

    public HomeRemoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!DefenseGate.check(serverPlayer, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        LevelChunk chunk = server.getChunkAt(player.blockPosition());
        ChunkPos pos = chunk.getPos();
        AABB box = new AABB(pos.getMinBlockX(), server.getMinY(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, server.getMaxY() + 1, pos.getMaxBlockZ() + 1);
        int removed = 0;
        for (Mob mob : server.getEntitiesOfClass(Mob.class, box, mob -> mob instanceof Enemy && !mob.isPersistenceRequired())) {
            mob.discard();
            removed++;
        }
        chunk.setData(PalaDefense.SPAWN_WARD.get(), server.getGameTime() + WARD_TICKS);
        chunk.markUnsaved();
        server.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        serverPlayer.sendSystemMessage(Component.translatable("message.palamod.home_remover", removed)
                .withStyle(ChatFormatting.GREEN));
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
