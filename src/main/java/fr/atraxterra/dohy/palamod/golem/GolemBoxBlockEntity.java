package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * {@code TileEntityGuardianBox} : trois mini golems y travaillent. Toutes les
 * 8 minutes, chacun mange une croquette et rend le metal choisi — 10 fer, 5
 * amethyste, 3 titane ou 1 paladium —, au plus le meilleur maker qu'ils ont
 * tous. Un mini golem fait 144 productions, puis se repare a la cle.
 *
 * <p>Le metal se choisit accroupi, main vide, d'un clic droit sur la boite.
 *
 * <p><b>Ecarts</b> : la structure est verifiee pour travailler, plus seulement
 * pour ouvrir ; le metal ne deborde plus dans les cases de croquettes ; les
 * mini golems se retirent a la main, comme un objet (l'original les rendait en
 * pierre, par un bouton, sans verifier qui).
 */
public class GolemBoxBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int GOLEM_SLOTS = 3;
    public static final int FIRST_CROQUETTE = 3;
    public static final int FIRST_OUTPUT = 6;
    public static final int SLOT_COUNT = 11;
    /** {@code delay = 9600} : 8 minutes. */
    public static final int DELAY = 9600;
    private static final int CHECK_PERIOD = 20;

    private static final int[] BOTTOM = {6, 7, 8, 9, 10};
    private static final int[] SIDES = {3, 4, 5};

    private int delay = DELAY;
    private int metal = 3;
    private int check;
    private boolean formed;

    public GolemBoxBlockEntity(BlockPos pos, BlockState state) {
        super(PalaGolem.GOLEM_BOX_ENTITY.get(), pos, state, SLOT_COUNT,
                Component.translatable("container.palamod.golem_box"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaGolem.GOLEM_BOX_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.GOLEM_BOX;
    }

    @Override
    public int work() {
        return available() < 0 ? 0 : DELAY - Math.max(0, delay);
    }

    @Override
    public int cycleDuration() {
        return DELAY;
    }

    /** {@code getAvailable} : le plus petit des meilleurs makers, -1 sans golem en boite. */
    public int available() {
        int best = Integer.MAX_VALUE;
        boolean any = false;
        for (int slot = 0; slot < GOLEM_SLOTS; slot++) {
            GolemData data = items.get(slot).get(PalaGolem.GOLEM_DATA.get());
            if (data != null && data.has(GolemUpgrade.INBOX)) {
                any = true;
                best = Math.min(best, data.bestMaker());
            }
        }
        return any ? Math.max(-1, best) : -1;
    }

    public GolemMetal metal() {
        return GolemMetal.of(Math.min(metal, Math.max(0, available())));
    }

    /** Accroupi, main vide : le metal suivant parmi ceux que les golems savent faire. */
    public void cycleMetal(ServerPlayer player) {
        int max = available();
        if (max < 0) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("message.palamod.golem_box.no_golem").withStyle(ChatFormatting.RED)));
            return;
        }
        metal = (Math.min(metal, max) + 1) % (max + 1);
        setChanged();
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                "message.palamod.golem_box.metal", Component.translatable("message.palamod.golem_box.metal."
                        + metal().key())).withStyle(ChatFormatting.GOLD)));
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        if (--check <= 0) {
            check = CHECK_PERIOD;
            formed = GolemBoxStructure.isComplete(level, worldPosition);
        }
        int max = available();
        if (!formed || max < 0) {
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        int tier = Math.min(metal, max);
        boolean produced = false;
        for (int slot = 0; slot < GOLEM_SLOTS; slot++) {
            ItemStack golem = items.get(slot);
            GolemData data = golem.get(PalaGolem.GOLEM_DATA.get());
            if (data == null || !data.has(GolemUpgrade.INBOX) || data.bestMaker() < tier || data.durability() <= 0) {
                continue;
            }
            int croquette = croquette();
            ItemStack yield = GolemMetal.of(tier).yield();
            if (croquette < 0 || !fits(yield)) {
                continue;
            }
            insert(yield);
            items.get(croquette).shrink(1);
            golem.set(PalaGolem.GOLEM_DATA.get(), data.withDurability(data.durability() - 1));
            produced = true;
        }
        if (produced) {
            delay = DELAY;
            setChanged();
        }
    }

    /** {@code slots 5 -> 3} : la derniere case de croquettes d'abord. */
    private int croquette() {
        for (int slot = FIRST_OUTPUT - 1; slot >= FIRST_CROQUETTE; slot--) {
            if (items.get(slot).is(PalaGolem.CROQUETTE.get())) {
                return slot;
            }
        }
        return -1;
    }

    private boolean fits(ItemStack stack) {
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            ItemStack here = items.get(slot);
            if (here.isEmpty() || ItemStack.isSameItemSameComponents(here, stack)
                    && here.getCount() + stack.getCount() <= here.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void insert(ItemStack stack) {
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            ItemStack here = items.get(slot);
            if (ItemStack.isSameItemSameComponents(here, stack)
                    && here.getCount() + stack.getCount() <= here.getMaxStackSize()) {
                here.grow(stack.getCount());
                return;
            }
        }
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, stack);
                return;
            }
        }
    }

    @Override
    public boolean canOpen(ServerPlayer player) {
        if (level != null && GolemBoxStructure.isComplete(level, worldPosition)) {
            return true;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.palamod.golem_box.incomplete").withStyle(ChatFormatting.RED)));
        return false;
    }

    // -- Cases -----------------------------------------------------------------

    /** Un mini golem habite qui sait se mettre en boite ; des croquettes ; rien en sortie. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < GOLEM_SLOTS) {
            GolemData data = stack.get(PalaGolem.GOLEM_DATA.get());
            return stack.is(PalaGolem.MINI_GOLEM.get()) && data != null && data.owner().isPresent()
                    && data.has(GolemUpgrade.INBOX);
        }
        if (slot < FIRST_OUTPUT) {
            return stack.is(PalaGolem.CROQUETTE.get());
        }
        return false;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.is(PalaGolem.MINI_GOLEM.get()) ? 1 : super.getMaxStackSize(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot >= FIRST_OUTPUT;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        delay = input.getIntOr("Delay", DELAY);
        metal = input.getIntOr("Metal", 3);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Delay", delay);
        output.putInt("Metal", metal);
    }
}
