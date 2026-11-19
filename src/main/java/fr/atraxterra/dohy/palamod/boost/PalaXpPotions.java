package fr.atraxterra.dohy.palamod.boost;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Les potions d'XP de metier : la Potion de Double XP de PalaMod
 * ({@code ItemBoostDoubleXp}) et sa grande soeur, la Triple, propre a DoHy.
 *
 * <p>Ce que dit le code d'origine : une potion qui se boit en 32 ticks, a
 * l'unite, en quatre durees (30 min, 1 h, 2 h, 4 h : la metadonnee 0 a 3). Bue,
 * elle double l'XP de <b>tous</b> les metiers ({@code JobsManager.setDoubleXpUntil}) ;
 * bue pendant un double XP deja actif, sa duree s'ajoute au reste.
 *
 * <p>Ce qui change : PalaMod comptait en temps reel (le boost filait hors
 * ligne) ; ici c'est un effet, qui ne compte que le temps de jeu, comme les
 * boosters. Le lait ne l'enleve pas et il survit a la mort ({@link XpPotionEvents}).
 * Le x3 passe avant le x2 ; les deux s'ecoulent ensemble.
 */
public final class PalaXpPotions {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    /** Les quatre durees de PalaMod, en minutes de jeu. */
    public static final int[] DURATIONS = {30, 60, 120, 240};

    /** La duree d'une potion, en minutes. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MINUTES =
            COMPONENTS.registerComponentType("boost_minutes",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<MobEffect, JobXpEffect> DOUBLE_JOB_XP =
            EFFECTS.register("double_job_xp", () -> new JobXpEffect(0x73F341));
    public static final DeferredHolder<MobEffect, JobXpEffect> TRIPLE_JOB_XP =
            EFFECTS.register("triple_job_xp", () -> new JobXpEffect(0xFFD24A));

    public static final DeferredItem<XpPotionItem> DOUBLE_XP_POTION = ITEMS.registerItem(
            "double_xp_potion", properties -> new XpPotionItem(2, potion(properties)));
    public static final DeferredItem<XpPotionItem> TRIPLE_XP_POTION = ITEMS.registerItem(
            "triple_xp_potion", properties -> new XpPotionItem(3, potion(properties)));

    private PalaXpPotions() {
    }

    /** A l'unite, bue en 32 ticks ({@code getMaxItemUseDuration}), sans fiole rendue. */
    private static Item.Properties potion(Item.Properties properties) {
        // La duree n'est pas posee ici : le type de composant n'est pas forcement
        // enregistre avant les objets. Sans composant, une potion dure 1 h.
        return properties.stacksTo(1)
                .component(DataComponents.CONSUMABLE, Consumable.builder()
                        .consumeSeconds(1.6f)
                        .animation(ItemUseAnimation.DRINK)
                        .sound(SoundEvents.GENERIC_DRINK)
                        .hasConsumeParticles(false)
                        .build());
    }

    /** L'effet d'un multiplicateur : 2 ou 3. */
    public static Holder<MobEffect> effect(int multiplier) {
        return multiplier >= 3 ? TRIPLE_JOB_XP : DOUBLE_JOB_XP;
    }

    /** La duree d'une potion, en minutes : 1 h sans composant. */
    public static int minutes(ItemStack stack) {
        return Math.max(1, stack.getOrDefault(MINUTES.get(), 60));
    }

    /** Une potion de cette duree. */
    public static ItemStack stack(int multiplier, int minutes) {
        ItemStack stack = new ItemStack(multiplier >= 3 ? TRIPLE_XP_POTION.get() : DOUBLE_XP_POTION.get());
        stack.set(MINUTES.get(), minutes);
        return stack;
    }

    /**
     * Les potions de la boutique : multiplicateur, minutes, prix. PalaMod les
     * vendait en argent reel ; ici en argent du jeu, au double environ de ce que
     * rapporte l'XP gagnee en plus au rythme de reference (BALANCE.md, 6 duovicies).
     */
    public static final List<Offer> SHOP = List.of(
            new Offer(2, 60, 120_000L),
            new Offer(2, 240, 400_000L),
            new Offer(3, 60, 300_000L),
            new Offer(3, 240, 1_000_000L));

    /** Une potion en vente. */
    public record Offer(int multiplier, int minutes, long price) {
    }

    /** L'offre de la boutique pour cette potion, ou {@code null}. */
    public static Offer offer(int multiplier, int minutes) {
        for (Offer offer : SHOP) {
            if (offer.multiplier() == multiplier && offer.minutes() == minutes) {
                return offer;
            }
        }
        return null;
    }

    /** Le multiplicateur d'XP de metier du joueur : 3, 2 ou 1. */
    public static int multiplier(ServerPlayer player) {
        if (player.hasEffect(TRIPLE_JOB_XP)) {
            return 3;
        }
        return player.hasEffect(DOUBLE_JOB_XP) ? 2 : 1;
    }

    /** Les huit potions de l'onglet creatif, comme les quatre metadonnees de PalaMod par potion. */
    public static List<ItemStack> tab() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int multiplier : new int[] {2, 3}) {
            for (int minutes : DURATIONS) {
                stacks.add(stack(multiplier, minutes));
            }
        }
        return stacks;
    }

    /** « 30 min », « 1 h », « 1 h 30 ». */
    public static String label(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        return minutes % 60 == 0 ? minutes / 60 + " h" : minutes / 60 + " h " + String.format("%02d", minutes % 60);
    }

    /** Une instance d'effet de cette duree, sans particules, l'icone visible. */
    static MobEffectInstance instance(int multiplier, int ticks) {
        return new MobEffectInstance(effect(multiplier), ticks, 0, false, false, true);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
    }
}
