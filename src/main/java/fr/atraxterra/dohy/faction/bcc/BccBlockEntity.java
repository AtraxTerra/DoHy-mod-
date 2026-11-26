package fr.atraxterra.dohy.faction.bcc;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le Base Claim Controller pose : son maitre (un joueur) ou la nation rivale qui
 * le tient, son coffre de 55 cases (sans tiroirs, comme l'original), et le compte
 * des intrus a 200 blocs, que l'interface et le bloc affichent.
 */
public class BccBlockEntity extends MachineBlockEntity {

    public static final int SLOTS = 55;
    public static final int COLUMNS = 11;
    /** Le rayon de surveillance du wiki. */
    public static final int WATCH = 200;

    public static final MachineLayout LAYOUT = layout();

    private UUID owner;
    private String ownerName = "";
    /** La nation rivale qui le tient, "" pour le BCC d'un joueur. */
    private String rival = "";
    private int intruders;

    public BccBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyBcc.BCC_TYPE.get(), pos, state, SLOTS, Component.translatable("block.dohy.base_claim_controller"));
    }

    private static MachineLayout layout() {
        List<MachineLayout.SlotPos> slots = new ArrayList<>();
        for (int index = 0; index < SLOTS; index++) {
            slots.add(new MachineLayout.SlotPos(32 + (index % COLUMNS) * 18, 56 + (index / COLUMNS) * 18, true));
        }
        return new MachineLayout(slots);
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return DoHyBcc.BCC_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public int work() {
        return 0;
    }

    @Override
    public int cycleDuration() {
        return 0;
    }

    /** 0 : les intrus a 200 blocs ; 1 : les jours tenus ; 2 : la gloire ; 3 : l'ELO du jour suivant. */
    @Override
    public int extra(int index) {
        BccData data = ownerData();
        return switch (index) {
            case 0 -> intruders;
            case 1 -> data == null ? 0 : (int) Bccs.daysHeld(data);
            case 2 -> data == null ? 0 : (int) data.glory();
            case 3 -> data == null ? 0 : (int) Bccs.eloForDay(Bccs.daysHeld(data) + 1);
            default -> 0;
        };
    }

    private BccData ownerData() {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        var player = server.getServer().getPlayerList().getPlayer(owner);
        return player == null ? null : player.getData(DoHyBcc.DATA.get());
    }

    /** Pas de tiroirs (Vault Drawers), ni de BCC dans un BCC. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return !"vault_drawers".equals(namespace) && !stack.is(DoHyBcc.BCC_ITEM.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return isOwner(player) && super.stillValid(player);
    }

    // -- Le maitre ---------------------------------------------------------------------------------

    public boolean isOwner(Player player) {
        return rival.isEmpty() && owner != null && owner.equals(player.getUUID());
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public String rival() {
        return rival;
    }

    public int intruders() {
        return intruders;
    }

    void setOwner(UUID uuid, String name) {
        owner = uuid;
        ownerName = name;
        rival = "";
        sync();
    }

    void setRival(String nation) {
        owner = null;
        ownerName = "";
        rival = nation;
        sync();
    }

    /** Toutes les deux secondes : les intrus a 200 blocs (autres joueurs, soldats rivaux, pillards). */
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 40L != 0L) {
            return;
        }
        AABB box = new AABB(worldPosition).inflate(WATCH);
        int count = server.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive()
                && worldPosition.distSqr(entity.blockPosition()) <= (double) WATCH * WATCH
                && Bccs.intruder(this, entity)).size();
        if (count != intruders) {
            intruders = count;
            sync();
        }
    }

    /** Le maitre oublie son BCC ; un BCC rival efface par la regeneration ne lache rien. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        Bccs.forget(this);
        super.preRemoveSideEffects(pos, state);
    }

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("owner_name", ownerName);
        tag.putString("rival", rival);
        tag.putInt("intruders", intruders);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr("owner", "");
        try {
            owner = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            owner = null;
        }
        ownerName = input.getStringOr("owner_name", "");
        rival = input.getStringOr("rival", "");
        intruders = input.getIntOr("intruders", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putString("rival", rival);
        output.putInt("intruders", intruders);
    }
}
