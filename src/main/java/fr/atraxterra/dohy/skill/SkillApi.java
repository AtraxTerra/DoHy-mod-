package fr.atraxterra.dohy.skill;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Les skills : gain d'XP, niveaux, recompenses.
 *
 * <p>La recompense en argent est volontairement <b>sous</b> le revenu de grind
 * equivalent : les huit skills maxés rapportent environ 43 M cumules, face aux
 * 500 M que coute l'autosell. Un skill est un bonus, jamais une source de
 * revenu — c'est le principe 2 de {@code DESIGN.md}, et les chiffres de
 * {@code BALANCE.md} le verifient.
 */
public final class SkillApi {

    private SkillApi() {
    }

    public static SkillData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.SKILLS);
    }

    /** XP necessaire pour passer du niveau {@code level - 1} au niveau {@code level}. */
    public static long xpForLevel(int level) {
        SkillConfig.Curve curve = SkillConfig.config().curve();
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
        SkillConfig.Curve curve = SkillConfig.config().curve();
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

    public static int levelOf(ServerPlayer player, Skill skill) {
        return levelOf(data(player).xpOf(skill));
    }

    /** Progression dans le niveau courant, entre 0 et 1. */
    public static double progress(ServerPlayer player, Skill skill) {
        SkillConfig.Curve curve = SkillConfig.config().curve();
        int level = levelOf(player, skill);
        if (level >= curve.maxLevel()) {
            return 1.0d;
        }
        long floor = xpToReach(level);
        long need = xpForLevel(level + 1);
        long into = data(player).xpOf(skill) - floor;
        return need <= 0 ? 0.0d : Math.min(1.0d, Math.max(0.0d, (double) into / need));
    }

    public static long moneyForLevel(int level) {
        SkillConfig.Curve curve = SkillConfig.config().curve();
        return Math.round(curve.moneyBase() * Math.pow(level, curve.moneyExponent()));
    }

    /**
     * Accorde de l'XP, applique les montees de niveau et leurs recompenses.
     *
     * <p>Le bonus d'XP issu des perks est applique ici, donc une seule fois et
     * au meme endroit pour toutes les sources.
     */
    public static void grant(ServerPlayer player, Skill skill, long amount) {
        if (amount <= 0) {
            return;
        }

        long boosted = Math.round(amount * (1.0d + SkillPerks.xpBonus(player, skill)));
        int before = levelOf(player, skill);
        data(player).addXp(skill, boosted);
        int after = levelOf(player, skill);

        if (after > before) {
            for (int level = before + 1; level <= after; level++) {
                reward(player, skill, level);
            }
            ContractApi.grantGlobalXp(player, (after - before) * 20L, "niveau de skill");
            ContractApi.progress(player, ContractType.SKILL_LEVELS, target -> true, after - before);
            // Les perks dependent du niveau : on les recalcule entierement
            // plutot que d'appliquer un delta, ce qui evite toute derive.
            SkillPerks.apply(player);
        }
    }

    private static void reward(ServerPlayer player, Skill skill, int level) {
        long money = moneyForLevel(level);
        long balance = Economy.credit(player, money);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 1.2f);

        player.sendSystemMessage(Component.literal(skill.display() + " niveau " + level)
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  +" + MoneyFormat.exact(money))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));

        int perkEvery = SkillConfig.config().curve().perkEvery();
        if (perkEvery > 0 && level % perkEvery == 0) {
            SkillConfig.config().def(skill).perks().stream()
                    .filter(perk -> perk.level() == level)
                    .forEach(perk -> player.sendSystemMessage(
                            Component.literal("  Perk : " + describePerk(perk))
                                    .withStyle(ChatFormatting.AQUA)));
        }
    }

    /** Un perk en une phrase. Expose pour que l'interface dise la meme chose. */
    public static String describePerk(SkillConfig.Perk perk) {
        String amount = perk.amount() >= 1 || perk.amount() <= -1
                ? String.format("%+.1f", perk.amount())
                : String.format("%+.0f %%", perk.amount() * 100);
        return switch (perk.type()) {
            case "attribute" -> perk.attribute().orElse("attribut") + " " + amount;
            case "double_drop_chance" -> "chance de double drop " + amount;
            case "xp_bonus" -> "XP de skill " + amount;
            default -> perk.type() + " " + amount + " (pas encore actif)";
        };
    }
}
