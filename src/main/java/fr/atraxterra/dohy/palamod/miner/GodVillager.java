package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Le God Villager : un marchand rarissime, un seul echange.
 *
 * <p>Releve dans {@code EventsManager.onGodVillager} et
 * {@code EntityGodVillager} :
 * <ul>
 *   <li>a chaque tick de chaque joueur de Mineur 19 (95 ici), une chance sur
 *       500 000 000 : en moyenne pres de 290 jours de jeu cumule ;</li>
 *   <li>il apparait sur le joueur, ne part jamais de lui-meme ;</li>
 *   <li>un echange : 64 blocs de paladium contre une pepite d'endium ;</li>
 *   <li>apres l'echange, il disparait et donne 15 000 XP de Mineur.</li>
 * </ul>
 *
 * <p><b>Ecart d'apparence.</b> PalaMod lui donnait une entite et un rendu
 * propres (et, par un bug, la texture du villageois de Noel). Ici c'est un
 * marchand ambulant vanilla, nomme, persistant, qui ne propose que cet echange :
 * le comportement est entier, sans une ligne de rendu a maintenir.
 *
 * <p>Les 15 000 XP sont des XP de PalaJobs ; converties au rapport des deux
 * courbes ({@link MinerXpEvents#convert}), elles valent environ 5 350 XP ici.
 */
@EventBusSubscriber
public final class GodVillager {

    /** Marque posee sur l'entite : c'est elle qui distingue le God Villager d'un marchand. */
    static final String TAG = "palamod_god_villager";

    /** {@code nextInt(500000000) == 0}. */
    private static final int ODDS = 500_000_000;

    /** Mineur 19, x5. */
    private static final int REQUIRED_LEVEL = 95;

    /** {@code JobsPlayer.addXp(MINER, ..., 15000)}. */
    private static final double PALAJOBS_XP = 15_000.0d;

    private GodVillager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getRandom().nextInt(ODDS) != 0
                || !JobApi.meets(player, Job.MINER, REQUIRED_LEVEL)) {
            return;
        }
        spawn(player);
    }

    /** Fait apparaitre un God Villager sur le joueur. Expose pour les tests en jeu. */
    public static void spawn(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
        if (trader == null) {
            return;
        }
        trader.setPos(player.getX(), player.getY(), player.getZ());
        trader.setCustomName(Component.literal("God Villager").withStyle(ChatFormatting.GOLD));
        trader.setCustomNameVisible(true);
        trader.setPersistenceRequired();
        // 0 : jamais de depart. maybeDespawn ne compte qu'un delai positif.
        trader.setDespawnDelay(0);
        trader.addTag(TAG);
        trader.getOffers().clear();
        trader.getOffers().add(new MerchantOffer(
                new ItemCost(PalaBlocks.PALADIUM_BLOCK.get(), 64),
                new ItemStack(PalaItems.ENDIUM_NUGGET.get()),
                1, 0, 0.0f));
        level.addFreshEntity(trader);
        level.playSound(null, trader.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, trader.getX(), trader.getY() + 1.0d, trader.getZ(),
                40, 0.5d, 1.0d, 0.5d, 0.2d);
        player.sendSystemMessage(Component.literal("Un God Villager est apparu !")
                .withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        AbstractVillager villager = event.getAbstractVillager();
        if (!villager.entityTags().contains(TAG) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        JobApi.grant(player, Job.MINER, MinerXpEvents.convert(player, PALAJOBS_XP));
        if (villager.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0d,
                    villager.getZ(), 30, 0.5d, 1.0d, 0.5d, 0.1d);
        }
        player.closeContainer();
        villager.discard();
    }
}
