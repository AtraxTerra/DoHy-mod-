package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.defense.DefenseAnvilBlock;
import fr.atraxterra.dohy.palamod.defense.DefenseEvents;
import fr.atraxterra.dohy.palamod.defense.DefenseObsidianBlock;
import fr.atraxterra.dohy.palamod.wither.PalaWithers;
import fr.atraxterra.dohy.palamod.wither.WitherSummons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Les cinq TNT du pillage ({@code pillage.common.effects.blocks.tnt}).
 *
 * <ul>
 *   <li><b>TNT compacte</b> ({@code BigTNT}) : une explosion de 6 au lieu de 4 ;</li>
 *   <li><b>TNT en endium</b> : 8,8 — la TNT vanilla multipliee par 2,2, la valeur
 *       du wiki ; le code de 1.7.10 disait 13,2, un cratere de quinze blocs qui
 *       ne laisse rien a piller. Surtout, elle <b>perce</b> : jusqu'a dix
 *       blocs d'obsidienne a trois blocs d'elle cedent, et les obsidiennes
 *       piegees se declenchent en cedant ;</li>
 *   <li><b>TNT eponge</b> : pas d'explosion, elle boit les liquides dans un cube
 *       de 5 de cote — de quoi assecher une douve avant d'y poser la suivante ;</li>
 *   <li><b>TNT magique</b> ({@code EffectTNT}) : une explosion de 4, et cecite II
 *       (3 s) et poison II (4 s) a cinq blocs ;</li>
 *   <li><b>TNT a wither</b> : invoque un wither, et un second une fois sur cent.</li>
 * </ul>
 *
 * <p>Les claims sont respectes partout : ce qu'une explosion vanilla n'y casse
 * pas (voir {@code ClaimProtection}), le percement, l'assechement ne le
 * touchent pas non plus.
 */
public enum TntKind {
    BIG("big_tnt", 6.0f),
    ENDIUM("endium_tnt", 8.8f),
    SPONGE("sponge_tnt", 0.0f),
    EFFECT("effect_tnt", 4.0f),
    WITHER("wither_tnt", 0.0f);

    /** La TNT en endium : dix blocs d'obsidienne au plus, a trois blocs au plus. */
    static final int BREACH_COUNT = 10;
    static final int BREACH_RADIUS = 3;
    /** La TNT eponge : le cube de {@code SpongeTNT}, de -2 a +2. */
    static final int SPONGE_RADIUS = 2;
    static final double EFFECT_RADIUS = 5.0d;

    private final String id;
    private final float power;

    TntKind(String id, float power) {
        this.id = id;
        this.power = power;
    }

    public String id() {
        return id;
    }

    public float power() {
        return power;
    }

    void detonate(ServerLevel level, PalaPrimedTnt tnt) {
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) {
            return;
        }
        LivingEntity igniter = tnt.getOwner();
        switch (this) {
            case BIG -> explode(level, tnt);
            case ENDIUM -> {
                explode(level, tnt);
                breach(level, tnt.blockPosition(), igniter);
                if (igniter instanceof ServerPlayer player) {
                    fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "explode:endium_tnt", 1);
                }
            }
            case SPONGE -> drain(level, tnt.blockPosition(), igniter);
            case EFFECT -> {
                explode(level, tnt);
                AABB box = tnt.getBoundingBox().inflate(EFFECT_RADIUS);
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                    living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 1));
                    living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                }
            }
            case WITHER -> {
                summonWither(level, tnt, igniter);
                if (level.getRandom().nextInt(100) == 0) {
                    summonWither(level, tnt, igniter);
                }
                if (igniter instanceof ServerPlayer player) {
                    FactionApi.stat(player, "spawn:wither", 1);
                }
            }
        }
    }

    private void explode(ServerLevel level, PalaPrimedTnt tnt) {
        level.explode(tnt, Explosion.getDefaultDamageSource(level, tnt), null, tnt.getX(), tnt.getY(0.0625d),
                tnt.getZ(), power, false, Level.ExplosionInteraction.TNT);
    }

    /** Un joueur peut-il changer ce bloc : hors claim, dans le sien, ou dans une base ennemie a piller. */
    static boolean mayAlter(ServerLevel level, BlockPos pos, LivingEntity cause) {
        ClaimOwner owner = FactionApi.claimAt(level, pos);
        return !owner.claimed() || owner.raidable() || cause instanceof Player player && owner.ownedBy(player.getUUID());
    }

    /** Les obsidiennes les plus proches cedent, dix au plus. */
    private static void breach(ServerLevel level, BlockPos center, LivingEntity igniter) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-BREACH_RADIUS, -BREACH_RADIUS, -BREACH_RADIUS),
                center.offset(BREACH_RADIUS, BREACH_RADIUS, BREACH_RADIUS))) {
            if (pos.distSqr(center) <= BREACH_RADIUS * BREACH_RADIUS
                    && level.getBlockState(pos).is(PalaPillage.ENDIUM_TNT_BREAKABLE)) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        int broken = 0;
        for (BlockPos pos : found) {
            if (broken >= BREACH_COUNT) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DefenseAnvilBlock || !mayAlter(level, pos, igniter)) {
                continue;
            }
            if (state.getBlock() instanceof DefenseObsidianBlock) {
                // Une obsidienne piegee cede sans se rendre, et se declenche au tic suivant.
                DefenseEvents.defer(level, pos, state);
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.destroyBlock(pos, true, igniter);
            }
            broken++;
        }
    }

    /** Les liquides du cube, sources et coulants, et l'eau des blocs noyes. */
    private static void drain(ServerLevel level, BlockPos center, LivingEntity igniter) {
        int drained = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SPONGE_RADIUS, -SPONGE_RADIUS, -SPONGE_RADIUS),
                center.offset(SPONGE_RADIUS, SPONGE_RADIUS, SPONGE_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().isEmpty() || !mayAlter(level, pos, igniter)) {
                continue;
            }
            if (state.getBlock() instanceof LiquidBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                drained++;
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
                drained++;
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, center.getX() + 0.5d, center.getY() + 0.5d, center.getZ() + 0.5d,
                30, 1.5d, 1.5d, 1.5d, 0.05d);
        level.playSound(null, center, drained > 0 ? SoundEvents.SPONGE_ABSORB : SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 1.2f, 0.9f);
    }

    /**
     * {@code WitherTNT.spawnWither} : le wither de base, invulnerable le temps de
     * sa charge. Celui du pillage (lot 22d-bis), pour qu'il compte a l'allumeur.
     */
    private static void summonWither(ServerLevel level, PalaPrimedTnt tnt, LivingEntity igniter) {
        WitherBoss wither = PalaWithers.PALADIUM_WITHER.get().create(level, EntitySpawnReason.TRIGGERED);
        if (wither == null) {
            return;
        }
        wither.setPos(tnt.getX(), tnt.getY(), tnt.getZ());
        wither.makeInvulnerable();
        if (igniter instanceof ServerPlayer player) {
            WitherSummons.mark(wither, player);
        }
        level.addFreshEntity(wither);
    }
}
