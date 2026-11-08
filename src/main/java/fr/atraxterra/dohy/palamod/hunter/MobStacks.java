package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * L'empilement des creatures des spawners customises, a la demande du joueur :
 * moins d'entites, moins de lag.
 *
 * <ul>
 *   <li>une vague du spawner (vanilla : jusqu'a quatre creatures) rejoint la
 *       pile de sa creature a 16 blocs de ce spawner au lieu d'apparaitre ;
 *       s'il n'y en a pas, la premiere creature de la vague la commence ;</li>
 *   <li>le palier fixe le plafond : 3, 6, 9 ou 12 creatures en tout autour du
 *       spawner ; au-dela, la vague n'ajoute rien ;</li>
 *   <li>le nombre est affiche au-dessus de la pile des qu'elle en compte deux ;</li>
 *   <li>tuer une pile n'en tue qu'une : son butin et son XP, puis une nouvelle
 *       creature reprend la pile, moins une.</li>
 * </ul>
 *
 * <p><b>Pourquoi a l'entree dans le monde, et non a l'apparition.</b> Le spawner
 * vanilla ne relance son delai que si l'ajout de la creature au monde echoue ou
 * reussit ; une apparition annulee plus tot le laisse reessayer au tick suivant,
 * quatre fois par tick, indefiniment. La vague est donc refusee a
 * {@code EntityJoinLevelEvent} : le spawner attend son delai normal (10 a 40
 * secondes), comme si sa vague avait eu lieu.
 */
@EventBusSubscriber
public final class MobStacks {

    /** Rayon autour du spawner ou l'on cherche ses piles. */
    private static final double RANGE = 16.0d;

    /** {@code SpawnCount} du spawner vanilla : une vague, au plus quatre. */
    private static final int WAVE = 4;

    /** Les creatures qui sortent d'un spawner customise, et de quel spawner. */
    private static final Map<Mob, BlockPos> PENDING = new WeakHashMap<>();

    /** Le dernier tick ou une vague a ete comptee, par spawner. */
    private static final Map<String, Long> LAST_WAVE = new HashMap<>();

    private MobStacks() {
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != EntitySpawnReason.SPAWNER || event.getSpawner() == null) {
            return;
        }
        Optional<BlockEntity> owner = event.getSpawner().left();
        if (owner.isPresent() && owner.get().getBlockState().getBlock() instanceof CustomSpawnerBlock) {
            PENDING.put(event.getEntity(), owner.get().getBlockPos());
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos origin = PENDING.remove(mob);
        if (origin == null) {
            return;
        }
        BlockState spawner = level.getBlockState(origin);
        int cap = spawner.getBlock() instanceof CustomSpawnerBlock
                ? SpawnerSouls.CREATURES_PER_TIER * spawner.getValue(CustomSpawnerBlock.TIER) : 0;
        // Une vague = un seul passage : les essais suivants du meme tick sont
        // refuses, ce qui relance aussi le delai du spawner.
        String key = level.dimension() + "@" + origin.asLong();
        long now = level.getGameTime();
        Long last = LAST_WAVE.put(key, now);
        if (last != null && last == now) {
            event.setCanceled(true);
            return;
        }
        List<Mob> stacks = level.getEntitiesOfClass(Mob.class, new AABB(origin).inflate(RANGE),
                other -> other != mob && other.isAlive() && other.getType() == mob.getType()
                        && other.hasData(PalaHunter.MOB_STACK.get())
                        && other.getData(PalaHunter.MOB_STACK.get()).origin().equals(origin));
        int total = stacks.stream().mapToInt(other -> other.getData(PalaHunter.MOB_STACK.get()).count()).sum();
        int added = Math.min(WAVE, cap - total);
        if (added <= 0) {
            event.setCanceled(true);
            return;
        }
        if (!stacks.isEmpty()) {
            Mob nearest = stacks.stream()
                    .min(Comparator.comparingDouble(other -> other.distanceToSqr(mob))).get();
            setCount(nearest, nearest.getData(PalaHunter.MOB_STACK.get()).count() + added, origin);
            event.setCanceled(true);
            return;
        }
        setCount(mob, added, origin);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            split(mob);
        }
    }

    /**
     * La creature quitte sa pile (mort ou capture) : une autre reprend le reste.
     * Celle qui part redevient une creature seule, sans nombre.
     */
    static void split(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.hasData(PalaHunter.MOB_STACK.get())) {
            return;
        }
        MobStack stack = mob.getData(PalaHunter.MOB_STACK.get());
        mob.removeData(PalaHunter.MOB_STACK.get());
        mob.setCustomName(null);
        mob.setCustomNameVisible(false);
        if (stack.count() <= 1) {
            return;
        }
        Entity next = mob.getType().create(level, EntitySpawnReason.SPAWNER);
        if (!(next instanceof Mob heir)) {
            return;
        }
        heir.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        heir.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.SPAWNER, null);
        setCount(heir, stack.count() - 1, stack.origin());
        level.addFreshEntity(heir);
    }

    private static void setCount(Mob mob, int count, BlockPos origin) {
        mob.setData(PalaHunter.MOB_STACK.get(), new MobStack(count, origin));
        if (count >= 2) {
            mob.setCustomName(Component.literal("x" + count).withStyle(ChatFormatting.GOLD));
            mob.setCustomNameVisible(true);
        } else {
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
        }
    }
}
