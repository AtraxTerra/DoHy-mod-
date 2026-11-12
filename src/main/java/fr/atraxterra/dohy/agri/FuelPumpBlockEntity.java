package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * La cuve d'une pompe a carburant.
 *
 * <p>Elle n'accepte que les fluides de la table des carburants
 * ({@code agriculture.json}) : le diesel, l'essence et les autres carburants
 * de NTM NEXT, la lave sans autre mod. Elle est offerte a la capacite de
 * fluide de NeoForge sur toutes ses faces — un tuyau s'y branche comme sur
 * n'importe quelle cuve.
 *
 * <p>A chaque tick, elle verse dans le reservoir de chaque tracteur a portee,
 * au debit de la configuration ; un mB vaut ce que la table dit du fluide.
 */
public class FuelPumpBlockEntity extends BlockEntity {

    private final Tank tank = new Tank(AgriConfig.config().farming().pumpTank());
    private int saved = -1;
    private double carry;

    public FuelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyAgri.PUMP_TYPE.get(), pos, state);
    }

    /** La cuve, telle que les tuyaux et les recipients la voient. */
    public FluidStacksResourceHandler tank() {
        return tank;
    }

    public int amount() {
        return tank.getAmountAsInt(0);
    }

    public Fluid fluid() {
        FluidResource resource = tank.getResource(0);
        return resource.isEmpty() ? Fluids.EMPTY : resource.getFluid();
    }

    public String report() {
        int capacity = AgriConfig.config().farming().pumpTank();
        if (amount() <= 0) {
            return "Pompe vide : verse un carburant, ou branche un tuyau.";
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid());
        return "Pompe : " + amount() + " / " + capacity + " mB (" + id.getPath() + ").";
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int held = amount();
        if (held > 0 && server.getGameTime() % 2L == 0L) {
            refuel(server);
        }
        if (amount() != saved) {
            saved = amount();
            setChanged();
        }
    }

    private void refuel(ServerLevel server) {
        AgriConfig.Farming config = AgriConfig.config().farming();
        FluidResource resource = tank.getResource(0);
        double factor = resource.isEmpty() ? 0.0d : TractorFuel.perMb(resource.getFluid());
        if (factor <= 0.0d) {
            return;
        }
        AABB reach = new AABB(worldPosition).inflate(config.pumpRange());
        for (Tractor tractor : server.getEntitiesOfClass(Tractor.class, reach)) {
            int room = Tractor.tank() - tractor.fuel();
            if (room <= 0) {
                continue;
            }
            // deux ticks de debit d'un coup : la pompe ne passe qu'un tick sur deux
            int wanted = (int) Math.min(Math.min(config.pumpRate() * 2L, amount()), Math.ceil(room / factor));
            int poured;
            try (Transaction transaction = Transaction.openRoot()) {
                poured = tank.extract(0, resource, wanted, transaction);
                transaction.commit();
            }
            if (poured <= 0) {
                return;
            }
            double units = poured * factor + carry;
            int whole = (int) units;
            carry = units - whole;
            tractor.setFuel(tractor.fuel() + Math.min(room, whole));
            if (server.getGameTime() % 20L == 0L) {
                server.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.4f, 1.4f);
            }
            return;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("amount", amount());
        output.putString("fluid", BuiltInRegistries.FLUID.getKey(fluid()).toString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int amount = Math.max(0, input.getIntOr("amount", 0));
        Identifier id = Identifier.tryParse(input.getStringOr("fluid", ""));
        Fluid fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getOptional(id).orElse(Fluids.EMPTY);
        if (amount > 0 && fluid != Fluids.EMPTY) {
            tank.set(0, FluidResource.of(fluid), amount);
        }
        saved = amount();
    }

    /** Une cuve d'un seul fluide, qui ne prend que des carburants. */
    private static final class Tank extends FluidStacksResourceHandler {

        Tank(int capacity) {
            super(1, capacity);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !resource.isEmpty() && TractorFuel.perMb(resource.getFluid()) > 0.0d;
        }
    }
}
