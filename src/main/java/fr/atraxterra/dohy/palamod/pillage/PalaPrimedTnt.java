package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Une TNT du pillage allumee ({@code EntityTNTEffectPrimed}).
 *
 * <p>La TNT vanilla, dont l'explosion est remplacee par celle de son genre.
 * Le genre se lit dans l'etat de bloc que la TNT allumee porte deja pour son
 * rendu (synchronise et sauvegarde par {@link PrimedTnt}) : un seul type
 * d'entite pour les cinq TNT, et le rendu vanilla dessine la bonne texture.
 *
 * <p><b>L'auteur.</b> Le constructeur de {@link PrimedTnt} qui fixe l'auteur
 * impose le type vanilla ; celui-ci garde le sien, et {@link #getOwner()} le
 * rend. C'est par lui que l'explosion remonte au joueur (credit des degats,
 * protection des claims).
 */
public class PalaPrimedTnt extends PrimedTnt {

    private EntityReference<LivingEntity> igniter;

    public PalaPrimedTnt(EntityType<? extends PalaPrimedTnt> type, Level level) {
        super(type, level);
    }

    public PalaPrimedTnt(Level level, double x, double y, double z, LivingEntity igniter, BlockState state) {
        this(PalaPillage.PRIMED_TNT.get(), level);
        setPos(x, y, z);
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0d;
        setDeltaMovement(-Math.sin(angle) * 0.02d, 0.2d, -Math.cos(angle) * 0.02d);
        setFuse(DEFAULT_FUSE_TIME);
        xo = x;
        yo = y;
        zo = z;
        this.igniter = igniter == null ? null : EntityReference.of(igniter);
        setBlockState(state);
    }

    public TntKind kind() {
        return getBlockState().getBlock() instanceof PalaTntBlock block ? block.kind() : TntKind.BIG;
    }

    @Override
    public LivingEntity getOwner() {
        return EntityReference.getLivingEntity(igniter, level());
    }

    @Override
    protected void explode() {
        if (level() instanceof ServerLevel server) {
            kind().detonate(server, this);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(igniter, output, "igniter");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        igniter = EntityReference.read(input, "igniter");
    }
}
