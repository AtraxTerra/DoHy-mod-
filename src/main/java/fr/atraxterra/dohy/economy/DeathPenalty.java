package fr.atraxterra.dohy.economy;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * La penalite de mort.
 *
 * <pre>
 * perte = 0                                        si morts &lt; 3
 * perte = min( 8 % du liquide ,
 *              50 000 + 1 % des gains cumules )    sinon
 * perte = perte / 2                                si assurance
 * </pre>
 *
 * <h2>Pourquoi les gains cumules et non le solde</h2>
 *
 * <p>Punir le solde instantane apprend au joueur a ne jamais avoir de solde : il
 * viderait son compte avant chaque sortie et la penalite ne toucherait plus
 * rien. Le plafond est donc indexe sur les <b>gains cumules</b>, qui mesurent la
 * richesse reelle et ne se contournent pas.
 *
 * <h2>Pourquoi un plafond absolu</h2>
 *
 * <p>Sans lui, une mort bete dans la lave a 500 M serait un abandon de partie,
 * pas une lecon. Avec lui, la perte reste une gene proportionnee : a 500 M de
 * liquide et 2 Md de gains cumules, elle plafonne a 20 M, soit 4 % au lieu de
 * 8 %. C'est voulu — plus le joueur est riche, moins la mort mord, parce qu'a ce
 * stade elle a deja cesse d'etre un apprentissage.
 *
 * <h2>Les trois premieres morts sont gratuites</h2>
 *
 * <p>Le temps d'apprendre le mod et de comprendre qu'une banque existe. Une
 * penalite sur la premiere mort d'une partie neuve n'enseigne rien, elle
 * decourage.
 */
@EventBusSubscriber
public final class DeathPenalty {

    /** Nombre de morts gratuites en debut de partie. */
    public static final int FREE_DEATHS = 3;

    /** Part du solde liquide perdue. */
    public static final double RATE = 0.08d;

    /** Plancher du plafond absolu. */
    public static final long CAP_BASE = 50_000L;

    /** Part des gains cumules ajoutee au plafond. */
    public static final double CAP_LIFETIME_RATE = 0.01d;

    private DeathPenalty() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerEconomy data = Economy.of(player).withDeath();
        int deathNumber = data.deaths();

        if (deathNumber <= FREE_DEATHS) {
            store(player, data);
            player.sendSystemMessage(Component.literal("Mort " + deathNumber + "/" + FREE_DEATHS
                            + " — aucune penalite. Au-dela, pense a la banque (/bank).")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        long loss = lossFor(data);
        boolean insured = data.insurance() > 0;
        if (insured) {
            loss = loss / 2;
            data = data.withInsurance(data.insurance() - 1);
        }

        if (loss <= 0) {
            store(player, data);
            return;
        }

        long finalLoss = Math.min(loss, data.balance());
        store(player, data.withBalance(data.balance() - finalLoss));

        Component message = Component.literal("Mort : -")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(MoneyFormat.exact(finalLoss)).withStyle(ChatFormatting.RED));
        if (insured) {
            message = message.copy().append(Component.literal(" (assurance appliquee, "
                            + data.insurance() + " restante(s))")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.banked() > 0) {
            message = message.copy().append(Component.literal(" · epargne intacte : "
                            + MoneyFormat.shortForm(data.banked()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        player.sendSystemMessage(message);
    }

    /** La perte brute, avant assurance. Exposee pour l'affichage de {@code /bank}. */
    public static long lossFor(PlayerEconomy data) {
        if (data.deaths() < FREE_DEATHS) {
            return 0L;
        }
        long byRate = Math.round(data.balance() * RATE);
        long cap = CAP_BASE + Math.round(data.lifetimeEarned() * CAP_LIFETIME_RATE);
        return Math.max(0L, Math.min(byRate, cap));
    }

    private static void store(ServerPlayer player, PlayerEconomy data) {
        player.setData(DoHyAttachments.ECONOMY, data);
        player.syncData(DoHyAttachments.ECONOMY);
    }
}
