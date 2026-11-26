package fr.atraxterra.dohy.casino;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;

/**
 * La loterie du spawn (la loterie de NationsGlory, « /loto ») : des tickets a 5 000 $, autant qu'on
 * veut (cent par tirage au plus), un tirage chaque dimanche a 20 h (heure de {@link RealClock}).
 * La cagnotte part de 50 000 $ et prend 80 % de chaque ticket vendu ; un seul ticket gagne tout.
 *
 * <p>En solo, des joueurs fictifs achetent aussi : chaque heure, a peu pres une fois sur deux, un
 * candidat de plus prend un ou deux tickets. Le gagnant absent au tirage est paye a sa connexion.
 * Un tirage sans aucun ticket reporte la cagnotte.
 */
@EventBusSubscriber
public final class Lottery {

    public static final long PRICE = 5_000L;
    public static final long BASE_POT = 50_000L;
    public static final double SHARE = 0.80d;
    public static final int MAX_TICKETS = 100;
    static final DayOfWeek DAY = DayOfWeek.SUNDAY;
    static final int HOUR = 20;
    /** Une heure sur deux a peu pres, un candidat fictif achete. */
    static final double SALE_CHANCE = 0.45d;

    private static final String[] FIRST = {"Kev", "Lumi", "Zox", "Mira", "Pixel", "Grizz", "Flo", "Naru", "Tchou",
            "Sky", "Rayan", "Nox", "Lola", "Brik", "Vortex", "Juju", "Saph", "Kenzo"};
    private static final String[] LAST = {"", "_", "31", "77", "_pvp", "Craft", "Gamer", "_fr", "Du13", "x"};

    private Lottery() {
    }

    public static LotteryData data(MinecraftServer server) {
        return server.overworld().getData(DoHyCasino.LOTTERY.get());
    }

    static void save(MinecraftServer server, LotteryData data) {
        server.overworld().setData(DoHyCasino.LOTTERY.get(), data.touched());
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Le prochain tirage strictement apres cet instant : le dimanche suivant a 20 h. */
    public static ZonedDateTime nextDraw(ZonedDateTime after) {
        ZonedDateTime draw = after.with(TemporalAdjusters.nextOrSame(DAY)).truncatedTo(ChronoUnit.DAYS)
                .withHour(HOUR);
        return draw.isAfter(after) ? draw : draw.plusWeeks(1L);
    }

    static ZonedDateTime at(long seconds) {
        return Instant.ofEpochSecond(seconds).atZone(RealClock.zone());
    }

    static long share(int tickets) {
        return Math.round(PRICE * tickets * SHARE);
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[Loterie] " + text).withStyle(color));
    }

    /** « Kev_pvp » : un joueur fictif. */
    static String fictionalName(RandomSource random) {
        return FIRST[random.nextInt(FIRST.length)] + LAST[random.nextInt(LAST.length)];
    }

    // -- Le temps qui passe ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 13) {
            return;
        }
        LotteryData data = data(server);
        long now = now();
        boolean changed = false;
        if (data.draw == 0L) {
            data.draw = nextDraw(at(now)).toEpochSecond();
            data.pot = BASE_POT;
            data.lastSale = now;
            save(server, data);
            return;
        }
        RandomSource random = server.overworld().getRandom();
        // Les joueurs fictifs, heure par heure ; deux jours de rattrapage au plus apres un arret.
        for (int hours = 0; hours < 48 && now - data.lastSale >= 3600L; hours++) {
            data.lastSale += 3600L;
            changed = true;
            if (data.lastSale < data.draw && random.nextDouble() < SALE_CHANCE) {
                int count = 1 + random.nextInt(2);
                data.fictional += count;
                data.candidates++;
                data.pot += share(count);
            }
        }
        if (now - data.lastSale >= 3600L) {
            data.lastSale = now;
        }
        if (now >= data.draw) {
            draw(server, data, random);
            changed = true;
        }
        if (changed) {
            save(server, data);
        }
    }

    /** Le tirage : un ticket au hasard parmi tous, joueurs fictifs compris. */
    static void draw(MinecraftServer server, LotteryData data, RandomSource random) {
        // Un tirage avance par un operateur garde le dimanche suivant ; un tirage manque (serveur
        // arrete) part du dimanche qui suit son rattrapage.
        long time = Math.min(data.draw, now());
        long pot = data.pot;
        int total = data.sold();
        data.draw = nextDraw(at(now())).toEpochSecond();
        if (total == 0) {
            data.history.add(0, new LotteryData.Draw(time, "", pot, 0, false));
            trim(data);
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Loterie] Aucun ticket vendu : la "
                    + "cagnotte de " + MoneyFormat.exact(pot) + " $ est reportee.").withStyle(ChatFormatting.GOLD),
                    false);
            return;
        }
        int pick = random.nextInt(total);
        UUID winner = null;
        for (Map.Entry<UUID, Integer> entry : data.tickets.entrySet()) {
            if (pick < entry.getValue()) {
                winner = entry.getKey();
                break;
            }
            pick -= entry.getValue();
        }
        String name;
        if (winner != null) {
            name = data.names.getOrDefault(winner, "?");
            ServerPlayer player = server.getPlayerList().getPlayer(winner);
            if (player != null) {
                pay(player, pot);
            } else {
                data.owed.merge(winner, pot, Long::sum);
            }
        } else {
            name = fictionalName(random);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Loterie] Tirage de la loterie du spawn : "
                + name + " remporte " + MoneyFormat.exact(pot) + " $ (" + total + " tickets vendus).")
                .withStyle(ChatFormatting.GOLD), false);
        DoHy.LOGGER.info("Loterie : {} remporte {} $ ({} tickets)", name, pot, total);
        data.history.add(0, new LotteryData.Draw(time, name, pot, total, winner != null));
        trim(data);
        data.tickets.clear();
        data.names.clear();
        data.fictional = 0;
        data.candidates = 0;
        data.pot = BASE_POT;
    }

    private static void trim(LotteryData data) {
        while (data.history.size() > 5) {
            data.history.remove(data.history.size() - 1);
        }
    }

    static void pay(ServerPlayer player, long pot) {
        Economy.credit(player, pot);
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Gros lot !")
                .withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("+" + MoneyFormat.exact(pot)
                + " $ a la loterie du spawn").withStyle(ChatFormatting.YELLOW)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                1.0f, 0.8f);
        tell(player, "Ton ticket gagne : +" + MoneyFormat.exact(pot) + " $.", ChatFormatting.GREEN);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        LotteryData data = data(server);
        Long owed = data.owed.remove(player.getUUID());
        if (owed != null && owed > 0L) {
            save(server, data);
            pay(player, owed);
        }
    }

    // -- Acheter --------------------------------------------------------------------------------

    /** Achete des tickets. Rend le refus, ou {@code null}. */
    public static String buy(ServerPlayer player, int count) {
        MinecraftServer server = player.level().getServer();
        LotteryData data = data(server);
        if (data.draw == 0L) {
            return "La loterie ouvre dans un instant.";
        }
        int mine = data.tickets(player.getUUID());
        if (mine + count > MAX_TICKETS) {
            return "Au plus " + MAX_TICKETS + " tickets par tirage : tu en as deja " + mine + ".";
        }
        long cost = PRICE * count;
        if (!Economy.tryDebit(player, cost)) {
            return "Il te faut " + MoneyFormat.exact(cost) + " $.";
        }
        data.tickets.put(player.getUUID(), mine + count);
        data.names.put(player.getUUID(), player.getGameProfile().name());
        data.pot += share(count);
        save(server, data);
        int sold = data.sold();
        tell(player, "+" + count + " ticket" + (count > 1 ? "s" : "") + " : tu en as " + (mine + count) + " sur "
                + sold + ", cagnotte " + MoneyFormat.exact(data.pot) + " $.", ChatFormatting.GREEN);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.6f, 1.4f);
        return null;
    }

    // -- Commandes ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("loto")
                .executes(context -> {
                    LotteryMenu.INSTANCE.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("tirer")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            LotteryData data = data(server);
                            if (data.draw == 0L) {
                                data.draw = nextDraw(at(now())).toEpochSecond();
                                data.pot = BASE_POT;
                                data.lastSale = now();
                            }
                            draw(server, data, server.overworld().getRandom());
                            save(server, data);
                            return 1;
                        }));
    }
}
