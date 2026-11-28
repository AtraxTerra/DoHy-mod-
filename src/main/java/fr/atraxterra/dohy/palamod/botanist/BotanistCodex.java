package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Le codex des mutations d'un joueur ({@code IBotanistJobsModuleData.getCodex} de JobsReborn) :
 * les plantes qu'il a decouvertes. On ne decouvre qu'au scanner de Genis, en revelant une mutation
 * inconnue ou en scannant la graine d'une plante absente du codex ; avoir fait pousser une plante
 * ne suffit pas.
 *
 * <p>Le codex se garde sur le joueur (piece jointe {@code palamod:botanist_codex}, mort comprise)
 * et part chez son client a la connexion et a chaque changement ({@link Sync}) : l'ecran du scanner
 * et celui du codex le lisent sans aller-retour.
 */
@EventBusSubscriber
public final class BotanistCodex {

    private BotanistCodex() {
    }

    /** Les identifiants des plantes decouvertes, dans l'ordre de leur decouverte. */
    public static Set<String> discovered(ServerPlayer player) {
        return new LinkedHashSet<>(player.getData(PalaBotanist.CODEX.get()));
    }

    public static boolean has(ServerPlayer player, Mutation mutation) {
        return player.getData(PalaBotanist.CODEX.get()).contains(mutation.id());
    }

    /** Ajoute la plante au codex ; vrai si elle n'y etait pas. Le client en est averti. */
    public static boolean discover(ServerPlayer player, Mutation mutation) {
        List<String> codex = player.getData(PalaBotanist.CODEX.get());
        if (codex.contains(mutation.id())) {
            return false;
        }
        List<String> next = new ArrayList<>(codex);
        next.add(mutation.id());
        player.setData(PalaBotanist.CODEX.get(), List.copyOf(next));
        sync(player);
        return true;
    }

    /** Envoie le codex et le niveau de Fermier au client du joueur. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Sync(List.copyOf(player.getData(PalaBotanist.CODEX.get())),
                JobApi.level(player, Job.FARMER)));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC);
    }

    /** Le codex d'un joueur et son niveau de Fermier, pour son client. */
    public record Sync(List<String> discovered, int level) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Identifier.fromNamespaceAndPath("palamod", "botanist_codex"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(64)), Sync::discovered,
                ByteBufCodecs.VAR_INT, Sync::level,
                Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }
}
