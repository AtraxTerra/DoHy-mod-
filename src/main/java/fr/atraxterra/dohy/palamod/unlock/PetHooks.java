package fr.atraxterra.dohy.palamod.unlock;

import fr.atraxterra.dohy.palamod.pet.PalaPets;
import fr.atraxterra.dohy.palamod.pet.PetActives;
import fr.atraxterra.dohy.palamod.pet.PetApi;
import fr.atraxterra.dohy.palamod.pet.PetLook;
import fr.atraxterra.dohy.palamod.pet.PetSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Le point d'appui des familiers sur les systemes de PalaMod : chaque machine ou
 * objet demande ici ce qu'une passive ou une active change pour ce joueur. Sans
 * familier, ou sans la competence posee, rien ne change.
 */
public final class PetHooks {

    /** La portee des machines qui ne savent pas qui s'en sert : le joueur le plus proche. */
    private static final double NEARBY = 32.0d;

    private PetHooks() {
    }

    /** « Recul puissant » : la part de poussee en plus de l'infernal knocker (0 sans familier). */
    public static double knockbackBonus(LivingEntity attacker) {
        return attacker instanceof ServerPlayer player
                ? PetApi.fraction(player, PetSkill.POWERFUL_KNOCKBACK, false) : 0.0d;
    }

    /** Combien de {@code count} objets sont doubles, chacun avec la chance de la passive. */
    public static int bonusCount(ServerPlayer player, PetSkill skill, int count) {
        double chance = PetApi.fraction(player, skill, true);
        if (chance <= 0.0d || count <= 0) {
            return 0;
        }
        int extra = 0;
        for (int index = 0; index < count; index++) {
            if (player.getRandom().nextDouble() < chance) {
                extra++;
            }
        }
        return extra;
    }

    /**
     * Ce qu'un joueur sort d'une machine a la main : « Broyeur acere » au crusher,
     * « Geologie » au cobblebreaker, « Artisan de la croquette » a la machine a
     * croquettes. Le bonus arrive dans l'inventaire.
     */
    public static void machineTaken(ServerPlayer player, PetSkill skill, ItemStack taken) {
        int extra = bonusCount(player, skill, taken.getCount());
        if (extra <= 0) {
            return;
        }
        ItemStack bonus = taken.copyWithCount(extra);
        if (!player.getInventory().add(bonus)) {
            player.drop(bonus, false);
        }
        player.displayClientMessage(Component.literal(skill.label() + " : +" + extra)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** « Portail magique » : la seve versee compte davantage. */
    public static int portalSap(ServerPlayer player, int added) {
        double more = PetApi.fraction(player, PetSkill.MAGIC_PORTAL, false);
        return more <= 0.0d ? added : added + (int) Math.round(added * more);
    }

    /** « Chance florale » : une fleur de plus, avec sa chance. */
    public static boolean extraFlower(ServerPlayer player) {
        double chance = PetApi.fraction(player, PetSkill.FLORAL_CHANCE, true);
        return chance > 0.0d && player.getRandom().nextDouble() < chance;
    }

    /** « Pousse amelioree » : les poussees en plus qu'offre un lingot pose dans le totem. */
    public static int totemBonus(ServerPlayer player, int perIngot) {
        return (int) Math.round(perIngot * PetApi.fraction(player, PetSkill.ENHANCED_GROWTH, false));
    }

    /** « Expert legendaire » : la recharge d'une pierre legendaire, raccourcie. */
    public static long legendaryCooldown(ServerPlayer player, long cooldown) {
        double hours = PetApi.value(player, PetSkill.LEGENDARY_EXPERT);
        return Math.max(0L, cooldown - Math.round(hours * 3_600_000.0d));
    }

    /** « Pioche experimentee » : l'XP gagnee par la pioche des dieux. */
    public static double godPickaxeXp(LivingEntity miner, double xp) {
        return miner instanceof ServerPlayer player
                ? xp * (1.0d + PetApi.fraction(player, PetSkill.EXPERIENCED_PICKAXE, false)) : xp;
    }

    /**
     * « Extracteur miraculeux » : l'extracteur ne sait pas qui l'a pose ; le joueur
     * le plus proche (32 blocs) lui evite parfois une usure.
     */
    public static boolean spareExtractorUse(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        Player nearest = server.getNearestPlayer(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, NEARBY,
                false);
        if (!(nearest instanceof ServerPlayer player)) {
            return false;
        }
        double chance = PetApi.fraction(player, PetSkill.MIRACULOUS_EXTRACTOR, true);
        return chance > 0.0d && player.getRandom().nextDouble() < chance;
    }

    /** « Aviateur » : la descente du deltaplane ralentit d'autant (cote client). */
    public static double glideTime(Player player) {
        return player.getData(PalaPets.LOOK.get()).glideTime();
    }

    /** « Expert aerien » : la poussee du deltaplane (cote client). */
    public static double glideSpeed(Player player) {
        PetLook look = player.getData(PalaPets.LOOK.get());
        return look.glideSpeed();
    }

    /**
     * « Explosion benie » : un creeper fait sauter un spawner ; le joueur le plus
     * proche (32 blocs) avec l'effet a sa chance d'un second spawner casse.
     */
    public static boolean blessedExplosion(ServerLevel level, BlockPos pos) {
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, NEARBY,
                false);
        if (!(nearest instanceof ServerPlayer player)) {
            return false;
        }
        double chance = PetActives.consume(player, PetActives.BLESSED_EXPLOSION);
        if (chance <= 0.0d) {
            return false;
        }
        boolean won = player.getRandom().nextDouble() * 100.0d < chance;
        player.sendSystemMessage(Component.literal(won ? "[Familier] Explosion benie : un second spawner casse !"
                : "[Familier] Explosion benie : pas de second spawner cette fois.")
                .withStyle(won ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return won;
    }

    /** « Veterinaire adroit » : le bandage pose soigne d'un coup ; l'effet se consomme. */
    public static boolean instantCare(ServerPlayer player) {
        return PetActives.consume(player, PetActives.VETERINARIAN) > 0.0d;
    }

    /** « Enchante » : le prix d'une gravure, reduit tant que l'effet n'est pas paye. */
    public static int engraveCost(ServerPlayer player, int cost) {
        double levels = PetActives.peek(player, PetActives.ENCHANTED);
        return levels <= 0.0d ? cost : Math.max(1, cost - (int) Math.floor(levels));
    }

    /** La gravure payee a moindre prix consomme « Enchante ». */
    public static void spendEnchanted(ServerPlayer player) {
        PetActives.consume(player, PetActives.ENCHANTED);
    }
}
