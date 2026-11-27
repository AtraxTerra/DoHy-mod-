package fr.atraxterra.dohy.palamod.press;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * La presse a imprimer ({@code TileEntityPrintPress}, PalaMod 10.3.0).
 *
 * <p>Quatre cases, celles de l'original : l'encre de paladium, la plaque
 * d'impression enchantee, les livres vierges, le livre imprime. La presse
 * tourne comme un four ({@code canSmelt}) : il lui faut la plaque, un livre, au
 * moins deux encres et une sortie vide ; chaque livre prend 200 ticks
 * ({@code furnaceCookTime}) et rend un livre enchante aux enchantements de la
 * plaque ({@code StoredEnchantments} recopie), qui s'use d'un cran
 * ({@code setItemDamage}) et casse a la troisieme impression.
 *
 * <p><b>L'encre.</b> Le wiki compte deux encres par livre, et c'est ce que
 * consomme cette presse a chaque livre. Le code d'origine bruloit une encre par
 * cycle de 200 ticks mais exigeait d'en avoir deux en reserve, si bien que la
 * derniere encre ne servait jamais : la regle du wiki est celle que les joueurs
 * connaissent, et elle ne laisse rien au fond de la case.
 *
 * <p>Le client recoit le contenu et l'etat d'impression ({@link #printing()}) :
 * le rendu en tire la plaque d'encre qui se remplit, les livres empiles et le
 * mouvement du plateau et des rouleaux.
 */
public class PrintPressBlockEntity extends MachineBlockEntity implements HandFed {

    public static final int SLOT_INK = 0;
    public static final int SLOT_PLATE = 1;
    public static final int SLOT_BOOKS = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    /** {@code furnaceCookTime == 200} : dix secondes par livre. */
    public static final int PRINT_TIME = 200;
    public static final int INK_PER_BOOK = 2;

    /** L'encre a gauche, la plaque au-dessus des livres, le livre imprime a droite. */
    public static final MachineLayout LAYOUT = new MachineLayout(List.of(
            new MachineLayout.SlotPos(40, 62, true),
            new MachineLayout.SlotPos(100, 62, true),
            new MachineLayout.SlotPos(100, 104, true),
            new MachineLayout.SlotPos(206, 82, false)));

    private int work;
    private boolean printing;

    public PrintPressBlockEntity(BlockPos pos, BlockState state) {
        super(PalaPress.PRINT_PRESS_TYPE.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.print_press"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaPress.PRINT_PRESS_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public int work() {
        return work;
    }

    @Override
    public int cycleDuration() {
        return canPrint() ? PRINT_TIME : 0;
    }

    /** 0 : impressions qui restent a la plaque ; 1 : 1 si une plaque est posee. */
    @Override
    public int extra(int index) {
        ItemStack plate = items.get(SLOT_PLATE);
        return switch (index) {
            case 0 -> plate.isEmpty() ? 0 : EnchantedPlateItem.printsLeft(plate);
            case 1 -> plate.isEmpty() ? 0 : 1;
            default -> 0;
        };
    }

    /** La presse imprime en ce moment : le rendu anime le plateau et les rouleaux. */
    public boolean printing() {
        return printing;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INK -> stack.is(PalaPress.PALADIUM_INK.get());
            case SLOT_PLATE -> EnchantedPlateItem.engraved(stack);
            case SLOT_BOOKS -> stack.is(Items.BOOK);
            default -> false;
        };
    }

    /** Un entonnoir sous la presse ne prend que les livres imprimes. */
    @Override
    protected boolean hopperMayTake(int slot) {
        return slot == SLOT_OUTPUT;
    }

    private boolean canPrint() {
        return items.get(SLOT_OUTPUT).isEmpty()
                && EnchantedPlateItem.engraved(items.get(SLOT_PLATE))
                && !items.get(SLOT_BOOKS).isEmpty()
                && items.get(SLOT_INK).getCount() >= INK_PER_BOOK;
    }

    @Override
    public void serverTick() {
        if (!canPrint()) {
            if (work != 0) {
                work = 0;
                setChanged();
            }
            setPrinting(false);
            return;
        }
        setPrinting(true);
        if (++work < PRINT_TIME) {
            setChanged();
            return;
        }
        work = 0;
        print();
    }

    /** {@code smeltItem} : un livre, deux encres, un cran d'usure. */
    private void print() {
        ItemStack plate = items.get(SLOT_PLATE);
        items.set(SLOT_OUTPUT, EnchantedPlateItem.print(plate));
        items.get(SLOT_BOOKS).shrink(1);
        items.get(SLOT_INK).shrink(INK_PER_BOOK);
        plate.setDamageValue(plate.getDamageValue() + 1);
        if (level != null) {
            if (plate.getDamageValue() >= plate.getMaxDamage()) {
                items.set(SLOT_PLATE, ItemStack.EMPTY);
                level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8f, 1.0f);
            }
            level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        setChanged();
        sync();
    }

    private void setPrinting(boolean value) {
        if (printing != value) {
            printing = value;
            setChanged();
            sync();
        }
    }

    // -- Le clic droit, objet en main --------------------------------------------

    @Override
    public boolean feed(ServerPlayer player, ItemStack held) {
        for (int slot : new int[] {SLOT_INK, SLOT_PLATE, SLOT_BOOKS}) {
            if (!canPlaceItem(slot, held)) {
                continue;
            }
            ItemStack merged = HandFed.merge(player, items.get(slot), held,
                    Math.min(getMaxStackSize(), held.getMaxStackSize()));
            if (merged == null) {
                return false;
            }
            items.set(slot, merged);
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.8f, 1.0f);
            }
            setChanged();
            sync();
            return true;
        }
        return false;
    }

    // -- Synchronisation avec le client ---------------------------------------------

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        sync();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = super.removeItem(slot, count);
        sync();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = super.removeItemNoUpdate(slot);
        sync();
        return removed;
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work = input.getIntOr("Work", 0);
        printing = input.getBooleanOr("Printing", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Work", work);
        output.putBoolean("Printing", printing);
    }
}
