package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Les creatures des evenements du cinquieme lot, faites de creatures du jeu marquees : PalaMod
 * avait les siennes ({@code EntityNervousBat}, {@code EntityPricklySheep}, {@code EntityPalachat},
 * {@code EntityInvisible}...), dont le comportement est repris ici, sur le modele du jeu.
 */
@EventBusSubscriber
public final class LuckyCreatures {

    static final String NERVOUS = "dohy_nervous_bat";
    static final String PRICKLY = "dohy_prickly_sheep";
    static final String PALACHAT = "dohy_palachat";
    static final String DROPS = "drop_count";
    static final String XP = "dohy_xp_monster";
    /** La poule de « XP encore plus gratuite » : cinquante fois plus. */
    static final String XP_MORE = "dohy_xp_more";
    static final String COLLECTOR = "dohy_seed_collector";
    /** {@code EntityNervousBat} : 3 de degats au contact, 40 blocs de poursuite. */
    private static final float BAT_DAMAGE = 3.0f;
    /** {@code EntityPalachat} : un cadeau toutes les 120 000 tics. */
    private static final int PALACHAT_PERIOD = 120_000;
    /** {@code EntityInvisible} : trente secondes, puis jusqu'a 32 graines changees. */
    private static final int COLLECT_TICKS = 600;
    private static final int COLLECT_MAX = 32;
    /** Les monstres du chasseur de « XP gratuite » lachent dix fois plus d'experience. */
    private static final int XP_FACTOR = 10;

    private LuckyCreatures() {
    }

    private static boolean tagged(Entity entity, String key) {
        return entity.getPersistentData().getBooleanOr(key, false);
    }

    static void tag(Entity entity, String key) {
        entity.getPersistentData().putBoolean(key, true);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || entity.getPersistentData().isEmpty()) {
            return;
        }
        if (tagged(entity, NERVOUS) && entity instanceof Mob bat) {
            nervous(bat, level);
        } else if (tagged(entity, PRICKLY) && entity.tickCount % 10 == 0) {
            for (Player player : level.getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(0.3d))) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.hurtServer(level, level.damageSources().cactus(), 2.0f);
                }
            }
        } else if (tagged(entity, PALACHAT) && entity.tickCount % PALACHAT_PERIOD == 0 && entity.tickCount > 0) {
            ItemStack gift = level.getRandom().nextBoolean() ? new ItemStack(Items.DIRT)
                    : new ItemStack(PalaItems.PALADIUM_INGOT.get());
            entity.spawnAtLocation(level, gift, 0.5f);
        } else if (tagged(entity, COLLECTOR)) {
            collector(entity, level);
        }
    }

    /** La chauve-souris nerveuse fonce sur le joueur le plus proche et le pique. */
    private static void nervous(Mob bat, ServerLevel level) {
        Player target = level.getNearestPlayer(bat, 40.0d);
        if (target == null || target.isCreative() || target.isSpectator()) {
            return;
        }
        Vec3 toward = target.getEyePosition().subtract(0.0d, 0.4d, 0.0d).subtract(bat.position());
        bat.setDeltaMovement(bat.getDeltaMovement().scale(0.6d).add(toward.normalize().scale(0.18d)));
        bat.setYRot((float) (Math.atan2(toward.z, toward.x) * 180.0d / Math.PI) - 90.0f);
        if (toward.lengthSqr() < 1.6d && bat.tickCount % 20 == 0) {
            target.hurtServer(level, level.damageSources().mobAttack(bat), BAT_DAMAGE);
        }
    }

    /**
     * « Orange ou bleu ? » ({@code EntityInvisible}) : « Jetez vos graines ici - N secondes restantes ».
     * Au bout, jusqu'a 32 graines jetees autour deviennent des graines d'orangeblue.
     */
    private static void collector(Entity marker, ServerLevel level) {
        int left = COLLECT_TICKS - marker.tickCount;
        if (left % 20 == 0) {
            marker.setCustomName(Component.literal("Jetez vos graines ici - " + Math.max(0, left / 20)
                    + " secondes restantes").withStyle(ChatFormatting.AQUA));
        }
        if (left > 0) {
            return;
        }
        int converted = 0;
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, marker.getBoundingBox().inflate(10.0d))) {
            ItemStack stack = drop.getItem();
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (!id.contains("seeds") || converted >= COLLECT_MAX) {
                continue;
            }
            int take = Math.min(stack.getCount(), COLLECT_MAX - converted);
            converted += take;
            stack.shrink(take);
            if (stack.isEmpty()) {
                drop.discard();
            }
        }
        if (converted > 0) {
            LuckyWorld.drop(level, marker.getX(), marker.getY() + 0.5d, marker.getZ(),
                    new ItemStack(PalaItems.ORANGEBLUE_SEEDS.get(), converted));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, marker.getX(), marker.getY() + 1.0d, marker.getZ(), 20,
                    0.5d, 0.5d, 0.5d, 0.0d);
        }
        marker.discard();
    }

    /** Un marqueur invisible et immobile, qui porte un nom ({@code EntityInvisible}). */
    static ArmorStand marker(ServerLevel level, double x, double y, double z) {
        ArmorStand stand = LuckyWorld.create(level, EntityTypes.ARMOR_STAND, x, y, z);
        if (stand != null) {
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setCustomNameVisible(true);
        }
        return stand;
    }

    /** « Tire aux pigeons » : chaque poule abattue lache ses lingots de paladium ({@code drop_count}). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !entity.getPersistentData().contains(DROPS)) {
            return;
        }
        int count = entity.getPersistentData().getIntOr(DROPS, 0);
        if (count > 0) {
            LuckyWorld.drop(level, entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(PalaItems.PALADIUM_INGOT.get(), count));
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (tagged(event.getEntity(), XP)) {
            int factor = tagged(event.getEntity(), XP_MORE) ? XP_FACTOR * 5 : XP_FACTOR;
            event.setDroppedExperience(Math.max(5, event.getDroppedExperience()) * factor);
        }
    }

    /** Une creature des evenements, posee a cote du joueur. */
    static <T extends Entity> T spawn(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, Vec3 at) {
        T entity = LuckyWorld.create(level, type, at.x, at.y, at.z);
        if (entity != null) {
            level.addFreshEntity(entity);
        }
        return entity;
    }

    /** Le voisinage du joueur ou poser une creature : a deux blocs, sur une case libre. */
    static Vec3 near(Player player, double radius) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0d;
        return player.position().add(Math.cos(angle) * radius, 0.0d, Math.sin(angle) * radius);
    }

    /** Toutes les creatures marquees d'un type dans la boite (outil des tests). */
    static int count(ServerLevel level, AABB box, String key) {
        return level.getEntities((Entity) null, box, entity -> tagged(entity, key)).size();
    }
}
