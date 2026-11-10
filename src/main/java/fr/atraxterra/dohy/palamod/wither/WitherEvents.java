package fr.atraxterra.dohy.palamod.wither;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Le liant des withers : les compteurs de l'arbre (obsidiennes cassees, baby
 * wither tue), la fleche de mi-vie, les tetes bleues de l'assoiffe, les tetes
 * affaiblies du baby, l'amelioration visqueuse, les points de vie du
 * receptacle et la poudre de wither des sorcieres.
 */
@EventBusSubscriber
public final class WitherEvents {

    /** Les blocs a base de slime, que le wither visqueux mange. */
    public static final TagKey<Block> SLIME_BLOCKS = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "wither_slime_blocks"));
    /** La poudre de wither : une sorciere sur deux, des le niveau 20 de Chasseur. */
    static final float DUST_CHANCE = 0.5f;
    static final int DUST_HUNTER_LEVEL = 20;

    private static final Map<Supplier<? extends Item>, String> TOOLTIPS = Map.ofEntries(
            Map.entry(PalaWithers.WITHER_GEM, "wither_gem"),
            Map.entry(PalaWithers.FUNNY_WITHER_GEM, "funny_wither_gem"),
            Map.entry(PalaWithers.BABY_WITHER_GEM, "baby_wither_gem"),
            Map.entry(PalaWithers.PASSIVE_WITHER_GEM, "passive_wither_gem"),
            Map.entry(PalaWithers.THIRSTY_WITHER_GEM, "thirsty_wither_gem"),
            Map.entry(PalaWithers.PREDATOR_WITHER_GEM, "predator_wither_gem"),
            Map.entry(PalaWithers.SUPREME_WITHER_GEM, "supreme_wither_gem"),
            Map.entry(PalaWithers.STEALTH_UPGRADE, "stealth_upgrade"),
            Map.entry(PalaWithers.WITHER_RESISTANCE_UPGRADE, "wither_resistance_upgrade"),
            Map.entry(PalaWithers.WITHER_SLIME_UPGRADE, "wither_slime_upgrade"),
            Map.entry(PalaWithers.WITHER_TARGET_LASER, "wither_target_laser"),
            Map.entry(PalaWithers.WITHER_SADDLE, "wither_saddle"),
            Map.entry(PalaWithers.WITHER_FISHING_ROD, "wither_fishing_rod"),
            Map.entry(PalaWithers.MID_LIFE_ARROW, "mid_life_arrow"),
            Map.entry(PalaWithers.WITHER_DUST, "wither_dust"));

    private WitherEvents() {
    }

    /** Un wither qui casse un bloc : le compteur d'obsidiennes, le repas du visqueux. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(LivingDestroyBlockEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof WitherBoss wither)) {
            return;
        }
        if (event.getState().is(PalaPillage.ENDIUM_TNT_BREAKABLE)) {
            ServerPlayer summoner = WitherSummons.summoner(wither);
            if (summoner != null) {
                FactionApi.stat(summoner, "wither:obsidian", 1);
            }
        }
        if (wither instanceof PalaWither pala && pala.upgrade() == WitherUpgrade.SLIME
                && event.getState().is(SLIME_BLOCKS)) {
            pala.heal(WitherUpgrade.SLIME_HEAL);
        }
    }

    /**
     * Une explosion : les points de vie des receptacles proches, et les
     * obsidiennes soufflees par les tetes d'un wither. Apres les claims, qui
     * retirent ce qui ne doit pas sauter.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity direct = event.getExplosion().getDirectSourceEntity();
        // La charge d'un wither qui nait ne blesse pas le receptacle qui l'a invoque.
        if (!(direct instanceof WitherBoss)) {
            Vec3 center = event.getExplosion().center();
            float radius = event.getExplosion().radius();
            for (WitherReceptacleBlockEntity receptacle : WitherReceptacleBlockEntity.LOADED) {
                if (receptacle.getLevel() == level && !receptacle.isRemoved()) {
                    receptacle.blast(level, Vec3.atCenterOf(receptacle.getBlockPos()).distanceTo(center), radius);
                }
            }
        }
        if (event.getExplosion().getIndirectSourceEntity() instanceof WitherBoss wither) {
            ServerPlayer summoner = WitherSummons.summoner(wither);
            if (summoner != null) {
                int obsidian = 0;
                for (BlockPos pos : event.getAffectedBlocks()) {
                    if (level.getBlockState(pos).is(PalaPillage.ENDIUM_TNT_BREAKABLE)) {
                        obsidian++;
                    }
                }
                if (obsidian > 0) {
                    FactionApi.stat(summoner, "wither:obsidian", obsidian);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }
        if (event.getEntity() instanceof PalaWither wither && wither.kind() == WitherKind.BABY) {
            FactionApi.stat(player, "kill:baby_wither", 1);
        }
        if (event.getEntity() instanceof Witch witch && witch.level() instanceof ServerLevel level
                && JobApi.meets(player, Job.HUNTER, DUST_HUNTER_LEVEL)
                && level.getRandom().nextFloat() < DUST_CHANCE) {
            level.addFreshEntity(new ItemEntity(level, witch.getX(), witch.getY(), witch.getZ(),
                    new ItemStack(PalaWithers.WITHER_DUST.get())));
        }
    }

    /** La fleche de mi-vie, tiree a l'arc en paladium : la moitie de la vie du wither. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !arrow.entityTags().contains(MidLifeArrowItem.TAG)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof WitherBoss wither)) {
            return;
        }
        arrow.removeTag(MidLifeArrowItem.TAG);
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || !weapon.is(PalaPillage.PALADIUM_BOW.get())
                || wither instanceof PalaWither pala && !pala.kind().halvable()) {
            return;
        }
        wither.setHealth(Math.max(1.0f, wither.getHealth() / 2.0f));
        if (wither.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, wither.getX(), wither.getY() + 2.0d, wither.getZ(),
                    20, 0.6d, 0.8d, 0.6d, 0.2d);
        }
    }

    /** L'assoiffe ne lance que des tetes bleues, celles qui percent l'obsidienne. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getEntity() instanceof WitherSkull skull
                && skull.getOwner() instanceof PalaWither wither && wither.kind() == WitherKind.THIRSTY) {
            skull.setDangerous(true);
        }
    }

    /** Les tetes du baby frappent deux fois moins fort. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof WitherSkull skull
                && skull.getOwner() instanceof PalaWither wither && wither.kind() == WitherKind.BABY) {
            event.setAmount(event.getAmount() * 0.5f);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        for (Map.Entry<Supplier<? extends Item>, String> entry : TOOLTIPS.entrySet()) {
            if (stack.is(entry.getKey().get())) {
                event.getToolTip().add(Component.translatable("tooltip.palamod." + entry.getValue())
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
        }
        if (stack.is(PalaWithers.WITHER_RECEPTACLE.get().asItem())) {
            event.getToolTip().add(Component.translatable("tooltip.palamod.wither_receptacle")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.getItem() instanceof WitherGemItem gem) {
            event.getToolTip().add(Component.translatable("tooltip.palamod.wither_gem.time",
                    WitherReceptacleBlock.duration(gem.kind().summonTicks())).withStyle(ChatFormatting.DARK_GRAY));
        }
        BlockPos target = stack.get(PalaWithers.LASER_TARGET.get());
        if (target != null) {
            event.getToolTip().add(Component.translatable("tooltip.palamod.wither_target_laser.target",
                    target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.AQUA));
        }
    }
}
