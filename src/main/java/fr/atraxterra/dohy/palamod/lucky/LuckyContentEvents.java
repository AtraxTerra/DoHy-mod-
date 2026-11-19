package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que les evenements du troisieme lot attendent du monde : le coffre maudit qu'on
 * ouvre ({@code onTriforce}), la reponse a l'enigme ({@code Enigme.handleChat}) et le
 * voyant de la boule de cristal.
 */
@EventBusSubscriber
public final class LuckyContentEvents {

    /** {@code 50} eclairs sur le coffre maudit. */
    private static final int CURSE_BOLTS = 50;
    /** {@code Thread.sleep(20000)} : le coffre maudit disparait vingt secondes apres. */
    private static final int CURSE_REMOVAL = 20 * 20;
    /** {@code tries == 2} : trois essais pour l'enigme. */
    private static final int RIDDLE_TRIES = 3;
    private static final int RIDDLE_REWARD = 10;
    private static final Map<UUID, Integer> TRIES = new ConcurrentHashMap<>();

    private LuckyContentEvents() {
    }

    static void startRiddle(ServerPlayer player) {
        TRIES.put(player.getUUID(), 0);
    }

    /**
     * Le coffre maudit : qui l'ouvre meurt, sous cinquante eclairs, et le coffre s'en va
     * vingt secondes plus tard. <b>Ecart</b> : les eclairs sont pour l'oeil ; chez PalaMod
     * ils brulaient aussi les objets tombes du joueur.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity chest = level.getBlockEntity(pos);
        if (chest == null || !chest.hasData(PalaLucky.CURSED) || !chest.getData(PalaLucky.CURSED)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.hurtServer(level, level.damageSources().magic(), Float.MAX_VALUE);
        for (int bolt = 0; bolt < CURSE_BOLTS; bolt++) {
            LuckyWorld.lightning(level, player, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, true);
        }
        LuckyTasks.later(null, CURSE_REMOVAL, () -> {
            if (level.getBlockEntity(pos) == chest) {
                level.removeBlock(pos, false);
            }
        });
    }

    /** {@code Enigme.handleChat} : la reponse ne part pas dans le chat. */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Integer tries = TRIES.get(player.getUUID());
        if (tries == null) {
            return;
        }
        event.setCanceled(true);
        // « la pensee », « Penser »... : compare sans accents ni majuscules.
        String answer = Normalizer.normalize(event.getRawText(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        if (answer.contains("pensee") || answer.contains("penser")) {
            TRIES.remove(player.getUUID());
            player.sendSystemMessage(FindiumContent.fouras().copy().append(Component.literal(
                    "Felicitations mon enfant. Tu as la bonne reponse !").withStyle(ChatFormatting.GREEN)));
            player.sendSystemMessage(Component.literal("Pere Fouras ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("vous a recompense avec ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(RIDDLE_REWARD + " Paladium.").withStyle(ChatFormatting.RED)));
            ItemStack reward = new ItemStack(PalaItems.PALADIUM_INGOT.get(), RIDDLE_REWARD);
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }
            return;
        }
        if (tries + 1 >= RIDDLE_TRIES) {
            TRIES.remove(player.getUUID());
            player.sendSystemMessage(FindiumContent.fouras().copy().append(Component.literal("Tu as echoue !")
                    .withStyle(ChatFormatting.RED)));
            return;
        }
        TRIES.put(player.getUUID(), tries + 1);
        player.sendSystemMessage(FindiumContent.fouras().copy().append(Component.literal(
                "Mauvaise reponse! Il ne te reste plus que " + (RIDDLE_TRIES - tries - 1) + " essais.")
                .withStyle(ChatFormatting.RED)));
    }

    /** Le voyant : un clic ouvre le Lucky Pass (le lancer offert y attend), puis il disparait. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!target.hasData(PalaLucky.SEER) || !target.getData(PalaLucky.SEER)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        LuckyPass.open(player);
        level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1.0d, target.getZ(), 30, 0.4d, 0.6d,
                0.4d, 0.05d);
        target.discard();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRIES.remove(event.getEntity().getUUID());
    }
}
