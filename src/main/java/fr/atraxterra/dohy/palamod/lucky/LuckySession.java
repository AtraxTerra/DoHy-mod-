package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ouverture d'un lucky block, cote serveur ({@code PacketGetLuckyEvent} et
 * {@code PacketUseLuckyBlock}).
 *
 * <ol>
 *   <li>clic droit sur le bloc : l'ecran s'ouvre, rien n'est tire ;</li>
 *   <li>« Ouvrir » : le serveur tire l'evenement (ou reprend celui deja tire)
 *       et le garde dans le bloc ; les rouleaux tournent chez le client ;</li>
 *   <li>a la fermeture de l'ecran, que la roulette ait fini ou non, le bloc
 *       disparait dans un feu d'artifice et l'evenement se produit.</li>
 * </ol>
 *
 * <p>Fermer l'ecran avant « Ouvrir » ne consomme rien. Un seul joueur a la fois
 * par bloc (« Ce Lucky Block est deja ouvert »).
 */
@EventBusSubscriber
public final class LuckySession {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Le bloc que chaque joueur a a l'ecran. */
    private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();
    /** Les cases de la bande d'un rouleau, et celle ou il s'arrete. */
    private static final int REEL = 26;
    private static final int STOP = 22;
    /** Plus loin que ca du bloc, on ne l'ouvre pas. */
    private static final double REACH_SQUARED = 10.0d * 10.0d;

    private LuckySession() {
    }

    /** Le clic droit sur le bloc : l'ecran, sans rien tirer. */
    public static void open(ServerPlayer player, BlockPos pos) {
        for (Map.Entry<UUID, BlockPos> entry : OPEN.entrySet()) {
            if (!entry.getKey().equals(player.getUUID()) && entry.getValue().equals(pos)) {
                ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(entry.getKey());
                if (other != null && other.level() == player.level() && UiNet.isOpen(other, LuckyBlockView.ID)) {
                    LuckyWorld.tell(player, "Ce Lucky Block est deja ouvert.", ChatFormatting.RED);
                    return;
                }
            }
        }
        if (!(player.level().getBlockEntity(pos) instanceof LuckyBlockEntity block)) {
            return;
        }
        OPEN.put(player.getUUID(), pos.immutable());
        UiNet.show(player, view(player, block, 0, false));
    }

    /** Les actions de l'ecran : « open » tire, « use » ouvre pour de bon. */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        BlockPos pos = OPEN.get(player.getUUID());
        if (pos == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof LuckyBlockEntity block)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REACH_SQUARED) {
            OPEN.remove(player.getUUID());
            UiNet.close(player);
            return;
        }
        switch (action.verb()) {
            case "open" -> {
                if (LuckyEvents.get(block.event()) == null) {
                    LuckyEvent event = LuckyEvents.draw(block.type(), level.getRandom(), block.chained());
                    if (event == null) {
                        return;
                    }
                    block.setEvent(event.id());
                }
                boolean fresh = !PalaLucky.data(player).seen(block.event());
                UiNet.show(player, view(player, block, action.amount() + 1, fresh));
            }
            case "use" -> {
                if (LuckyEvents.get(block.event()) != null) {
                    OPEN.remove(player.getUUID());
                    perform(player, level, pos, block);
                }
            }
            default -> {
            }
        }
    }

    /** Le bloc s'ouvre : il disparait, et l'evenement se produit a sa place. */
    static void perform(ServerPlayer player, ServerLevel level, BlockPos pos, LuckyBlockEntity block) {
        LuckyEvent event = LuckyEvents.get(block.event());
        LuckyType type = block.type();
        level.removeBlock(pos, false);
        double x = pos.getX() + 0.5d;
        double y = pos.getY() + 0.5d;
        double z = pos.getZ() + 0.5d;
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 30, 0.1d, 0.1d, 0.1d, 0.1d);
        LuckyWorld.sound(level, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
        if (event == null) {
            return;
        }
        PalaLucky.data(player).record(event.id());
        fr.atraxterra.dohy.ui.Toasts.send(player, type.stack(1), "LUCKY BLOCK " + type.label(), event.name(),
                event.bad() ? "Un malus..." : "Un bonus !", event.bad() ? 0xFFEF3926 : 0xFF2BD15A, "lucky");
        try {
            event.action().perform(player, level, pos);
        } catch (RuntimeException failure) {
            LOGGER.error("[dohy/lucky] l'evenement {} a echoue", event.id(), failure);
            LuckyWorld.tell(player, "Oups :( une erreur est survenue...", ChatFormatting.RED);
        }
    }

    /** La vue de l'ecran ; {@code spin} a 0 tant que rien n'est tire. */
    static LuckyBlockView view(ServerPlayer player, LuckyBlockEntity block, int spin, boolean fresh) {
        LuckyType type = block.type();
        List<LuckyEvent> pool = LuckyEvents.pool(type);
        LuckyEvent event = spin > 0 ? LuckyEvents.get(block.event()) : null;
        List<String> reel = new ArrayList<>(REEL);
        RandomSource random = player.getRandom();
        for (int index = 0; index < REEL && !pool.isEmpty(); index++) {
            reel.add(pool.get(random.nextInt(pool.size())).icon());
        }
        LuckyData data = PalaLucky.data(player);
        return new LuckyBlockView(block.getBlockPos(), type.ordinal(), event != null,
                event == null ? "" : event.id(), event == null ? "" : event.icon(), event == null ? "" : event.name(),
                event != null && event.bad(), fresh, reel, STOP, spin, data.discovered(), LuckyEvents.count());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }
}
