package fr.atraxterra.dohy.cryo;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * La machine de la chambre cryogenique, sur la case maitre.
 *
 * <p>Une fiole d'ADN dans sa case, du froid dans la reserve : a chaque cycle, la
 * chambre tire le butin de la bete — sa table de butin vanilla, sur un modele de
 * la bete qui n'entre jamais dans le monde, comme le controleur de spawners — et
 * ce que la bete produit de son vivant (oeufs, ecailles, rayons de miel). Le
 * grade de l'ADN donne la cadence et le nombre de tirages ({@link Dna}).
 *
 * <p>Le froid vient de la glace : boule de neige, neige, glace, glace compactee,
 * glace bleue, de 1 a 648 unites ; un cycle en coute {@link #COLD_PER_CYCLE}. Sans
 * froid, ou sortie pleine, la chambre s'arrete sans rien perdre.
 *
 * <p>La fusion se fait ici aussi : {@link Dna#FUSION} fioles d'une meme bete et
 * d'un meme grade dans la case de fusion, un clic, une fiole du grade suivant.
 */
public class CryoChamberBlockEntity extends MachineBlockEntity
        implements MachineMenu.Buttons, MachineMenu.SlotLimits, WorldlyContainer {

    public static final int SLOT_DNA = 0;
    public static final int SLOT_COLD = 1;
    public static final int FIRST_OUTPUT = 2;
    public static final int OUTPUTS = 9;
    public static final int SLOT_FUSE_IN = FIRST_OUTPUT + OUTPUTS;
    public static final int SLOT_FUSE_OUT = SLOT_FUSE_IN + 1;
    public static final int SIZE = SLOT_FUSE_OUT + 1;

    /** Le bouton de fusion de l'ecran. */
    public static final int BUTTON_FUSE = 0;

    public static final int COLD_MAX = 2000;
    public static final int COLD_PER_CYCLE = 2;

    /** Ce que l'ecran lit dans {@code extra(0)}. */
    public static final int STATE_RUNNING = 0;
    public static final int STATE_NO_DNA = 1;
    public static final int STATE_NO_COLD = 2;
    public static final int STATE_FULL = 3;
    public static final int STATE_STERILE = 4;

    public static final MachineLayout LAYOUT;

    static {
        List<MachineLayout.SlotPos> slots = new ArrayList<>();
        slots.add(new MachineLayout.SlotPos(26, 58, true));
        slots.add(new MachineLayout.SlotPos(26, 116, true));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slots.add(new MachineLayout.SlotPos(156 + column * 18, 52 + row * 18, false));
            }
        }
        slots.add(new MachineLayout.SlotPos(156, 120, true));
        slots.add(new MachineLayout.SlotPos(222, 120, false));
        LAYOUT = new MachineLayout(List.copyOf(slots));
    }

    private static final int[] ALL_SLOTS = new int[SIZE];

    static {
        for (int slot = 0; slot < SIZE; slot++) {
            ALL_SLOTS[slot] = slot;
        }
    }

    private int work;
    private int cold;
    private int state = STATE_NO_DNA;
    private int checked;
    private LivingEntity template;

    /** Ce que voit le client : la bete du clone, son grade, et si la cuve tourne. */
    private Identifier shownMob;
    private int shownGrade;
    private boolean running;
    private Entity display;
    private Identifier displayMob;

    public CryoChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(DoHyCryo.CHAMBER_TYPE.get(), pos, blockState, SIZE,
                Component.translatable("block.dohy.cryo_chamber"));
    }

    // -- Le menu -------------------------------------------------------------------------------

    @Override
    protected MenuType<MachineMenu> menuType() {
        return DoHyCryo.MENU.get();
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
        Optional<EntityType<?>> type = Dna.type(items.get(SLOT_DNA));
        return type.isPresent() ? Dna.cycle(Dna.grade(items.get(SLOT_DNA))) : 0;
    }

    @Override
    public int tankFill() {
        return cold;
    }

    @Override
    public int tankGoal() {
        return COLD_MAX;
    }

    @Override
    public int extra(int index) {
        return switch (index) {
            case 0 -> state;
            case 1 -> Dna.type(items.get(SLOT_DNA)).isPresent() ? Dna.rolls(Dna.grade(items.get(SLOT_DNA))) : 0;
            default -> 0;
        };
    }

    @Override
    public int slotLimit(int slot) {
        return slot == SLOT_DNA ? 1 : 64;
    }

    // -- Les cases -----------------------------------------------------------------------------

    /** Ce qu'une unite de chaque glace apporte de froid ; 0 si ce n'est pas un refrigerant. */
    public static int coldOf(ItemStack stack) {
        if (stack.is(Items.SNOWBALL)) {
            return 1;
        }
        if (stack.is(Items.SNOW_BLOCK)) {
            return 4;
        }
        if (stack.is(Items.ICE)) {
            return 8;
        }
        if (stack.is(Items.PACKED_ICE)) {
            return 72;
        }
        if (stack.is(Items.BLUE_ICE)) {
            return 648;
        }
        return 0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_DNA, SLOT_FUSE_IN -> Dna.type(stack).isPresent();
            case SLOT_COLD -> coldOf(stack) > 0;
            default -> false;
        };
    }

    @Override
    protected boolean hopperMayTake(int slot) {
        return slot >= FIRST_OUTPUT && slot < FIRST_OUTPUT + OUTPUTS || slot == SLOT_FUSE_OUT;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return (slot == SLOT_COLD || slot == SLOT_DNA && items.get(SLOT_DNA).isEmpty()) && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return hopperMayTake(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (slot == SLOT_DNA) {
            work = 0;
            sync();
        }
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = super.removeItem(slot, count);
        if (slot == SLOT_DNA && !removed.isEmpty()) {
            work = 0;
            sync();
        }
        return removed;
    }

    // -- Le travail ----------------------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (++checked >= 40) {
            checked = 0;
            if (!intact(server)) {
                CryoChamberBlock.dismantle(server, worldPosition, getBlockState(), true);
                server.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
                return;
            }
        }
        refill();
        int next = step(server);
        if (next != state) {
            state = next;
            sync();
        }
    }

    /** Remplit la reserve de froid depuis sa case, une glace a la fois, sans en perdre. */
    private void refill() {
        ItemStack ice = items.get(SLOT_COLD);
        int value = coldOf(ice);
        if (value > 0 && cold + value <= COLD_MAX) {
            cold += value;
            ice.shrink(1);
            setChanged();
        }
    }

    private int step(ServerLevel server) {
        ItemStack dna = items.get(SLOT_DNA);
        Optional<EntityType<?>> type = Dna.type(dna);
        if (type.isEmpty()) {
            work = 0;
            return STATE_NO_DNA;
        }
        if (!Dna.yields(server, type.get())) {
            work = 0;
            return STATE_STERILE;
        }
        if (cold < COLD_PER_CYCLE) {
            return STATE_NO_COLD;
        }
        if (!hasRoom()) {
            return STATE_FULL;
        }
        int grade = Dna.grade(dna);
        if (++work < Dna.cycle(grade)) {
            if (work % 20 == 0) {
                setChanged();
            }
            return STATE_RUNNING;
        }
        work = 0;
        cold -= COLD_PER_CYCLE;
        List<ItemStack> drops = harvest(server, type.get(), Dna.rolls(grade));
        for (ItemStack drop : drops) {
            store(drop);
        }
        server.playSound(null, worldPosition.above(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f,
                0.7f + server.getRandom().nextFloat() * 0.3f);
        setChanged();
        return STATE_RUNNING;
    }

    /** Il reste au moins une case de sortie vide : un cycle ne perd jamais son butin faute de place. */
    private boolean hasRoom() {
        for (int slot = FIRST_OUTPUT; slot < FIRST_OUTPUT + OUTPUTS; slot++) {
            if (items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Le butin de {@code rolls} tirages sur le modele de la bete, et ce qu'elle produit de son vivant. */
    private List<ItemStack> harvest(ServerLevel server, EntityType<?> type, int rolls) {
        List<ItemStack> drops = new ArrayList<>();
        LivingEntity model = template(server, type);
        if (model != null) {
            Optional<net.minecraft.resources.ResourceKey<LootTable>> key = model.getLootTable();
            if (key.isPresent()) {
                LootTable table = server.getServer().reloadableRegistries().getLootTable(key.get());
                LootParams params = new LootParams.Builder(server)
                        .withParameter(LootContextParams.THIS_ENTITY, model)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                        .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().freeze())
                        .create(LootContextParamSets.ENTITY);
                for (int roll = 0; roll < rolls; roll++) {
                    table.getRandomItems(params, drops::add);
                }
            }
        }
        for (int roll = 0; roll < rolls; roll++) {
            drops.addAll(Dna.extras(type, server.getRandom()));
        }
        return drops;
    }

    private LivingEntity template(ServerLevel server, EntityType<?> type) {
        if (template == null || template.getType() != type) {
            template = type.create(server, EntitySpawnReason.LOAD) instanceof LivingEntity living ? living : null;
        }
        return template;
    }

    /** Range une pile dans les sorties ; ce qui ne tient pas est perdu (le cycle a verifie qu'il y avait de la place). */
    private void store(ItemStack stack) {
        for (int slot = FIRST_OUTPUT; slot < FIRST_OUTPUT + OUTPUTS && !stack.isEmpty(); slot++) {
            ItemStack here = items.get(slot);
            if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, stack)
                    && here.getCount() < here.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
                here.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int slot = FIRST_OUTPUT; slot < FIRST_OUTPUT + OUTPUTS && !stack.isEmpty(); slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
            }
        }
    }

    /** Toutes les cases de la chambre sont la : sinon elle se demonte. */
    private boolean intact(ServerLevel server) {
        BlockState own = getBlockState();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockState state = server.getBlockState(worldPosition.offset(x, y, z));
                    if (!state.is(own.getBlock()) || state.getValue(CryoChamberBlock.PX) != x + 1
                            || state.getValue(CryoChamberBlock.PY) != y
                            || state.getValue(CryoChamberBlock.PZ) != z + 1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // -- La fusion -------------------------------------------------------------------------

    @Override
    public boolean pressButton(ServerPlayer player, int id) {
        if (id != BUTTON_FUSE) {
            return false;
        }
        ItemStack input = items.get(SLOT_FUSE_IN);
        Optional<EntityType<?>> type = Dna.type(input);
        if (type.isEmpty()) {
            tell(player, "Pose des fioles d'ADN dans la case de fusion.", ChatFormatting.GRAY);
            return true;
        }
        int grade = Dna.grade(input);
        if (grade >= Dna.MAX_GRADE) {
            tell(player, "Cet ADN est deja au grade " + Dna.roman(Dna.MAX_GRADE) + ".", ChatFormatting.GRAY);
            return true;
        }
        if (input.getCount() < Dna.FUSION) {
            tell(player, "Il faut " + Dna.FUSION + " fioles du meme ADN, de meme grade : tu en as "
                    + input.getCount() + ".", ChatFormatting.GRAY);
            return true;
        }
        ItemStack result = Dna.stack(type.get(), grade + 1);
        ItemStack output = items.get(SLOT_FUSE_OUT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() >= output.getMaxStackSize())) {
            tell(player, "Vide d'abord la case du resultat.", ChatFormatting.GRAY);
            return true;
        }
        input.shrink(Dna.FUSION);
        if (output.isEmpty()) {
            items.set(SLOT_FUSE_OUT, result);
        } else {
            output.grow(1);
        }
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition.above(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f,
                    1.2f);
        }
        tell(player, "Fusion reussie : ADN de grade " + Dna.roman(grade + 1) + ".", ChatFormatting.AQUA);
        return true;
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
    }

    // -- Ce que voit le client ---------------------------------------------------------------

    /** Envoie au client la bete, le grade et l'etat, quand ils changent. */
    private void sync() {
        Optional<EntityType<?>> type = Dna.type(items.get(SLOT_DNA));
        Identifier mob = type.map(BuiltInRegistries.ENTITY_TYPE::getKey).orElse(null);
        int grade = type.isPresent() ? Dna.grade(items.get(SLOT_DNA)) : 0;
        boolean on = state == STATE_RUNNING && type.isPresent();
        if (java.util.Objects.equals(mob, shownMob) && grade == shownGrade && on == running) {
            return;
        }
        shownMob = mob;
        shownGrade = grade;
        running = on;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean running() {
        return running;
    }

    public int shownGrade() {
        return shownGrade;
    }

    /** Le clone dans la cuve, cote client : un modele de la bete qui n'entre pas dans le monde. */
    public Entity display() {
        if (shownMob == null || level == null) {
            display = null;
            displayMob = null;
            return null;
        }
        if (display == null || !shownMob.equals(displayMob)) {
            displayMob = shownMob;
            display = BuiltInRegistries.ENTITY_TYPE.getOptional(shownMob)
                    .map(type -> (Entity) type.create(level, EntitySpawnReason.LOAD)).orElse(null);
        }
        return display;
    }

    /** Le cote client n'a rien a faire, sinon tenir son clone pret. */
    public void clientTick() {
        Entity entity = display();
        if (entity != null) {
            entity.tickCount++;
        }
    }

    // -- La sauvegarde ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("work", work);
        output.putInt("cold", cold);
        output.putInt("state", state);
        if (shownMob != null) {
            output.putString("mob", shownMob.toString());
        }
        output.putInt("grade", shownGrade);
        output.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work = Math.max(0, input.getIntOr("work", 0));
        cold = Math.max(0, Math.min(COLD_MAX, input.getIntOr("cold", 0)));
        state = input.getIntOr("state", STATE_NO_DNA);
        shownMob = input.getString("mob").map(Identifier::tryParse).orElse(null);
        shownGrade = input.getIntOr("grade", 0);
        running = input.getBooleanOr("running", false);
    }

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
}
