package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.List;
import java.util.Map;

/**
 * L'XP de Mineur que donne l'effet de durete, et celle des particules du
 * cobblebreaker.
 *
 * <p>Releve dans {@code AJobsEventHandler.breakEvent} : sur la recolte d'une
 * obsidienne, sans toucher de soie, un joueur sous durete gagne 4 XP de Mineur.
 *
 * <p><b>4 XP de PalaJobs ne sont pas 4 XP de DoHy.</b> PalaJobs demande
 * 17 302 375 XP pour le niveau 20, DoHy 6 175 195 pour le niveau 100 : a
 * progression egale, une XP de PalaJobs vaut 0,357 XP d'ici. Le gain est donc
 * converti par ce rapport, recalcule depuis la courbe en vigueur pour suivre un
 * reequilibrage. 1,43 XP ne se donne pas : la partie entiere est donnee, la
 * fraction tiree au sort, ce qui fait la bonne moyenne sans rien stocker.
 *
 * <p>Une obsidienne posee par le joueur ne rapporte rien, comme toute source
 * d'XP du mod — sinon poser et casser la meme serait une machine a XP.
 */
@EventBusSubscriber
public final class MinerXpEvents {

    /** {@code JobsPlayer.addXp(MINER, ..., 4.0)}. */
    public static final double PALAJOBS_XP = 4.0d;

    /** Somme de {@code JobExpUtils.LEVELS} : l'XP de PalaJobs pour atteindre le niveau 20. */
    private static final double PALAJOBS_TOTAL = 17_302_375.0d;

    private MinerXpEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getState().is(Blocks.OBSIDIAN) || !player.hasEffect(PalaMiner.HARDNESS)) {
            return;
        }
        var silkTouch = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silkTouch, event.getTool()) > 0) {
            return;
        }
        if (PlacedBlocks.wasPlayerPlaced(event)) {
            return;
        }
        JobApi.grant(player, Job.MINER, convert(player, PALAJOBS_XP));
    }

    /**
     * Niveau des particules : le wiki (« Evolution du metier Mineur ») les donne au
     * niveau 5, soit 25 ici, le niveau ou le cobblebreaker se debloque.
     */
    public static final int PARTICLE_XP_LEVEL = 25;

    /**
     * L'XP de PalaJobs d'une particule du cobblebreaker, d'apres le wiki : fer 2,
     * or 4, diamant 8, amethyste 12, titane 16, paladium 20. Une liste construite a
     * l'appel : les objets ne sont pas enregistres quand cette classe se charge.
     */
    public static List<Map.Entry<Item, Double>> particles() {
        return List.of(Map.entry(PalaItems.IRON_PARTICLE.get(), 2.0d),
                Map.entry(PalaItems.GOLD_PARTICLE.get(), 4.0d),
                Map.entry(PalaItems.DIAMOND_PARTICLE.get(), 8.0d),
                Map.entry(PalaItems.AMETHYST_PARTICLE.get(), 12.0d),
                Map.entry(PalaItems.TITANE_PARTICLE.get(), 16.0d),
                Map.entry(PalaItems.PALADIUM_PARTICLE.get(), 20.0d));
    }

    /**
     * Des particules sorties a la main du cobblebreaker, par clic ou par maj-clic.
     * Un entonnoir qui vide la machine ne passe pas par ici : comme dans
     * l'original, il faut « les recuperer a la main ». Rien sous le niveau 25.
     */
    public static void particlesTaken(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || JobApi.level(player, Job.MINER) < PARTICLE_XP_LEVEL) {
            return;
        }
        for (Map.Entry<Item, Double> particle : particles()) {
            if (stack.is(particle.getKey())) {
                long xp = convert(player, particle.getValue() * stack.getCount());
                if (xp > 0) {
                    JobApi.grant(player, Job.MINER, xp);
                }
                return;
            }
        }
    }

    /** XP de PalaJobs vers XP de DoHy, a progression egale, sans l'arrondi : ce que vaut un gain en moyenne. */
    public static double exact(double palajobsXp) {
        return palajobsXp * JobApi.xpToReach(JobApi.maxLevel()) / PALAJOBS_TOTAL;
    }

    /** XP de PalaJobs vers XP de DoHy, a progression egale, arrondie au hasard. */
    public static long convert(ServerPlayer player, double palajobsXp) {
        double exact = exact(palajobsXp);
        long whole = (long) Math.floor(exact);
        return whole + (player.getRandom().nextDouble() < exact - whole ? 1 : 0);
    }
}
