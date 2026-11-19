package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.boost.PalaXpPotions;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import fr.atraxterra.dohy.palamod.godpickaxe.GodPickaxeItem;
import fr.atraxterra.dohy.palamod.godpickaxe.GodPickaxeLevels;
import fr.atraxterra.dohy.palamod.godpickaxe.PalaGodPickaxe;
import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.palamod.hunter.Elephant;
import fr.atraxterra.dohy.palamod.hunter.PalaHunter;
import fr.atraxterra.dohy.palamod.nature.PalaNature;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Les evenements du lucky block en findium ({@code LuckyType.FINDIUM}).
 *
 * <p>Le findium a 130 evenements a lui, presque tous adosses a des objets ou des
 * creatures propres a Paladium. Ce premier lot porte ceux qui tiennent avec ce
 * que le jeu et le mod ont deja.
 */
final class FindiumEvents {

    /** La duree du double XP de « DoubleXP » ({@code setDoubleXpUntil}, 15 minutes). */
    private static final int DOUBLE_XP_TICKS = 15 * 60 * 20;

    private FindiumEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.findium("hidehead", "Ou donner de la tete", "hidehead", true, 4, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.HIDE_HEAD, 2400)));
        c.findium("dogarmy", "Doggy Army", "wolfarmy", false, 50, (player, level, pos) -> wolves(player, level, pos,
                10, true));
        c.findium("frozenground", "Sol glace", "frozenground", true, 50, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.FROZEN_GROUND, 3600)));
        c.findium("not_too_far", "C'est pas passe loin", "c_est_pas_passe_loin", true, 100, (player, level, pos) -> {
            LuckyWorld.tell(player, "Pas si vite !!", ChatFormatting.RED);
            LuckyTasks.later(player, 20, () -> {
                player.setHealth(1.0f);
                LuckyWorld.tell(player, "C'est pas passe loin...", ChatFormatting.YELLOW);
            });
        });
        c.findium("sonic", "Sonic", "sonic", true, 100, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 8));
            player.addEffect(new MobEffectInstance(PalaLucky.NO_JUMP, 1200));
        });
        c.findium("stormlighting", "Danse maintenant", "danselighting", false, 100, FindiumEvents::storm);
        c.findium("teleportyone", "20 000 lieu sous terre", "teleportyone", false, 100, FindiumEvents::deep);
        c.findium("grandmamix", "Melange de grand mere", "grandmamix", true, 100, FindiumEvents::grandmaMix);
        c.findium("blindness", "Long time no see", "blindness", true, 100, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 12000)));
        c.findium("bee", "A-Bee-me", "abeeme", true, 100, FindiumEvents::bees);
        c.findium("viewtwoperson", "Prendre du recul", "recul", true, 100, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.THIRD_PERSON, MobEffectInstance.INFINITE_DURATION)));
        c.findium("readyforeverything", "Pret a tout", "danselighting", false, 100, FindiumEvents::anvils);
        c.findium("stalactites", "Si j'etais toi je bougerais", "stalactites", true, 100, FindiumEvents::stalactites);
        c.findium("elephant", "Et ! Les phans !", "et_les_phan", false, 200, FindiumEvents::elephant);
        c.findium("notch_is_here", "Pala est la", "notch_est_la", false, 200, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaGolem.PALADIUM_APPLE.get())));
        c.findium("doublexp", "DoubleXP", "double_xp", false, 300, FindiumEvents::doubleXp);
        c.findium("wolf_ally", "L'ami des loups", "l_ami_des_loups", false, 300, (player, level, pos) ->
                wolves(player, level, pos, 20, false));
        c.findium("goodbutnottoomuch", "Bien mais pas trop", "goodbutnottoomuch", false, 300, FindiumEvents::armor);
        c.findium("tryagain", "Try Again", "tryagain", false, 400, FindiumEvents::tryAgain);
        c.findium("over_nine_thousand", "Over 9000", "over_9000", false, 1000, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaNature.COMPRESSED_XP_BERRY.get(), 64)));
        c.findium("godpickaxemax", "Pioche de Legende", "godpickaxemax", false, 1000, FindiumEvents::godPickaxe);
        c.findium("endium_block", "Endium Block", "endium_block", false, 9000, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaBlocks.ENDIUM_BLOCK.get())));
    }

    /**
     * Des loups ({@code WolfArmy} et {@code WolfAlly}), en pleine sante. L'armee
     * est apprivoisee et porte le nom « Wof wof ! » ; les allies sont sauvages.
     */
    private static void wolves(ServerPlayer player, ServerLevel level, BlockPos pos, int count, boolean tamed) {
        for (int index = 0; index < count; index++) {
            TamableAnimal wolf = LuckyWorld.create(level, EntityTypes.WOLF, pos.getX() + 0.5d, pos.getY(),
                    pos.getZ() + 0.5d);
            if (wolf == null) {
                continue;
            }
            if (tamed) {
                wolf.tame(player);
                wolf.setCustomName(Component.literal("Wof wof !").withStyle(ChatFormatting.AQUA));
                wolf.setCustomNameVisible(true);
                wolf.setPersistenceRequired();
            }
            wolf.setHealth(wolf.getMaxHealth());
            level.addFreshEntity(wolf);
        }
    }

    /**
     * Dix eclairs, un toutes les trois secondes, chacun annonce dans le chat la
     * ou il tombera ({@code StormLighting}). Le premier tombe a cote du joueur.
     * L'eclair blesse et enflamme ce qui est dessous, mais n'allume pas le sol.
     */
    private static void storm(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyWorld.tell(player, "[Lucky Block] Attention, un eclair tombera toutes les 3 secondes pendant 30 secondes.",
                ChatFormatting.GOLD);
        double[] next = {player.getX() + 1.0d, player.getZ()};
        LuckyTasks.repeat(player, 0, 60, 10, step -> {
            double y = player.getY();
            LuckyWorld.lightning(level, player, next[0], y, next[1], true);
            AABB zone = new AABB(next[0] - 1.5d, y - 1.0d, next[1] - 1.5d, next[0] + 1.5d, y + 3.0d, next[1] + 1.5d);
            for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, zone)) {
                hit.hurtServer(level, level.damageSources().lightningBolt(), 5.0f);
                hit.igniteForSeconds(8.0f);
            }
            if (step < 9) {
                next[0] = Math.round(player.getX() + level.getRandom().nextInt(8) - 4);
                next[1] = Math.round(player.getZ() + level.getRandom().nextInt(8) - 4);
                LuckyWorld.tell(player, "[Lucky Block] Un eclair tombera en x: " + (long) next[0] + " y: "
                        + Math.round(y) + " z: " + (long) next[1], ChatFormatting.RED);
            }
        });
    }

    /**
     * Vingt mille lieues sous terre ({@code TeleportYOne}) : deux blocs creuses
     * sous le joueur, tout en bas du monde, et il y est depose. PalaMod le posait
     * un bloc trop haut, la tete dans la pierre ; ici les deux blocs creuses
     * l'accueillent en entier, et rien ne se passe au-dessus du vide.
     */
    private static void deep(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int x = player.getBlockX();
        int z = player.getBlockZ();
        BlockPos feet = new BlockPos(x, level.getMinY() + 6, z);
        if (level.getBlockState(feet.below()).isAir()) {
            LuckyWorld.bar(player, "Rien a creuser sous tes pieds", ChatFormatting.GRAY);
            return;
        }
        for (BlockPos at : List.of(feet, feet.above())) {
            BlockState state = level.getBlockState(at);
            if (state.isAir() || !LuckyWorld.canEdit(player, level, at)) {
                continue;
            }
            Item item = state.getBlock().asItem();
            level.removeBlock(at, false);
            if (item != net.minecraft.world.item.Items.AIR) {
                LuckyWorld.drop(level, at, new ItemStack(item));
            }
        }
        if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
            player.teleportTo(x + 0.5d, feet.getY(), z + 0.5d);
        }
    }

    /** Les douze potions de Bukkit ({@code PotionType}) d'un coup, une minute chacune. */
    private static void grandmaMix(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<Holder<MobEffect>> effects = List.of(MobEffects.REGENERATION, MobEffects.SPEED,
                MobEffects.FIRE_RESISTANCE, MobEffects.POISON, MobEffects.INSTANT_HEALTH, MobEffects.NIGHT_VISION,
                MobEffects.WEAKNESS, MobEffects.STRENGTH, MobEffects.SLOWNESS, MobEffects.INSTANT_DAMAGE,
                MobEffects.WATER_BREATHING, MobEffects.INVISIBILITY);
        for (Holder<MobEffect> effect : effects) {
            player.addEffect(new MobEffectInstance(effect, 1200, 0));
        }
    }

    /** Seize abeilles en colere contre le joueur. */
    private static void bees(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 16; index++) {
            Mob bee = LuckyWorld.create(level, EntityTypes.BEE, pos.getX() + 0.5d, pos.getY() + 1.0d,
                    pos.getZ() + 0.5d);
            if (bee == null) {
                continue;
            }
            level.addFreshEntity(bee);
            bee.setLastHurtByMob(player);
            if (bee instanceof NeutralMob angry) {
                angry.setPersistentAngerTarget(EntityReference.of(player));
                angry.startPersistentAngerTimer();
            }
            bee.setTarget(player);
        }
    }

    /**
     * Pret a tout ({@code ReadyForEverything}) : de sept a quinze enclumes tombent
     * pres du joueur, une toutes les 1,6 seconde, a dix blocs de haut. Chaque
     * enclume recue rapporte 1 000 $ (et ses degats).
     */
    private static void anvils(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int count = 7 + level.getRandom().nextInt(9);
        LuckyWorld.tell(player, "[Lucky Block] Attention, une pluie d'enclumes a dollars arrive : 1 000 $ par enclume"
                + " recue.", ChatFormatting.GOLD);
        player.addEffect(new MobEffectInstance(PalaLucky.ANVIL_MONEY, 32 * count + 100));
        double[] next = {player.getX() + 1.0d, player.getZ()};
        LuckyTasks.repeat(player, 0, 32, count, step -> {
            BlockPos at = BlockPos.containing(next[0], player.getY() + 10.0d, next[1]);
            if (level.getBlockState(at).isAir()) {
                FallingBlockEntity anvil = FallingBlockEntity.fall(level, at, Blocks.DAMAGED_ANVIL.defaultBlockState());
                anvil.dropItem = false;
                anvil.setHurtsEntities(2.0f, 40);
            }
            next[0] = Math.round(player.getX() + level.getRandom().nextInt(8) - 4);
            next[1] = Math.round(player.getZ() + level.getRandom().nextInt(8) - 4);
        });
    }

    /** Une pluie de stalactites sur le joueur : une toutes les trois secondes, pendant trente. */
    private static void stalactites(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyWorld.tell(player, "[Lucky Block] Attention, une pluie de stalactites va tomber sur toi pendant 30"
                + " secondes.", ChatFormatting.GOLD);
        BlockState tip = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
        LuckyTasks.repeat(player, 20, 60, 10, step -> {
            BlockPos at = BlockPos.containing(player.getX(), player.getY() + 10.0d, player.getZ());
            while (at.getY() > player.getBlockY() + 2 && !level.getBlockState(at).isAir()) {
                at = at.below();
            }
            if (level.getBlockState(at).isAir()) {
                FallingBlockEntity falling = FallingBlockEntity.fall(level, at, tip);
                falling.dropItem = false;
                falling.setHurtsEntities(6.0f, 40);
                LuckyWorld.particles(level, ParticleTypes.FALLING_DRIPSTONE_WATER, at.getX() + 0.5d, at.getY(),
                        at.getZ() + 0.5d, 6, 0.2d, 0.0d);
            }
        });
    }

    /** Un elephant, deja apprivoise par le joueur. */
    private static void elephant(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Elephant elephant = LuckyWorld.create(level, PalaHunter.ELEPHANT.get(), pos.getX() + 0.5d, pos.getY(),
                pos.getZ() + 0.5d);
        if (elephant == null) {
            return;
        }
        level.addFreshEntity(elephant);
        elephant.tame(player);
    }

    /** L'XP des metiers doublee quinze minutes ; la duree s'ajoute a une potion deja bue. */
    private static void doubleXp(ServerPlayer player, ServerLevel level, BlockPos pos) {
        MobEffectInstance current = player.getEffect(PalaXpPotions.DOUBLE_JOB_XP);
        int ticks = DOUBLE_XP_TICKS + (current == null || current.isInfiniteDuration() ? 0 : current.getDuration());
        player.addEffect(new MobEffectInstance(PalaXpPotions.DOUBLE_JOB_XP, ticks, 0, false, false, true));
        LuckyWorld.tell(player, "L'XP de tes metiers est doublee pendant 15 minutes !", ChatFormatting.GREEN);
        LuckyWorld.tell(player, "Profites-en !", ChatFormatting.YELLOW);
    }

    /** L'armure en paladium, Protection IV et Solidite III, a un point de solidite. */
    private static void armor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (Item piece : List.of(PalaEquipment.PALADIUM_HELMET.get(), PalaEquipment.PALADIUM_CHESTPLATE.get(),
                PalaEquipment.PALADIUM_LEGGINGS.get(), PalaEquipment.PALADIUM_BOOTS.get())) {
            ItemStack stack = new ItemStack(piece);
            enchantments.get(Enchantments.PROTECTION).ifPresent(holder -> stack.enchant(holder, 4));
            enchantments.get(Enchantments.UNBREAKING).ifPresent(holder -> stack.enchant(holder, 3));
            if (stack.isDamageableItem()) {
                stack.setDamageValue(stack.getMaxDamage() - 1);
            }
            LuckyWorld.drop(level, pos, stack);
        }
    }

    /** Trois lucky blocks d'un meme type : paladium une fois sur deux, sinon endium ou findium. */
    private static void tryAgain(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyType type = switch (level.getRandom().nextInt(4)) {
            case 2 -> LuckyType.ENDIUM;
            case 3 -> LuckyType.FINDIUM;
            default -> LuckyType.PALADIUM;
        };
        LuckyWorld.drop(level, pos, type.stack(3));
    }

    /** La pioche des dieux, deja au niveau 99. */
    private static void godPickaxe(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack pickaxe = new ItemStack(PalaGodPickaxe.GOD_PICKAXE.get());
        GodPickaxeItem.addXp(pickaxe, GodPickaxeLevels.xpFor(99) + 1.0d, level.registryAccess());
        LuckyWorld.drop(level, pos, pickaxe);
    }
}
