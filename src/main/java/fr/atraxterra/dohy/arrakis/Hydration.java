package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La soif sur Arrakis.
 *
 * <p>Chaque joueur porte une reserve d'eau de 0 a {@link #MAX} (la barre bleue du HUD).
 * Elle ne baisse que sur Arrakis, hors d'Arrakeen (la ville a ses citernes) : en plein
 * soleil, celle d'un homme qui marche dure huit minutes ; a l'ombre, la nuit ou dans la
 * cabine d'une moissonneuse, plus de deux fois plus ; en courant, deux fois moins. Le
 * distille en garde la plus grande part : chaque piece portee ralentit la soif, les
 * quatre ensemble de pres de sept fois.
 *
 * <p>On boit a la gourde, au piege a vent, une bouteille d'eau, ou a la borne EAU
 * d'Arrakeen. Sous {@value #THIRSTY}, on ralentit ; sous {@value #PARCHED}, on faiblit
 * et la tete tourne ; a sec, on perd de la vie jusqu'a boire ou mourir. Hors d'Arrakis,
 * la reserve se remplit d'elle-meme, vite ; on reapparait desaltere.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class Hydration {

    /** La reserve pleine : comme la faim, vingt points. */
    public static final float MAX = 20.0f;
    /** Une gorgee : un cinquieme de la reserve. */
    public static final float SIP = 4.0f;
    /** Sous ce seuil, on a soif : on ralentit. */
    public static final int THIRSTY = 6;
    /** Sous celui-ci, on se desseche : faiblesse, fatigue, vertiges. */
    public static final int PARCHED = 3;

    /** En plein soleil, l'eau d'un homme qui marche dure huit minutes. */
    private static final float BASE = MAX / (8.0f * 60.0f);
    /** A l'ombre, la nuit, dans une cabine : la soif va plus de deux fois moins vite. */
    private static final float SHADE = 0.45f;
    private static final float SPRINT = 2.2f;
    /** Ce que garde chaque piece du distille : le masque, la combinaison, les jambieres, les bottes. */
    private static final float MASK = 0.15f;
    private static final float SUIT = 0.35f;
    private static final float LEGS = 0.25f;
    private static final float BOOTS = 0.10f;
    /** Le distille de sietch, cousu a nouveau : chaque piece garde un peu plus (0,93 en tout). */
    private static final float SIETCH_MASK = 0.17f;
    private static final float SIETCH_SUIT = 0.39f;
    private static final float SIETCH_LEGS = 0.26f;
    private static final float SIETCH_BOOTS = 0.11f;
    /** Hors d'Arrakis, on boit a sa soif : la reserve regagne un point par seconde. */
    private static final float ELSEWHERE = 1.0f;
    /** A sec, un demi-coeur toutes les trois secondes. */
    private static final int DAMAGE_EVERY = 3;

    /** Le dernier seuil annonce a chaque joueur, pour ne pas repeter les avertissements. */
    private static final Map<UUID, Integer> STAGE = new HashMap<>();

    private Hydration() {
    }

    /** L'eau qu'il reste au joueur, de 0 a {@link #MAX}. */
    public static float water(Player player) {
        return player.getData(DoHyArrakis.HYDRATION);
    }

    /** Vrai s'il lui manque au moins un point : une gorgee ne serait pas perdue. */
    public static boolean thirsty(Player player) {
        return water(player) <= MAX - 1.0f;
    }

    /** Il boit : {@code amount} points d'eau de plus. */
    public static void drink(ServerPlayer player, float amount) {
        set(player, water(player) + amount);
    }

    static void set(ServerPlayer player, float value) {
        float clamped = Mth.clamp(value, 0.0f, MAX);
        if (clamped != water(player)) {
            player.setData(DoHyArrakis.HYDRATION, clamped);
        }
    }

    /** La part de l'eau du corps que garde le distille porte : 0,85 pour celui d'Arrakeen, 0,93 pour celui d'un sietch. */
    public static float saved(Player player) {
        float saved = 0.0f;
        saved += worn(player, EquipmentSlot.HEAD, ArrakisContent.STILLSUIT_MASK.get(), MASK,
                SietchContent.SIETCH_MASK.get(), SIETCH_MASK);
        saved += worn(player, EquipmentSlot.CHEST, ArrakisContent.STILLSUIT_SUIT.get(), SUIT,
                SietchContent.SIETCH_SUIT.get(), SIETCH_SUIT);
        saved += worn(player, EquipmentSlot.LEGS, ArrakisContent.STILLSUIT_LEGGINGS.get(), LEGS,
                SietchContent.SIETCH_LEGGINGS.get(), SIETCH_LEGS);
        saved += worn(player, EquipmentSlot.FEET, ArrakisContent.STILLSUIT_BOOTS.get(), BOOTS,
                SietchContent.SIETCH_BOOTS.get(), SIETCH_BOOTS);
        return saved;
    }

    private static float worn(Player player, EquipmentSlot slot, Item piece, float share, Item sietch,
                              float sietchShare) {
        ItemStack worn = player.getItemBySlot(slot);
        return worn.is(sietch) ? sietchShare : worn.is(piece) ? share : 0.0f;
    }

    /** Vrai si le joueur a le soleil d'Arrakis sur la tete. */
    public static boolean inSun(Player player) {
        Level level = player.level();
        return level.isBrightOutside() && !(player.getVehicle() instanceof SpiceHarvester)
                && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
    }

    /** Vrai a Arrakeen : la ville a ses citernes, on n'y a pas soif. */
    public static boolean inCity(Player player) {
        return Math.hypot(player.getX(), player.getZ()) < ArrakisRelief.CITY_R;
    }

    /** Ce que le joueur perd d'eau par seconde, la ou il se tient. */
    public static float loss(Player player) {
        if (inCity(player)) {
            return 0.0f;
        }
        float rate = BASE * (inSun(player) ? 1.0f : SHADE);
        if (player.isSprinting()) {
            rate *= SPRINT;
        }
        return rate * (1.0f - saved(player));
    }

    /** Combien de minutes la reserve pleine durerait au rythme d'ici (pour les menus). */
    public static int minutesLeft(Player player) {
        float loss = loss(player);
        return loss <= 0.0f ? -1 : Math.round(water(player) / loss / 60.0f);
    }

    // -- Chaque seconde ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !player.isAlive()) {
            return;
        }
        float water = water(player);
        if (!DoHyArrakis.onArrakis(player)) {
            if (water < MAX) {
                set(player, water + ELSEWHERE);
            }
            STAGE.remove(player.getUUID());
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float now = water - loss(player);
        set(player, now);
        effects(player, now);
        announce(player, now);
    }

    private static void effects(ServerPlayer player, float water) {
        if (water <= THIRSTY) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 0, true, false, true));
        }
        if (water <= PARCHED) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 50, 0, true, false, true));
            if (player.tickCount % (20 * 20) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0, true, false, true));
            }
        }
        if (water <= 0.0f && player.tickCount % (20 * DAMAGE_EVERY) == 0
                && player.level() instanceof ServerLevel level) {
            player.hurtServer(level, level.damageSources().source(ArrakisContent.DEHYDRATION), 1.0f);
        }
    }

    /** Les avertissements : une fois chaque seuil franchi en descendant. */
    private static void announce(ServerPlayer player, float water) {
        int stage = water <= 0.0f ? 4 : water <= PARCHED ? 3 : water <= THIRSTY ? 2 : water <= MAX / 2.0f ? 1 : 0;
        int before = STAGE.getOrDefault(player.getUUID(), 0);
        if (stage > before) {
            switch (stage) {
                case 1 -> player.sendSystemMessage(Component.literal("La soif vient : garde de l'eau sur toi.")
                        .withStyle(ChatFormatting.GOLD), true);
                case 2 -> player.sendSystemMessage(Component.literal(
                        "Tu as soif.")
                        .withStyle(ChatFormatting.GOLD));
                case 3 -> player.sendSystemMessage(Component.literal(
                        "Tu te desseches : bois vite, ou rentre a Arrakeen !").withStyle(ChatFormatting.RED));
                case 4 -> player.sendSystemMessage(Component.literal("Deshydratation : tu perds de la vie.")
                        .withStyle(ChatFormatting.DARK_RED));
                default -> {
                }
            }
        }
        STAGE.put(player.getUUID(), stage);
    }

    // -- Boire ----------------------------------------------------------------------------

    /** Une bouteille d'eau bue desaltere d'une gorgee. */
    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PotionContents potion = event.getItem().get(DataComponents.POTION_CONTENTS);
            if (potion != null && potion.is(Potions.WATER)) {
                drink(player, SIP);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STAGE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        STAGE.clear();
    }
}
