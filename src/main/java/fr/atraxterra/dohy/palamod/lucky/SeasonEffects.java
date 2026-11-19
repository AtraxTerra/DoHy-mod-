package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Les effets des lucky blocks saisonniers ({@code PotionsRegister} des modules du mois).
 * Comme ceux de {@link PalaLucky}, ils ne font rien par eux-memes : ce qui suit les regarde,
 * a chaque seconde du joueur.
 */
@EventBusSubscriber
public final class SeasonEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);

    // -- Mars -----------------------------------------------------------------------------------

    /** {@code PotionStar} : des etoiles plein l'ecran ; a la fin, le casque des etoiles. */
    public static final DeferredHolder<MobEffect, LuckyEffect> STAR =
            EFFECTS.register("star", () -> new LuckyEffect(MobEffectCategory.BENEFICIAL, 0xF5E06B));
    /** {@code PotionNoOxygen} : un coeur toutes les deux secondes, sauf casque des etoiles sur la tete. */
    public static final DeferredHolder<MobEffect, LuckyEffect> NO_OXYGEN =
            EFFECTS.register("no_oxygen", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x3A62D8));
    /** {@code PotionJupiterGravity} : la gravite de Jupiter, on ne peut plus sauter. */
    public static final DeferredHolder<MobEffect, LuckyEffect> JUPITER_GRAVITY = EFFECTS.register("jupiter_gravity",
            () -> modified(MobEffectCategory.HARMFUL, 0xC9874A, "jupiter_gravity", -1.0d, Attributes.JUMP_STRENGTH));

    // -- Paques ---------------------------------------------------------------------------------

    /** {@code PLuckyBlock.FLIP} : l'ecran a l'envers ({@code LuckyClientEvents}). */
    public static final DeferredHolder<MobEffect, LuckyEffect> FLIP =
            EFFECTS.register("flip", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x9A6CE8));
    /** {@code PLuckyBlock.H4CK3D} : des chiffres verts partout sur l'ecran. */
    public static final DeferredHolder<MobEffect, LuckyEffect> HACKED =
            EFFECTS.register("hacked", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x22D14B));
    /** {@code ItemAprilFish} : un poisson d'argent dans le dos toutes les trente minutes. */
    public static final DeferredHolder<MobEffect, LuckyEffect> APRIL_FISH =
            EFFECTS.register("april_fish", () -> new LuckyEffect(MobEffectCategory.NEUTRAL, 0xF0873A));

    // -- Mai ------------------------------------------------------------------------------------

    /** {@code PotionPink} : la vie en rose, un voile rose sur l'ecran. */
    public static final DeferredHolder<MobEffect, LuckyEffect> PINK =
            EFFECTS.register("pink", () -> new LuckyEffect(MobEffectCategory.NEUTRAL, 0xF59BC8));
    /** {@code PLuckyBlock.CANCEL_FORWARD} : on ne peut plus avancer ({@code LuckyClientEvents}). */
    public static final DeferredHolder<MobEffect, LuckyEffect> NO_FORWARD =
            EFFECTS.register("no_forward", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x7D7D7D));

    // -- Juin -----------------------------------------------------------------------------------

    /** {@code PLuckyBlock.GANGNAM_STYLE} et {@code RADIO} : on danse, on saute, des notes partout. */
    public static final DeferredHolder<MobEffect, LuckyEffect> DANCE =
            EFFECTS.register("dance", () -> new LuckyEffect(MobEffectCategory.NEUTRAL, 0xF26CCB));
    /** {@code PLuckyBlock.QUACK} : chaque message dans le chat devient un « Coin coin ». */
    public static final DeferredHolder<MobEffect, LuckyEffect> QUACK =
            EFFECTS.register("quack", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xF2E14A));
    /** {@code PLuckyBlock.CINEMA_MUET} : plus un son, et l'ecran en gris ({@code LuckyClientEvents}). */
    public static final DeferredHolder<MobEffect, LuckyEffect> SILENT_MOVIE =
            EFFECTS.register("silent_movie", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x8C8C8C));
    /** {@code PLuckyBlock.FIRE_WALK} : le sol prend feu derriere soi. */
    public static final DeferredHolder<MobEffect, LuckyEffect> FIRE_WALK =
            EFFECTS.register("fire_walk", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xFF7A1A));

    private SeasonEffects() {
    }

    private static LuckyEffect modified(MobEffectCategory category, int color, String name, double amount,
                                        Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        LuckyEffect effect = new LuckyEffect(category, color);
        effect.addAttributeModifier(attribute, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "season_" + name),
                amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return effect;
    }

    /** « Quack » : le message part, mais en canard. */
    @SubscribeEvent
    public static void onChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        if (event.getPlayer().hasEffect(QUACK)) {
            event.setMessage(net.minecraft.network.chat.Component.literal("Coin coin !"));
        }
    }

    static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.hasEffect(STAR) && player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.3d, player.getZ(), 2, 0.4d, 0.1d,
                    0.4d, 0.0d);
        }
        if (player.hasEffect(APRIL_FISH) && player.tickCount % 36_000 == 0 && player.tickCount > 0) {
            var look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
            var fish = LuckyWorld.create(level, net.minecraft.world.entity.EntityTypes.SILVERFISH,
                    player.getX() - look.x * 1.5d, player.getY(), player.getZ() - look.z * 1.5d);
            if (fish != null) {
                level.addFreshEntity(fish);
                LuckyWorld.tell(player, "Un poisson d'avril est apparu dans ton dos !", net.minecraft.ChatFormatting.YELLOW);
            }
        }
        if (player.tickCount % 40 == 0 && EasterEvents.ringing(player.getMainHandItem())) {
            level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BELL_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.2f);
        }
        if (player.hasEffect(DANCE) && player.tickCount % 10 == 0) {
            if (player.onGround()) {
                player.setDeltaMovement(player.getDeltaMovement().add(0.0d, 0.42d, 0.0d));
                player.hurtMarked = true;
            }
            level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.2d, player.getZ(), 1, 0.3d, 0.1d,
                    0.3d, player.getRandom().nextFloat());
        }
        if (player.hasEffect(QUACK) && player.tickCount % 200 == 0) {
            LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(), "entity.parrot.ambient",
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        if (player.hasEffect(FIRE_WALK) && player.onGround() && player.tickCount % 4 == 0) {
            net.minecraft.core.BlockPos behind = net.minecraft.core.BlockPos.containing(
                    player.position().subtract(player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize()));
            if (level.getBlockState(behind).isAir() && level.getBlockState(behind.below()).isFaceSturdy(level,
                    behind.below(), net.minecraft.core.Direction.UP)) {
                LuckyWorld.place(player, level, behind, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
            }
        }
        if (player.tickCount % 40 == 0 && JuneEvents.squeaking(player.getMainHandItem())) {
            LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(), "block.note_block.didgeridoo",
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        if (player.hasEffect(NO_OXYGEN) && player.tickCount % 40 == 0
                && !player.getItemBySlot(EquipmentSlot.HEAD).is(SeasonItems.STAR_HELMET.get())) {
            player.hurtServer(level, level.damageSources().drown(), 2.0f);
        }
    }
}
