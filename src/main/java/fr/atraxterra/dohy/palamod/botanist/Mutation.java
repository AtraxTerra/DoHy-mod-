package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.agri.Climate;
import fr.atraxterra.dohy.palamod.PalaItems;
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
 * <p>Quinze plantes, cinq par climat, sur quatre raretes. Les trois communes (ble de fer,
 * givrelune, corail des sables) ouvrent le metier ; les douze autres ne s'obtiennent qu'en
 * mutation : une floraison peut muter vers une plante de la rarete suivante de son climat
 * ({@link #targets()}), et sa recolte rend alors une mutation inconnue, a reveler au scanner
 * de Genis. Les noms anglais sont les identifiants de JobsReborn ; les noms francais, les
 * familles, varietes et roles sont a nous (dans la configuration de son serveur, absente du
 * jar).
 *
 * <p>Les durees, les recoltes et les besoins de JobsReborn sont aussi dans cette configuration :
 * les valeurs d'ici sont calibrees (voir {@code docs/BALANCE.md}). Toutes se comptent en temps
 * passe pres de la plante : loin de son maitre, elle attend.
 *
 * <p>Le climat est celui de l'agriculture ({@link Climate}, lu sur le biome) :
 * froid pour glacial, chaud pour desertique. Hors de son climat, une plante fane
 * au bourgeon ({@code outOfClimate}).
 */
public enum Mutation implements StringRepresentable {
    // -- Climat normal --------------------------------------------------------------------------
    IRONWHEAT("ironwheat", "Ble de fer", Climate.TEMPERATE, Rarity.COMMON, 8, 12, 10, 60,
            new Sheet("Cereales", "Ferreuse", "Fer et ble"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.ITEM),
            List.of("minecraft:coal", "minecraft:bone_meal", "minecraft:gold_nugget"),
            List.of(new Drop(() -> Items.WHEAT, 1, 2), new Drop(() -> Items.IRON_NUGGET, 3, 6)),
            "glowbloom", "puffbloom"),
    GLOWBLOOM("glowbloom", "Luminelle", Climate.TEMPERATE, Rarity.RARE, 10, 15, 12, 100,
            new Sheet("Luminescentes", "Rayonnante", "Glowstone et baies"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:glowstone_dust", "minecraft:bone_meal", "minecraft:gold_nugget"),
            List.of(new Drop(() -> Items.GLOWSTONE_DUST, 2, 4), new Drop(() -> Items.GLOW_BERRIES, 2, 4)),
            "jade_maw"),
    PUFFBLOOM("puffbloom", "Duvetine", Climate.TEMPERATE, Rarity.RARE, 10, 14, 12, 100,
            new Sheet("Sporees", "Duveteuse", "Plumes et fil"),
            List.of(Need.WATER, Need.SHAKE, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:bone_meal", "minecraft:coal", "minecraft:gold_nugget"),
            List.of(new Drop(() -> Items.FEATHER, 1, 3), new Drop(() -> Items.STRING, 2, 4),
                    new Drop(() -> Items.SLIME_BALL, 0, 1)),
            "jade_maw"),
    JADE_MAW("jade_maw", "Gueule de jade", Climate.TEMPERATE, Rarity.EPIC, 12, 18, 14, 160,
            new Sheet("Carnivores", "Jade", "Slime et prismarine"),
            List.of(Need.WATER, Need.MUSIC, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:gold_nugget", "minecraft:glowstone_dust", "palamod:paladium_ingot"),
            List.of(new Drop(() -> Items.SLIME_BALL, 2, 4), new Drop(() -> Items.PRISMARINE_CRYSTALS, 1, 2),
                    new Drop(() -> Items.EMERALD, 2, 4)),
            "motherroot"),
    MOTHERROOT("motherroot", "Racine-mere", Climate.TEMPERATE, Rarity.LEGENDARY, 15, 22, 18, 260,
            new Sheet("Racines", "Ancestrale", "Paladium et miel"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:bone_meal", "minecraft:glowstone_dust", "palamod:paladium_ingot"),
            List.of(new Drop(() -> PalaItems.PALADIUM_INGOT.get(), 2, 3), new Drop(() -> Items.HONEYCOMB, 1, 3),
                    new Drop(() -> Items.MOSS_BLOCK, 2, 4))),
    // -- Climat glacial -------------------------------------------------------------------------
    MOONFROST("moonfrost", "Givrelune", Climate.COLD, Rarity.COMMON, 8, 13, 10, 70,
            new Sheet("Lunaires", "Givree", "Glace et neige"),
            List.of(Need.WATER, Need.MUSIC, Need.BURST, Need.ITEM),
            List.of("minecraft:snowball", "minecraft:ice", "minecraft:glowstone_dust"),
            List.of(new Drop(() -> Items.SNOWBALL, 2, 4), new Drop(() -> Items.ICE, 1, 2),
                    new Drop(() -> Items.GLOWSTONE_DUST, 1, 2)),
            "thornwick", "songlily"),
    THORNWICK("thornwick", "Meche-epine", Climate.COLD, Rarity.RARE, 10, 15, 12, 100,
            new Sheet("Epineuses", "Glaciale", "Baies et fil"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:snowball", "minecraft:bone_meal", "minecraft:coal"),
            List.of(new Drop(() -> Items.SWEET_BERRIES, 3, 5), new Drop(() -> Items.STRING, 1, 3),
                    new Drop(() -> Items.STICK, 1, 2)),
            "aurelys"),
    SONGLILY("songlily", "Lys chantant", Climate.COLD, Rarity.RARE, 11, 15, 12, 100,
            new Sheet("Lys", "Cristalline", "Amethyste"),
            List.of(Need.WATER, Need.MUSIC, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:ice", "minecraft:snowball", "minecraft:glowstone_dust"),
            List.of(new Drop(() -> Items.AMETHYST_SHARD, 2, 4), new Drop(() -> Items.INK_SAC, 0, 1)),
            "aurelys"),
    AURELYS("aurelys", "Aurelys", Climate.COLD, Rarity.EPIC, 12, 18, 14, 160,
            new Sheet("Lys", "Doree", "Or"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:gold_nugget", "minecraft:ice", "palamod:paladium_ingot"),
            List.of(new Drop(() -> Items.GOLD_NUGGET, 6, 12), new Drop(() -> Items.GOLD_INGOT, 1, 2)),
            "arcanium"),
    ARCANIUM("arcanium", "Arcanium", Climate.COLD, Rarity.LEGENDARY, 15, 22, 18, 260,
            new Sheet("Arcanes", "Astrale", "Lapis et membranes"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:glowstone_dust", "minecraft:ice", "palamod:paladium_ingot"),
            List.of(new Drop(() -> Items.LAPIS_LAZULI, 6, 12), new Drop(() -> Items.AMETHYST_SHARD, 3, 5),
                    new Drop(() -> Items.PHANTOM_MEMBRANE, 0, 1))),
    // -- Climat desertique ----------------------------------------------------------------------
    SAND_CORAL("sand_coral", "Corail des sables", Climate.HOT, Rarity.COMMON, 9, 14, 11, 80,
            new Sheet("Coraux", "Sableuse", "Sable et or"),
            List.of(Need.WATER, Need.SHAKE, Need.BURST, Need.ITEM),
            List.of("minecraft:sand", "minecraft:bone_meal", "minecraft:coal"),
            List.of(new Drop(() -> Items.SAND, 2, 4), new Drop(() -> Items.GOLD_NUGGET, 2, 5),
                    new Drop(() -> Items.CACTUS, 0, 1)),
            "sunbloom", "smokepod"),
    SUNBLOOM("sunbloom", "Fleur solaire", Climate.HOT, Rarity.RARE, 10, 16, 12, 100,
            new Sheet("Solaires", "Ardente", "Or et glowstone"),
            List.of(Need.WATER, Need.SHAKE, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:sand", "minecraft:gold_nugget", "minecraft:glowstone_dust"),
            List.of(new Drop(() -> Items.GOLD_NUGGET, 3, 6), new Drop(() -> Items.GLOWSTONE_DUST, 2, 4),
                    new Drop(() -> Items.HONEYCOMB, 0, 1)),
            "miasma_bloom"),
    SMOKEPOD("smokepod", "Cosse fumante", Climate.HOT, Rarity.RARE, 10, 15, 13, 100,
            new Sheet("Cosses", "Fumante", "Charbon et poudre"),
            List.of(Need.WATER, Need.MUSIC, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:coal", "minecraft:sand", "minecraft:bone_meal"),
            List.of(new Drop(() -> Items.COAL, 2, 4), new Drop(() -> Items.GUNPOWDER, 1, 2)),
            "miasma_bloom"),
    MIASMA_BLOOM("miasma_bloom", "Fleur des miasmes", Climate.HOT, Rarity.EPIC, 13, 18, 14, 160,
            new Sheet("Toxiques", "Putride", "Yeux d'araignee"),
            List.of(Need.WATER, Need.SHAKE, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:coal", "minecraft:bone_meal", "palamod:paladium_ingot"),
            List.of(new Drop(() -> Items.SPIDER_EYE, 2, 3), new Drop(() -> Items.NETHER_WART, 2, 4),
                    new Drop(() -> Items.SLIME_BALL, 0, 1)),
            "ember_orchid"),
    EMBER_ORCHID("ember_orchid", "Orchidee de braise", Climate.HOT, Rarity.LEGENDARY, 16, 22, 18, 260,
            new Sheet("Orchidees", "Volcanique", "Blaze et magma"),
            List.of(Need.WATER, Need.MUSIC, Need.SHAKE, Need.BURST, Need.ITEM, Need.NIBBLER),
            List.of("minecraft:coal", "minecraft:sand", "palamod:paladium_ingot"),
            List.of(new Drop(() -> Items.BLAZE_ROD, 2, 3), new Drop(() -> Items.MAGMA_CREAM, 1, 3),
                    new Drop(() -> Items.GOLD_INGOT, 0, 1)));

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

    /**
     * La fiche du codex ({@code family}, {@code variety}, {@code role} de JobsReborn) : la famille,
     * la variete, et ce que la plante produit. Sans accents (polices du kit).
     */
    public record Sheet(String family, String variety, String role) {
    }

    private final String id;
    private final String label;
    private final Climate climate;
    private final Rarity rarity;
    private final int budTicks;
    private final int bloomTicks;
    private final int rebloomTicks;
    private final int xp;
    private final Sheet sheet;
    private final List<Need> needs;
    private final List<String> needItems;
    private final List<Drop> drops;
    /** Les identifiants des plantes vers lesquelles une floraison peut muter. */
    private final List<String> targetIds;

    Mutation(String id, String label, Climate climate, Rarity rarity, int budMinutes, int bloomMinutes,
             int rebloomMinutes, int xp, Sheet sheet, List<Need> needs, List<String> needItems, List<Drop> drops,
             String... targets) {
        this.id = id;
        this.label = label;
        this.climate = climate;
        this.rarity = rarity;
        this.budTicks = budMinutes * 60 * 20;
        this.bloomTicks = bloomMinutes * 60 * 20;
        this.rebloomTicks = rebloomMinutes * 60 * 20;
        this.xp = xp;
        this.sheet = sheet;
        this.needs = needs;
        this.needItems = needItems;
        this.drops = drops;
        this.targetIds = List.of(targets);
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

    public Rarity rarity() {
        return rarity;
    }

    /** Le niveau de Fermier pour la planter (aussi dans {@code data/jobs.json}) : celui de sa rarete. */
    public int level() {
        return rarity.level();
    }

    public Sheet sheet() {
        return sheet;
    }

    /** Les plantes vers lesquelles une floraison de celle-ci peut muter (vide : bout de lignee). */
    public List<Mutation> targets() {
        List<Mutation> found = new ArrayList<>();
        for (String target : targetIds) {
            Mutation mutation = byId(target);
            if (mutation != null) {
                found.add(mutation);
            }
        }
        return found;
    }

    /** Les plantes dont une floraison peut donner celle-ci (« Peut aussi apparaitre a la floraison de »). */
    public List<Mutation> parents() {
        List<Mutation> found = new ArrayList<>();
        for (Mutation mutation : values()) {
            if (mutation.targetIds.contains(id)) {
                found.add(mutation);
            }
        }
        return found;
    }

    /** La chance qu'une floraison mute, celle de sa rarete ; nulle sans plante vers laquelle muter. */
    public double mutationChance() {
        return targetIds.isEmpty() ? 0.0d : rarity.chance();
    }

    /** L'XP de Fermier de sa decouverte au scanner. */
    public int discoveryXp() {
        return rarity.discoveryXp();
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
