package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le plant d'une graine minerale : ses caracteres et son temps de pousse
 * ({@code BotanistMineralSeedBlockData} de JobsReborn).
 *
 * <p><b>Le temps qui compte</b>, releve dans {@code BotanistMineralSeedBlockData.elapse}
 * et {@code BotanistMineralSeedManager.update} :
 * <ul>
 *   <li>graine <b>hors ligne</b> : le temps reel depuis la plantation, en ticks de
 *       50 ms — il court chunk decharge et serveur arrete ;</li>
 *   <li>sinon, le temps du monde ecoule <b>pendant que le plant est charge</b> : chaque
 *       passage ajoute l'ecart depuis le precedent, et le premier passage apres un
 *       chargement repart de zero ({@code checkedAt} n'est pas sauvegarde).</li>
 * </ul>
 * Le stade vaut {@code min(3, ecoule x 3 / pousse)}, comme chez Paladium.
 */
public class MineralCropBlockEntity extends BlockEntity {

    /** Un passage par seconde : le stade est recalcule, le temps compte. */
    static final int TICK_INTERVAL = 20;

    /** Le temps de pousse est sauvegarde au plus tard toutes les minutes de passages. */
    private static final int SAVE_EVERY = 60;

    private SeedTraits traits = SeedTraits.DEFAULT;
    private long plantedAt;
    private long growth;
    private long checkedAt = -1L;
    private int sinceSave;

    public MineralCropBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBotanist.MINERAL_CROP.get(), pos, state);
    }

    public SeedTraits traits() {
        return traits;
    }

    MineralSeed seed() {
        return getBlockState().getBlock() instanceof MineralCropBlock crop ? crop.seed() : MineralSeed.AMETHYST;
    }

    /** La plantation : le compte repart de zero. */
    void plant(Level level) {
        plantedAt = System.currentTimeMillis();
        growth = 0L;
        checkedAt = level.getGameTime();
        setChanged();
    }

    /** Le temps de pousse ecoule, en ticks, selon la regle de la graine. */
    long elapsed(Level level) {
        if (plantedAt <= 0L) {
            // Pose sans passer par la graine (commande, structure) : le compte commence ici.
            plant(level);
        }
        if (traits.offline()) {
            return Math.max(0L, (System.currentTimeMillis() - plantedAt) / 50L);
        }
        long now = level.getGameTime();
        if (checkedAt >= 0L) {
            growth += Math.max(0L, now - checkedAt);
        }
        checkedAt = now;
        return growth;
    }

    /** Le stade correspondant au temps ecoule. */
    int stage(Level level) {
        long target = seed().growthTicks(traits.speed());
        return (int) Math.min(MineralSeed.MATURE, elapsed(level) * MineralSeed.MATURE / target);
    }

    void serverTick(Level level, BlockPos pos, BlockState state) {
        int stage = stage(level);
        if (stage != state.getValue(MineralCropBlock.AGE)) {
            level.setBlock(pos, state.setValue(MineralCropBlock.AGE, stage), Block.UPDATE_CLIENTS);
            setChanged();
            sinceSave = 0;
        } else if (++sinceSave >= SAVE_EVERY) {
            setChanged();
            sinceSave = 0;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        traits = input.read("traits", SeedTraits.CODEC).orElse(SeedTraits.DEFAULT);
        plantedAt = Math.max(0L, input.getLongOr("planted_at", 0L));
        growth = Math.max(0L, input.getLongOr("growth", 0L));
        checkedAt = -1L;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("traits", SeedTraits.CODEC, traits);
        output.putLong("planted_at", plantedAt);
        output.putLong("growth", growth);
    }

    /** La graine posee passe ses caracteres au plant. */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SeedTraits carried = components.get(PalaBotanist.TRAITS.get());
        traits = carried != null ? carried : SeedTraits.DEFAULT;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!SeedTraits.DEFAULT.equals(traits)) {
            components.set(PalaBotanist.TRAITS.get(), traits);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("traits");
    }
}
