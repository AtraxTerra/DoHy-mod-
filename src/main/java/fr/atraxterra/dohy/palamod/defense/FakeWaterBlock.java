package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.alchemist.MysticEnchant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La Fake Water, l'eau sulfurique de PalaMod : elle a l'air de l'eau, elle se
 * nage comme l'eau, et elle ronge ce qui s'y baigne.
 *
 * <p>Deux points de degats par seconde a ce qui vit dedans — sauf au
 * proprietaire du claim ou elle coule : la douve d'une base ne blesse pas son
 * chef. L'enchantement Fake Water Protection (lot 22d, table de l'Alchimiste)
 * fait encaisser le casque a la place du joueur, 66 points d'usure par
 * seconde ; chaque seconde evitee compte pour l'arbre de faction.
 */
public class FakeWaterBlock extends LiquidBlock {

    static final float DAMAGE = 2.0f;

    /** Usure du casque Fake Water Protection par seconde passee dedans. */
    static final int HELMET_WEAR = 66;

    /** Le dernier tic compte pour chaque joueur protege. */
    private static final Map<UUID, Integer> LAST_COUNTED = new HashMap<>();

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
        if (protectedFrom(living)) {
            // Une seconde par seconde : un joueur dans plusieurs blocs a la fois
            // passe ici une fois par bloc touche.
            if (!(living instanceof ServerPlayer player)) {
                wearHelmet(living);
            } else if (!Integer.valueOf(player.tickCount).equals(LAST_COUNTED.put(player.getUUID(), player.tickCount))) {
                if (LAST_COUNTED.size() > 256) {
                    LAST_COUNTED.clear();
                }
                wearHelmet(player);
                FactionApi.stat(player, "protect:fake_water", 1);
            }
            return;
        }
        living.hurtServer(server, server.damageSources().magic(), DAMAGE);
    }

    /**
     * Le casque enchante encaisse a la place du joueur ({@code BlockSulfuricWater}) :
     * il faut un casque Fake Water Protection qui s'use encore. Les autres pieces
     * d'armure n'y font rien, comme dans l'original.
     */
    private static boolean protectedFrom(LivingEntity living) {
        ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
        return !helmet.isEmpty() && helmet.isDamageableItem()
                && MysticEnchant.FAKE_WATER_PROTECTION.levelOn(helmet) > 0;
    }

    /** 66 points d'usure par seconde, le prix de la protection. */
    private static void wearHelmet(LivingEntity living) {
        living.getItemBySlot(EquipmentSlot.HEAD).hurtAndBreak(HELMET_WEAR, living, EquipmentSlot.HEAD);
    }
}
