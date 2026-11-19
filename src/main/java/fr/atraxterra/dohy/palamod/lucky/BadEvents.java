package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.pillage.DynamiteEntity;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import fr.atraxterra.dohy.palamod.pillage.PalaPrimedTnt;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.List;

/**
 * Les malus des lucky blocks classiques (paladium seulement).
 *
 * <p>Chacun reprend le {@code perform} de sa classe de PalaMod. Les explosions
 * n'ont pas d'auteur joueur ({@link LuckyWorld}) : elles blessent, mais ne
 * cassent rien dans un claim.
 */
final class BadEvents {

    private static final Identifier ZOMBIE_HERO = Identifier.fromNamespaceAndPath("palamod", "zombie_hero");

    private BadEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.classic("geyser", "Geyser", "geyser", true, 20, BadEvents::geyser);
        c.classic("star_fish", "StarFish", "starfish", true, 20, BadEvents::starFish);
        c.classic("confined", "Enferme", "enferme", true, 20, BadEvents::confined);
        c.classic("big_dyna", "BOOM", "big_dyna", true, 20, (player, level, pos) ->
                dynamite(level, player, new ItemStack(PalaPillage.BIG_DYNAMITE.get()), 10));
        c.classic("la_haut", "La-Haut", "la-haut", true, 20, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 4)));
        c.classic("piggy_rodeo", "Piggy Rodeo", "piggy_rodeo", true, 30, BadEvents::piggyRodeo);
        c.classic("fake_powered_creeper", "Pssssssss", "pssssssss", true, 30, BadEvents::fakePoweredCreeper);
        c.classic("trap", "C'est un traquenard !", "traquenard", true, 30, BadEvents::trap);
        c.classic("boom", "Boom", "boom", true, 30, (player, level, pos) ->
                dynamite(level, player, new ItemStack(PalaPillage.DYNAMITE.get()), 40));
        c.classic("put_fire", "Allumer le feu", "put_fire", true, 40, BadEvents::putFire);
        c.classic("zombie_hero", "Zombie Hero", "zombie_hero", true, 40, BadEvents::zombieHero);
        c.classic("instant_break_up", "Instant break up", "instant_break_up", true, 40, BadEvents::instantBreakUp);
        c.classic("clear_xp", "0 + 0 = La tete a Toto", "clearxp", true, 40, (player, level, pos) -> {
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.totalExperience = 0;
            LuckyWorld.bar(player, "Ton exp tu perdras dans l'instant qui suivra", ChatFormatting.RED);
        });
        c.classic("on_the_moon", "Sur la Lune", "sur_la_lune", true, 40, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 6000, 9)));
        c.classic("gros_relou", "Bunny Life", "bunny_life", true, 50, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.BUNNY_LIFE, 6000)));
        c.classic("fire_man", "Tout feu tout flamme", "tout_feu_tout_flamme", true, 50, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(PalaLucky.FIRE_MAN, MobEffectInstance.INFINITE_DURATION));
            LuckyWorld.bar(player, "Hey ! Attention tu chauffes", ChatFormatting.RED);
        });
        c.classic("dont_break", "Ne pas casser", "ne_pas_casser", true, 50, BadEvents::dontBreak);
        c.classic("megaboom", "MEGABOOM", "megaboom", true, 70, BadEvents::megaBoom);
        c.classic("arachno_trap", "ArachnoTrap", "arachnotrap", true, 80, BadEvents::arachnoTrap);
        c.classic("obsi_trap", "ObsiTrap", "obsitrap", true, 80, BadEvents::obsiTrap);
        c.classic("badaboum", "Badaboum", "badaboum", true, 100, (player, level, pos) ->
                LuckyWorld.tnt(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 80));
        c.classic("nausea", "High attitude", "high_attitude", true, 100, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 2400, 1)));
        c.classic("one_shot", "One Shot", "one_shot", true, 100, BadEvents::oneShot);
        c.classic("remove_money", "Et paf plus de sous", "remove_money", true, 5000, BadEvents::removeMoney);
        c.classic("seum", "Le seum...", "seum", true, 10000, BadEvents::seum);
        c.classic("enderchest", "Le malus le plus rare...", "enderchest", true, 30000, BadEvents::enderChest);
    }

    /** Une dynamite allumee aux pieds du joueur ({@code DynamiteEntity}, sans lanceur). */
    private static void dynamite(ServerLevel level, ServerPlayer player, ItemStack kind, int fuse) {
        DynamiteEntity dynamite = new DynamiteEntity(level, player.getX(), player.getY() + 0.5d, player.getZ(), kind);
        level.addFreshEntity(dynamite.fuse(fuse));
    }

    /**
     * Le geyser souleve le joueur de vingt blocs en une demi-seconde, au-dessus du
     * bloc, tant que l'air le permet. La retombee fait le reste.
     */
    private static void geyser(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyTasks.repeat(player, 0, 1, 10, step -> {
            BlockPos at = pos.above((step + 1) * 2);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                player.teleportTo(at.getX() + 0.5d, at.getY(), at.getZ() + 0.5d);
                LuckyWorld.particles(level, ParticleTypes.SPLASH, at.getX() + 0.5d, at.getY(), at.getZ() + 0.5d,
                        50, 0.4d, 0.0d);
            }
        });
    }

    /** Un poisson d'argent nomme StarFish, Force X et Vitesse V. */
    private static void starFish(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob fish = LuckyWorld.create(level, EntityTypes.SILVERFISH, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (fish == null) {
            return;
        }
        fish.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 50000, 9));
        fish.addEffect(new MobEffectInstance(MobEffects.SPEED, 50000, 4));
        fish.setCustomName(Component.literal("StarFish"));
        level.addFreshEntity(fish);
    }

    /**
     * Une cage de verre de cinq blocs de haut autour du joueur, ouverte en haut,
     * et une source au-dessus. PalaMod y versait de l'eau sulfurique ; ici, de
     * l'eau : le malus est la cage.
     */
    private static void confined(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos feet = player.blockPosition();
        BlockPos center = feet.above(2);
        LuckyWorld.box(player, level, center, 5, 5, 5, Blocks.GLASS.defaultBlockState(), null);
        LuckyWorld.box(player, level, center, 3, 5, 3, Blocks.AIR.defaultBlockState(), null);
        LuckyWorld.place(player, level, feet.above(5), Blocks.WATER.defaultBlockState());
    }

    /**
     * Un cochon, le joueur dessus et impossible de descendre ; dix secondes plus
     * tard, le cochon explose (puissance 15). Un cochon tue avant n'explose pas.
     */
    private static void piggyRodeo(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob pig = LuckyWorld.create(level, EntityTypes.PIG, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (pig == null) {
            return;
        }
        pig.setPersistenceRequired();
        level.addFreshEntity(pig);
        player.startRiding(pig);
        LuckyTasks.repeat(player, 1, 1, 200, step -> {
            if (!pig.isAlive()) {
                return;
            }
            if (step < 199) {
                if (player.getVehicle() != pig) {
                    player.startRiding(pig);
                }
                return;
            }
            level.explode(pig, pig.getX(), pig.getY(), pig.getZ(), 15.0f, Level.ExplosionInteraction.TNT);
            pig.discard();
        });
    }

    /**
     * Dix creepers charges qui gonflent et sautent... sans rien casser ni
     * blesser ({@code EntityFakePoweredCreeper}). Un sur vingt s'appelle Bobby.
     */
    private static void fakePoweredCreeper(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag powered = new CompoundTag();
        powered.putBoolean("powered", true);
        for (int index = 0; index < 10; index++) {
            Creeper creeper = LuckyWorld.create(level, EntityTypes.CREEPER, pos.getX() + 0.5d, pos.getY() + 3.0d,
                    pos.getZ() + 0.5d);
            if (creeper == null) {
                continue;
            }
            float yaw = creeper.getYRot();
            creeper.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), powered.copy()));
            creeper.snapTo(pos.getX() + 0.5d, pos.getY() + 3.0d, pos.getZ() + 0.5d, yaw, 0.0f);
            creeper.addTag(LuckyWorldEvents.FAKE_CREEPER_TAG);
            if (level.getRandom().nextInt(20) == 1) {
                creeper.setCustomName(Component.literal("Bobby le creeper"));
            }
            level.addFreshEntity(creeper);
        }
    }

    /** Un coffre vide : l'ouvrir allume une TNT a sa place. */
    private static void trap(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (LuckyWorld.place(player, level, pos, Blocks.CHEST.defaultBlockState())) {
            LuckyWorldEvents.trap(level, pos);
        }
    }

    /** Le feu sur tout le sol, dix blocs autour. */
    private static void putFire(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                if (level.getBlockState(at).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, at)) {
                    LuckyWorld.place(player, level, at, BaseFireBlock.getState(level, at));
                }
            }
        }
    }

    /**
     * Un zombie de 200 points de vie (+900 %), en armure de diamant, Force X et
     * Vitesse IV.
     */
    private static void zombieHero(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Zombie zombie = LuckyWorld.create(level, EntityTypes.ZOMBIE, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (zombie == null) {
            return;
        }
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addPermanentModifier(new AttributeModifier(ZOMBIE_HERO, 9.0d,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        zombie.setHealth(zombie.getMaxHealth());
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        zombie.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 500000, 9));
        zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 500000, 3));
        zombie.setCustomName(Component.literal("Zombie Hero"));
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
    }

    /**
     * L'outil en main disparait ; et le premier outil de l'inventaire aussi : le
     * code de PalaMod enchaine les deux.
     */
    private static void instantBreakUp(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (isTool(player.getMainHandItem())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            LuckyWorld.bar(player, "Tu feras gaffe a ce que tu as en main, c'est fragile", ChatFormatting.RED);
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isTool(stack)) {
                LuckyWorld.bar(player, "Il me semble que tu avais " + stack.getCount() + "x "
                        + stack.getHoverName().getString(), ChatFormatting.RED);
                inventory.setItem(slot, ItemStack.EMPTY);
                return;
            }
        }
    }

    /** Un outil ou une arme ({@code ItemTool}, {@code ItemSword}). */
    private static boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON));
    }

    /** Une coque de pierre infestee de 11 blocs de cote autour du joueur, creuse sur 9. */
    private static void dontBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = player.blockPosition().above(4);
        LuckyWorld.box(player, level, center, 11, 11, 11, Blocks.INFESTED_STONE.defaultBlockState(), null);
        LuckyWorld.box(player, level, center, 9, 9, 9, Blocks.AIR.defaultBlockState(), null);
    }

    /**
     * La dynamite en endium de PalaMod. Il n'y en a pas ici : c'est la TNT en
     * endium, allumee aux pieds du joueur, qui saute en deux secondes.
     */
    private static void megaBoom(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PalaPrimedTnt tnt = new PalaPrimedTnt(level, player.getX(), player.getY(), player.getZ(), null,
                PalaPillage.ENDIUM_TNT.get().defaultBlockState());
        tnt.setFuse(40);
        level.addFreshEntity(tnt);
    }

    /**
     * Le joueur sur le bloc, des toiles autour, et des araignees bleues qui
     * tombent dessus (une case de toile sur trois a peu pres).
     */
    private static void arachnoTrap(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.teleportTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (!LuckyWorld.place(player, level, at, Blocks.COBWEB.defaultBlockState())
                            || level.getRandom().nextInt(50) <= 35) {
                        continue;
                    }
                    Mob spider = LuckyWorld.create(level, EntityTypes.CAVE_SPIDER, at.getX() + 0.5d,
                            at.getY() + 3.0d, at.getZ() + 0.5d);
                    if (spider != null) {
                        spider.setTarget(player);
                        level.addFreshEntity(spider);
                    }
                }
            }
        }
    }

    /**
     * Un cube d'obsidienne de 5 de cote autour du joueur. PalaMod le remplissait,
     * le joueur dedans : on y etouffait. Ici, sa place reste libre.
     */
    private static void obsiTrap(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos feet = player.blockPosition();
        LuckyWorld.box(player, level, feet, 5, 5, 5, Blocks.OBSIDIAN.defaultBlockState(),
                at -> at.equals(feet) || at.equals(feet.above()));
        LuckyWorld.bar(player, "Puisse le sort vous etre favorable", ChatFormatting.RED);
    }

    /** L'armure portee tombe a un point de solidite. */
    private static void oneShot(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isDamageableItem()) {
                armor.setDamageValue(armor.getMaxDamage() - 1);
            }
        }
        LuckyWorld.bar(player, "Ton armure me semble bien fragile", ChatFormatting.RED);
    }

    /** Un dixieme du solde en moins. */
    private static void removeMoney(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long loss = Economy.balance(player) / 10L;
        if (loss > 0L) {
            Economy.debit(player, loss);
        }
        LuckyWorld.tell(player, "Tu viens de perdre " + MoneyFormat.exact(loss), ChatFormatting.RED);
    }

    /** Un niveau de moins dans un metier au hasard. */
    private static void seum(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Job job = Job.values()[level.getRandom().nextInt(Job.values().length)];
        if (JobApi.loseLevel(player, job)) {
            LuckyWorld.bar(player, "Ho non :(, tu as perdu un niveau en " + job.display(), ChatFormatting.RED);
        } else {
            LuckyWorld.bar(player, "Ho non :(... " + job.display() + " etait deja au niveau 0", ChatFormatting.GRAY);
        }
    }

    /** Une pile au hasard du coffre de l'Ender disparait. */
    private static void enderChest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PlayerEnderChestContainer chest = player.getEnderChestInventory();
        List<Integer> filled = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (!chest.getItem(slot).isEmpty()) {
                filled.add(slot);
            }
        }
        if (filled.isEmpty()) {
            LuckyWorld.bar(player, "Ton enderchest etait vide... chanceux", ChatFormatting.GRAY);
            return;
        }
        chest.setItem(filled.get(level.getRandom().nextInt(filled.size())), ItemStack.EMPTY);
        chest.setChanged();
        LuckyWorld.bar(player, "Voila, ton enderchest sera plus leger comme ca", ChatFormatting.RED);
    }
}
