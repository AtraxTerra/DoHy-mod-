package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.ui.menu.HubController;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Les boutons de l'inventaire : le client demande un menu, le serveur l'ouvre.
 *
 * <p>Le nom du menu vient du client, donc il est verifie contre une liste
 * fermee ({@link HubController#openTarget}) : un nom inconnu ne fait rien.
 */
@EventBusSubscriber
public final class InventoryNav {

    /** La demande d'ouverture d'un menu depuis l'inventaire. */
    public record Open(String target) implements CustomPacketPayload {
        public static final Type<Open> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "inventory_nav"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(32).map(Open::new, Open::target);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    private InventoryNav() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                HubController.openTarget(player, payload.target());
            }
        });
    }
}
