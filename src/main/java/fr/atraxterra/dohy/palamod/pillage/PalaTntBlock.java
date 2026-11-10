package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Une TNT du pillage ({@code TNTEffect}) : la TNT vanilla — allumee au briquet,
 * au courant, au feu, par une fleche enflammee ou par une autre explosion —
 * qui libere une {@link PalaPrimedTnt} au lieu de la sienne.
 *
 * <p>Tous ces allumages passent, chez NeoForge, par {@link #onCaughtFire} ;
 * l'explosion voisine par {@link #wasExploded}. Les deux suffisent.
 */
public class PalaTntBlock extends TntBlock {

    private final TntKind kind;

    public PalaTntBlock(TntKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public TntKind kind() {
        return kind;
    }

    @Override
    public boolean onCaughtFire(BlockState state, Level level, BlockPos pos, Direction face, LivingEntity igniter) {
        if (!(level instanceof ServerLevel server) || !server.getGameRules().get(GameRules.TNT_EXPLODES)) {
            return false;
        }
        PalaPrimedTnt tnt = new PalaPrimedTnt(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, igniter,
                defaultBlockState());
        level.addFreshEntity(tnt);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS,
                1.0f, 1.0f);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        return true;
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) {
            return;
        }
        PalaPrimedTnt tnt = new PalaPrimedTnt(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d,
                explosion.getIndirectSourceEntity(), defaultBlockState());
        tnt.setFuse(PrimedTnt.getRandomShortFuse(tnt.getFuse(), level.getRandom()));
        level.addFreshEntity(tnt);
    }
}
