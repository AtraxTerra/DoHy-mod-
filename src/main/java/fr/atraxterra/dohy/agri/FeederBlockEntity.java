package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Le contenu d'une mangeoire (des rations) ou d'un abreuvoir (des doses d'eau).
 *
 * <p>C'est la <b>mangeoire</b> qui mene la tournee : a intervalle fixe, si elle
 * a des rations et trouve un abreuvoir en eau a portee, elle nourrit les betes
 * adultes autour d'elle. Chaque bete nourrie coute une ration et laisse son
 * produit a ses pieds ; la tournee entiere coute une dose d'eau. L'abreuvoir ne
 * fait rien de lui-meme.
 *
 * <p>Sans eau, pas de tournee : NationsGlory demande les deux, la bete doit
 * pouvoir manger et boire.
 */
public class FeederBlockEntity extends BlockEntity {

    private int amount;
    /** Ticks depuis la derniere tournee ; ne sert qu'a la mangeoire. */
    private int since;

    public FeederBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyAgri.FEEDER_TYPE.get(), pos, state);
    }

    private boolean trough() {
        return getBlockState().getBlock() instanceof FeederBlock feeder && feeder.kind() == FeederBlock.Kind.TROUGH;
    }

    public int amount() {
        return amount;
    }

    public int capacity() {
        AgriConfig.Config config = AgriConfig.config();
        return trough() ? config.troughCapacity() : config.waterCapacity();
    }

    /** Ajoute au contenu, a hauteur de la place libre ; rend ce qui a ete pris. */
    public int add(int offered) {
        int taken = Math.max(0, Math.min(offered, capacity() - amount));
        if (taken > 0) {
            amount += taken;
            sync();
        }
        return taken;
    }

    private void take(int count) {
        amount = Math.max(0, amount - count);
        sync();
    }

    /** Recale le niveau affiche par le bloc sur le contenu. */
    private void sync() {
        setChanged();
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        int shown = amount <= 0 ? 0 : Math.max(1, Math.min(FeederBlock.MAX_FILL,
                (int) Math.ceil(amount * (double) FeederBlock.MAX_FILL / Math.max(1, capacity()))));
        if (state.hasProperty(FeederBlock.FILL) && state.getValue(FeederBlock.FILL) != shown) {
            level.setBlock(worldPosition, state.setValue(FeederBlock.FILL, shown), Block.UPDATE_CLIENTS);
        }
    }

    // -- La tournee ------------------------------------------------------------------

    void serverTick() {
        if (!(level instanceof ServerLevel server) || !trough()) {
            return;
        }
        AgriConfig.Config config = AgriConfig.config();
        if (++since < config.feedIntervalTicks()) {
            return;
        }
        since = 0;
        feed(server, config);
    }

    /**
     * Une tournee, tout de suite.
     *
     * @return le nombre de betes nourries
     */
    int feed(ServerLevel server, AgriConfig.Config config) {
        int ration = Math.max(1, config.feedRation());
        if (amount < ration) {
            return 0;
        }
        FeederBlockEntity water = water(server, config.feedRadius());
        if (water == null) {
            return 0;
        }
        AABB area = new AABB(worldPosition).inflate(config.feedRadius(), 3.0d, config.feedRadius());
        List<Mob> animals = server.getEntitiesOfClass(Mob.class, area,
                animal -> animal.isAlive() && !animal.isBaby() && product(animal.getType()) != null);
        int fed = 0;
        for (Mob animal : animals) {
            if (fed >= config.feedMaxAnimals() || amount < ration) {
                break;
            }
            amount -= ration;
            fed++;
            ItemEntity drop = new ItemEntity(server, animal.getX(), animal.getY() + 0.3d, animal.getZ(),
                    new ItemStack(product(animal.getType())));
            drop.setDefaultPickUpDelay();
            server.addFreshEntity(drop);
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, animal.getX(), animal.getY() + 0.8d, animal.getZ(),
                    4, 0.3d, 0.3d, 0.3d, 0.0d);
        }
        if (fed > 0) {
            sync();
            water.take(1);
            server.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return fed;
    }

    /** Ce que laisse une bete nourrie, ou {@code null} si elle ne s'eleve pas ici. */
    private static Item product(EntityType<?> type) {
        if (type == EntityTypes.COW || type == EntityTypes.MOOSHROOM) {
            return DoHyAgri.CREAM.get();
        }
        if (type == EntityTypes.PIG) {
            return DoHyAgri.LARD.get();
        }
        if (type == EntityTypes.CHICKEN) {
            return Items.EGG;
        }
        return null;
    }

    /** L'abreuvoir en eau le plus proche, a portee. */
    private FeederBlockEntity water(ServerLevel server, int radius) {
        FeederBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-radius, -2, -radius),
                worldPosition.offset(radius, 2, radius))) {
            if (server.getBlockState(pos).getBlock() instanceof FeederBlock feeder
                    && feeder.kind() == FeederBlock.Kind.WATER
                    && server.getBlockEntity(pos) instanceof FeederBlockEntity other && other.amount > 0) {
                double distance = pos.distSqr(worldPosition);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = other;
                }
            }
        }
        return best;
    }

    /** Minutes avant la prochaine tournee, arrondies au-dessus. */
    public int minutesUntilFeed() {
        int left = Math.max(0, AgriConfig.config().feedIntervalTicks() - since);
        return (left + 1199) / 1200;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("amount", amount);
        output.putInt("since", since);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        amount = Math.max(0, input.getIntOr("amount", 0));
        since = Math.max(0, input.getIntOr("since", 0));
    }
}
