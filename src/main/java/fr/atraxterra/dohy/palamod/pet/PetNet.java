package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.ui.view.UiNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * La touche de la roue : le client demande la roue, le serveur l'ouvre avec les
 * actives posees ({@link PetMenu#openWheel}). Les vues du familier passent par
 * {@link UiNet}.
 */
@EventBusSubscriber
public final class PetNet {

    /** La touche de la roue, enfoncee. */
    public record OpenWheel() implements CustomPacketPayload {
        public static final OpenWheel INSTANCE = new OpenWheel();
        public static final Type<OpenWheel> TYPE = new Type<>(UiNet.id("pet_wheel_open"));
        public static final StreamCodec<ByteBuf, OpenWheel> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<OpenWheel> type() {
            return TYPE;
        }
    }

    private PetNet() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenWheel.TYPE, OpenWheel.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PetMenu.openWheel(player);
            }
        });
    }
}
