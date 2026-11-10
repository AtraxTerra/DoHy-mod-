package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * La Fake Water, l'eau sulfurique de PalaMod : elle a l'air de l'eau, elle se
 * nage comme l'eau, et elle ronge ce qui s'y baigne.
 *
 * <p>Deux points de degats par seconde a ce qui vit dedans — sauf au
 * proprietaire du claim ou elle coule : la douve d'une base ne blesse pas son
 * chef. L'enchantement Fake Water Protection (lot 22d) en protegera les autres.
 */
public class FakeWaterBlock extends LiquidBlock {

    static final float DAMAGE = 2.0f;

    public FakeWaterBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean intersects) {
        super.entityInside(state, level, pos, entity, effects, intersects);
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)
                || living.tickCount % 20 != 0) {
            return;
        }
        if (living instanceof Player player && (player.isCreative() || player.isSpectator()
                || FactionApi.claimAt(server, pos).ownedBy(player.getUUID()))) {
            return;
        }
        living.hurtServer(server, server.damageSources().magic(), DAMAGE);
    }
}
