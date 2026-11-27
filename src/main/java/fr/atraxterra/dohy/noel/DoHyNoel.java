package fr.atraxterra.dohy.noel;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Noel : le calendrier de l'Avent ({@link AdventCalendar}, {@link AdventMenu}), la commande
 * {@code /calendrier}, et un mot a la connexion quand la case du jour attend. Le decor du
 * parvis est au spawn ({@link fr.atraxterra.dohy.spawn.SpawnChristmas}).
 */
@EventBusSubscriber
public final class DoHyNoel {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** Les cases ouvertes cette saison : gardees a la mort. */
    public static final Supplier<AttachmentType<AdventCalendar.Opened>> OPENED = ATTACHMENTS.register(
            "advent", () -> AttachmentType.builder(() -> AdventCalendar.Opened.NONE)
                    .serialize(AdventCalendar.Opened.CODEC.fieldOf("opened"), opened -> !opened.isEmpty())
                    .copyOnDeath()
                    .build());

    private DoHyNoel() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        AdventCalendar.load(event.getServer());
    }

    /** Du 1er au 24 decembre, la case du jour pas encore ouverte : un mot a la connexion. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int door = AdventCalendar.waitingToday(player);
        if (door > 0) {
            player.sendSystemMessage(Component.literal("Calendrier de l'Avent : la case du " + AdventCalendar.label(door)
                    + " t'attend au spawn, aupres du Pere Noel du parvis (/calendrier).")
                    .withStyle(ChatFormatting.GOLD));
        } else if (AdventCalendar.dayIndex() >= AdventCalendar.BONUS
                && AdventCalendar.state(player, AdventCalendar.BONUS) == AdventCalendar.State.READY) {
            player.sendSystemMessage(Component.literal("Joyeux Noel ! Tes vingt-quatre cases sont ouvertes : le grand "
                    + "cadeau t'attend au spawn.").withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * {@code /calendrier} montre le calendrier de partout (les cases ne s'ouvrent qu'au
     * spawn). Pour les operateurs, {@code /calendrier reset <joueurs>} referme les cases.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calendrier")
                .executes(context -> {
                    AdventMenu.INSTANCE.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("joueurs", EntityArgument.players())
                                .executes(context -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "joueurs");
                                    for (ServerPlayer target : targets) {
                                        AdventCalendar.reset(target);
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "Calendrier de l'Avent referme pour " + targets.size() + " joueur(s)."),
                                            true);
                                    return targets.size();
                                }))));
    }
}
