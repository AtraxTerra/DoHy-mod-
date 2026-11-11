package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Le butin d'une creature de controleur ({@code ASpawnerEntityData.getDrops}).
 *
 * <p>PalaSpawner recopiait a la main le butin 1.7.10 de ses six creatures. Ici
 * c'est la <b>table de butin vanilla</b> de la creature qui est tiree, sur un
 * modele de la creature qui n'entre jamais dans le monde : le butin suit les
 * packs de donnees, et l'enchantement Butin de l'arme du joueur compte en mode
 * manuel, comme dans l'original.
 *
 * <p><b>Ecart</b> : sans joueur (mode automatique), les tables vanilla ne
 * donnent pas le butin rare (lingot de fer, carotte, pomme de terre du
 * zombie), qu'elles reservent a une mort causee par un joueur ; l'original en
 * donnait dans 2,5 % des cas.
 */
final class SpawnerLoot {

    private SpawnerLoot() {
    }

    /**
     * Le butin de {@code amount} creatures, piles regroupees.
     *
     * @param looting ameliorations de butin : dix chances sur cent chacune de
     *                tirer le butin d'une creature une seconde fois
     */
    static List<ItemStack> roll(ServerLevel level, BlockPos origin, LivingEntity template, ServerPlayer player,
                                int amount, int looting) {
        List<ItemStack> drops = new ArrayList<>();
        Optional<ResourceKey<LootTable>> key = template.getLootTable();
        if (key.isEmpty() || amount <= 0) {
            return drops;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key.get());
        DamageSource source = player != null ? level.damageSources().playerAttack(player)
                : level.damageSources().generic();
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, template)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
                .withParameter(LootContextParams.DAMAGE_SOURCE, source);
        if (player != null) {
            builder.withParameter(LootContextParams.ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withLuck(player.getLuck());
        }
        LootParams params = builder.create(LootContextParamSets.ENTITY);
        for (int index = 0; index < amount; index++) {
            table.getRandomItems(params, stack -> add(drops, stack));
            if (looting > 0 && level.getRandom().nextInt(100) < looting * 10) {
                table.getRandomItems(params, stack -> add(drops, stack));
            }
        }
        return drops;
    }

    /** Ajoute a la pile du meme objet, sans borne : le depot la redecoupe. */
    private static void add(List<ItemStack> drops, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemStack existing : drops) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                return;
            }
        }
        drops.add(stack.copy());
    }
}
