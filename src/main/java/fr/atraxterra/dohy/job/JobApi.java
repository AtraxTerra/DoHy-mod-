package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Les metiers : XP, niveaux, recompenses, et la question « ai-je le droit ».
 *
 * <p>Un niveau rapporte trois choses : de l'argent (la courbe de
 * {@code job_progression.json}), un perk tous les cinq niveaux, et les objets
 * que Paladium donnait a ce palier ({@code jobs.json}). L'argent reste
 * volontairement <b>sous</b> le revenu de grind equivalent : un metier est un
 * bonus, jamais une source de revenu — principe 2 de {@code DESIGN.md}.
 */
public final class JobApi {

    private JobApi() {
    }

    public static JobData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.JOBS);
    }

    // -- Courbe --------------------------------------------------------------

    /** XP necessaire pour passer du niveau {@code level - 1} au niveau {@code level}. */
    public static long xpForLevel(int level) {
        JobProgression.Curve curve = JobProgression.config().curve();
        return Math.round(curve.xpBase() * Math.pow(level, curve.xpExponent()));
    }

    /** XP cumulee necessaire pour atteindre ce niveau. */
    public static long xpToReach(int level) {
        long total = 0L;
        for (int l = 1; l <= level; l++) {
            total += xpForLevel(l);
        }
        return total;
    }

    /** Niveau correspondant a une XP cumulee. */
    public static int levelOf(long xp) {
        JobProgression.Curve curve = JobProgression.config().curve();
        int level = 0;
        long needed = 0L;
        while (level < curve.maxLevel()) {
            needed += xpForLevel(level + 1);
            if (xp < needed) {
                break;
            }
            level++;
        }
        return level;
    }

    public static int maxLevel() {
        return JobProgression.config().curve().maxLevel();
    }

    public static int level(ServerPlayer player, Job job) {
        return levelOf(data(player).xpOf(job));
    }

    public static long xp(ServerPlayer player, Job job) {
        return data(player).xpOf(job);
    }

    /** Progression dans le niveau courant, entre 0 et 1. */
    public static double progress(ServerPlayer player, Job job) {
        int level = level(player, job);
        if (level >= maxLevel()) {
            return 1.0d;
        }
        long floor = xpToReach(level);
        long need = xpForLevel(level + 1);
        long into = data(player).xpOf(job) - floor;
        return need <= 0 ? 0.0d : Math.min(1.0d, Math.max(0.0d, (double) into / need));
    }

    public static boolean meets(ServerPlayer player, Job job, int required) {
        return level(player, job) >= required;
    }

    /** Niveau cumule des quatre metiers. */
    public static int totalLevels(ServerPlayer player) {
        int total = 0;
        for (Job job : Job.values()) {
            total += level(player, job);
        }
        return total;
    }

    public static long moneyForLevel(int level) {
        JobProgression.Curve curve = JobProgression.config().curve();
        return Math.round(curve.moneyBase() * Math.pow(level, curve.moneyExponent()));
    }

    // -- Gain d'XP -----------------------------------------------------------

    /**
     * Accorde de l'XP, applique les montees de niveau et leurs recompenses.
     *
     * <p>Le bonus d'XP issu des perks et des ameliorations est applique ici,
     * donc une seule fois et au meme endroit pour toutes les sources.
     */
    public static void grant(ServerPlayer player, Job job, long amount) {
        if (amount <= 0) {
            return;
        }

        long boosted = Math.round(amount * (1.0d + JobPerks.xpBonus(player, job)
                + UpgradeApi.bonus(player, UpgradeApi.JOB_XP_BONUS)));
        int before = level(player, job);
        data(player).addXp(job, boosted);
        int after = level(player, job);

        // La barre se rafraichit a chaque gain, avant les recompenses : elle doit
        // deja montrer le nouveau niveau quand le message de montee arrive.
        JobBossBar.show(player, job, boosted, after > before);

        if (after > before) {
            for (int level = before + 1; level <= after; level++) {
                reward(player, job, level);
            }
            ContractApi.grantGlobalXp(player, (after - before) * 20L, "niveau de metier");
            ContractApi.progress(player, ContractType.JOB_LEVELS, target -> true, after - before);
            // Les perks dependent du niveau : on les recalcule entierement
            // plutot que d'appliquer un delta, ce qui evite toute derive.
            JobPerks.apply(player);
        }
    }

    private static void reward(ServerPlayer player, Job job, int level) {
        long money = moneyForLevel(level);
        long balance = Economy.credit(player, money);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 1.2f);

        player.sendSystemMessage(Component.literal(job.display() + " niveau " + level)
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  +" + MoneyFormat.exact(money))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));

        int perkEvery = JobProgression.config().curve().perkEvery();
        if (perkEvery > 0 && level % perkEvery == 0) {
            JobProgression.config().def(job).perks().stream()
                    .filter(perk -> perk.level() == level)
                    .forEach(perk -> player.sendSystemMessage(
                            Component.literal("  Perk : " + describePerk(perk))
                                    .withStyle(ChatFormatting.AQUA)));
        }

        giveItems(player, job, level);
    }

    /**
     * Les objets de Paladium pour ce palier.
     *
     * <p>Un objet que le mod n'a pas encore enregistre est saute en silence :
     * le chargement l'a deja signale, et le joueur n'a pas a lire un
     * identifiant. Il le recevra... jamais, ce palier etant franchi — c'est le
     * prix d'une table posee avant ses objets, et la raison pour laquelle
     * {@code /metiers audit} les liste.
     */
    static void giveItems(ServerPlayer player, Job job, int level) {
        for (JobConfig.Reward reward : JobConfig.config().rewardsAt(job, level)) {
            ItemStack stack = rewardStack(player, reward);
            if (stack.isEmpty()) {
                continue;
            }
            Component name = stack.getHoverName();
            int count = stack.getCount();
            give(player, stack);
            player.sendSystemMessage(Component.literal("  Recompense : ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(count + "x ").withStyle(ChatFormatting.WHITE))
                    .append(name.copy().withStyle(ChatFormatting.WHITE)));
        }
    }

    /**
     * La pile d'une recompense, enchantements compris, ou une pile vide si
     * l'objet n'existe pas encore.
     *
     * <p>La quantite peut depasser la taille d'une pile (105 lingots au niveau
     * 100) : c'est {@link #give} qui decoupe.
     */
    public static ItemStack rewardStack(ServerPlayer player, JobConfig.Reward reward) {
        Identifier id = JobConfig.parse(reward.id());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, reward.count()));
        var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (JobConfig.Enchant enchant : reward.enchantments()) {
            Identifier enchantId = JobConfig.parse(enchant.id());
            if (enchantId == null) {
                continue;
            }
            enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, enchantId))
                    .ifPresent(holder -> stack.enchant(holder, enchant.level()));
        }
        return stack;
    }

    /** Donne une pile, decoupee a la taille maximale, et lache ce qui ne rentre pas. */
    private static void give(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        List<ItemStack> parts = new ArrayList<>();
        while (remaining > 0) {
            int size = Math.min(max, remaining);
            parts.add(stack.copyWithCount(size));
            remaining -= size;
        }
        for (ItemStack part : parts) {
            if (!player.getInventory().add(part)) {
                player.drop(part, false);
            }
        }
    }

    // -- Textes --------------------------------------------------------------

    /** Un perk en une phrase. Expose pour que l'interface dise la meme chose. */
    public static String describePerk(JobProgression.Perk perk) {
        String amount = perk.amount() >= 1 || perk.amount() <= -1
                ? String.format("%+.1f", perk.amount())
                : String.format("%+.0f %%", perk.amount() * 100);
        return switch (perk.type()) {
            case "attribute" -> perk.attribute().orElse("attribut") + " " + amount;
            case "double_drop_chance" -> "chance de double drop " + amount;
            case "xp_bonus" -> "XP de metier " + amount;
            case "harvest_bonus" -> "recolte accrue " + amount;
            case "loot_bonus" -> "chance de butin double " + amount;
            default -> perk.type() + " " + amount + " (pas encore actif)";
        };
    }

    /**
     * Le nom a montrer pour un deblocage.
     *
     * <p>Le libelle du datapack d'abord, parce qu'il est ecrit pour etre lu ;
     * sinon le nom traduit de l'item ; en dernier recours le chemin embelli. Un
     * identifiant brut n'arrive jamais jusqu'au joueur.
     */
    public static Component name(JobConfig.Unlock unlock) {
        if (unlock.label().isPresent()) {
            return Component.literal(unlock.label().get());
        }
        if (unlock.isTag()) {
            return Component.literal(Gui.prettify(unlock.plainId()));
        }
        Identifier id = JobConfig.parse(unlock.plainId());
        return id == null
                ? Component.literal(Gui.prettify(unlock.plainId()))
                : Gui.displayName(id);
    }

    /** Le nom d'une recompense : celui de l'objet s'il existe, sinon le libelle. */
    public static Component name(JobConfig.Reward reward) {
        Identifier id = JobConfig.parse(reward.id());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return Gui.displayName(id);
        }
        return Component.literal(reward.label().orElseGet(() -> Gui.prettify(reward.id())));
    }

    /** « Mineur niveau 25 ». */
    public static Component requirementText(JobConfig.Unlock unlock) {
        Job job = Job.byKey(unlock.job());
        String jobName = job == null ? unlock.job() : job.display();
        return Component.literal(jobName + " niveau " + unlock.level());
    }

    /**
     * Le refus, en une ligne.
     *
     * <p>Il dit le niveau requis <i>et</i> le niveau atteint : « il te manque
     * 12 niveaux » est actionnable, « tu n'as pas le niveau » ne l'est pas.
     */
    public static Component refusal(ServerPlayer player, JobConfig.Unlock unlock) {
        Job job = Job.byKey(unlock.job());
        int current = job == null ? 0 : level(player, job);
        int missing = Math.max(0, unlock.level() - current);

        return Component.empty()
                .append(name(unlock).copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — il faut ").withStyle(ChatFormatting.GRAY))
                .append(requirementText(unlock).copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(", tu es " + current + " (" + missing + " a monter)")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }
}
