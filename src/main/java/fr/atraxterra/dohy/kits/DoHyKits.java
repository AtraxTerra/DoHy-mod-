package fr.atraxterra.dohy.kits;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Les kits : ce qu'un joueur en a pris, la commande {@code /kit}, le kit de depart a la
 * premiere connexion ({@link PlayerKits}).
 */
@EventBusSubscriber
public final class DoHyKits {

    /** En dessous d'une minute de jeu, un joueur est un nouveau venu (comme pour l'arrivee au spawn). */
    private static final int NEWCOMER_TICKS = 1200;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** Les kits pris et quand : gardes a la mort, comme sur Paladium ou un kit ne revient pas en mourant. */
    public static final Supplier<AttachmentType<PlayerKits.Claims>> CLAIMS = ATTACHMENTS.register(
            "kits", () -> AttachmentType.builder(() -> PlayerKits.Claims.NONE)
                    .serialize(PlayerKits.Claims.CODEC.fieldOf("taken"), claims -> !claims.isEmpty())
                    .copyOnDeath()
                    .build());

    private static final SuggestionProvider<CommandSourceStack> KITS = (context, builder) -> {
        List<String> ids = new ArrayList<>();
        for (PlayerKits.Kit kit : PlayerKits.kits()) {
            ids.add(kit.id());
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private DoHyKits() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PlayerKits.load(event.getServer());
    }

    /**
     * La premiere connexion : le kit de depart arrive de lui-meme. Un joueur qui jouait deja
     * avant les kits n'est que prevenu : il le prend quand il veut.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean fresh = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) < NEWCOMER_TICKS;
        if (fresh) {
            PlayerKits.welcome(player);
            return;
        }
        long now = System.currentTimeMillis();
        for (PlayerKits.Kit kit : PlayerKits.kits()) {
            if (kit.firstJoin() && PlayerKits.ready(player, kit, now)) {
                PlayerKits.remind(player, kit);
            }
        }
    }

    /**
     * {@code /kit} ouvre la liste ; {@code /kit <kit>} le prend, comme sur Paladium. Pour les
     * operateurs, {@code /kit reset <joueurs>} remet a zero les kits pris.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kit")
                .executes(context -> {
                    PlayerKitsMenu.INSTANCE.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("joueurs", EntityArgument.players())
                                .executes(context -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "joueurs");
                                    for (ServerPlayer target : targets) {
                                        PlayerKits.reset(target);
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Kits remis a zero pour "
                                            + targets.size() + " joueur(s)."), true);
                                    return targets.size();
                                })))
                .then(Commands.argument("kit", StringArgumentType.word()).suggests(KITS)
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String refusal = PlayerKits.claim(player, StringArgumentType.getString(context, "kit"));
                            if (refusal != null) {
                                player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            return 1;
                        })));
    }
}
