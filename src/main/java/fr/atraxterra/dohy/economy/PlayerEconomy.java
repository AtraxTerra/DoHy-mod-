package fr.atraxterra.dohy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
public record PlayerEconomy(long balance, long lifetimeEarned, int deaths, boolean hudHidden,
                            long banked, int insurance) {

    /** Solde de depart : zero. La premiere heure de jeu doit etre pauvre. */
    public static final PlayerEconomy EMPTY = new PlayerEconomy(0L, 0L, 0, false, 0L, 0);

    /**
     * {@code MapCodec} et non {@code Codec} : le builder d'attachment de
     * NeoForge 26.2 n'accepte qu'un {@code MapCodec}.
     */
    public static final MapCodec<PlayerEconomy> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(PlayerEconomy::balance),
            Codec.LONG.optionalFieldOf("lifetime_earned", 0L).forGetter(PlayerEconomy::lifetimeEarned),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(PlayerEconomy::deaths),
            Codec.BOOL.optionalFieldOf("hud_hidden", false).forGetter(PlayerEconomy::hudHidden),
            // optionalFieldOf : les sauvegardes d'avant la phase 7 n'ont pas ces
            // champs et doivent continuer de se charger.
            Codec.LONG.optionalFieldOf("banked", 0L).forGetter(PlayerEconomy::banked),
            Codec.INT.optionalFieldOf("insurance", 0).forGetter(PlayerEconomy::insurance)
    ).apply(instance, PlayerEconomy::new));

    /** Derive du precedent, pour l'encodage JSON des sauvegardes de secours. */
    public static final Codec<PlayerEconomy> CODEC = MAP_CODEC.codec();

    /** Le HUD est dessine cote client : les donnees doivent y etre synchronisees. */
    public static final StreamCodec<ByteBuf, PlayerEconomy> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlayerEconomy::balance,
            ByteBufCodecs.VAR_LONG, PlayerEconomy::lifetimeEarned,
            ByteBufCodecs.VAR_INT, PlayerEconomy::deaths,
            ByteBufCodecs.BOOL, PlayerEconomy::hudHidden,
            ByteBufCodecs.VAR_LONG, PlayerEconomy::banked,
            ByteBufCodecs.VAR_INT, PlayerEconomy::insurance,
            PlayerEconomy::new
    );

    public PlayerEconomy withBalance(long newBalance) {
        return new PlayerEconomy(newBalance, lifetimeEarned, deaths, hudHidden, banked, insurance);
    }

    public PlayerEconomy withBanked(long newBanked) {
        return new PlayerEconomy(balance, lifetimeEarned, deaths, hudHidden,
                Math.max(0L, newBanked), insurance);
    }

    public PlayerEconomy withInsurance(int charges) {
        return new PlayerEconomy(balance, lifetimeEarned, deaths, hudHidden,
                banked, Math.max(0, charges));
    }

    /** Solde liquide plus epargne. C'est ce chiffre qui mesure la richesse. */
    public long netWorth() {
        return saturatedAdd(balance, banked);
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
        return new PlayerEconomy(newBalance, newLifetime, deaths, hudHidden, banked, insurance);
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
        return new PlayerEconomy(balance, lifetimeEarned, deaths + 1, hudHidden, banked, insurance);
    }

    public PlayerEconomy withHudHidden(boolean hidden) {
        return new PlayerEconomy(balance, lifetimeEarned, deaths, hidden, banked, insurance);
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
