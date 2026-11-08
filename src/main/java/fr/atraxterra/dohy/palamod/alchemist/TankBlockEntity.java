package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le contenu d'un tank ({@code TileEntityTank}) : une seve et sa quantite.
 *
 * <p>Releve dans {@code BlockTank} : une fiole versee apporte
 * {@code (int)(meta / 6 * 100) * 10} unites, et seulement si elle tient en
 * entier ; un sixieme de fiole en coute autant que la difference de deux
 * remplissages successifs (160 ou 170), pour toute la pile d'un coup.
 */
public class TankBlockEntity extends BlockEntity {

    private TankSap content;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(PalaAlchemist.TANK.get(), pos, state);
    }

    public TankSap content() {
        return content;
    }

    private int capacity() {
        return getBlockState().getBlock() instanceof TankBlock tank ? tank.tier().capacity() : 0;
    }

    private void set(TankSap sap) {
        content = sap == null || sap.amount() <= 0 ? null : sap;
        setChanged();
        if (level != null && getBlockState().getBlock() instanceof TankBlock tank) {
            BlockState shown = tank.withContent(getBlockState(), content);
            if (shown != getBlockState()) {
                level.setBlock(worldPosition, shown, Block.UPDATE_ALL);
            }
        }
    }

    /** Vide dans le tank les fioles de seve tenues en main. */
    void pourFlasks(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        FlaskSap flask = stack.get(PalaAlchemist.FLASK_SAP.get());
        if (flask == null) {
            return;
        }
        if (content != null && content.type() != flask.type()) {
            AlchemistXp.actionBar(player, Component.translatable("message.palamod.tank.other_sap")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int current = content == null ? 0 : content.amount();
        int poured = Math.min(stack.getCount(), (capacity() - current) / flask.units());
        if (poured <= 0) {
            AlchemistXp.actionBar(player, Component.translatable("message.palamod.tank.full")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        set(new TankSap(flask.type(), current + poured * flask.units()));
        FlaskItem.replace(player, hand, stack, poured, new ItemStack(PalaAlchemist.FLASK.get(), poured));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        AlchemistXp.actionBar(player, describe());
    }

    /** Ajoute un sixieme de seve a chaque fiole de la pile tenue en main. */
    void fillFlasks(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (content == null) {
            AlchemistXp.actionBar(player, describe());
            return;
        }
        FlaskSap flask = stack.get(PalaAlchemist.FLASK_SAP.get());
        int from = flask == null ? 0 : flask.level();
        if (flask != null && flask.type() != content.type()) {
            AlchemistXp.actionBar(player, Component.translatable("message.palamod.tank.other_sap")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (from >= FlaskSap.FULL) {
            return;
        }
        int cost = (Sap.units(from + 1) - Sap.units(from)) * stack.getCount();
        if (content.amount() < cost) {
            AlchemistXp.actionBar(player, Component.translatable("message.palamod.tank.not_enough", cost)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Sap type = content.type();
        set(new TankSap(type, content.amount() - cost));
        ItemStack filled = new ItemStack(PalaAlchemist.FLASK.get(), stack.getCount());
        FlaskItem.setSap(filled, new FlaskSap(type, from + 1));
        player.setItemInHand(hand, filled);
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        AlchemistXp.actionBar(player, describe());
    }

    /** {@code « level / max »} et le nom du liquide, ou « Vide ». */
    Component describe() {
        if (content == null) {
            return Component.translatable("message.palamod.tank.empty", capacity()).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("message.palamod.tank.content", content.type().displayName(),
                content.amount(), capacity()).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        content = input.read("Sap", TankSap.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (content != null) {
            output.store("Sap", TankSap.CODEC, content);
        }
    }

    /** Le tank casse rend un objet qui porte son contenu (fonction {@code copy_components}). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (content != null) {
            components.set(PalaAlchemist.TANK_SAP.get(), content);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        content = components.get(PalaAlchemist.TANK_SAP.get());
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Sap");
    }
}
