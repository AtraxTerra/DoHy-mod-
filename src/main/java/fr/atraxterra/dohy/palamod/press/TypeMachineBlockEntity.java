package fr.atraxterra.dohy.palamod.press;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * La table d'ecriture ({@code TileEntityTypeMachine}, PalaMod 10.3.0).
 *
 * <p>Trois cases, celles de l'original : le livre, les plaques d'impression
 * ({@code chase}), la plaque gravee. Graver ({@code enchantPlate}) prend le livre
 * enchante, une plaque et des niveaux d'experience ({@link TypesetCost}) et rend
 * une plaque d'impression enchantee. Rien ne tourne : la gravure se fait d'un
 * coup, au bouton de l'interface ou, comme dans l'original, accroupi main vide.
 *
 * <p><b>Ecart.</b> L'original gravait aussi les livres ecrits et signes, qu'il
 * enregistrait dans des fichiers du serveur pour les reimprimer ; seuls les
 * livres enchantes passent ici. Les livres signes se copient deja en vanilla,
 * a l'etabli.
 */
public class TypeMachineBlockEntity extends MachineBlockEntity
        implements HandFed, MachineMenu.Buttons, MachineMenu.SlotLimits {

    public static final int SLOT_BOOK = 0;
    public static final int SLOT_CHASE = 1;
    public static final int SLOT_PLATE = 2;
    public static final int SLOT_COUNT = 3;

    public static final int BUTTON_ENGRAVE = 0;

    /** Le livre au-dessus des plaques, la plaque gravee a droite. */
    public static final MachineLayout LAYOUT = new MachineLayout(List.of(
            new MachineLayout.SlotPos(40, 62, true),
            new MachineLayout.SlotPos(40, 104, true),
            new MachineLayout.SlotPos(206, 82, false)));

    public TypeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(PalaPress.TYPE_MACHINE_TYPE.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.type_machine"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaPress.TYPE_MACHINE_MENU.get();
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

    /** 0 : le prix en niveaux du livre pose, 0 sans livre. */
    @Override
    public int extra(int index) {
        return index == 0 ? TypesetCost.levels(items.get(SLOT_BOOK)) : 0;
    }

    @Override
    public void serverTick() {
        // La gravure est instantanee : rien a faire entre deux clics.
    }

    /** Un livre enchante, qui porte au moins un enchantement. */
    public static boolean engravable(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK)
                && !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BOOK -> engravable(stack);
            case SLOT_CHASE -> stack.is(PalaPress.CHASE.get());
            default -> false;
        };
    }

    /** Un livre a la fois, comme l'original. */
    @Override
    public int slotLimit(int slot) {
        return slot == SLOT_BOOK ? 1 : 64;
    }

    @Override
    protected boolean hopperMayTake(int slot) {
        return slot == SLOT_PLATE;
    }

    @Override
    public boolean pressButton(ServerPlayer player, int id) {
        if (id != BUTTON_ENGRAVE) {
            return false;
        }
        engrave(player);
        return true;
    }

    /**
     * {@code enchantPlate} : le livre, une plaque, des niveaux, une sortie libre.
     * Un joueur en creatif ne paie pas, comme a la table d'enchantement.
     */
    public boolean engrave(ServerPlayer player) {
        ItemStack book = items.get(SLOT_BOOK);
        ItemStack chase = items.get(SLOT_CHASE);
        if (!engravable(book) || chase.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.palamod.type_machine.missing")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!items.get(SLOT_PLATE).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.palamod.type_machine.taken")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        int base = TypesetCost.levels(book);
        // Le familier : « Enchante », la gravure coute moins tant que l'effet n'est pas paye.
        int cost = fr.atraxterra.dohy.palamod.unlock.PetHooks.engraveCost(player, base);
        boolean free = player.hasInfiniteMaterials();
        if (!free && player.experienceLevel < cost) {
            player.sendSystemMessage(Component.translatable("message.palamod.type_machine.levels", cost)
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        items.set(SLOT_PLATE, EnchantedPlateItem.engrave(book));
        items.set(SLOT_BOOK, ItemStack.EMPTY);
        chase.shrink(1);
        if (!free) {
            player.giveExperienceLevels(-cost);
            if (cost < base) {
                fr.atraxterra.dohy.palamod.unlock.PetHooks.spendEnchanted(player);
            }
        }
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        player.sendSystemMessage(Component.translatable("message.palamod.type_machine.engraved", free ? 0 : cost)
                .withStyle(ChatFormatting.GRAY));
        setChanged();
        sync();
        return true;
    }

    // -- Le clic droit, objet en main --------------------------------------------

    @Override
    public boolean feed(ServerPlayer player, ItemStack held) {
        for (int slot : new int[] {SLOT_BOOK, SLOT_CHASE}) {
            if (!canPlaceItem(slot, held)) {
                continue;
            }
            ItemStack merged = HandFed.merge(player, items.get(slot), held,
                    Math.min(slotLimit(slot), held.getMaxStackSize()));
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
}
