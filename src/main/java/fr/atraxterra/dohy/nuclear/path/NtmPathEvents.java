package fr.atraxterra.dohy.nuclear.path;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.atraxterra.dohy.ui.Toasts;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le parcours NTM en jeu : le chargement du fichier, la commande
 * {@code /parcours}, le mot d'accueil, et le signal « etape prete » — des que
 * le sac contient ce qu'il faut (vu toutes les 5 secondes), ou des que la
 * machine de l'etape est posee.
 */
@EventBusSubscriber
public final class NtmPathEvents {

    /** Toutes les 5 secondes : un tour du sac, rien de plus. */
    private static final int CHECK_TICKS = 100;

    /** L'etape deja annoncee comme prete, par joueur : on ne la redit pas a chaque tour. */
    private static final Map<UUID, String> ANNOUNCED = new ConcurrentHashMap<>();

    private NtmPathEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        NtmPath.load(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("parcours")
                .executes(context -> {
                    NtmPathMenu.openFor(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("raz")
                                .then(Commands.argument("joueur", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                                            NtmPath.reset(target);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "Parcours NTM remis a zero pour "
                                                            + target.getName().getString() + "."), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("valider")
                                .then(Commands.argument("joueur", EntityArgument.player())
                                        .then(Commands.argument("etape", StringArgumentType.word())
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                                                    String id = StringArgumentType.getString(context, "etape");
                                                    if (NtmPath.find(id).isEmpty()) {
                                                        context.getSource().sendFailure(Component.literal(
                                                                "Etape inconnue : " + id));
                                                        return 0;
                                                    }
                                                    NtmPath.data(target).complete(id, 0L);
                                                    forget(target);
                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                            "Etape " + id + " validee pour "
                                                                    + target.getName().getString()
                                                                    + " (sans recompense)."), true);
                                                    return 1;
                                                }))))));
    }

    /** Le mot d'accueil, une fois par joueur, quand NTM est la et le parcours pas commence. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !NtmPath.installed()
                || NtmPath.steps().isEmpty()) {
            return;
        }
        NtmPathData data = NtmPath.data(player);
        if (data.greeted()) {
            return;
        }
        data.greet();
        if (!data.doneSteps().isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.literal("[NTM] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Le parcours NTM te guide de l'enclume au premier reacteur, avec une "
                        + "recompense a chaque etape. ").withStyle(ChatFormatting.WHITE))
                .append(NtmPath.link("[Ouvrir]")));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ANNOUNCED.remove(event.getEntity().getUUID());
        NtmPathMenu.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0
                || NtmPath.steps().isEmpty() || !NtmPath.installed()) {
            return;
        }
        NtmPath.Located current = NtmPath.current(player);
        // Une machine a poser s'annonce a la pose (onPlace) : pas de balayage des blocs ici.
        if (current == null || current.step().goal().near()) {
            return;
        }
        if (NtmPath.status(player, current.step()).met()) {
            announce(player, current);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || NtmPath.steps().isEmpty()
                || !NtmPath.installed() || !NtmPath.placesCurrent(player, event.getPlacedBlock().getBlock())) {
            return;
        }
        NtmPath.Located current = NtmPath.current(player);
        if (current != null) {
            announce(player, current);
        }
    }

    private static void announce(ServerPlayer player, NtmPath.Located current) {
        String id = current.step().id();
        if (id.equals(ANNOUNCED.get(player.getUUID()))) {
            return;
        }
        ANNOUNCED.put(player.getUUID(), id);
        player.sendSystemMessage(Component.literal("[NTM] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Etape " + current.number() + " prete : " + current.step().title() + ". ")
                        .withStyle(ChatFormatting.WHITE))
                .append(NtmPath.link("[Valider]")));
        Toasts.send(player, current.step().iconStack(), "ETAPE PRETE", current.step().title(), "/parcours pour valider",
                current.chapter().accent(), "");
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.5f, 1.2f);
    }

    /** Oublie l'annonce : l'etape a change (validee, remise a zero). */
    static void forget(ServerPlayer player) {
        ANNOUNCED.remove(player.getUUID());
    }
}
