package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le familier au quotidien, cote serveur : connexion, quetes d'entretien,
 * passives qui tiennent aux evenements du jeu (minage, chute, combat, nourriture,
 * potions, XP, peche, mort) et effets « au prochain coup » des actives.
 *
 * <p>Les passives des machines (crusher, cobblebreaker, croquettes, totem,
 * portail, chaudron, extracteur, pierres legendaires, pioche des dieux,
 * deltaplane, knocker) passent par {@link fr.atraxterra.dohy.palamod.unlock.PetHooks}.
 */
@EventBusSubscriber
public final class PetEvents {

    private static final Identifier FAST_WALKING = Identifier.fromNamespaceAndPath("palamod", "pet_fast_walking");

    /** Les mouvements suivis pour « Marcher » et « Passer du temps ». */
    private static final class Walk {
        double x;
        double z;
        double meters;
        int seconds;
        boolean placed;
    }

    private static final Map<UUID, Walk> WALKS = new HashMap<>();
    /** Le dernier cout d'enclume reduit par « Enchante », pour ne pas le reduire deux fois. */
    private static final Map<UUID, Integer> ANVIL_COSTS = new HashMap<>();

    /** La recolte en cours d'une active : ses drops vont dans l'inventaire. */
    private static ServerPlayer collecting;
    private static final List<ItemStack> COLLECTED = new ArrayList<>();

    private static Field lureField;
    private static boolean lureSearched;

    private PetEvents() {
    }

    // -- Connexion --------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PetApi.login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WALKS.remove(event.getEntity().getUUID());
        ANVIL_COSTS.remove(event.getEntity().getUUID());
    }

    // -- Le tick du joueur : quetes, nuage electrique, marche rapide, aimant, peche --------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !PetApi.has(player)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        fishing(player);
        anvil(player);
        long tick = player.level().getGameTime();
        if (tick % 5L == 0L) {
            magnet(player);
        }
        if (tick % 20L != 0L) {
            return;
        }
        PetApi.rollDay(player);
        PetApi.syncLook(player);
        fastWalking(player);
        if (PetApi.data(player).effect(PetActives.ELECTRIC_CLOUD) > 0.0d) {
            PetActives.strike(player);
        }
        if (player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA)) {
            PetApi.progress(player, PetMission.IMMERSE, 1);
        }
        Walk walk = WALKS.computeIfAbsent(player.getUUID(), id -> new Walk());
        if (walk.placed && player.onGround() && !player.isPassenger() && !player.getAbilities().flying) {
            double dx = player.getX() - walk.x;
            double dz = player.getZ() - walk.z;
            // Au plus 12 blocs par seconde : une teleportation ne compte pas.
            walk.meters += Math.min(12.0d, Math.sqrt(dx * dx + dz * dz));
        }
        walk.x = player.getX();
        walk.z = player.getZ();
        walk.placed = true;
        if (walk.meters >= 1.0d) {
            int whole = (int) walk.meters;
            walk.meters -= whole;
            PetApi.progress(player, PetMission.WALK, whole);
        }
        if (PetApi.data(player).visible()) {
            walk.seconds++;
            if (walk.seconds >= 60) {
                walk.seconds = 0;
                PetApi.progress(player, PetMission.TIME, 1);
            }
        }
    }

    /** « Marche rapide » : un bonus de vitesse tant que la competence est posee. */
    private static void fastWalking(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double bonus = PetApi.fraction(player, PetSkill.FAST_WALKING, false);
        AttributeModifier current = speed.getModifier(FAST_WALKING);
        if (current != null && Math.abs(current.amount() - bonus) < 1.0e-6d) {
            return;
        }
        speed.removeModifier(FAST_WALKING);
        if (bonus > 0.0d) {
            speed.addTransientModifier(new AttributeModifier(FAST_WALKING, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** « Aimante » : les objets au sol, a ce rayon, viennent au joueur ({@code MagnetItem}). */
    private static void magnet(ServerPlayer player) {
        double radius = PetApi.value(player, PetSkill.MAGNET);
        if (radius <= 0.0d) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(radius);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box,
                entity -> entity.isAlive() && entity.distanceTo(player) <= radius)) {
            item.setPos(player.getX(), player.getY(), player.getZ());
        }
    }

    /**
     * « Pecheur avise » : l'attente du poisson raccourcit. {@code FishingHook} ne
     * donne pas son compte a rebours : il est avance a la main, d'autant de ticks
     * qu'il faut pour que l'attente perde la part voulue.
     */
    private static void fishing(ServerPlayer player) {
        FishingHook hook = player.fishing;
        if (hook == null) {
            return;
        }
        double cut = Math.min(0.9d, PetApi.fraction(player, PetSkill.SKILLED_FISHERMAN, false));
        if (cut <= 0.0d) {
            return;
        }
        Field field = lureField();
        if (field == null) {
            return;
        }
        try {
            int left = field.getInt(hook);
            if (left <= 1) {
                return;
            }
            double extra = cut / (1.0d - cut);
            int whole = (int) extra;
            if (player.getRandom().nextDouble() < extra - whole) {
                whole++;
            }
            field.setInt(hook, Math.max(1, left - whole));
        } catch (IllegalAccessException ignored) {
            // Le champ a change de nom : la passive se tait.
        }
    }

    private static Field lureField() {
        if (!lureSearched) {
            lureSearched = true;
            try {
                lureField = FishingHook.class.getDeclaredField("timeUntilLured");
                lureField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                lureField = null;
            }
        }
        return lureField;
    }

    // -- Les drops des actives de recolte --------------------------------------------------------------

    /** Fait {@code action} en mettant les drops des blocs casses par ce joueur dans son inventaire. */
    static void collect(ServerPlayer player, Runnable action) {
        collecting = player;
        COLLECTED.clear();
        try {
            action.run();
        } finally {
            collecting = null;
            List<ItemStack> got = new ArrayList<>(COLLECTED);
            COLLECTED.clear();
            for (ItemStack stack : got) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    /** En dernier : l'XP des metiers et les autres bonus ont deja vu les drops. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDropsCollected(BlockDropsEvent event) {
        if (collecting != null && event.getBreaker() == collecting) {
            for (ItemEntity entity : event.getDrops()) {
                COLLECTED.add(entity.getItem().copy());
            }
            event.getDrops().clear();
        }
    }

    // -- Minage, recolte, mineur chanceux ---------------------------------------------------------

    /** Les ameliorations de « Mineur chanceux » ({@code LuckyMinerListener}). */
    static Block upgrade(Block block) {
        if (block == Blocks.COAL_ORE) {
            return Blocks.IRON_ORE;
        }
        if (block == Blocks.DEEPSLATE_COAL_ORE) {
            return Blocks.DEEPSLATE_IRON_ORE;
        }
        if (block == Blocks.IRON_ORE) {
            return Blocks.GOLD_ORE;
        }
        if (block == Blocks.DEEPSLATE_IRON_ORE) {
            return Blocks.DEEPSLATE_GOLD_ORE;
        }
        if (block == Blocks.GOLD_ORE) {
            return Blocks.DIAMOND_ORE;
        }
        if (block == Blocks.DEEPSLATE_GOLD_ORE) {
            return Blocks.DEEPSLATE_DIAMOND_ORE;
        }
        if (block == Blocks.DIAMOND_ORE) {
            return Blocks.EMERALD_ORE;
        }
        if (block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return Blocks.DEEPSLATE_EMERALD_ORE;
        }
        if (block == PalaBlocks.AMETHYST_ORE.get()) {
            return PalaBlocks.TITANE_ORE.get();
        }
        if (block == PalaBlocks.TITANE_ORE.get()) {
            return PalaBlocks.PALADIUM_ORE.get();
        }
        if (block == PalaBlocks.PALADIUM_ORE.get()) {
            return PalaBlocks.PALADIUM_GREEN_ORE.get();
        }
        if (block == PalaBlocks.PALADIUM_GREEN_ORE.get()) {
            return PalaBlocks.FINDIUM_ORE.get();
        }
        return null;
    }

    /**
     * « Mineur chanceux » : au prochain minerai, la casse est annulee et le bloc
     * devient le minerai d'au-dessus (a miner a nouveau), ou l'effet se perd.
     */
    @SubscribeEvent
    public static void onLuckyBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Block next = upgrade(event.getState().getBlock());
        if (next == null || PetActives.peek(player, PetActives.LUCKY_MINER) <= 0.0d) {
            return;
        }
        double chance = PetActives.consume(player, PetActives.LUCKY_MINER);
        if (player.getRandom().nextDouble() * 100.0d > chance) {
            PetActives.tell(player, "Mineur chanceux : pas de chance cette fois.", ChatFormatting.GRAY);
            return;
        }
        event.setCanceled(true);
        level.setBlock(event.getPos(), next.defaultBlockState(), Block.UPDATE_ALL);
        PetActives.tell(player, "Mineur chanceux : le minerai s'ameliore !", ChatFormatting.GOLD);
    }

    /** Les quetes « Miner » et « Recolter », une fois la casse acceptee. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakCounted(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !PetApi.has(player)) {
            return;
        }
        BlockState state = event.getState();
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            PetApi.progress(player, PetMission.HARVEST, 1);
        } else if (state.getDestroySpeed(event.getLevel(), event.getPos()) > 0.0f) {
            PetApi.progress(player, PetMission.MINE, 1);
        }
    }

    /** « Expert agronome » : une graine de plus sur une culture mure ({@code MiningFortuneListener}). */
    @SubscribeEvent
    public static void onCropDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || !(event.getState().getBlock() instanceof CropBlock crop)
                || !crop.isMaxAge(event.getState())) {
            return;
        }
        double chance = PetApi.fraction(player, PetSkill.FARMING_EXPERT, true);
        Item seed = crop.asItem();
        if (chance <= 0.0d || seed == Items.AIR || player.getRandom().nextDouble() >= chance) {
            return;
        }
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                new ItemStack(seed)));
    }

    /** « Bonne pioche » et « Pioche incassable », des deux cotes (le client calcule aussi la casse). */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        PetLook look = event.getEntity().getData(PalaPets.LOOK.get());
        if (look.familiar() == null) {
            return;
        }
        float speed = event.getNewSpeed() * (1.0f + look.mineSpeed());
        if (look.obsidian() > 0.0f && (event.getState().is(Blocks.OBSIDIAN) || event.getState().is(Blocks.CRYING_OBSIDIAN))) {
            speed *= look.obsidian();
        }
        event.setNewSpeed(speed);
    }

    // -- Combat, chute, XP ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            double cut = PetApi.fraction(player, PetSkill.LIGHTWEIGHT, true);
            if (cut > 0.0d) {
                event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1.0d - cut)));
            }
        }
    }

    /** « Point vital » : les coups du joueur font plus mal aux monstres. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Enemy) {
            double bonus = PetApi.fraction(player, PetSkill.WEAK_SPOT, false);
            if (bonus > 0.0d) {
                event.setAmount((float) (event.getAmount() * (1.0d + bonus)));
            }
        }
    }

    /** La quete « Vaincre des monstres ». */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Enemy) {
            PetApi.progress(player, PetMission.KILL, 1);
        }
    }

    /** « Absorbeur d'experience » : un orbe ramasse compte parfois double. */
    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            double chance = PetApi.fraction(player, PetSkill.EXPERIENCE_ABSORBER, true);
            if (chance > 0.0d && player.getRandom().nextDouble() < chance) {
                player.giveExperiencePoints(event.getOrb().getValue());
            }
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PetApi.has(player)) {
            PetApi.progress(player, PetMission.JUMP, 1);
        }
    }

    /** Avant le mini-jeu de peche, qui annule l'evenement pour ses cannes. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PetApi.has(player)) {
            PetApi.progress(player, PetMission.FISH, 1);
        }
    }

    // -- Nourriture et potions ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !PetApi.has(player)) {
            return;
        }
        ItemStack stack = event.getItem();
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            double more = PetApi.fraction(player, PetSkill.STOMACH, false);
            int nutrition = (int) Math.round(food.nutrition() * more);
            if (nutrition > 0) {
                player.getFoodData().eat(nutrition, food.saturation() / Math.max(1, food.nutrition()) * 0.5f);
            }
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            double longer = PetApi.fraction(player, PetSkill.BUDDING_CHEMIST, false);
            if (longer <= 0.0d) {
                return;
            }
            for (MobEffectInstance drunk : potion.getAllEffects()) {
                MobEffectInstance active = player.getEffect(drunk.getEffect());
                if (active == null || active.isInfiniteDuration() || drunk.getEffect().value().isInstantenous()) {
                    continue;
                }
                int duration = (int) Math.round(active.getDuration() * (1.0d + longer));
                player.addEffect(new MobEffectInstance(active.getEffect(), duration, active.getAmplifier(),
                        active.isAmbient(), active.isVisible(), active.showIcon()));
            }
        }
    }

    // -- Effets « au prochain coup » -------------------------------------------------------------------

    /**
     * « Enchante » : l'enclume coute moins, tant que l'effet n'est pas paye. Le
     * cout vanilla est calcule apres {@code AnvilUpdateEvent} : il est repris ici,
     * a chaque nouveau resultat, et envoye au client par la case de donnees.
     */
    private static void anvil(ServerPlayer player) {
        if (!(player.containerMenu instanceof AnvilMenu menu)) {
            ANVIL_COSTS.remove(player.getUUID());
            return;
        }
        double levels = PetActives.peek(player, PetActives.ENCHANTED);
        int cost = menu.getCost();
        Integer adjusted = ANVIL_COSTS.get(player.getUUID());
        if (levels <= 0.0d || cost <= 1 || adjusted != null && adjusted == cost) {
            return;
        }
        int lower = Math.max(1, cost - (int) Math.floor(levels));
        menu.setMaximumCost(lower);
        ANVIL_COSTS.put(player.getUUID(), lower);
    }

    /** Les niveaux payes a l'enclume consomment « Enchante ». */
    @SubscribeEvent
    public static void onLevelsSpent(PlayerXpEvent.LevelChange event) {
        if (event.getLevels() < 0 && event.getEntity() instanceof ServerPlayer player
                && player.containerMenu instanceof AnvilMenu
                && PetActives.consume(player, PetActives.ENCHANTED) > 0.0d) {
            PetActives.tell(player, "Enchante : la reduction a servi.", ChatFormatting.GRAY);
        }
    }

    /** « Reparo ! » : le prochain objet casse a sa chance de revenir neuf. */
    @SubscribeEvent
    public static void onItemBroken(PlayerDestroyItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        double chance = PetActives.consume(player, PetActives.REPAIR);
        if (chance <= 0.0d) {
            return;
        }
        if (player.getRandom().nextDouble() * 100.0d > chance) {
            PetActives.tell(player, "Reparo ! : l'objet n'a pas pu etre sauve.", ChatFormatting.GRAY);
            return;
        }
        ItemStack repaired = event.getOriginal().copy();
        repaired.setDamageValue(0);
        if (event.getHand() != null && player.getItemInHand(event.getHand()).isEmpty()) {
            player.setItemInHand(event.getHand(), repaired);
        } else if (!player.getInventory().add(repaired)) {
            player.drop(repaired, false);
        }
        PetActives.tell(player, "Reparo ! : l'objet est comme neuf.", ChatFormatting.GOLD);
    }

    // -- Cercueil portable ({@code PortableCoffinListener}) ---------------------------------------------

    /**
     * A la mort, l'inventaire va dans deux coffres poses a l'endroit de la mort,
     * pas dans le claim d'un autre ; sans place, tout tombe comme d'habitude.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || PetApi.value(player, PetSkill.PORTABLE_COFFIN) <= 0.0d) {
            return;
        }
        ServerLevel level = player.level();
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }
        BlockPos pos = coffinSpot(level, player.blockPosition());
        if (pos == null || PetActives.foreign(player, level, pos) || PetActives.foreign(player, level, pos.above())) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (items.size() > 27) {
            level.setBlock(pos.above(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        }
        int index = 0;
        for (int chest = 0; chest < 2 && index < items.size(); chest++) {
            if (!(level.getBlockEntity(chest == 0 ? pos : pos.above()) instanceof ChestBlockEntity box)) {
                break;
            }
            for (int slot = 0; slot < box.getContainerSize() && index < items.size(); slot++) {
                box.setItem(slot, items.get(index++).copy());
            }
        }
        // Deux coffres (54 cases) tiennent tout l'inventaire (41 cases).
        player.getInventory().clearContent();
        PetActives.tell(player, "Cercueil portable : tes affaires t'attendent dans un coffre en " + pos.getX() + " "
                + pos.getY() + " " + pos.getZ() + ".", ChatFormatting.LIGHT_PURPLE);
    }

    /** La place des coffres : le bloc de la mort ou un peu au-dessus, deux blocs libres. */
    private static BlockPos coffinSpot(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        for (int up = 0; up < 6 && pos.getY() + 1 < level.getMaxY(); up++, pos = pos.above()) {
            if (pos.getY() <= level.getMinY()) {
                continue;
            }
            if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced()) {
                return pos;
            }
        }
        return null;
    }
}
