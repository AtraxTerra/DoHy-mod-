package fr.atraxterra.dohy.ui.view;

import fr.atraxterra.dohy.DoHy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le transport des vues et des actions du kit d'interface.
 *
 * <p>Deux messages : {@link Show} (serveur vers client) porte une vue, codee
 * par le codec enregistre pour son identifiant ; {@link Action} (client vers
 * serveur) porte un verbe, un argument texte et un nombre. Le serveur n'accepte
 * une action que pour la vue qu'il a lui-meme ouverte a ce joueur.
 */
@EventBusSubscriber
public final class UiNet {

    private static final Map<Identifier, StreamCodec<RegistryFriendlyByteBuf, ? extends UiView>> CODECS =
            new HashMap<>();
    private static final Map<Identifier, ActionHandler> HANDLERS = new HashMap<>();
    private static final Map<UUID, Identifier> OPEN = new ConcurrentHashMap<>();

    /** Traite une action d'un joueur sur une vue. */
    @FunctionalInterface
    public interface ActionHandler {
        void handle(ServerPlayer player, Action action);
    }

    private UiNet() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, path);
    }

    /** A appeler une fois par vue, dans un initialiseur statique cote commun. */
    public static synchronized void register(Identifier id, StreamCodec<RegistryFriendlyByteBuf, ? extends UiView> codec,
                                             ActionHandler handler) {
        CODECS.put(id, codec);
        if (handler != null) {
            HANDLERS.put(id, handler);
        }
    }

    /** Ouvre ou met a jour la vue chez le joueur. */
    public static void show(ServerPlayer player, UiView view) {
        OPEN.put(player.getUUID(), view.viewId());
        PacketDistributor.sendToPlayer(player, new Show(view));
    }

    /** Le joueur regarde-t-il encore cette vue ? */
    public static boolean isOpen(ServerPlayer player, Identifier view) {
        return view.equals(OPEN.get(player.getUUID()));
    }

    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    /** Ferme l'ecran du kit chez le joueur : avant une teleportation, une invocation. */
    public static void close(ServerPlayer player) {
        if (OPEN.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, new Close());
        }
    }

    /** Ferme l'ecran du kit ouvert, s'il y en a un. */
    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(id("ui_close"));
        public static final StreamCodec<ByteBuf, Close> STREAM_CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }

    // -- Messages --------------------------------------------------------------

    public record Show(UiView view) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(id("ui_show"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(
                (buffer, show) -> {
                    Identifier viewId = show.view().viewId();
                    Identifier.STREAM_CODEC.encode(buffer, viewId);
                    ((StreamCodec) CODECS.get(viewId)).encode(buffer, show.view());
                },
                buffer -> {
                    Identifier viewId = Identifier.STREAM_CODEC.decode(buffer);
                    StreamCodec<RegistryFriendlyByteBuf, ? extends UiView> codec = CODECS.get(viewId);
                    if (codec == null) {
                        throw new IllegalStateException("vue inconnue : " + viewId);
                    }
                    return new Show(codec.decode(buffer));
                });

        @Override
        public Type<Show> type() {
            return TYPE;
        }
    }

    /**
     * Une action : {@code verb} (« buy », « open »...), un argument texte (un
     * identifiant d'objet, un metier) et un nombre (une quantite).
     */
    public record Action(Identifier view, String verb, String argument, int amount) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("ui_action"));

        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Action::view,
                ByteBufCodecs.stringUtf8(64), Action::verb,
                ByteBufCodecs.stringUtf8(256), Action::argument,
                ByteBufCodecs.VAR_INT, Action::amount,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        register(ShopView.ID, ShopView.STREAM_CODEC, fr.atraxterra.dohy.shop.ShopController::handle);
        register(JobsView.ID, JobsView.STREAM_CODEC, fr.atraxterra.dohy.job.JobsController::handle);
        register(JobDetailView.ID, JobDetailView.STREAM_CODEC, fr.atraxterra.dohy.job.JobsController::handleDetail);
        register(CrateView.ID, CrateView.STREAM_CODEC, fr.atraxterra.dohy.ui.menu.BoutiqueController::handleCrate);
        register(fr.atraxterra.dohy.palamod.clicker.ClickerView.ID,
                fr.atraxterra.dohy.palamod.clicker.ClickerView.STREAM_CODEC,
                fr.atraxterra.dohy.palamod.clicker.Clicker::handle);
        register(fr.atraxterra.dohy.faction.FactionEditView.ID, fr.atraxterra.dohy.faction.FactionEditView.STREAM_CODEC,
                fr.atraxterra.dohy.faction.FactionEdit::handle);
        for (fr.atraxterra.dohy.ui.menu.MenuController menu : fr.atraxterra.dohy.ui.menu.Menus.all()) {
            register(menu.id(), MenuView.STREAM_CODEC, menu::handle);
        }
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Show.TYPE, Show.STREAM_CODEC);
        registrar.playToClient(Close.TYPE, Close.STREAM_CODEC);
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, (action, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if ("close".equals(action.verb())) {
                // Seulement si c'est bien la vue ouverte : un « close » en retard
                // ne doit pas fermer la vue suivante.
                OPEN.remove(player.getUUID(), action.view());
                return;
            }
            if (!action.view().equals(OPEN.get(player.getUUID()))) {
                return;
            }
            ActionHandler handler = HANDLERS.get(action.view());
            if (handler != null) {
                handler.handle(player, action);
            }
        });
    }
}
