package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.miner.PalaMiner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Le casque de speleologue ({@code CaveHelmetListener}) : « C'est cheat un peu ».
 *
 * <p>Tant qu'il le porte, le joueur voit les neuf blocs sous ses pieds changes en cave
 * blocks — le verre troue qui laisse voir les grottes ({@link PalaMiner#CAVE_BLOCK}).
 * Un bloc revele le reste deux secondes apres le dernier passage, puis redevient
 * lui-meme : en marchant, on laisse derriere soi une courte trainee de vision.
 * Le casque s'use d'un point par seconde, et disparait apres ses 300 points : cinq
 * minutes de vision.
 *
 * <p>Releve dans le code : la case visee est {@code floor(posY) - 2}, ou posY est la
 * hauteur des yeux du joueur cote client (1,62 au-dessus des pieds) — soit le bloc qui
 * le porte ; les blocs pleins seulement, jamais un conteneur ni un cave block.
 *
 * <p><b>Ecarts</b> : PalaMod changeait les blocs dans le monde du client ; ici le serveur
 * envoie au seul porteur un faux bloc, puis le vrai (celui du moment : rien ne reste
 * fige si le bloc a change entre-temps). Seuls les blocs a la collision pleine sont
 * reveles : un cave block a la place d'une dalle ou d'un escalier ferait buter le
 * joueur sur un bloc que le serveur ne voit pas.
 */
@EventBusSubscriber
public final class CaveHelmetVision {

    /** {@code DAMAGE_INTERVAL = 1000} ms : un point d'usure par seconde. */
    private static final int WEAR_TICKS = 20;
    /** {@code RESTORE_DELAY = 2000} ms apres le dernier passage. */
    private static final int RESTORE_TICKS = 40;
    /** La hauteur des yeux du joueur au-dessus de ses pieds, que PalaMod lisait dans posY cote client. */
    private static final double EYES = 1.62d;

    private static final Map<UUID, Vision> VISIONS = new HashMap<>();

    private CaveHelmetVision() {
    }

    /** Ce qu'un porteur voit : les blocs reveles et le tic de leur dernier passage. */
    private static final class Vision {
        private final ResourceKey<Level> dimension;
        private final Map<BlockPos, Long> shown = new HashMap<>();
        private long worn;

        private Vision(ResourceKey<Level> dimension, long now) {
            this.dimension = dimension;
            this.worn = now;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        Vision vision = VISIONS.get(player.getUUID());
        if (!helmet.is(LuckyItems.CAVE_HELMET.get())) {
            if (vision != null) {
                restoreAll(player, level, vision);
            }
            return;
        }
        long now = level.getGameTime();
        if (vision == null || vision.dimension != level.dimension()) {
            // Changer de dimension recharge les chunks : les faux blocs sont deja partis.
            vision = new Vision(level.dimension(), now);
            VISIONS.put(player.getUUID(), vision);
        }
        if (now - vision.worn >= WEAR_TICKS) {
            vision.worn = now;
            if (helmet.getDamageValue() + 1 >= helmet.getMaxDamage()) {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(),
                        SoundSource.PLAYERS, 0.8f, 1.0f);
                restoreAll(player, level, vision);
                return;
            }
            helmet.setDamageValue(helmet.getDamageValue() + 1);
        }
        reveal(player, level, vision, now);
    }

    private static void reveal(ServerPlayer player, ServerLevel level, Vision vision, long now) {
        int x = Mth.floor(player.getX());
        int y = Mth.floor(player.getY() + EYES) - 2;
        int z = Mth.floor(player.getZ());
        BlockState cave = PalaMiner.CAVE_BLOCK.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                if (vision.shown.containsKey(pos)) {
                    vision.shown.put(pos, now);
                } else if (shows(level, pos)) {
                    vision.shown.put(pos, now);
                    player.connection.send(new ClientboundBlockUpdatePacket(pos, cave));
                }
            }
        }
        Iterator<Map.Entry<BlockPos, Long>> entries = vision.shown.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<BlockPos, Long> entry = entries.next();
            if (now - entry.getValue() >= RESTORE_TICKS) {
                player.connection.send(new ClientboundBlockUpdatePacket(level, entry.getKey()));
                entries.remove();
            }
        }
    }

    /** Un bloc plein, qui n'est ni un conteneur ni deja un cave block ({@code isContainerBlock}). */
    private static boolean shows(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.hasBlockEntity() && !state.is(PalaMiner.CAVE_BLOCK.get())
                && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Le casque retire ({@code restoreAll}) : tous les vrais blocs reviennent d'un coup. */
    private static void restoreAll(ServerPlayer player, ServerLevel level, Vision vision) {
        if (vision.dimension == level.dimension()) {
            for (BlockPos pos : vision.shown.keySet()) {
                player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
            }
        }
        VISIONS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VISIONS.remove(event.getEntity().getUUID());
    }
}
