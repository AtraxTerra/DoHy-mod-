package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.alchemist.MysticEnchant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
 * chef. L'enchantement Fake Water Protection (lot 22d, table de l'Alchimiste)
 * protege qui le porte sur une piece d'armure ; chaque seconde evitee compte
 * pour l'arbre de faction.
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
        if (protectedFrom(server, living)) {
            if (living instanceof ServerPlayer player) {
                FactionApi.stat(player, "protect:fake_water", 1);
            }
            return;
        }
        living.hurtServer(server, server.damageSources().magic(), DAMAGE);
    }

    private static boolean protectedFrom(ServerLevel level, LivingEntity living) {
        return MysticEnchant.FAKE_WATER_PROTECTION.holder(level)
                .map(holder -> EnchantmentHelper.getEnchantmentLevel(holder, living) > 0).orElse(false);
    }
}
