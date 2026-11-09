package fr.atraxterra.dohy.palamod.legendary;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Ce que font les pierres legendaires ({@code LegendaryStone.onItemRightClick}).
 *
 * <p><b>Les recharges.</b> Chaque pierre a la sienne, 24 heures de temps reel
 * ({@code LAST_USE} sur l'objet ; pour le gant, une par pierre, 12 heures quand
 * il en porte six). Teleportation et chaos ont en plus un verrou de 10 minutes
 * par joueur, commun a toutes leurs pierres. Rien d'autre : le verrou d'une
 * heure des quatre autres effets n'etait jamais ecrit dans l'original.
 *
 * <p>Les pierres ne se consomment pas, sauf la pierre aleatoire.
 */
public final class LegendaryStones {

    public static final long STONE_COOLDOWN = 24L * 3600L * 1000L;
    public static final long FULL_GAUNTLET_COOLDOWN = STONE_COOLDOWN / 2L;
    private static final long PLAYER_LOCK = 10L * 60L * 1000L;

    /** {@code minX/maxX/minZ/maxZ}. */
    private static final int TELEPORT_RANGE = 25000;
    private static final int TELEPORT_ATTEMPTS = 11;

    private LegendaryStones() {
    }

    /**
     * Le tronc commun de la pierre et du gant : les deux recharges, l'effet,
     * les particules, puis l'ecriture des recharges.
     *
     * @param lastUse  la derniere utilisation de cette pierre (0 : jamais)
     * @param cooldown sa recharge
     * @param onUsed   ou ecrire la nouvelle derniere utilisation
     * @return vrai si la pierre a servi
     */
    static boolean activate(ServerPlayer player, ServerLevel level, LegendaryEffect effect,
                            long lastUse, long cooldown, LongConsumer onUsed) {
        long now = System.currentTimeMillis();
        Map<String, Long> locks = player.getData(PalaLegendary.PLAYER_LOCKS);
        if (effect.playerLocked()) {
            long left = locks.getOrDefault(effect.key(), 0L) + PLAYER_LOCK - now;
            if (left > 0L) {
                tell(player, Component.translatable("message.palamod.legendary.wait_type", duration(left)));
                return false;
            }
        }
        long left = lastUse + cooldown - now;
        if (left > 0L) {
            tell(player, Component.translatable("message.palamod.legendary.wait", duration(left)));
            return false;
        }
        // Ecart : la teleportation au hasard a 25 000 blocs n'a de sens qu'a la
        // surface de l'Overworld — dans le Nether on atterrirait sur le toit,
        // dans l'End dans le vide, et la dimension du Mineur a ses sections.
        if (effect == LegendaryEffect.TELEPORTATION && level.dimension() != Level.OVERWORLD) {
            tell(player, Component.translatable("message.palamod.legendary.teleport_overworld"));
            return false;
        }

        perform(player, level, effect);
        particles(player, level, effect);
        if (effect.playerLocked()) {
            locks.put(effect.key(), now);
            player.setData(PalaLegendary.PLAYER_LOCKS, locks);
        }
        onUsed.accept(now);
        return true;
    }

    private static void perform(ServerPlayer player, ServerLevel level, LegendaryEffect effect) {
        switch (effect) {
            case TELEPORTATION -> teleport(player, level);
            case INVISIBILITY -> invisibility(player, level);
            case FORTUNE -> fortune(player, level);
            case POWER -> {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 36000, 0));
                tell(player, Component.translatable("message.palamod.legendary.power"));
            }
            case JOBS -> jobs(player, level.getRandom());
            case CHAOS -> chaos(player, level);
            case RANDOM -> {
                // Traitee par LegendaryStoneItem : elle s'ouvre, elle ne s'active pas.
            }
        }
    }

    /**
     * Un point au hasard dans le carre de 50 000 blocs centre sur l'origine,
     * jusqu'a onze essais. Le cout est paye meme en cas d'echec, comme dans
     * l'original.
     *
     * <p>L'original montait de y = 63 jusqu'au premier bloc d'air, ce qui
     * pouvait poser le joueur dans une grotte ; on prend la surface (la carte
     * des hauteurs, l'eau comprise). Un point est refuse hors de la bordure du
     * monde, sur la bedrock, sur la lave, ou la ou le joueur ne pourrait pas
     * casser de bloc (la protection du spawn).
     */
    private static void teleport(ServerPlayer player, ServerLevel level) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int x = random.nextInt(2 * TELEPORT_RANGE) - TELEPORT_RANGE;
            int z = random.nextInt(2 * TELEPORT_RANGE) - TELEPORT_RANGE;
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
                continue;
            }
            // Charge, et au besoin genere, le chunk vise.
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockState ground = level.getBlockState(feet.below());
            if (y <= level.getMinY() || y >= level.getMaxY() || ground.is(Blocks.BEDROCK)
                    || ground.getFluidState().is(FluidTags.LAVA) || !level.mayInteract(player, feet)) {
                continue;
            }
            player.teleportTo(x + 0.5d, y, z + 0.5d);
            player.resetFallDistance();
            tell(player, Component.translatable("message.palamod.legendary.teleported"));
            return;
        }
        tell(player, Component.translatable("message.palamod.legendary.teleport_failed"));
    }

    /** Tous les joueurs d'un cube de 3 blocs autour du lanceur, lui compris : invisibilite II, 2 minutes. */
    private static void invisibility(ServerPlayer player, ServerLevel level) {
        AABB area = new AABB(player.getX() - 3.0d, player.getY() - 3.0d, player.getZ() - 3.0d,
                player.getX() + 3.0d, player.getY() + 3.0d, player.getZ() + 3.0d);
        for (Player target : level.getEntitiesOfClass(Player.class, area)) {
            if (target.isSpectator()) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2400, 1));
            if (target instanceof ServerPlayer other) {
                tell(other, Component.translatable("message.palamod.legendary.invisible"));
            }
        }
    }

    /**
     * De 2 a 25 blocs d'un meme metal, jetes devant le joueur et ramassables
     * aussitot : charbon, fer, or, diamant, amethyste, titane a 15 % chacun,
     * paladium a 10 %.
     */
    private static void fortune(ServerPlayer player, ServerLevel level) {
        RandomSource random = level.getRandom();
        int count = 2 + random.nextInt(24);
        int roll = 1 + random.nextInt(100);
        Block block;
        if (roll <= 15) {
            block = Blocks.COAL_BLOCK;
        } else if (roll <= 30) {
            block = Blocks.IRON_BLOCK;
        } else if (roll <= 45) {
            block = Blocks.GOLD_BLOCK;
        } else if (roll <= 60) {
            block = Blocks.DIAMOND_BLOCK;
        } else if (roll <= 75) {
            block = PalaBlocks.AMETHYST_BLOCK.get();
        } else if (roll <= 90) {
            block = PalaBlocks.TITANE_BLOCK.get();
        } else {
            block = PalaBlocks.PALADIUM_BLOCK.get();
        }
        ItemEntity dropped = player.drop(new ItemStack(block, count), false);
        if (dropped != null) {
            dropped.setNoPickUpDelay();
        }
        tell(player, Component.translatable("message.palamod.legendary.fortune"));
    }

    /** Un metier au hasard parmi les quatre, de 5 000 a 20 000 points, bonus du joueur compris. */
    private static void jobs(ServerPlayer player, RandomSource random) {
        Job job = Job.values()[random.nextInt(Job.values().length)];
        int xp = 5000 + random.nextInt(15001);
        JobApi.grant(player, job, xp);
        tell(player, Component.translatable("message.palamod.legendary.jobs", xp, job.display()));
    }

    /**
     * Chaque autre joueur a 10 blocs ou moins voit son inventaire principal
     * battu : vingt echanges de deux cases au hasard parmi les 36.
     *
     * <p>L'original le faisait sur un fil a part, sans aucun controle ; ici sur
     * le fil du serveur, et seulement sur les joueurs que le lanceur pourrait
     * frapper (PvP du serveur, equipes).
     */
    private static void chaos(ServerPlayer player, ServerLevel level) {
        RandomSource random = level.getRandom();
        for (ServerPlayer other : level.players()) {
            if (other == player || other.isSpectator() || other.distanceTo(player) > 10.0f
                    || !player.canHarmPlayer(other)) {
                continue;
            }
            Inventory inventory = other.getInventory();
            for (int swap = 0; swap < 20; swap++) {
                int a = random.nextInt(36);
                int b = random.nextInt(36);
                ItemStack first = inventory.getItem(a);
                inventory.setItem(a, inventory.getItem(b));
                inventory.setItem(b, first);
            }
        }
        tell(player, Component.translatable("message.palamod.legendary.chaos"));
    }

    /** {@code LengendaryStoneFx} : une double helice de la couleur de la pierre, trois tours. */
    static void particles(ServerPlayer player, ServerLevel level, LegendaryEffect effect) {
        DustParticleOptions dust = new DustParticleOptions(effect.color(), 1.0f);
        for (int step = 0; step < 60; step++) {
            double t = step / 60.0d;
            double angle = t * Math.PI * 6.0d;
            double y = player.getY() + t * 3.0d;
            for (double offset : new double[]{0.0d, Math.PI}) {
                level.sendParticles(dust, player.getX() + Math.cos(angle + offset) * 1.2d, y,
                        player.getZ() + Math.sin(angle + offset) * 1.2d, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
    }

    /**
     * {@code DurationConverter} : les deux plus grandes unites. On l'ecrit court,
     * en heures et minutes, ou en minutes et secondes.
     */
    public static Component duration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        if (hours > 0L) {
            return Component.translatable("duration.palamod.hours", hours, minutes);
        }
        if (minutes > 0L) {
            return Component.translatable("duration.palamod.minutes", minutes, seconds % 60L);
        }
        return Component.translatable("duration.palamod.seconds", seconds);
    }

    static void tell(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GRAY));
    }
}
