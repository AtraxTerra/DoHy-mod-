package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Le rattrapage des graines minerales, une fois par joueur.
 *
 * <p>Les graines sont des recompenses de niveau de Fermier ({@code data/jobs.json}),
 * versees en montant de niveau. Un joueur qui avait deja passe ces niveaux avant
 * l'arrivee du Botaniste ne les recevrait jamais, et n'aurait aucun moyen d'en
 * avoir : a la premiere connexion, il recoit celles des niveaux deja franchis.
 * Un nouveau joueur passe ici au niveau 0, ne recoit rien, et les aura en montant.
 *
 * <p>Priorite basse : la migration des anciens skills ({@code JobMigration}) passe
 * avant, les niveaux lus ici sont les bons.
 */
@EventBusSubscriber
public final class BotanistEvents {

    private BotanistEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getData(PalaBotanist.SEEDS_CAUGHT_UP.get())) {
            return;
        }
        player.setData(PalaBotanist.SEEDS_CAUGHT_UP.get(), Boolean.TRUE);
        int level = JobApi.level(player, Job.FARMER);
        Set<String> seeds = new HashSet<>();
        for (MineralSeed seed : MineralSeed.values()) {
            seeds.add("palamod:" + seed.seedName());
        }
        int given = 0;
        for (JobConfig.Reward reward : JobConfig.config().rewards()) {
            if (Job.FARMER != Job.byKey(reward.job()) || reward.level() > level || !seeds.contains(reward.id())) {
                continue;
            }
            ItemStack stack = JobApi.rewardStack(player, reward);
            if (stack.isEmpty()) {
                continue;
            }
            if (given == 0) {
                player.sendSystemMessage(Component.translatable("message.palamod.mineral_seed.catch_up")
                        .withStyle(ChatFormatting.GOLD));
            }
            player.sendSystemMessage(Component.literal("  " + stack.getCount() + "x ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE)));
            give(player, stack);
            given++;
        }
        if (given > 0) {
            DoHy.LOGGER.info("Graines minerales rattrapees pour {} : {} recompense(s), Fermier {}",
                    player.getScoreboardName(), given, level);
        }
    }

    /** Une pile decoupee a la taille maximale ; ce qui ne rentre pas tombe aux pieds. */
    private static void give(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            int size = Math.min(max, remaining);
            ItemStack part = stack.copyWithCount(size);
            if (!player.getInventory().add(part)) {
                player.drop(part, false);
            }
            remaining -= size;
        }
    }
}
