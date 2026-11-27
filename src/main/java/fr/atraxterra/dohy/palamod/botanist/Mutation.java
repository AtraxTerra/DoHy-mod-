package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.agri.Climate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Les plantes du laboratoire, les « mutations » de JobsReborn
 * ({@code BotanistMutationConfig}).
 *
 * <p>Ce lot en apporte trois, une par climat, celles qui ouvrent le metier : le
 * ble de fer (climat normal), la givrelune (glacial) et le corail des sables
 * (desertique). Les douze autres, et les mutations a la floraison, sont du lot
 * suivant.
 *
 * <p>Les durees, les recoltes et les besoins de JobsReborn sont dans la
 * configuration de son serveur, absente du jar : les valeurs d'ici sont calibrees
 * (voir {@code docs/BALANCE.md}). Toutes se comptent en temps passe pres de la
 * plante : loin de son maitre, elle attend.
 *
 * <p>Le climat est celui de l'agriculture ({@link Climate}, lu sur le biome) :
 * froid pour glacial, chaud pour desertique. Hors de son climat, une plante fane
 * au bourgeon ({@code outOfClimate}).
 */
public enum Mutation implements StringRepresentable {
    IRONWHEAT("ironwheat", "Ble de fer", Climate.TEMPERATE, 10, 8, 12, 10, 60,
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.ITEM),
            List.of("minecraft:coal", "minecraft:bone_meal", "minecraft:gold_nugget"),
            List.of(new Drop(() -> Items.WHEAT, 1, 2), new Drop(() -> Items.IRON_NUGGET, 3, 6))),
    MOONFROST("moonfrost", "Givrelune", Climate.COLD, 10, 8, 13, 10, 70,
            List.of(Need.WATER, Need.MUSIC, Need.BURST, Need.ITEM),
            List.of("minecraft:snowball", "minecraft:ice", "minecraft:glowstone_dust"),
            List.of(new Drop(() -> Items.SNOWBALL, 2, 4), new Drop(() -> Items.ICE, 1, 2),
                    new Drop(() -> Items.GLOWSTONE_DUST, 1, 2))),
    SAND_CORAL("sand_coral", "Corail des sables", Climate.HOT, 10, 9, 14, 11, 80,
            List.of(Need.WATER, Need.SHAKE, Need.BURST, Need.ITEM),
            List.of("minecraft:sand", "minecraft:bone_meal", "minecraft:coal"),
            List.of(new Drop(() -> Items.SAND, 2, 4), new Drop(() -> Items.GOLD_NUGGET, 2, 5),
                    new Drop(() -> Items.CACTUS, 0, 1)));

    /** Les recoltes d'une plante : une par floraison. */
    public static final int HARVESTS = 3;
    /** Une fleur non recoltee fane en vingt minutes, et la plante fanee disparait en vingt de plus. */
    public static final int WILT_TICKS = 20 * 60 * 20;
    public static final int GONE_TICKS = 20 * 60 * 20;
    /** Les graines rendues par une plante fanee : une ou deux. */
    public static final int SEEDS_MIN = 1;
    public static final int SEEDS_MAX = 2;
    /** L'XP de Fermier d'un besoin rempli. */
    public static final int NEED_XP = 15;

    /** Une recolte : un objet, entre min et max. */
    public record Drop(Supplier<Item> item, int min, int max) {
    }

    private final String id;
    private final String label;
    private final Climate climate;
    private final int level;
    private final int budTicks;
    private final int bloomTicks;
    private final int rebloomTicks;
    private final int xp;
    private final List<Need> needs;
    private final List<String> needItems;
    private final List<Drop> drops;

    Mutation(String id, String label, Climate climate, int level, int budMinutes, int bloomMinutes,
             int rebloomMinutes, int xp, List<Need> needs, List<String> needItems, List<Drop> drops) {
        this.id = id;
        this.label = label;
        this.climate = climate;
        this.level = level;
        this.budTicks = budMinutes * 60 * 20;
        this.bloomTicks = bloomMinutes * 60 * 20;
        this.rebloomTicks = rebloomMinutes * 60 * 20;
        this.xp = xp;
        this.needs = needs;
        this.needItems = needItems;
        this.drops = drops;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    /** Le nom affiche, sans accent (police du jeu). */
    public String label() {
        return label;
    }

    public Climate climate() {
        return climate;
    }

    /** Le niveau de Fermier pour la planter (aussi dans {@code data/jobs.json}). */
    public int level() {
        return level;
    }

    /** De la graine au bourgeon. */
    public int budTicks() {
        return budTicks;
    }

    /** Du bourgeon a la premiere fleur. */
    public int bloomTicks() {
        return bloomTicks;
    }

    /** D'une recolte a la fleur suivante. */
    public int rebloomTicks() {
        return rebloomTicks;
    }

    /** L'XP de Fermier d'une recolte. */
    public int xp() {
        return xp;
    }

    public List<Need> needs() {
        return needs;
    }

    public List<String> needItems() {
        return needItems;
    }

    public List<Drop> drops() {
        return drops;
    }

    public String seedName() {
        return id + "_seed";
    }

    /** Ce que rend une recolte. */
    public List<ItemStack> roll(RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Drop drop : drops) {
            int count = Mth.nextInt(random, drop.min(), drop.max());
            if (count > 0) {
                stacks.add(new ItemStack(drop.item().get(), count));
            }
        }
        return stacks;
    }

    public Need pickNeed(RandomSource random) {
        return needs.get(random.nextInt(needs.size()));
    }

    public String pickItem(RandomSource random) {
        return needItems.get(random.nextInt(needItems.size()));
    }

    /** L'objet d'un besoin, depuis son identifiant ; l'air si le registre ne le connait pas. */
    public static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
    }

    /** Le climat dans les mots de JobsReborn ({@code BotanistClimate}). */
    public static String climateLabel(Climate climate) {
        return switch (climate) {
            case COLD -> "glacial";
            case HOT -> "desertique";
            default -> "normal";
        };
    }

    public static Mutation byId(String id) {
        for (Mutation mutation : values()) {
            if (mutation.id.equals(id)) {
                return mutation;
            }
        }
        return null;
    }
}
