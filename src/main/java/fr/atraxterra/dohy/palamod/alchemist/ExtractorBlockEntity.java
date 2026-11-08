package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * La mecanique de l'extracteur ({@code TileEntityExtractor.func_145845_h}).
 *
 * <p>Chaque tick, s'il est en marche, une fiole dedans et une buche a seve
 * devant : le minuteur monte de 10 jusqu'a 100, et au tick suivant la fiole
 * gagne un sixieme, la buche en perd un, l'extracteur une utilisation. La
 * premiere fois, une buche entiere devient une buche entamee de 36 sixiemes ;
 * vide, elle devient du bois mort. La fiole pleine, la seve d'un autre arbre
 * ou une cible qui n'est pas une buche arretent l'extracteur.
 */
public class ExtractorBlockEntity extends BlockEntity {

    /** {@code DEFAULT_USE} : 144 sixiemes, 24 fioles. */
    public static final int DEFAULT_USES = 144;

    /** Le minuteur monte de 10 par tick et declenche a 100 : un pas toutes les 11 ticks. */
    private static final int TIMER_STEP = 10;
    private static final int TIMER_TRIGGER = 100;

    private ItemStack flask = ItemStack.EMPTY;
    private boolean modified;
    private int timer;
    private int uses = DEFAULT_USES;

    public ExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(PalaAlchemist.EXTRACTOR.get(), pos, state);
    }

    private BlockPos target() {
        return worldPosition.relative(getBlockState().getValue(ExtractorBlock.FACING));
    }

    private boolean enabled() {
        return getBlockState().getValue(ExtractorBlock.ENABLED);
    }

    private void setEnabled(boolean enabled) {
        if (level != null && enabled() != enabled) {
            level.setBlock(worldPosition, getBlockState().setValue(ExtractorBlock.ENABLED, enabled), Block.UPDATE_ALL);
        }
        if (!enabled) {
            timer = 0;
        }
    }

    /** Une fiole vide entre, et l'extracteur demarre. */
    boolean insert(Player player, InteractionHand hand, ItemStack stack) {
        if (!flask.isEmpty() || stack.has(PalaAlchemist.FLASK_SAP.get())) {
            return false;
        }
        flask = stack.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        modified = false;
        setEnabled(true);
        setChanged();
        return true;
    }

    /** La fiole ressort, avec l'XP de ce qu'elle a recu. */
    void retrieve(ServerPlayer player) {
        if (flask.isEmpty()) {
            return;
        }
        FlaskSap sap = flask.get(PalaAlchemist.FLASK_SAP.get());
        if (sap != null && modified) {
            Sap type = sap.type();
            AlchemistXp.grant(player, type.extractionXp() * sap.level() / (double) FlaskSap.FULL,
                    type.extractionLevel());
        }
        ItemStack given = flask;
        flask = ItemStack.EMPTY;
        modified = false;
        setEnabled(false);
        setChanged();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
    }

    /** Marche / arret, sur une buche a seve seulement. */
    void toggle() {
        if (level == null) {
            return;
        }
        if (PalaWoods.sapOf(level.getBlockState(target())) == null) {
            setEnabled(false);
            return;
        }
        setEnabled(!enabled());
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f);
    }

    void serverTick() {
        if (level == null) {
            return;
        }
        if (uses <= 0) {
            level.removeBlock(worldPosition, false);
            return;
        }
        if (flask.isEmpty()) {
            setEnabled(false);
            return;
        }
        if (!enabled()) {
            timer = 0;
            return;
        }
        BlockPos target = target();
        BlockState log = level.getBlockState(target);
        Sap type = PalaWoods.sapOf(log);
        FlaskSap sap = flask.get(PalaAlchemist.FLASK_SAP.get());
        // Pas de melange dans une fiole, pas de fiole qui deborde.
        if (type == null || (sap != null && (sap.type() != type || sap.isFull()))) {
            setEnabled(false);
            return;
        }
        if (timer < TIMER_TRIGGER) {
            timer += TIMER_STEP;
            return;
        }
        timer = 0;

        int left = log.getBlock() instanceof TappedLogBlock ? log.getValue(TappedLogBlock.SAP) : TappedLogBlock.MAX_SAP;
        left--;
        if (left <= 0) {
            level.setBlock(target, PalaWoods.deadWood().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                    Block.UPDATE_ALL);
        } else {
            level.setBlock(target, PalaWoods.tapped(type).setValue(TappedLogBlock.SAP, left), Block.UPDATE_ALL);
        }
        FlaskSap filled = new FlaskSap(type, sap == null ? 1 : sap.level() + 1);
        FlaskItem.setSap(flask, filled);
        modified = true;
        uses--;
        setChanged();
        if (filled.isFull() || left <= 0) {
            setEnabled(false);
        }
        if (uses <= 0) {
            // Use : il se brise, sa fiole tombe (preRemoveSideEffects).
            level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.removeBlock(worldPosition, false);
        }
    }

    public int uses() {
        return uses;
    }

    /** La fiole tombe quand le bloc disparait, casse, explose ou use. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !flask.isEmpty()) {
            Block.popResource(level, pos, flask);
            flask = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        flask = input.read("Flask", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        modified = input.getBooleanOr("Modified", false);
        timer = input.getIntOr("Timer", 0);
        uses = input.getIntOr("Uses", DEFAULT_USES);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!flask.isEmpty()) {
            output.store("Flask", ItemStack.CODEC, flask);
        }
        output.putBoolean("Modified", modified);
        output.putInt("Timer", timer);
        output.putInt("Uses", uses);
    }

    /** L'objet casse garde ses utilisations restantes ({@code use} / {@code maxUse}). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(PalaAlchemist.EXTRACTOR_USES.get(), uses);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(PalaAlchemist.EXTRACTOR_USES.get());
        uses = stored == null ? DEFAULT_USES : stored;
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Uses");
    }
}
