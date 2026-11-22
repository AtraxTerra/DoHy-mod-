package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.enterprise.Enterprises;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Le contenu d'une planque securisee et son proprietaire (lot 24c). Cassee, elle lache son
 * contenu : le jeu vide au sol toute entite qui est un conteneur.
 *
 * <p>Par ses faces, les machines (tapis et entonnoirs de Create, convoyeurs de NTM NEXT, simples
 * entonnoirs) y mettent ce qui va aux stocks, et seulement cela ; elles n'en sortent rien, la
 * planque reste un coffre-fort. Chaque minute, si son proprietaire est en ligne, elle verse
 * d'elle-meme aux stocks ce qu'elle a recu ; le journal des entreprises en fait le compte toutes
 * les dix minutes.
 */
public class StashChestBlockEntity extends BlockEntity implements WorldlyContainer {

    public static final int SIZE = 27;
    private static final int[] SLOTS = IntStream.range(0, SIZE).toArray();
    /** Une minute entre deux versements automatiques. */
    private static final int SETTLE_TICKS = 1_200;
    /** Dix versements automatiques entre deux lignes du journal. */
    private static final int LOG_EVERY = 10;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final Map<String, Long> pending = new LinkedHashMap<>();
    private UUID owner;
    private String ownerName = "?";
    private int ticks;
    private int settles;

    public StashChestBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyUnderground.STASH_TYPE.get(), pos, state);
        // toutes les planques ne versent pas au meme instant
        ticks = (int) Math.floorMod(pos.asLong(), (long) SETTLE_TICKS);
    }

    /** Chaque minute : ce qu'ont apporte les machines passe aux stocks, si le proprietaire est la. */
    void serverTick() {
        if (++ticks < SETTLE_TICKS) {
            return;
        }
        ticks = 0;
        if (owner == null || isEmpty() || !(level instanceof ServerLevel server)) {
            return;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        try {
            Goods.move(player, this).units().forEach((key, units) -> pending.merge(key, units, Long::sum));
            if (++settles >= LOG_EVERY && !pending.isEmpty()) {
                Enterprises.log(player, "Planque alimentee par les machines : " + Goods.describe(pending) + ".");
                pending.clear();
                settles = 0;
            }
        } catch (RuntimeException error) {
            // la planque garde tout ce qui n'a pas ete verse : rien ne se perd
            DoHy.LOGGER.error("Planque : echec du versement automatique", error);
        }
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    /** Les machines n'y mettent que ce que les stocks prennent : produits, sachets, colis, matieres, caisses. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return Goods.of(stack).isPresent();
    }

    /** Rien ne sort d'une planque par une machine. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getName().getString();
        setChanged();
    }

    public boolean allows(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.isCreative();
    }

    public boolean ownedBy(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    public String ownerName() {
        return ownerName;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player) && allows(player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
        output.putString("owner_name", ownerName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "?");
    }
}
