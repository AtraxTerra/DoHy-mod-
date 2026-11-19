package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le lucky block pose ({@code TileEntityLuckyBlock}) : l'evenement deja tire, et
 * s'il vient de « Quelle dinguerie ! ».
 *
 * <p>L'evenement est tire une fois pour toutes, a la premiere ouverture : un
 * joueur qui se deconnecte en pleine roulette retrouve le meme, et un bloc casse
 * le garde ({@link PalaLucky#EVENT}).
 */
public class LuckyBlockEntity extends BlockEntity {

    private String event = "";
    private boolean chained;

    public LuckyBlockEntity(BlockPos pos, BlockState state) {
        super(PalaLucky.LUCKY_ENTITY.get(), pos, state);
    }

    public LuckyType type() {
        return getBlockState().getBlock() instanceof LuckyBlock block ? block.type() : LuckyType.PALADIUM;
    }

    /** L'evenement tire, ou une chaine vide. */
    public String event() {
        return event;
    }

    void setEvent(String id) {
        event = id == null ? "" : id;
        setChanged();
    }

    public boolean chained() {
        return chained;
    }

    void setChained(boolean value) {
        chained = value;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        event = input.getStringOr("event", "");
        chained = input.getBooleanOr("chained", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!event.isEmpty()) {
            output.putString("event", event);
        }
        if (chained) {
            output.putBoolean("chained", true);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!event.isEmpty()) {
            components.set(PalaLucky.EVENT.get(), event);
        }
        if (chained) {
            components.set(PalaLucky.CHAINED.get(), true);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String stored = components.get(PalaLucky.EVENT.get());
        event = stored == null ? "" : stored;
        Boolean link = components.get(PalaLucky.CHAINED.get());
        chained = link != null && link;
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("event");
        output.discard("chained");
    }
}
