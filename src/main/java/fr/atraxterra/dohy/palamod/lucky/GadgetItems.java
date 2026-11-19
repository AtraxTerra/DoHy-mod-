package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.hunter.CapturedEntity;
import fr.atraxterra.dohy.palamod.hunter.PalaHunter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Les objets des lucky blocks du cinquieme lot ({@code luckyblock/items}). Chaque classe dit
 * ce que l'objet faisait chez PalaMod et ou le portage s'en ecarte.
 */
final class GadgetItems {

    private GadgetItems() {
    }

    /** Le point vise, a {@code range} blocs au plus. */
    static BlockHitResult aim(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    /** Un message au-dessus de la barre d'objets. */
    static void bar(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(message, true);
        }
    }

    static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    // -- Hook hook jab --------------------------------------------------------------------

    /**
     * Le grappin ({@code ItemGrappin}) : il tire un crochet (il en faut un dans l'inventaire) et
     * hisse le joueur jusqu'au bloc touche, a 32 blocs au plus. <b>Ecart</b> : PalaMod le
     * bandait comme un arc ; ici un clic suffit.
     */
    static class GrapplingHookItem extends Item {
        GrapplingHookItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            ItemStack hook = find(player, LuckyGadgets.GRAPPIN_HOOK.get());
            if (hook.isEmpty() && !player.getAbilities().instabuild) {
                player.sendSystemMessage(Component.literal("Attention un crochet de grappin est necessaire !")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            BlockHitResult hit = aim(player, 32.0d);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return InteractionResult.FAIL;
            }
            Vec3 pull = hit.getLocation().subtract(player.position());
            double strength = Math.min(3.0d, 0.6d + pull.length() * 0.12d);
            player.setDeltaMovement(pull.normalize().scale(strength).add(0.0d, 0.3d, 0.0d));
            player.hurtMarked = true;
            player.fallDistance = 0.0d;
            if (!player.getAbilities().instabuild) {
                hook.shrink(1);
                player.getItemInHand(hand).hurtAndBreak(1, player, hand);
            }
            server.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0f, 0.6f);
            player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
            return InteractionResult.SUCCESS;
        }
    }

    static ItemStack find(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // -- Lasso ------------------------------------------------------------------------------------

    /**
     * Le lasso ({@code ItemLasso}) : il attrape une creature de sa liste (animaux, monstres
     * ordinaires, elephant) et la relache sur le bloc clique. Il ne sert qu'une fois
     * ({@code used}).
     */
    static class LassoItem extends Item {
        static final List<EntityType<?>> WHITELIST = List.of(EntityTypes.PIG, EntityTypes.COW, EntityTypes.SHEEP,
                EntityTypes.SLIME, EntityTypes.ZOMBIE, EntityTypes.CREEPER, EntityTypes.RABBIT, EntityTypes.HORSE,
                EntityTypes.CHICKEN, EntityTypes.PARROT, EntityTypes.PANDA, EntityTypes.GOAT, EntityTypes.TURTLE,
                EntityTypes.DOLPHIN, EntityTypes.SPIDER, EntityTypes.SKELETON, EntityTypes.MOOSHROOM,
                EntityTypes.DONKEY, EntityTypes.LLAMA, EntityTypes.WOLF, EntityTypes.CAT, EntityTypes.FOX,
                EntityTypes.SQUID);

        LassoItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                      InteractionHand hand) {
            if (stack.has(PalaHunter.CAPTURED.get()) || data(stack).getBooleanOr("used", false)) {
                return InteractionResult.PASS;
            }
            boolean allowed = WHITELIST.contains(target.getType())
                    || BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath().equals("elephant");
            if (!allowed || !target.isAlive() || target instanceof Player) {
                return InteractionResult.PASS;
            }
            if (player.level() instanceof ServerLevel level) {
                TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                target.saveWithoutId(output);
                CompoundTag data = output.buildResult();
                data.remove("UUID");
                ItemStack held = player.getItemInHand(hand);
                held.set(PalaHunter.CAPTURED.get(), new CapturedEntity(BuiltInRegistries.ENTITY_TYPE.getKey(
                        target.getType()), data, target.getName()));
                target.discard();
                level.playSound(null, player.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            ItemStack stack = context.getItemInHand();
            CapturedEntity captured = stack.get(PalaHunter.CAPTURED.get());
            if (captured == null) {
                return InteractionResult.PASS;
            }
            if (!(context.getLevel() instanceof ServerLevel level)) {
                return InteractionResult.SUCCESS;
            }
            BlockPos target = context.getClickedPos().relative(context.getClickedFace());
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(captured.type());
            Entity entity = type == null ? null : type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            if (entity == null) {
                return InteractionResult.FAIL;
            }
            entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), captured.data().copy()));
            entity.snapTo(target.getX() + 0.5d, target.getY(), target.getZ() + 0.5d, entity.getYRot(), entity.getXRot());
            entity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(entity);
            stack.remove(PalaHunter.CAPTURED.get());
            CompoundTag data = data(stack);
            data.putBoolean("used", true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            return InteractionResult.SUCCESS;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    net.minecraft.world.item.component.TooltipDisplay display,
                                    Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            CapturedEntity captured = stack.get(PalaHunter.CAPTURED.get());
            if (captured != null) {
                tooltip.accept(Component.literal("Entite: ").withStyle(ChatFormatting.YELLOW)
                        .append(captured.name().copy().withStyle(ChatFormatting.AQUA)));
            } else if (data(stack).getBooleanOr("used", false)) {
                tooltip.accept(Component.literal("Deja utilise").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // -- Analyste ---------------------------------------------------------------------------------

    /**
     * L'analyseur de chunk ({@code ItemChunkAnalyser}) : « Clic droit pour analyser le chunk ».
     * <b>Ecart</b> : PalaMod ouvrait un ecran ; ici les minerais du chunk s'affichent dans le chat.
     */
    static class ChunkAnalyserItem extends Item {
        ChunkAnalyserItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            int chunkX = player.getBlockX() >> 4;
            int chunkZ = player.getBlockZ() >> 4;
            Map<String, Integer> ores = new TreeMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                    for (int y = server.getMinY(); y < server.getMaxY(); y++) {
                        BlockState state = server.getBlockState(pos.set(x, y, z));
                        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        if (id.getPath().endsWith("_ore") || state.is(BlockTags.COAL_ORES)) {
                            ores.merge(state.getBlock().getName().getString(), 1, Integer::sum);
                        }
                    }
                }
            }
            player.sendSystemMessage(Component.literal("Analyse du chunk " + chunkX + ", " + chunkZ + " :")
                    .withStyle(ChatFormatting.AQUA));
            if (ores.isEmpty()) {
                player.sendSystemMessage(Component.literal("  aucun minerai").withStyle(ChatFormatting.GRAY));
            }
            ores.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(entry ->
                    player.sendSystemMessage(Component.literal("  " + entry.getKey() + " : " + entry.getValue())
                            .withStyle(ChatFormatting.WHITE)));
            player.getItemInHand(hand).hurtAndBreak(1, player, hand);
            return InteractionResult.SUCCESS;
        }
    }

    // -- Oui metre ----------------------------------------------------------------------------------

    /** Le metre ({@code ItemMeter}) : un premier clic sur un bloc, un second, et la distance. */
    static class MeterItem extends Item {
        MeterItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (context.getLevel().isClientSide() || context.getPlayer() == null) {
                return InteractionResult.SUCCESS;
            }
            ItemStack stack = context.getItemInHand();
            CompoundTag data = data(stack);
            BlockPos clicked = context.getClickedPos();
            Player player = context.getPlayer();
            Optional<BlockPos> first = data.read("from", BlockPos.CODEC);
            if (first.isEmpty()) {
                data.store("from", BlockPos.CODEC, clicked);
                player.sendSystemMessage(Component.literal("Premier point : " + clicked.toShortString())
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                BlockPos from = first.get();
                int dx = Math.abs(clicked.getX() - from.getX()) + 1;
                int dy = Math.abs(clicked.getY() - from.getY()) + 1;
                int dz = Math.abs(clicked.getZ() - from.getZ()) + 1;
                double length = Math.sqrt(from.distSqr(clicked));
                player.sendSystemMessage(Component.literal(String.format(java.util.Locale.ROOT,
                        "%d x %d x %d blocs, %.1f blocs en ligne droite", dx, dy, dz, length))
                        .withStyle(ChatFormatting.GREEN));
                data.remove("from");
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            return InteractionResult.SUCCESS;
        }
    }

    // -- Biome Painter ----------------------------------------------------------------------------------

    /**
     * Le peintre de biome ({@code ItemBiomePainter}) : il repeint le biome autour du bloc clique
     * (9 x 9 x 9) ; accroupi, il change de biome. Il s'use de 1 par coup.
     */
    static class BiomePainterItem extends Item {
        static final List<String> BIOMES = List.of("plains", "desert", "snowy_plains", "jungle", "swamp",
                "mushroom_fields", "cherry_grove", "badlands", "flower_forest", "savanna", "dark_forest", "ice_spikes");

        BiomePainterItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                ItemStack stack = player.getItemInHand(hand);
                CompoundTag data = data(stack);
                int next = (data.getIntOr("biome", 0) + 1) % BIOMES.size();
                data.putInt("biome", next);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                bar(player, Component.literal("Biome : " + BIOMES.get(next)).withStyle(ChatFormatting.GREEN));
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null || player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            BlockPos at = context.getClickedPos();
            if (!LuckyWorld.canEdit(server, level, at)) {
                return InteractionResult.FAIL;
            }
            String biome = BIOMES.get(data(context.getItemInHand()).getIntOr("biome", 0));
            var source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            level.getServer().getCommands().performPrefixedCommand(source, String.format(java.util.Locale.ROOT,
                    "fillbiome %d %d %d %d %d %d minecraft:%s", at.getX() - 4, at.getY() - 4, at.getZ() - 4,
                    at.getX() + 4, at.getY() + 4, at.getZ() + 4, biome));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5d, at.getY() + 1.2d, at.getZ() + 0.5d, 20,
                    2.0d, 0.5d, 2.0d, 0.0d);
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            return InteractionResult.SUCCESS;
        }
    }

    // -- Bouseul --------------------------------------------------------------------------------------

    /**
     * La boussole de pistage ({@code ItemFollowCompass}) : un clic retient ou est le joueur le plus
     * proche, a 50 blocs au plus, et l'aiguille y pointe. <b>Ecart</b> : en solo, faute d'autre
     * joueur, elle retient la creature hostile la plus proche.
     */
    static class FollowCompassItem extends Item {
        FollowCompassItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            AABB box = player.getBoundingBox().inflate(50.0d);
            Comparator<Entity> nearest = Comparator.comparingDouble(entity -> entity.distanceToSqr(player));
            Entity target = server.getEntitiesOfClass(Player.class, box, other -> other != player).stream()
                    .min(nearest).map(Entity.class::cast)
                    .or(() -> server.getEntitiesOfClass(LivingEntity.class, box, living -> living instanceof Enemy)
                            .stream().min(nearest))
                    .orElse(null);
            if (target == null) {
                bar(player, Component.literal("Personne a suivre ici.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            player.getItemInHand(hand).set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                    Optional.of(GlobalPos.of(server.dimension(), target.blockPosition())), false));
            bar(player, Component.literal("Cible : " + target.getName().getString())
                    .withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
    }

    // -- PoulpoGun --------------------------------------------------------------------------------------

    /**
     * Le PoulpoGun ({@code ItemPoulpoGun}) : il lance un poulpe, qui retombe a l'endroit vise.
     * <b>Ecart</b> : PalaMod suivait une boule de neige ; ici le poulpe tombe la ou l'on vise.
     */
    static class PoulpoGunItem extends Item {
        PoulpoGunItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer shooter)) {
                return InteractionResult.SUCCESS;
            }
            BlockHitResult hit = aim(player, 40.0d);
            Vec3 at = hit.getLocation();
            if (!LuckyWorld.canEdit(shooter, server, BlockPos.containing(at))) {
                player.sendSystemMessage(Component.literal("Vous ne pouvez pas lancer de poulpe ici.")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            var squid = LuckyWorld.create(server, EntityTypes.SQUID, at.x, at.y + 5.0d, at.z);
            if (squid != null) {
                server.addFreshEntity(squid);
            }
            server.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0d, at.z, 30, 0.8d, 0.6d, 0.8d, 0.02d);
            server.playSound(null, player.blockPosition(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
            return InteractionResult.SUCCESS;
        }
    }

    // -- Potion magique, nourriture, soda ----------------------------------------------------------------

    /** La potion magique ({@code ItemMagicPotion}) : Regeneration II et Saturation pendant 30 secondes. */
    static class MagicPotionItem extends Item {
        MagicPotionItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide()) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
                entity.addEffect(new MobEffectInstance(MobEffects.SATURATION, 600, 0));
                stack.hurtAndBreak(1, entity, entity.getUsedItemHand());
            }
            return stack;
        }
    }

    /**
     * La nourriture de l'espace ({@code ItemSpaceFood}) : « X s'en va vers les etoiles !! », de la
     * fumee, et le joueur monte. <b>Ecart</b> : il redescend en planant, sans se tuer.
     */
    static class SpaceFoodItem extends Item {
        SpaceFoodItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
                server.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[LuckyBlock] ")
                        .withStyle(ChatFormatting.GOLD).append(Component.literal(player.getName().getString()
                        + " s'en va vers les etoiles !!").withStyle(ChatFormatting.AQUA)), false);
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 80, 20, false, false));
                LuckyTasks.repeat(player, 0, 4, 20, step -> server.sendParticles(ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY(), player.getZ(), 6, 0.2d, 0.1d, 0.2d, 0.01d));
                LuckyTasks.later(player, 80, () -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                        600, 0)));
            }
            return left;
        }
    }

    /** Le champignon geant ({@code ItemMarioMushroom}) : on le mange, et l'on grandit. */
    static class GiantMushroomItem extends Item {
        GiantMushroomItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (!level.isClientSide()) {
                entity.addEffect(new MobEffectInstance(PalaLucky.GIANT, 99999, 0));
            }
            return left;
        }
    }

    /**
     * Le soda ({@code potion.soda}) : trop sucre, il retire un coeur tant que dure l'effet
     * ({@code REMOVE_HEART}, 60 heures). Le lait le fait passer.
     */
    static class SodaItem extends Item {
        SodaItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide()) {
                entity.addEffect(new MobEffectInstance(PalaLucky.SUGAR, 4320000, 0));
                if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return stack;
        }
    }

    // -- Nouvel an ----------------------------------------------------------------------------------------

    /**
     * La fusee du nouvel an ({@code ItemHappyNewYearFirework}) : posee, elle tire un bouquet.
     * <b>Ecart</b> : PalaMod ecrivait dans le ciel ; ici douze fusees de toutes les couleurs.
     */
    static class NewYearFireworkItem extends Item {
        NewYearFireworkItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (!(context.getLevel() instanceof ServerLevel server) || context.getPlayer() == null) {
                return InteractionResult.SUCCESS;
            }
            Vec3 at = context.getClickLocation();
            int[] colors = {0xFF0000, 0xFFD700, 0x00FF7F, 0x1E90FF, 0xFF69B4, 0xFFFFFF};
            LuckyTasks.repeat(null, 0, 5, 12, step -> {
                ItemStack rocket = new ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
                rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + step % 2, List.of(new FireworkExplosion(
                        FireworkExplosion.Shape.values()[step % FireworkExplosion.Shape.values().length],
                        it.unimi.dsi.fastutil.ints.IntList.of(colors[step % colors.length], colors[(step + 2) % colors.length]),
                        it.unimi.dsi.fastutil.ints.IntList.of(), step % 3 == 0, true))));
                double dx = (server.getRandom().nextDouble() - 0.5d) * 6.0d;
                double dz = (server.getRandom().nextDouble() - 0.5d) * 6.0d;
                server.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(server,
                        at.x + dx, at.y, at.z + dz, rocket));
            });
            context.getItemInHand().consume(1, context.getPlayer());
            return InteractionResult.SUCCESS;
        }
    }

    // -- Turboat ---------------------------------------------------------------------------------------------

    /**
     * Le bateau a moteur ({@code ItemBoatFurnace}, {@code EntityBoatFurnace}) : un bateau qui file.
     * <b>Ecart</b> : un bateau du jeu, pousse tant qu'on avance ({@link GadgetEvents}).
     */
    static class BoatFurnaceItem extends Item {
        BoatFurnaceItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (!(context.getLevel() instanceof ServerLevel server) || context.getPlayer() == null) {
                return InteractionResult.SUCCESS;
            }
            Vec3 at = context.getClickLocation();
            Entity boat = EntityTypes.OAK_BOAT.create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (boat == null) {
                return InteractionResult.FAIL;
            }
            boat.snapTo(at.x, at.y, at.z, context.getPlayer().getYRot(), 0.0f);
            boat.getPersistentData().putBoolean(GadgetEvents.TURBOAT, true);
            boat.setCustomName(Component.literal("Turboat"));
            server.addFreshEntity(boat);
            context.getItemInHand().consume(1, context.getPlayer());
            return InteractionResult.SUCCESS;
        }
    }

    // -- Pierre de statistiques ------------------------------------------------------------------------------

    /**
     * La pierre de statistiques ({@code ItemStatsStone}) : toutes les 30 minutes, elle dit ou en
     * est le joueur. Elle brille quand elle est prete ({@code hasEffect}).
     */
    static class StatsStoneItem extends Item {
        static final long COOLDOWN = 1_800_000L;

        StatsStoneItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            ItemStack stack = player.getItemInHand(hand);
            CompoundTag data = data(stack);
            long now = System.currentTimeMillis();
            long last = data.getLongOr("last_use", 0L);
            if (now - last < COOLDOWN) {
                long minutes = (COOLDOWN - (now - last)) / 60_000L + 1;
                bar(player, Component.literal("Encore " + minutes + " minutes").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            data.putLong("last_use", now);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            var stats = server.getStats();
            player.sendSystemMessage(Component.literal("Tes statistiques :").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal(String.format(java.util.Locale.ROOT,
                    "  temps de jeu %d h, morts %d, monstres tues %d, blocs marches %d",
                    stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000,
                    stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)),
                    stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)),
                    stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) / 100)).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }

        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity,
                                  net.minecraft.world.entity.EquipmentSlot slot) {
            if (entity.tickCount % 100 == 0) {
                boolean ready = System.currentTimeMillis() - data(stack).getLongOr("last_use", 0L) >= COOLDOWN;
                if (!Boolean.valueOf(ready).equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
                    stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, ready);
                }
            }
        }
    }

    // -- Saumon Fume -----------------------------------------------------------------------------------------------

    /**
     * La bombe fumigene ({@code ItemSmokeBomb}, seize couleurs) : lancee, elle laisse un nuage de sa
     * couleur pendant dix secondes. <b>Ecart</b> : une seule bombe, teinte par sa couleur
     * ({@code DYED_COLOR}), au lieu de seize objets.
     */
    static class SmokeBombItem extends Item {
        SmokeBombItem(Properties properties) {
            super(properties);
        }

        static int color(ItemStack stack) {
            var dyed = stack.get(DataComponents.DYED_COLOR);
            return dyed == null ? 0xC8C8C8 : dyed.rgb();
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                var bomb = new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(server, player,
                        stack.copyWithCount(1));
                bomb.getPersistentData().putInt(GadgetEvents.SMOKE, color(stack));
                bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.5f, 1.0f);
                server.addFreshEntity(bomb);
                server.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.4f);
            }
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }

    // -- Wi-Fi ---------------------------------------------------------------------------------------------------

    /** Le levier sans fil en main : accroupi, un clic sur un bloc le relie a ce bloc. */
    static class WirelessLeverItem extends BlockItem {
        WirelessLeverItem(Block block, Properties properties) {
            super(block, properties);
        }

        static Optional<BlockPos> target(ItemStack stack) {
            return data(stack).read("target", BlockPos.CODEC);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null || !player.isShiftKeyDown()) {
                return super.useOn(context);
            }
            if (!context.getLevel().isClientSide()) {
                ItemStack stack = context.getItemInHand();
                CompoundTag data = data(stack);
                data.store("target", BlockPos.CODEC, context.getClickedPos());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                bar(player, Component.literal("Relie a " + context.getClickedPos().toShortString())
                        .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
    }
}
