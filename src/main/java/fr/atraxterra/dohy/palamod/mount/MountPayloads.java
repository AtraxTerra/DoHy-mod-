package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Les deux messages du client : la touche K ({@code SCOpenUiHome} etait la
 * reponse du serveur a {@code /mount opengui}) et le clic droit monte
 * ({@code CSMountMovement(3)}).
 *
 * <p>Le saut passe par le protocole vanilla des montures sauteuses, et
 * l'inventaire par la touche d'inventaire en selle : ni l'un ni l'autre ne
 * demande de message.
 */
@EventBusSubscriber
public final class MountPayloads {

    public record OpenMenu() implements CustomPacketPayload {
        public static final OpenMenu INSTANCE = new OpenMenu();
        public static final Type<OpenMenu> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "mount_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    public record HornStrike() implements CustomPacketPayload {
        public static final HornStrike INSTANCE = new HornStrike();
        public static final Type<HornStrike> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "horn_strike"));
        public static final StreamCodec<ByteBuf, HornStrike> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<HornStrike> type() {
            return TYPE;
        }
    }

    private MountPayloads() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenMenu.TYPE, OpenMenu.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                MountMenus.openHome(player);
            }
        });
        registrar.playToServer(HornStrike.TYPE, HornStrike.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HornMining.strike(player);
            }
        });
    }
}
