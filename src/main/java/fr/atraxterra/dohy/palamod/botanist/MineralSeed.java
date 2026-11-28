package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.dimminer.PalaDimMiner;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Les huit graines minerales du Botaniste de JobsReborn ({@code ItemBotanistMineralSeed}) :
 * amethyste, titane, paladium, paladium vert, findium, trixium, invoker et endium.
 *
 * <p><b>Ce qui vient du jar.</b> Chaque graine pose son plant
 * ({@code BlockBotanistMineralSeed}, un buisson a quatre stades qui ne pousse que
 * sur terre labouree) ; le plant garde la vitesse et le caractere « hors ligne »
 * de la graine ; casse avant maturite, il rend sa graine telle quelle ; mur, il
 * rend un nombre de tirages ({@code dropCount}) dans une liste ponderee
 * ({@code BotanistDropConfig}) ou figurent le minerai et la graine, dont les
 * caracteres sont tires a neuf. L'endium sort en endium organique, qui se change
 * en pepite d'endium a l'etabli.
 *
 * <p><b>Ce qui est calibre.</b> Les temps de pousse, les quantites, les poids et le
 * nombre de tirages sont des expressions de la configuration du serveur de
 * Paladium, que le jar client ne porte pas. Les valeurs ci-dessous suivent la
 * valeur des matieres au catalogue (BALANCE.md, « Graines minerales ») : plus la
 * matiere est chere, plus la pousse est longue, plus le tirage rend souvent rien,
 * et plus une recolte rend rarement sa graine — une lignee de graines s'eteint
 * en moyenne apres quelques recoltes, sauf pour l'amethyste, le titane et le
 * paladium au Fermier 100, ou elle se maintient.
 *
 * <p>Le niveau de deblocage et la recompense de chaque graine sont dans
 * {@code data/jobs.json}, l'XP de recolte dans {@code data/job_progression.json} ;
 * ce fichier n'en garde que la copie qui sert au rattrapage
 * ({@link BotanistEvents}).
 */
public enum MineralSeed implements StringRepresentable {
    //            id                 base (ticks)  min max  minerai graine rien  Fermier  graines
    AMETHYST("amethyst", 36_000, 2, 3, 75, 25, 0, 20, 16, () -> PalaItems.AMETHYST_INGOT.get()),
    TITANE("titane", 54_000, 2, 3, 75, 25, 0, 35, 16, () -> PalaItems.TITANE_INGOT.get()),
    PALADIUM("paladium", 72_000, 1, 2, 75, 25, 0, 50, 16, () -> PalaItems.PALADIUM_INGOT.get()),
    GREEN_PALADIUM("green_paladium", 108_000, 1, 1, 70, 25, 5, 60, 8, () -> PalaItems.GREEN_PALADIUM_INGOT.get()),
    FINDIUM("findium", 108_000, 1, 1, 70, 25, 5, 70, 8, () -> PalaItems.FINDIUM.get()),
    TRIXIUM("trixium", 216_000, 1, 1, 60, 25, 15, 80, 4, () -> PalaItems.TRIXIUM.get()),
    INVOKER("invoker", 216_000, 1, 1, 60, 25, 15, 90, 4, () -> PalaDimMiner.INVOKER_STONE.get()),
    ENDIUM("endium", 432_000, 1, 1, 10, 20, 70, 100, 2, () -> PalaBotanist.ORGANIC_ENDIUM.get());

    public static final Codec<MineralSeed> CODEC = StringRepresentable.fromEnum(MineralSeed::values);

    /** Le stade mur : quatre stades, de 0 a 3, comme les textures {@code _stage_0} a {@code _stage_3}. */
    public static final int MATURE = 3;

    private final String id;
    private final int baseGrowthTicks;
    private final int minOre;
    private final int maxOre;
    private final int oreWeight;
    private final int seedWeight;
    private final int emptyWeight;
    private final int unlockLevel;
    private final int rewardCount;
    private final Supplier<? extends ItemLike> ore;

    MineralSeed(String id, int baseGrowthTicks, int minOre, int maxOre, int oreWeight, int seedWeight,
                int emptyWeight, int unlockLevel, int rewardCount, Supplier<? extends ItemLike> ore) {
        this.id = id;
        this.baseGrowthTicks = baseGrowthTicks;
        this.minOre = minOre;
        this.maxOre = maxOre;
        this.oreWeight = oreWeight;
        this.seedWeight = seedWeight;
        this.emptyWeight = emptyWeight;
        this.unlockLevel = unlockLevel;
        this.rewardCount = rewardCount;
        this.ore = ore;
    }

    public String id() {
        return id;
    }

    /** {@code amethyst_seed} : le nom de l'objet, celui de JobsReborn. */
    public String seedName() {
        return id + "_seed";
    }

    /** {@code amethyst_crop} : le nom du plant, celui de JobsReborn. */
    public String cropName() {
        return id + "_crop";
    }

    /** Temps de pousse d'une graine moyenne, en ticks (20 par seconde). */
    public int baseGrowthTicks() {
        return baseGrowthTicks;
    }

    /** Temps de pousse a cette vitesse, en ticks. */
    public long growthTicks(SeedSpeed speed) {
        return Math.max(1L, Math.round(baseGrowthTicks * speed.growthFactor()));
    }

    /** Niveau de Fermier qui permet de la planter. */
    public int unlockLevel() {
        return unlockLevel;
    }

    /** Graines donnees en atteignant {@link #unlockLevel()}. */
    public int rewardCount() {
        return rewardCount;
    }

    public ItemStack oreStack(int count) {
        return new ItemStack(ore.get(), count);
    }

    /**
     * Le nombre de tirages d'une recolte : 2, puis 3 au Fermier 50, 4 au Fermier 100
     * (l'info-bulle de Paladium l'affiche sous « Tirages par recolte »).
     */
    public static int draws(int level) {
        return 2 + SeedSpeed.clampLevel(level) / 50;
    }

    /** Graines rendues en moyenne par une recolte a ce niveau. */
    public double expectedSeeds(int level) {
        return draws(level) * (double) seedWeight / (oreWeight + seedWeight + emptyWeight);
    }

    /**
     * Ce que rend un plant mur, tirage par tirage : du minerai, une graine aux
     * caracteres tires a neuf, ou rien. Les piles identiques sont regroupees.
     */
    List<ItemStack> roll(int level, RandomSource random, ItemLike seedItem) {
        List<ItemStack> drops = new ArrayList<>();
        int total = oreWeight + seedWeight + emptyWeight;
        for (int i = 0; i < draws(level); i++) {
            int pick = random.nextInt(total);
            ItemStack stack;
            if (pick < oreWeight) {
                stack = oreStack(minOre + random.nextInt(maxOre - minOre + 1));
            } else if (pick < oreWeight + seedWeight) {
                stack = MineralSeedItem.create(seedItem, SeedTraits.roll(level, random), 1);
            } else {
                continue;
            }
            merge(drops, stack);
        }
        return drops;
    }

    private static void merge(List<ItemStack> drops, ItemStack stack) {
        for (ItemStack existing : drops) {
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                return;
            }
        }
        drops.add(stack);
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
