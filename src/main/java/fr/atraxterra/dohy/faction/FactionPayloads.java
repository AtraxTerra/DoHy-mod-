package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.ui.view.UiNet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** La touche de faction : la page de la faction, ou l'ecran de creation. */
@EventBusSubscriber
public final class FactionPayloads {

    public record Open() implements CustomPacketPayload {
        public static final Open INSTANCE = new Open();
        public static final Type<Open> TYPE = new Type<>(UiNet.id("faction_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    private FactionPayloads() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                FactionMenus.Overview.INSTANCE.open(player);
            }
        });
    }
}
