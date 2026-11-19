package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.DoHy;
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
 * Ce que le client des lucky blocks dit au serveur : le serveur ne voit pas le joueur ouvrir son
 * propre inventaire, le client le lui signale (« Inventaire explosif »).
 */
@EventBusSubscriber
public final class LuckyPayloads {

    /** Le joueur vient d'ouvrir son inventaire. */
    public record InventoryOpened() implements CustomPacketPayload {
        public static final InventoryOpened INSTANCE = new InventoryOpened();
        public static final Type<InventoryOpened> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "lucky_inventory_opened"));
        public static final StreamCodec<ByteBuf, InventoryOpened> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<InventoryOpened> type() {
            return TYPE;
        }
    }

    private LuckyPayloads() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(InventoryOpened.TYPE, InventoryOpened.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                NovemberEvents.inventoryOpened(player);
            }
        });
    }
}
