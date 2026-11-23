package fr.atraxterra.dohy.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le poste d'un PNJ du spawn : son role et la direction ou il regarde.
 *
 * <p>Comme les etiquettes flottantes ({@link HologramBlockEntity}), le poste fait venir son
 * PNJ des que son coin est charge (qu'il tourne ou non : un serveur a courte distance de
 * simulation ne fait tourner que les abords du joueur), verifie chaque seconde qu'il est
 * toujours la, et le renvoie quand le bloc disparait ou que son coin se decharge. Le PNJ
 * n'etant jamais sauvegarde, il n'y en a qu'un par poste, meme apres un spawn reconstruit ou
 * un monde recharge.
 */
public class NpcPostBlockEntity extends BlockEntity {

    private SpawnNpc.Role role = SpawnNpc.Role.SHOP;
    private int facing;
    private boolean configured;
    private SpawnNpc npc;

    /** Les postes des coins charges, qu'ils tournent ou non. */
    static final Set<NpcPostBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    public NpcPostBlockEntity(BlockPos pos, BlockState state) {
        super(DoHySpawn.NPC_POST_TYPE.get(), pos, state);
    }

    public SpawnNpc.Role role() {
        return role;
    }

    /** Le PNJ du poste s'il est la, sinon {@code null}. */
    public SpawnNpc npc() {
        return npc != null && npc.isAlive() ? npc : null;
    }

    /** Regle le poste ; le PNJ se refait au tick suivant. */
    public void set(SpawnNpc.Role role, float facing) {
        this.role = role;
        this.facing = Math.round(facing);
        this.configured = true;
        drop();
        setChanged();
    }

    /** Le tick d'un coin qui tourne : s'inscrire, au cas ou le chargement ne l'aurait pas fait. */
    void serverTick() {
        LOADED.add(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    /** Chaque seconde : fait venir les PNJ des postes charges qui n'ont pas le leur. */
    static void upkeep() {
        for (NpcPostBlockEntity post : LOADED) {
            post.ensure();
        }
    }

    private void ensure() {
        if (!(level instanceof ServerLevel server) || isRemoved() || !configured) {
            return;
        }
        if (npc != null && npc.isAlive()) {
            return;
        }
        npc = null;
        // un PNJ de ce poste reste peut-etre d'un poste recharge : on le reprend, et un seul
        AABB around = new AABB(worldPosition.getX() - 1.0d, worldPosition.getY() - 1.0d, worldPosition.getZ() - 1.0d,
                worldPosition.getX() + 2.0d, worldPosition.getY() + 3.0d, worldPosition.getZ() + 2.0d);
        List<SpawnNpc> found = server.getEntitiesOfClass(SpawnNpc.class, around,
                other -> other.isAlive() && other.post().equals(worldPosition));
        for (SpawnNpc other : found) {
            if (npc == null) {
                npc = other;
                other.settle(role, worldPosition, facing);
            } else {
                other.discard();
            }
        }
        if (npc != null) {
            return;
        }
        SpawnNpc created = DoHySpawn.NPC.get().create(server, EntitySpawnReason.EVENT);
        if (created == null) {
            return;
        }
        created.settle(role, worldPosition, facing);
        server.addFreshEntity(created);
        npc = created;
    }

    private void drop() {
        if (npc != null) {
            npc.discard();
            npc = null;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
        drop();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("role", role.id());
        output.putInt("facing", facing);
        output.putBoolean("configured", configured);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        role = SpawnNpc.Role.byId(input.getStringOr("role", SpawnNpc.Role.SHOP.id()));
        facing = input.getIntOr("facing", 0);
        configured = input.getBooleanOr("configured", false);
    }
}
