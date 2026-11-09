package fr.atraxterra.dohy.palamod.dungeon;

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

/** Le bouton « Profil RPG » de l'inventaire (wiki) : il demande au serveur d'ouvrir le profil. */
@EventBusSubscriber
public final class RpgPayloads {

    public record OpenProfile() implements CustomPacketPayload {
        public static final OpenProfile INSTANCE = new OpenProfile();
        public static final Type<OpenProfile> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "rpg_profile"));
        public static final StreamCodec<ByteBuf, OpenProfile> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<OpenProfile> type() {
            return TYPE;
        }
    }

    private RpgPayloads() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenProfile.TYPE, OpenProfile.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                RpgMenu.open(player);
            }
        });
    }
}
