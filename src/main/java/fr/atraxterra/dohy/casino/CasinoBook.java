package fr.atraxterra.dohy.casino;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Les comptes des casinos, gardes sur l'overworld (un joueur absent garde son casino) : pour chaque
 * maitre, le coffre du casino et ses jeux, avec ce que NationsGlory affiche d'une entreprise de
 * casino — somme jouee, somme distribuee, taux de redistribution.
 *
 * <p>Les cartes se modifient en place ; {@link Casinos#save} remet le livre sur l'overworld pour
 * qu'il soit ecrit.
 */
public final class CasinoBook {

    /** Un jeu : un panneau [Casino] sur un monnayeur, et ce qu'il a fait. */
    public static final class Game {
        static final Codec<Game> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("casino").forGetter(game -> game.casino),
                Codec.STRING.optionalFieldOf("game", "").forGetter(game -> game.game),
                Codec.STRING.fieldOf("dimension").forGetter(game -> game.dimension),
                Codec.LONG.fieldOf("slot").forGetter(game -> game.slot),
                Codec.LONG.fieldOf("sign").forGetter(game -> game.sign),
                Codec.LONG.fieldOf("price").forGetter(game -> game.price),
                Codec.LONG.optionalFieldOf("plays", 0L).forGetter(game -> game.plays),
                Codec.LONG.optionalFieldOf("played", 0L).forGetter(game -> game.played),
                Codec.LONG.optionalFieldOf("wins", 0L).forGetter(game -> game.wins),
                Codec.LONG.optionalFieldOf("paid", 0L).forGetter(game -> game.paid),
                Codec.LONG.optionalFieldOf("unpaid", 0L).forGetter(game -> game.unpaid),
                Codec.STRING.optionalFieldOf("law", "TOLERE").forGetter(game -> game.law)
        ).apply(instance, Game::new));

        String casino;
        String game;
        String dimension;
        long slot;
        long sign;
        long price;
        long plays;
        long played;
        long wins;
        long paid;
        long unpaid;
        /** La loi du jeu dans le pays du casino, relue quand son maitre est la. */
        String law;

        Game(String casino, String game, String dimension, long slot, long sign, long price, long plays, long played,
             long wins, long paid, long unpaid, String law) {
            this.casino = casino;
            this.game = game;
            this.dimension = dimension;
            this.slot = slot;
            this.sign = sign;
            this.price = price;
            this.plays = plays;
            this.played = played;
            this.wins = wins;
            this.paid = paid;
            this.unpaid = unpaid;
            this.law = law;
        }

        /** « Le Lucky · roulette », ou le nom du casino seul. */
        public String title() {
            return game.isEmpty() ? casino : casino + " · " + game;
        }

        /** Le taux de redistribution : ce qui est rendu sur ce qui est joue, en pourcents. */
        public double redistribution() {
            return played <= 0L ? 0.0d : 100.0d * paid / played;
        }
    }

    /** Le casino d'un joueur : son coffre et ses jeux. */
    public static final class Account {
        static final Codec<Account> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(account -> account.name),
                Codec.LONG.optionalFieldOf("balance", 0L).forGetter(account -> account.balance),
                Codec.LONG.optionalFieldOf("seized", 0L).forGetter(account -> account.seized),
                Codec.LONG.optionalFieldOf("last_raid_check", 0L).forGetter(account -> account.lastRaidCheck),
                Codec.unboundedMap(Codec.STRING, Game.CODEC).optionalFieldOf("games", Map.of())
                        .forGetter(account -> account.games)
        ).apply(instance, Account::new));

        String name;
        long balance;
        /** Ce que la police a saisi en tout. */
        long seized;
        long lastRaidCheck;
        final Map<String, Game> games;

        Account(String name, long balance, long seized, long lastRaidCheck, Map<String, Game> games) {
            this.name = name;
            this.balance = balance;
            this.seized = seized;
            this.lastRaidCheck = lastRaidCheck;
            this.games = new LinkedHashMap<>(games);
        }

        public long balance() {
            return balance;
        }

        public Map<String, Game> games() {
            return games;
        }
    }

    public static final MapCodec<CasinoBook> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Account.CODEC)
            .fieldOf("accounts").xmap(CasinoBook::new, book -> book.accounts);

    final Map<UUID, Account> accounts;

    public CasinoBook() {
        this(Map.of());
    }

    CasinoBook(Map<UUID, Account> accounts) {
        this.accounts = new HashMap<>(accounts);
    }

    boolean isEmpty() {
        return accounts.isEmpty();
    }

    /** Le compte d'un maitre, ouvert au besoin. */
    Account account(UUID owner, String name) {
        Account account = accounts.computeIfAbsent(owner, id -> new Account(name, 0L, 0L, 0L, Map.of()));
        if (name != null && !name.isEmpty()) {
            account.name = name;
        }
        return account;
    }

    /** Le compte d'un maitre, ou {@code null}. */
    public Account find(UUID owner) {
        return accounts.get(owner);
    }

    /** La meme carte, sous un autre objet : l'overworld voit un changement et ecrit le livre. */
    CasinoBook touched() {
        CasinoBook copy = new CasinoBook(Map.of());
        copy.accounts.putAll(accounts);
        return copy;
    }

    /** La cle d'un jeu : le casino et le nom de jeu, en minuscules. */
    static String key(String casino, String game) {
        return casino.trim().toLowerCase(java.util.Locale.ROOT) + "|" + game.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
