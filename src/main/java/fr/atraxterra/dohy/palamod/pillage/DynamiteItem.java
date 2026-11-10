package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/**
 * Une dynamite a lancer. L'arbre de faction en debloque l'usage (branche
 * Pillage : la dynamite au palier 1, la ninja au 3, la big au 7) ; chaque
 * lancer alimente le compteur du meme nom.
 *
 * <p>Un distributeur la tire aussi ({@link ProjectileItem}) : c'est ainsi
 * qu'on l'envoie dans les claims d'une base ennemie, ou rien ne se pose.
 */
public class DynamiteItem extends Item implements ProjectileItem {

    private final DynamiteKind kind;

    public DynamiteItem(DynamiteKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public DynamiteKind kind() {
        return kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!DefenseGate.check(server, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        DynamiteEntity dynamite = new DynamiteEntity(level, player, stack.copyWithCount(1));
        dynamite.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.2f, 1.0f);
        level.addFreshEntity(dynamite);
        FactionApi.stat(server, kind.key(), 1);
        // La ninja dynamite s'allume sans bruit.
        if (!kind.silent()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TNT_PRIMED,
                    SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        return new DynamiteEntity(level, position.x(), position.y(), position.z(), stack.copyWithCount(1));
    }
}
