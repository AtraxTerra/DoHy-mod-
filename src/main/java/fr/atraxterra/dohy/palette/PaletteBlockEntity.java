package fr.atraxterra.dohy.palette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Une palette : ses 16 piles, et sa place dans un groupe.
 *
 * <p><b>Le stock reste a chaque palette.</b> Chacune garde ses propres piles ;
 * le groupe n'est qu'une vue qui les met bout a bout, rangee par rangee depuis
 * le coin nord-ouest. Assembler ou separer des palettes ne deplace donc aucun
 * objet, et une palette cassee ne rend que ce qu'elle portait.
 *
 * <p><b>Vu de dehors, un seul stock.</b> L'inventaire de l'entite (celui des
 * entonnoirs, et la capacite d'objets par une face, celle des entonnoirs et
 * bras de Create ou des grues de NTM NEXT) est celui du groupe entier, quelle
 * que soit la palette touchee. Sans face, la capacite ne donne que les piles
 * de cette palette : c'est elle que lit Create pour embarquer une palette sur
 * une machine mobile, palette par palette, sans rien compter deux fois.
 *
 * <p><b>La pile de caisses</b> est calculee par le coin nord-ouest du groupe,
 * le <i>controleur</i>. Il tient l'occupation par famille, que le client lit
 * pour dessiner les caisses ({@link CrateLayout}), et donne a chaque palette
 * la hauteur de sa pile, sa forme de collision. Le client ne recoit pas les
 * objets, seulement cela, et seulement quand la pile visible change.
 */
public class PaletteBlockEntity extends BlockEntity implements WorldlyContainer {

    private static final int SLOTS = CrateLayout.SLOTS;
    /** L'occupation compte en 1/64 de pile : une cartouche sur 64 en vaut 1. */
    private static final int UNITS = 64;
    private static final int[] NO_SLOTS = new int[0];

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final Stock stock = new Stock();

    // Le groupe : le decalage vers le controleur (jamais positif), et la taille.
    private int ctrlX;
    private int ctrlZ;
    private int groupW = 1;
    private int groupD = 1;
    // Le controleur : l'occupation par famille et l'axe des caisses.
    private final int[] units = new int[CrateLayout.FAMILIES];
    private boolean alongX = true;
    // Chaque palette : le sommet de sa pile, en quarts de pixel.
    private int top = Math.round(CrateLayout.PALLET * 4);

    /** Une palette tout juste posee doit chercher ses voisines. */
    private boolean pendingRegroup = true;
    private List<PaletteBlockEntity> view;
    private int[] faceSlots;
    private CrateLayout.Layout serverLayout;
    private CrateLayout.Layout clientLayout;

    public PaletteBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyPalette.TYPE.get(), pos, state);
    }

    // -- Le groupe -----------------------------------------------------------------------

    public boolean isController() {
        return ctrlX == 0 && ctrlZ == 0;
    }

    public BlockPos controllerPos() {
        return worldPosition.offset(ctrlX, 0, ctrlZ);
    }

    public int groupW() {
        return groupW;
    }

    public int groupD() {
        return groupD;
    }

    boolean pendingRegroup() {
        return pendingRegroup;
    }

    void markForRegroup() {
        pendingRegroup = true;
    }

    /** Ce que deviendra cette palette apres un regroupement. */
    void assign(BlockPos controller, int w, int d) {
        ctrlX = controller.getX() - worldPosition.getX();
        ctrlZ = controller.getZ() - worldPosition.getZ();
        groupW = w;
        groupD = d;
        pendingRegroup = false;
        view = null;
        serverLayout = null;
        if (!isController()) {
            Arrays.fill(units, 0);
        }
        invalidateCapabilities();
        dirtyOnly();
    }

    void setAlongX(boolean alongX) {
        this.alongX = alongX;
    }

    /**
     * Les palettes du groupe, rangee par rangee depuis le nord-ouest. Si l'une
     * manque (chunk decharge, palette cassee), la vue se reduit a celle-ci ; une
     * incoherence reelle — et non un chunk qui dort — appelle un regroupement.
     */
    public List<PaletteBlockEntity> members() {
        if (view != null && intact(view)) {
            return view;
        }
        view = resolve();
        return view;
    }

    private static boolean intact(List<PaletteBlockEntity> list) {
        for (PaletteBlockEntity member : list) {
            if (member.isRemoved()) {
                return false;
            }
        }
        return true;
    }

    private List<PaletteBlockEntity> resolve() {
        if (level == null || groupW * groupD <= 1) {
            return List.of(this);
        }
        BlockPos ctrl = controllerPos();
        List<PaletteBlockEntity> list = new ArrayList<>(groupW * groupD);
        for (int dz = 0; dz < groupD; dz++) {
            for (int dx = 0; dx < groupW; dx++) {
                BlockPos at = ctrl.offset(dx, 0, dz);
                if (!level.isLoaded(at)) {
                    return List.of(this);
                }
                if (!(level.getBlockEntity(at) instanceof PaletteBlockEntity member) || member.isRemoved()
                        || !member.controllerPos().equals(ctrl) || member.groupW != groupW
                        || member.groupD != groupD) {
                    requestRegroup();
                    return List.of(this);
                }
                list.add(member);
            }
        }
        return List.copyOf(list);
    }

    /** Demande un regroupement autour de cette palette, au prochain tick. */
    void requestRegroup() {
        pendingRegroup = true;
        schedule(worldPosition);
    }

    /** Demande au controleur de refaire la pile de caisses, au prochain tick. */
    private void requestRefresh() {
        schedule(controllerPos());
    }

    private void schedule(BlockPos pos) {
        if (level instanceof ServerLevel server && server.isLoaded(pos)) {
            Block block = getBlockState().getBlock();
            if (!server.getBlockTicks().hasScheduledTick(pos, block)) {
                server.scheduleTick(pos, block, 1);
            }
        }
    }

    /** Le tick programme : un regroupement s'il le faut, sinon la pile de caisses. */
    void onScheduledTick(ServerLevel server) {
        if (pendingRegroup) {
            PaletteGroups.regroupAround(server, worldPosition);
            return;
        }
        if (isController()) {
            refresh(false);
        } else {
            requestRefresh();
        }
    }

    /**
     * Le controleur refait la pile de caisses depuis le stock du groupe. Le
     * client n'est prevenu que si la pile visible change (ou si {@code force}).
     */
    void refresh(boolean force) {
        if (!isController() || level == null || level.isClientSide()) {
            return;
        }
        List<PaletteBlockEntity> members = members();
        if (members.size() != groupW * groupD) {
            return;
        }
        int[] fresh = new int[CrateLayout.FAMILIES];
        for (PaletteBlockEntity member : members) {
            for (ItemStack stack : member.items) {
                PaletteFamily family = PaletteFamily.of(stack);
                if (family != null) {
                    fresh[family.ordinal()] += stack.getCount() * UNITS / Math.max(1, stack.getMaxStackSize());
                }
            }
        }
        CrateLayout.Layout layout = CrateLayout.compute(groupW, groupD, alongX, stacks(fresh));
        boolean visible = force || serverLayout == null || !serverLayout.crates().equals(layout.crates())
                || !Arrays.equals(serverLayout.tops(), layout.tops());
        System.arraycopy(fresh, 0, units, 0, units.length);
        serverLayout = layout;
        dirtyOnly();
        if (!visible) {
            return;
        }
        for (int index = 0; index < members.size(); index++) {
            PaletteBlockEntity member = members.get(index);
            int newTop = Math.round(layout.tops()[index] * 4);
            if (member == this || force || member.top != newTop) {
                member.top = newTop;
                member.dirtyOnly();
                member.sync();
            }
        }
    }

    private static float[] stacks(int[] units) {
        float[] stacks = new float[units.length];
        for (int family = 0; family < units.length; family++) {
            stacks[family] = units[family] / (float) UNITS;
        }
        return stacks;
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Le sommet de la pile de cette palette, en pixels : sa forme de collision. */
    public float top() {
        return top / 4.0f;
    }

    /** La pile de caisses du groupe, cote client (le controleur seul en a une). */
    public CrateLayout.Layout clientLayout() {
        if (clientLayout == null) {
            clientLayout = CrateLayout.compute(groupW, groupD, alongX, stacks(units));
        }
        return clientLayout;
    }

    /** L'occupation du groupe par famille, en piles (le controleur). */
    public float[] stacks() {
        return stacks(units);
    }

    // -- Le stock de cette palette ---------------------------------------------------------

    /** Les 16 piles de cette palette seule : la capacite sans face, celle que Create embarque. */
    public Container stock() {
        return stock;
    }

    public boolean accepts(ItemStack stack) {
        return PaletteFamily.of(stack) != null;
    }

    /** Un changement du stock : sauvegarde, comparateurs de tout le groupe, pile a refaire. */
    void touch() {
        for (PaletteBlockEntity member : members()) {
            member.dirtyOnly();
        }
        requestRefresh();
    }

    private void dirtyOnly() {
        super.setChanged();
    }

    /**
     * Range une pile dans le groupe : d'abord sur les piles du meme objet, puis
     * dans les cases vides. Rend ce qui n'a pas trouve de place.
     */
    public ItemStack insert(ItemStack stack) {
        if (!accepts(stack)) {
            return stack;
        }
        ItemStack left = stack.copy();
        List<PaletteBlockEntity> members = members();
        for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
            for (PaletteBlockEntity member : members) {
                for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) {
                    ItemStack here = member.items.get(slot);
                    int max = Math.min(getMaxStackSize(), left.getMaxStackSize());
                    if (pass == 0 && !here.isEmpty() && ItemStack.isSameItemSameComponents(here, left)
                            && here.getCount() < max) {
                        int moved = Math.min(max - here.getCount(), left.getCount());
                        here.grow(moved);
                        left.shrink(moved);
                    } else if (pass == 1 && here.isEmpty()) {
                        int moved = Math.min(max, left.getCount());
                        member.items.set(slot, left.split(moved));
                    }
                }
            }
        }
        if (left.getCount() != stack.getCount()) {
            touch();
        }
        return left;
    }

    /** Le compte d'objets du groupe pour une famille. */
    public int count(PaletteFamily family) {
        int count = 0;
        for (PaletteBlockEntity member : members()) {
            for (ItemStack stack : member.items) {
                if (PaletteFamily.of(stack) == family) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /** Les piles occupees du groupe. */
    public int usedSlots() {
        int used = 0;
        for (PaletteBlockEntity member : members()) {
            for (ItemStack stack : member.items) {
                if (!stack.isEmpty()) {
                    used++;
                }
            }
        }
        return used;
    }

    // -- Le stock du groupe, tel que le voient les entonnoirs et les mods ---------------

    private PaletteBlockEntity memberFor(int index) {
        List<PaletteBlockEntity> members = members();
        int which = index / SLOTS;
        return index >= 0 && which < members.size() ? members.get(which) : null;
    }

    @Override
    public int getContainerSize() {
        return members().size() * SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (PaletteBlockEntity member : members()) {
            for (ItemStack stack : member.items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        PaletteBlockEntity member = memberFor(index);
        return member == null ? ItemStack.EMPTY : member.items.get(index % SLOTS);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        PaletteBlockEntity member = memberFor(index);
        if (member == null) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = ContainerHelper.removeItem(member.items, index % SLOTS, count);
        if (!taken.isEmpty()) {
            touch();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        PaletteBlockEntity member = memberFor(index);
        return member == null ? ItemStack.EMPTY : ContainerHelper.takeItem(member.items, index % SLOTS);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        PaletteBlockEntity member = memberFor(index);
        if (member == null) {
            return;
        }
        member.items.set(index % SLOTS, stack);
        stack.limitSize(getMaxStackSize(stack));
        touch();
    }

    /** Un entonnoir ou un mod a touche au stock du groupe. */
    @Override
    public void setChanged() {
        if (level == null) {
            super.setChanged();
            return;
        }
        touch();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (PaletteBlockEntity member : members()) {
            member.items.clear();
        }
        touch();
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return memberFor(index) != null && accepts(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int size = getContainerSize();
        if (size == 0) {
            return NO_SLOTS;
        }
        if (faceSlots == null || faceSlots.length != size) {
            faceSlots = new int[size];
            for (int index = 0; index < size; index++) {
                faceSlots[index] = index;
            }
        }
        return faceSlots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
        return canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return true;
    }

    /**
     * Une palette cassee rend ses propres piles (pas celles du groupe), et ses
     * voisines du groupe se regroupent sans elle au tick suivant.
     *
     * <p>Sans appel a la version du jeu : pour une entite qui est un
     * {@code Container}, elle vide ce conteneur au sol — ici le stock du groupe
     * entier. Vu en jeu : casser une palette d'un groupe de 3 x 3 jetait les
     * 6 400 objets des neuf palettes.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) {
            return;
        }
        List<PaletteBlockEntity> members = members();
        Containers.dropContents(level, pos, items);
        items.clear();
        for (PaletteBlockEntity member : members) {
            if (member != this) {
                member.requestRegroup();
            }
        }
    }

    // -- Sauvegarde et synchronisation -----------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("ctrl_x", ctrlX);
        output.putInt("ctrl_z", ctrlZ);
        output.putInt("w", groupW);
        output.putInt("d", groupD);
        output.putInt("obus", units[CrateLayout.OBUS]);
        output.putInt("munitions", units[CrateLayout.MUNITIONS]);
        output.putInt("missiles", units[CrateLayout.MISSILES]);
        output.putInt("along_x", alongX ? 1 : 0);
        output.putInt("top", top);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        ctrlX = Math.min(0, Math.max(-(PalettePlan.MAX - 1), input.getIntOr("ctrl_x", 0)));
        ctrlZ = Math.min(0, Math.max(-(PalettePlan.MAX - 1), input.getIntOr("ctrl_z", 0)));
        groupW = Math.max(1, Math.min(PalettePlan.MAX, input.getIntOr("w", 1)));
        groupD = Math.max(1, Math.min(PalettePlan.MAX, input.getIntOr("d", 1)));
        units[CrateLayout.OBUS] = Math.max(0, input.getIntOr("obus", 0));
        units[CrateLayout.MUNITIONS] = Math.max(0, input.getIntOr("munitions", 0));
        units[CrateLayout.MISSILES] = Math.max(0, input.getIntOr("missiles", 0));
        alongX = input.getIntOr("along_x", 1) != 0;
        top = Math.max(0, Math.min(24 * 4, input.getIntOr("top", Math.round(CrateLayout.PALLET * 4))));
        pendingRegroup = false;
        view = null;
        serverLayout = null;
        clientLayout = null;
    }

    /** Au chargement, une palette dont le groupe ne tient plus se regroupe. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            schedule(worldPosition);
        }
    }

    /** Le client recoit le groupe et la pile de caisses, pas les objets. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.remove("Items");
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Le stock propre a la palette, comme un petit coffre de 16 cases. */
    private final class Stock implements Container {

        @Override
        public int getContainerSize() {
            return SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack taken = ContainerHelper.removeItem(items, slot, count);
            if (!taken.isEmpty()) {
                touch();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < SLOTS) {
                items.set(slot, stack);
                stack.limitSize(getMaxStackSize(stack));
                touch();
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(stack);
        }

        @Override
        public void setChanged() {
            touch();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(PaletteBlockEntity.this, player);
        }

        @Override
        public void clearContent() {
            items.clear();
            touch();
        }
    }
}
