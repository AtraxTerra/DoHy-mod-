package fr.atraxterra.dohy.casino;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.enterprise.Enterprises;
import fr.atraxterra.dohy.faction.enterprise.Law;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Le casino (« Entreprise de casino » de NationsGlory) : on paie un panneau [Casino] accroche a un
 * monnayeur, l'impulsion lance un circuit de redstone (machines aleatoires, des...), et si le circuit
 * allume la caisse des gains, son panneau [Casino2] paie le gagnant. Le ticket entre dans le coffre
 * du casino, le gain en sort.
 *
 * <p><b>Les clients.</b> En solo, personne ne vient jouer : des joueurs fictifs le font, selon la loi
 * sur le jeu du pays ou est le casino ({@link Law.Topic#JEU}). Chaque jeu ouvert en recoit environ
 * {@value #BASE_PER_HOUR} par heure, fois 1,5 si le jeu y est legal, 1 s'il est tolere, 0,4 s'il est
 * interdit ; moins si le jeu rend peu (un taux de redistribution de 80 % les attire tous, 40 % en
 * chasse neuf sur dix) ou si le ticket est cher (1 000 $ est la norme). Soixante parties par heure au
 * plus par casino.
 *
 * <p>Quand le monnayeur est charge, le client joue vraiment : il paie, l'impulsion part, et c'est le
 * circuit qui decide s'il gagne. Loin de tout joueur, la partie se joue sur les chiffres du jeu (il
 * en faut dix pour savoir ce qu'il rend). La loi touche aussi la recette : 20 % de taxes si le jeu est
 * legal, des pots-de-vin s'il est tolere ; interdit, la police peut saisir la moitie du coffre (6 %
 * de risque par heure).
 */
@EventBusSubscriber
public final class Casinos {

    static final double BASE_PER_HOUR = 12.0d;
    static final double MAX_PER_HOUR = 60.0d;
    /** Le temps qu'a le circuit pour allumer la caisse apres une mise. */
    static final int WINDOW = 100;
    /** Les parties qu'il faut pour connaitre ce que rend un jeu. */
    static final long KNOWN = 10L;
    static final double RAID_CHANCE = 0.06d;
    static final long LAW_REFRESH = 600L;

    /** Une mise qui attend son resultat : un joueur, un essai du maitre, ou un client fictif. */
    record Pending(UUID player, boolean test, long tick) {
    }

    private static final Map<String, Pending> PENDING = new HashMap<>();
    /** La prochaine partie d'un client fictif, par jeu (secondes). */
    private static final Map<String, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> LAW_READ = new HashMap<>();

    private Casinos() {
    }

    public static CasinoBook book(MinecraftServer server) {
        return server.overworld().getData(DoHyCasino.BOOK.get());
    }

    static void save(MinecraftServer server, CasinoBook book) {
        server.overworld().setData(DoHyCasino.BOOK.get(), book.touched());
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    static String pendingKey(UUID owner, String key) {
        return owner + "|" + key;
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[Casino] " + text).withStyle(color));
    }

    // -- La loi ---------------------------------------------------------------------------------

    static Law.Status law(CasinoBook.Game game) {
        Law.Status status = Law.Status.of(game.law);
        return status == null ? Law.Topic.JEU.common() : status;
    }

    static ServerLevel level(MinecraftServer server, CasinoBook.Game game) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(game.dimension)));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Le pays ou est le monnayeur, sur la carte de la Terre ; 0 ailleurs. */
    static int country(Level level, BlockPos pos) {
        EarthData earth = EarthData.get();
        if (earth == null || level == null || !earth.covers(level)) {
            return 0;
        }
        EarthData.Country country = earth.countryAt(pos.getX(), pos.getZ());
        return country == null ? 0 : country.id();
    }

    /** Relit la loi sur le jeu la ou est ce jeu : la tienne chez toi, celle de la nation qui tient le pays. */
    static void readLaw(ServerPlayer owner, CasinoBook.Game game) {
        int country = country(level(owner.level().getServer(), game), BlockPos.of(game.slot));
        Law.Status status = country > 0 ? Enterprises.lawAt(owner, country, Law.Topic.JEU) : Law.Topic.JEU.common();
        game.law = status.name();
    }

    /** Ce qui reste d'un ticket au casino : 20 % de taxes si le jeu est legal, des pots-de-vin s'il est tolere. */
    static long net(long price, Law.Status law) {
        return switch (law) {
            case LEGAL -> Math.round(price * Law.LEGAL_REVENUE);
            case TOLERE -> Math.round(price / law.cost());
            case INTERDIT -> price;
        };
    }

    /** Les clients par heure d'un jeu, avant le plafond du casino. */
    public static double rate(CasinoBook.Game game) {
        double byLaw = switch (law(game)) {
            case LEGAL -> 1.5d;
            case TOLERE -> 1.0d;
            case INTERDIT -> 0.4d;
        };
        double rendered = game.plays >= KNOWN ? game.redistribution() / 100.0d : 0.75d;
        double fairness = Math.max(0.1d, Math.min(1.0d, (rendered - 0.4d) / 0.4d));
        double byPrice = Math.max(0.25d, Math.min(1.5d, 1000.0d / Math.max(1L, game.price)));
        return BASE_PER_HOUR * byLaw * fairness * byPrice;
    }

    /** Le plafond du casino, reparti sur ses jeux. */
    static double scale(CasinoBook.Account account) {
        double total = 0.0d;
        for (CasinoBook.Game game : account.games.values()) {
            total += rate(game);
        }
        return total > MAX_PER_HOUR ? MAX_PER_HOUR / total : 1.0d;
    }

    // -- Les panneaux ---------------------------------------------------------------------------

    /** Inscrit (ou met a jour) le jeu d'un panneau [Casino]. */
    static CasinoBook.Game register(CasinoBook.Account account, String key, CasinoSigns.Lines lines, Level level,
                                    BlockPos slot, BlockPos sign) {
        CasinoBook.Game game = account.games.get(key);
        String dimension = level.dimension().identifier().toString();
        if (game == null) {
            game = new CasinoBook.Game(lines.casino(), lines.game(), dimension, slot.asLong(), sign.asLong(),
                    lines.amount(), 0L, 0L, 0L, 0L, 0L, Law.Topic.JEU.common().name());
            account.games.put(key, game);
        } else {
            if (game.price != lines.amount()) {
                // Un autre prix, un autre jeu : ce qu'il rendait ne vaut plus.
                game.plays = 0L;
                game.played = 0L;
                game.wins = 0L;
                game.paid = 0L;
                game.unpaid = 0L;
            }
            game.casino = lines.casino();
            game.game = lines.game();
            game.dimension = dimension;
            game.slot = slot.asLong();
            game.sign = sign.asLong();
            game.price = lines.amount();
        }
        return game;
    }

    /** Un clic sur un panneau [Casino]. Rend le refus, ou {@code null}. */
    static String play(ServerPlayer player, ServerLevel level, BlockPos sign, BlockPos slot, CasinoBlockEntity casino,
                       CasinoSigns.Lines lines) {
        if (casino == null || !(level.getBlockState(slot).getBlock() instanceof CoinSlotBlock)) {
            return "Un panneau [Casino] s'accroche a un monnayeur.";
        }
        if (casino.owner() == null) {
            return "Ce monnayeur n'a pas de maitre.";
        }
        if (lines.casino().isEmpty()) {
            return "Ecris le nom du casino sur la deuxieme ligne.";
        }
        if (lines.amount() < 0L) {
            return "Prix illisible sur la troisieme ligne : " + lines.rawAmount() + ".";
        }
        MinecraftServer server = level.getServer();
        CasinoBook book = book(server);
        String key = CasinoBook.key(lines.casino(), lines.game());
        String pending = pendingKey(casino.owner(), key);
        CasinoBook.Account account = book.account(casino.owner(), casino.ownerName());
        CasinoBook.Game game = register(account, key, lines, level, slot, sign);
        if (casino.ownedBy(player)) {
            readLaw(player, game);
            LAW_READ.put(player.getUUID(), now());
            save(server, book);
            PENDING.put(pending, new Pending(player.getUUID(), true, level.getGameTime()));
            if (!CoinSlotBlock.pulse(level, slot)) {
                return "Le jeu tourne deja : attends la fin de l'impulsion.";
            }
            tell(player, "Partie d'essai de " + game.title() + ", sans argent. Le jeu est ouvert aux clients : "
                    + String.format(java.util.Locale.ROOT, "%.1f", rate(game) * scale(account)).replace('.', ',')
                    + " par heure (jeu " + law(game).name().toLowerCase(java.util.Locale.ROOT) + " ici).",
                    ChatFormatting.GOLD);
            return null;
        }
        long price = lines.amount();
        if (Economy.balance(player) < price) {
            return "Il te faut " + MoneyFormat.exact(price) + " $ pour jouer.";
        }
        PENDING.put(pending, new Pending(player.getUUID(), false, level.getGameTime()));
        if (!CoinSlotBlock.pulse(level, slot)) {
            PENDING.remove(pending);
            return "Le jeu tourne deja : attends la fin de l'impulsion.";
        }
        Economy.debit(player, price);
        account.balance += net(price, law(game));
        game.plays++;
        game.played += price;
        save(server, book);
        level.playSound(null, slot, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, 1.6f);
        tell(player, "Mise de " + MoneyFormat.exact(price) + " $ sur " + game.title() + ". Bonne chance !",
                ChatFormatting.YELLOW);
        return null;
    }

    /** Un clic sur un panneau [Casino2] : ce qu'il paie. */
    static String describePayout(ServerPlayer player, ServerLevel level, BlockPos block, CasinoBlockEntity casino,
                                 CasinoSigns.Lines lines) {
        if (casino == null || !(level.getBlockState(block).getBlock() instanceof PayoutBoxBlock)) {
            return "Un panneau [Casino2] s'accroche a une caisse des gains.";
        }
        if (lines.amount() < 0L) {
            return "Gain illisible sur la troisieme ligne : " + lines.rawAmount() + ".";
        }
        String game = lines.game().isEmpty() ? lines.casino() : lines.casino() + " · " + lines.game();
        tell(player, "Caisse de " + game + " : " + MoneyFormat.exact(lines.amount())
                + " $ au gagnant, quand le jeu allume la caisse.", ChatFormatting.GOLD);
        return null;
    }

    /** La caisse s'allume : ses panneaux [Casino2] paient la derniere mise de leur jeu. */
    public static void payout(ServerLevel level, BlockPos box) {
        if (!(level.getBlockEntity(box) instanceof CasinoBlockEntity casino) || casino.owner() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        CasinoBook book = book(server);
        boolean changed = false;
        for (SignBlockEntity sign : CasinoSigns.signsOn(level, box)) {
            CasinoSigns.Lines lines = CasinoSigns.read(sign);
            if (lines == null || !lines.payout() || lines.amount() < 0L) {
                continue;
            }
            String key = CasinoBook.key(lines.casino(), lines.game());
            String pendingKey = pendingKey(casino.owner(), key);
            Pending pending = PENDING.get(pendingKey);
            if (pending == null || level.getGameTime() - pending.tick() > WINDOW) {
                continue;
            }
            PENDING.remove(pendingKey);
            long reward = lines.amount();
            ServerPlayer winner = pending.player() == null ? null
                    : server.getPlayerList().getPlayer(pending.player());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, box.getX() + 0.5d, box.getY() + 1.1d,
                    box.getZ() + 0.5d, 12, 0.3d, 0.2d, 0.3d, 0.0d);
            if (pending.test()) {
                if (winner != null) {
                    tell(winner, "Essai gagnant : ce jeu paierait " + MoneyFormat.exact(reward) + " $.",
                            ChatFormatting.GREEN);
                }
                continue;
            }
            CasinoBook.Account account = book.account(casino.owner(), casino.ownerName());
            CasinoBook.Game game = account.games.get(key);
            changed = true;
            if (account.balance < reward) {
                if (game != null) {
                    game.unpaid++;
                }
                if (winner != null) {
                    tell(winner, "Gagne, mais le coffre du casino est vide : " + MoneyFormat.exact(reward)
                            + " $ non payes.", ChatFormatting.RED);
                }
                ServerPlayer master = server.getPlayerList().getPlayer(casino.owner());
                if (master != null) {
                    tell(master, "Ton casino n'a pas pu payer " + MoneyFormat.exact(reward)
                            + " $ : remplis son coffre (/casino).", ChatFormatting.RED);
                }
                continue;
            }
            account.balance -= reward;
            if (game != null) {
                game.wins++;
                game.paid += reward;
            }
            if (winner != null) {
                Economy.credit(winner, reward);
                tell(winner, "Gagne ! +" + MoneyFormat.exact(reward) + " $.", ChatFormatting.GREEN);
                level.playSound(null, box, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7f, 1.3f);
            }
        }
        if (changed) {
            save(server, book);
        }
    }

    // -- Les clients ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 11) {
            return;
        }
        CasinoBook book = book(server);
        if (book.isEmpty()) {
            return;
        }
        long now = now();
        RandomSource random = server.overworld().getRandom();
        boolean changed = false;
        for (Map.Entry<UUID, CasinoBook.Account> entry : book.accounts.entrySet()) {
            UUID owner = entry.getKey();
            CasinoBook.Account account = entry.getValue();
            ServerPlayer master = server.getPlayerList().getPlayer(owner);
            if (master != null && now - LAW_READ.getOrDefault(owner, 0L) >= LAW_REFRESH) {
                LAW_READ.put(owner, now);
                for (CasinoBook.Game game : account.games.values()) {
                    readLaw(master, game);
                }
                changed = true;
            }
            double scale = scale(account);
            Iterator<Map.Entry<String, CasinoBook.Game>> games = account.games.entrySet().iterator();
            while (games.hasNext()) {
                Map.Entry<String, CasinoBook.Game> each = games.next();
                CasinoBook.Game game = each.getValue();
                String key = pendingKey(owner, each.getKey());
                double rate = rate(game) * scale;
                if (rate <= 0.0d) {
                    continue;
                }
                Long next = NEXT.get(key);
                if (next == null) {
                    NEXT.put(key, now + interval(rate, random) / 2L);
                    continue;
                }
                if (now < next) {
                    continue;
                }
                NEXT.put(key, now + interval(rate, random));
                String outcome = client(server, account, game, key, random);
                if ("gone".equals(outcome)) {
                    games.remove();
                    NEXT.remove(key);
                    if (master != null) {
                        tell(master, "Le jeu " + game.title() + " a disparu (monnayeur ou panneau casse) : il est "
                                + "retire de ton casino.", ChatFormatting.YELLOW);
                    }
                }
                changed |= outcome != null;
            }
            if (now - account.lastRaidCheck >= 3600L) {
                account.lastRaidCheck = now;
                changed = true;
                boolean illegal = account.games.values().stream().anyMatch(game -> law(game) == Law.Status.INTERDIT);
                if (illegal && account.balance > 0L && random.nextDouble() < RAID_CHANCE) {
                    long seized = account.balance / 2L;
                    account.balance -= seized;
                    account.seized += seized;
                    if (master != null) {
                        tell(master, "Descente de police dans ton casino, ou le jeu est interdit : "
                                + MoneyFormat.exact(seized) + " $ saisis.", ChatFormatting.RED);
                    }
                }
            }
        }
        if (changed) {
            save(server, book);
        }
    }

    /** L'attente avant le client suivant, en secondes : autour de 3 600 / rate. */
    static long interval(double rate, RandomSource random) {
        return Math.max(20L, Math.round(3600.0d / rate * (0.5d + random.nextDouble())));
    }

    /**
     * Un client fictif joue. Rend « gone » si le jeu n'existe plus, {@code null} si rien n'a change,
     * sinon « play ».
     */
    static String client(MinecraftServer server, CasinoBook.Account account, CasinoBook.Game game, String key,
                         RandomSource random) {
        ServerLevel level = level(server, game);
        BlockPos slot = BlockPos.of(game.slot);
        if (level != null && level.isLoaded(slot)) {
            BlockPos sign = BlockPos.of(game.sign);
            boolean signThere = level.isLoaded(sign) && level.getBlockEntity(sign) instanceof SignBlockEntity entity
                    && CasinoSigns.read(entity) != null;
            if (!(level.getBlockState(slot).getBlock() instanceof CoinSlotBlock) || !signThere) {
                return "gone";
            }
            if (level.shouldTickBlocksAt(slot)) {
                PENDING.put(key, new Pending(null, false, level.getGameTime()));
                if (!CoinSlotBlock.pulse(level, slot)) {
                    PENDING.remove(key);
                    return null;
                }
                account.balance += net(game.price, law(game));
                game.plays++;
                game.played += game.price;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, slot.getX() + 0.5d, slot.getY() + 1.1d,
                        slot.getZ() + 0.5d, 6, 0.3d, 0.2d, 0.3d, 0.0d);
                return "play";
            }
        }
        if (game.plays < KNOWN) {
            return null;
        }
        // Loin de tout joueur : la partie se joue sur les chiffres du jeu.
        double odds = (double) (game.wins + game.unpaid) / game.plays;
        long reward = game.wins > 0L ? game.paid / game.wins : 0L;
        account.balance += net(game.price, law(game));
        game.plays++;
        game.played += game.price;
        if (reward > 0L && random.nextDouble() < odds) {
            if (account.balance >= reward) {
                account.balance -= reward;
                game.wins++;
                game.paid += reward;
            } else {
                game.unpaid++;
            }
        }
        return "play";
    }

    // -- Le coffre ------------------------------------------------------------------------------

    /** Vide le coffre du casino dans le porte-monnaie. Rend le refus, ou {@code null}. */
    static String withdraw(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        CasinoBook book = book(server);
        CasinoBook.Account account = book.find(player.getUUID());
        if (account == null || account.balance <= 0L) {
            return "Le coffre du casino est vide.";
        }
        long amount = account.balance;
        account.balance = 0L;
        save(server, book);
        Economy.credit(player, amount);
        tell(player, "+" + MoneyFormat.exact(amount) + " $ retires du coffre du casino.", ChatFormatting.GREEN);
        return null;
    }

    /** Remplit le coffre du casino depuis le porte-monnaie. Rend le refus, ou {@code null}. */
    static String deposit(ServerPlayer player, long amount) {
        if (!Economy.tryDebit(player, amount)) {
            return "Il te faut " + MoneyFormat.exact(amount) + " $.";
        }
        MinecraftServer server = player.level().getServer();
        CasinoBook book = book(server);
        book.account(player.getUUID(), player.getGameProfile().name()).balance += amount;
        save(server, book);
        tell(player, MoneyFormat.exact(amount) + " $ verses au coffre du casino.", ChatFormatting.GREEN);
        return null;
    }

    /** Retire un jeu du casino (le panneau reste). */
    static String forget(ServerPlayer player, String key) {
        MinecraftServer server = player.level().getServer();
        CasinoBook book = book(server);
        CasinoBook.Account account = book.find(player.getUUID());
        if (account == null || account.games.remove(key) == null) {
            return "Ce jeu n'est plus dans ton casino.";
        }
        NEXT.remove(pendingKey(player.getUUID(), key));
        save(server, book);
        return null;
    }

    /** Les clients de tous les casinos viennent tout de suite (commande d'operateur). */
    static int rush() {
        int count = NEXT.size();
        NEXT.replaceAll((key, value) -> 0L);
        return count;
    }

    // -- Commandes ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("casino")
                .executes(context -> {
                    CasinoMenus.Book.INSTANCE.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("clients")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            int count = rush();
                            context.getSource().sendSuccess(() -> Component.literal("[Casino] " + count
                                    + " jeu(x) : leurs clients arrivent."), false);
                            return count;
                        }));
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        PENDING.clear();
        NEXT.clear();
        LAW_READ.clear();
    }
}
