package fr.atraxterra.dohy.palamod.wither;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le receptacle a wither : ses 15 000 points de vie, l'invocation en cours et
 * la regeneration des withers proches.
 *
 * <p><b>L'invocation</b> : la gemme posee, l'invocateur doit rester a six
 * blocs le temps qu'il faut (5 s a 20 min selon le wither). S'il s'eloigne, le
 * compte s'arrete ; au bout d'une minute d'absence, l'invocation echoue et la
 * gemme est perdue. Le wither nait au-dessus du receptacle, dans la charge du
 * wither vanilla — et son explosion.
 *
 * <p><b>Les points de vie</b> : une explosion proche les entame (celle d'une
 * TNT a un bloc : 350 environ). A zero, le receptacle saute avec sa gemme.
 */
public class WitherReceptacleBlockEntity extends BlockEntity {

    public static final int MAX_HEALTH = 15_000;
    static final double RANGE = 6.0d;
    static final int GRACE = 60 * 20;
    static final float REGEN = 2.0f;

    /** Les receptacles charges, pour que les explosions les trouvent sans balayer les chunks. */
    static final Set<WitherReceptacleBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    private String owner = "";
    private int health = MAX_HEALTH;
    private int summoning = -1;
    private int progress;
    private int absent;

    public WitherReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(PalaWithers.RECEPTACLE_ENTITY.get(), pos, state);
    }

    public int health() {
        return health;
    }

    /** Le wither en cours d'invocation, ou {@code null}. */
    public WitherKind summoning() {
        return summoning < 0 || summoning >= WitherKind.values().length ? null : WitherKind.values()[summoning];
    }

    public int progress() {
        return progress;
    }

    public int absent() {
        return absent;
    }

    public void setOwner(ServerPlayer player) {
        owner = player.getUUID().toString();
        setChanged();
    }

    boolean busy() {
        return summoning() != null;
    }

    void start(WitherKind kind, ServerPlayer player) {
        owner = player.getUUID().toString();
        summoning = kind.ordinal();
        progress = 0;
        absent = 0;
        sync();
    }

    private ServerPlayer ownerOnline(ServerLevel level) {
        if (owner.isEmpty()) {
            return null;
        }
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(owner));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    static void tick(Level level, BlockPos pos, BlockState state, WitherReceptacleBlockEntity receptacle) {
        if (level instanceof ServerLevel server) {
            receptacle.serverTick(server, pos);
        }
    }

    private void serverTick(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        ServerPlayer player = ownerOnline(level);
        WitherKind kind = summoning();
        if (kind != null) {
            if (player != null && player.level() == level && player.position().distanceTo(center) <= RANGE) {
                progress++;
                absent = 0;
                if (progress % 10 == 0) {
                    level.sendParticles(ParticleTypes.SOUL, center.x, center.y + 1.0d, center.z, 6, 0.4d, 0.6d, 0.4d,
                            0.02d);
                }
                if (progress >= kind.summonTicks()) {
                    summon(level, pos, kind, player);
                }
            } else if (++absent >= GRACE) {
                fail(player, kind);
            }
            if (level.getGameTime() % 20L == 0L) {
                sync();
            }
        }
        if (level.getGameTime() % 20L == 0L) {
            regenerate(level, pos, player);
        }
    }

    private void summon(ServerLevel level, BlockPos pos, WitherKind kind, ServerPlayer player) {
        PalaWither wither = kind.type().get().create(level, EntitySpawnReason.MOB_SUMMONED);
        summoning = -1;
        progress = 0;
        sync();
        if (wither == null) {
            return;
        }
        wither.setPos(pos.getX() + 0.5d, pos.getY() + 2.0d, pos.getZ() + 0.5d);
        wither.makeInvulnerable();
        if (kind == WitherKind.SUPREME) {
            wither.setInvulnerableTicks(PalaWither.SUPREME_WAKE);
        }
        WitherSummons.mark(wither, player);
        level.addFreshEntity(wither);
        level.playSound(null, pos, kind == WitherKind.FUNNY ? PalaWithers.FUNNY_WITHER_SPAWN.get()
                : SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.0f);
        FactionApi.stat(player, kind.counter(), 1);
        FactionApi.tell(player, "Wither invoque : " + Component.translatable("entity.palamod." + kind.id()).getString()
                + ".", ChatFormatting.GREEN);
    }

    private void fail(ServerPlayer player, WitherKind kind) {
        summoning = -1;
        progress = 0;
        absent = 0;
        sync();
        if (player != null) {
            FactionApi.tell(player, "Invocation annulee : tu t'es eloigne du receptacle trop longtemps. La gemme est "
                    + "perdue.", ChatFormatting.RED);
        }
    }

    /** Deux points de vie par seconde aux withers a six blocs. */
    private void regenerate(ServerLevel level, BlockPos pos, ServerPlayer player) {
        boolean healed = false;
        for (WitherBoss wither : level.getEntitiesOfClass(WitherBoss.class, new AABB(pos).inflate(RANGE),
                wither -> wither.isAlive() && wither.getHealth() < wither.getMaxHealth())) {
            wither.heal(REGEN);
            healed = true;
        }
        if (healed && player != null) {
            FactionApi.stat(player, "regen:wither", 1);
        }
    }

    /** Une explosion a {@code distance} blocs, de rayon {@code radius}. */
    void blast(ServerLevel level, double distance, float radius) {
        double reach = radius * 2.0d;
        if (distance >= reach) {
            return;
        }
        health -= (int) Math.round(radius * 100.0d * (1.0d - distance / reach));
        if (health > 0) {
            sync();
            return;
        }
        BlockPos pos = getBlockPos();
        ServerPlayer player = ownerOnline(level);
        if (player != null) {
            FactionApi.tell(player, "Ton receptacle a wither a ete detruit.", ChatFormatting.RED);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(getBlockState()));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner);
        output.putInt("health", health);
        output.putInt("summoning", summoning);
        output.putInt("progress", progress);
        output.putInt("absent", absent);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.getStringOr("owner", "");
        health = input.getIntOr("health", MAX_HEALTH);
        summoning = input.getIntOr("summoning", -1);
        progress = input.getIntOr("progress", 0);
        absent = input.getIntOr("absent", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
