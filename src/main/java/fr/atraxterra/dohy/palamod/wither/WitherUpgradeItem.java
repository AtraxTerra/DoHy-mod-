package fr.atraxterra.dohy.palamod.wither;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Une amelioration de wither : clic droit sur un wither du pillage que tu as
 * invoque. Une seule a la fois, pour une heure.
 */
public class WitherUpgradeItem extends Item {

    private final WitherUpgrade upgrade;

    public WitherUpgradeItem(WitherUpgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    public WitherUpgrade upgrade() {
        return upgrade;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof PalaWither wither)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!WitherSummons.controls(wither, server)) {
            FactionApi.tell(server, "Ce wither n'est pas le tien.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (!upgrade.fits(wither.kind())) {
            FactionApi.tell(server, "Le wither supreme ne prend que l'amelioration visqueuse.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (wither.upgrade() != null) {
            FactionApi.tell(server, "Ce wither porte deja une amelioration (une a la fois, une heure).",
                    ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (!DefenseGate.check(server, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        wither.applyUpgrade(upgrade);
        stack.consume(1, player);
        server.level().playSound(null, wither.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                1.0f, 0.8f);
        FactionApi.tell(server, "Amelioration posee pour une heure.", ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }
}
