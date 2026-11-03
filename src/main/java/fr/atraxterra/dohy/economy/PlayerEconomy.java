package fr.atraxterra.dohy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Donnees economiques d'un joueur.
 *
 * <p>Immuable a dessein : chaque modification remplace l'enregistrement entier
 * via {@code setData}. Un enregistrement immuable se code en codec sans effort
 * et supprime toute une classe de bugs ou une reference partagee est mutee sans
 * que la sauvegarde ou la synchronisation soit declenchee.
 *
 * <p>{@code lifetimeEarned} n'est pas une statistique decorative : la penalite
 * de mort est indexee sur les gains cumules et non sur le solde instantane,
 * parce qu'indexer sur le solde apprend au joueur a ne jamais avoir de solde
 * (voir {@code docs/DESIGN.md} section 5).
 */
public record PlayerEconomy(long balance, long lifetimeEarned, int deaths, boolean hudHidden) {

    /** Solde de depart : zero. La premiere heure de jeu doit etre pauvre. */
    public static final PlayerEconomy EMPTY = new PlayerEconomy(0L, 0L, 0, false);

    public static final Codec<PlayerEconomy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(PlayerEconomy::balance),
            Codec.LONG.optionalFieldOf("lifetime_earned", 0L).forGetter(PlayerEconomy::lifetimeEarned),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(PlayerEconomy::deaths),
            Codec.BOOL.optionalFieldOf("hud_hidden", false).forGetter(PlayerEconomy::hudHidden)
    ).apply(instance, PlayerEconomy::new));

    /** Le HUD est dessine cote client : les donnees doivent y etre synchronisees. */
    public static final StreamCodec<ByteBuf, PlayerEconomy> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlayerEconomy::balance,
            ByteBufCodecs.VAR_LONG, PlayerEconomy::lifetimeEarned,
            ByteBufCodecs.VAR_INT, PlayerEconomy::deaths,
            ByteBufCodecs.BOOL, PlayerEconomy::hudHidden,
            PlayerEconomy::new
    );

    public PlayerEconomy withBalance(long newBalance) {
        return new PlayerEconomy(newBalance, lifetimeEarned, deaths, hudHidden);
    }

    /**
     * Credite le joueur. Sature a {@link Long#MAX_VALUE} au lieu de deborder :
     * un debordement silencieux rendrait le solde negatif, ce qui est la pire
     * facon de perdre une partie.
     */
    public PlayerEconomy credit(long amount) {
        if (amount <= 0L) {
            return this;
        }
        long newBalance = saturatedAdd(balance, amount);
        long newLifetime = saturatedAdd(lifetimeEarned, amount);
        return new PlayerEconomy(newBalance, newLifetime, deaths, hudHidden);
    }

    /** Debite le joueur, sans jamais passer sous zero. */
    public PlayerEconomy debit(long amount) {
        if (amount <= 0L) {
            return this;
        }
        return withBalance(Math.max(0L, balance - amount));
    }

    public boolean canAfford(long amount) {
        return balance >= amount;
    }

    public PlayerEconomy withDeath() {
        return new PlayerEconomy(balance, lifetimeEarned, deaths + 1, hudHidden);
    }

    public PlayerEconomy withHudHidden(boolean hidden) {
        return new PlayerEconomy(balance, lifetimeEarned, deaths, hidden);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        // Debordement : les deux operandes sont positifs mais la somme ne l'est pas.
        if (((a ^ sum) & (b ^ sum)) < 0L) {
            return Long.MAX_VALUE;
        }
        return sum;
    }
}
