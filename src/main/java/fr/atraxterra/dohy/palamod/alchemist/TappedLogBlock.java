package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Buche entamee ({@code BlockWood} + {@code TileEntityWood}) : ce que devient
 * une buche des que l'extracteur y puise. Elle garde 36 sixiemes de seve, six
 * fioles, et <b>ne se regenere jamais</b> ({@code canUpdate() = false}). Le bois
 * mort monte a mesure qu'elle se vide ; cassee, elle donne du bois mort et sa
 * seve est perdue.
 *
 * <p>Un bloc par arbre plutot qu'une block entity typee : le type et le niveau
 * tiennent dans l'etat, et le modele se lit directement.
 */
public class TappedLogBlock extends Block {

    public static final int MAX_SAP = 36;

    public static final IntegerProperty SAP = IntegerProperty.create("sap", 1, MAX_SAP);

    public static final MapCodec<TappedLogBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Sap.CODEC.fieldOf("type").forGetter(TappedLogBlock::type),
            propertiesCodec()
    ).apply(instance, TappedLogBlock::new));

    private final Sap type;

    public TappedLogBlock(Sap type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(SAP, MAX_SAP));
    }

    public Sap type() {
        return type;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SAP);
    }
}
