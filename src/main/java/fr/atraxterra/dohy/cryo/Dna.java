package fr.atraxterra.dohy.cryo;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * L'ADN : de quelle bete il vient, son grade, et ce que la chambre cryogenique en
 * tire.
 *
 * <p>Seules les betes paisibles donnent un ADN : les categories d'apparition des
 * animaux, des poissons et des chauves-souris, moins celles que le tag
 * {@code dohy:no_dna} ecarte. Une bete dont le clone ne donnerait rien (pas de
 * table de butin, rien a produire) n'en donne pas non plus : la seringue le dit
 * plutot que de remplir une fiole inutile.
 *
 * <h2>Le grade</h2>
 *
 * <p>Un ADN preleve est de grade I. {@link #FUSION} ADN d'une meme bete et d'un
 * meme grade en font un du grade suivant, jusqu'au grade V : un grade V demande
 * donc 256 prelevements. La chambre va plus vite et tire plus de butin a chaque
 * grade ; au grade V elle produit douze fois ce qu'elle produit au grade I.
 */
public final class Dna {

    public static final int MAX_GRADE = 5;
    /** ADN d'un grade qu'il faut pour un ADN du grade suivant. */
    public static final int FUSION = 4;
    /** Une bete ne se preleve qu'une fois toutes les cinq minutes de jeu. */
    public static final long COOLDOWN = 20L * 60L * 5L;

    /** La duree d'un cycle de la chambre, en ticks, par grade. */
    private static final int[] CYCLE = {2400, 1800, 1200, 900, 600};
    /** Les tirages de butin par cycle, par grade. */
    private static final int[] ROLLS = {1, 1, 2, 2, 3};
    /** La bague de la fiole, par grade : acier, vert, bleu, violet, or. */
    private static final int[] GRADE_COLORS = {0xFF9AA4AE, 0xFF4BD66B, 0xFF3F8CF0, 0xFFB45CF0, 0xFFF2C230};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    public static final TagKey<EntityType<?>> NO_DNA = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "no_dna"));

    private static final Set<MobCategory> PASSIVE = EnumSet.of(MobCategory.CREATURE, MobCategory.AMBIENT,
            MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT, MobCategory.UNDERGROUND_WATER_CREATURE,
            MobCategory.AXOLOTLS);

    /** La couleur du liquide de la fiole, par bete ; les autres tirent la leur de leur nom. */
    private static final Map<EntityType<?>, Integer> COLORS = Map.ofEntries(
            Map.entry(EntityTypes.COW, 0x7A5232), Map.entry(EntityTypes.MOOSHROOM, 0xC23A2E),
            Map.entry(EntityTypes.PIG, 0xF2A3A0), Map.entry(EntityTypes.SHEEP, 0xEDEDE6),
            Map.entry(EntityTypes.CHICKEN, 0xF4EAD2), Map.entry(EntityTypes.RABBIT, 0xB0926C),
            Map.entry(EntityTypes.HORSE, 0x8E5B2C), Map.entry(EntityTypes.DONKEY, 0x8C8274),
            Map.entry(EntityTypes.MULE, 0x5E4632), Map.entry(EntityTypes.LLAMA, 0xDCCBA2),
            Map.entry(EntityTypes.CAMEL, 0xDBA968), Map.entry(EntityTypes.GOAT, 0xD4D0C4),
            Map.entry(EntityTypes.CAT, 0x9C7752), Map.entry(EntityTypes.OCELOT, 0xE2B252),
            Map.entry(EntityTypes.WOLF, 0xC9C1B8), Map.entry(EntityTypes.FOX, 0xE27C2E),
            Map.entry(EntityTypes.PARROT, 0x34B84E), Map.entry(EntityTypes.PANDA, 0xA0A0A0),
            Map.entry(EntityTypes.POLAR_BEAR, 0xF4F4F0), Map.entry(EntityTypes.BEE, 0xF4C430),
            Map.entry(EntityTypes.TURTLE, 0x44A84E), Map.entry(EntityTypes.FROG, 0xC97C3C),
            Map.entry(EntityTypes.AXOLOTL, 0xF49ECA), Map.entry(EntityTypes.SQUID, 0x30527E),
            Map.entry(EntityTypes.GLOW_SQUID, 0x34D8C8), Map.entry(EntityTypes.COD, 0xB49A6C),
            Map.entry(EntityTypes.SALMON, 0xC4483C), Map.entry(EntityTypes.TROPICAL_FISH, 0xF2822C),
            Map.entry(EntityTypes.PUFFERFISH, 0xE8C832), Map.entry(EntityTypes.DOLPHIN, 0x7393AC),
            Map.entry(EntityTypes.SNIFFER, 0xB44A30), Map.entry(EntityTypes.ARMADILLO, 0xAC6654),
            Map.entry(EntityTypes.STRIDER, 0xA0403C), Map.entry(EntityTypes.BAT, 0x5A4A3A),
            Map.entry(EntityTypes.ALLAY, 0x5CC6F2));

    /** Ce que la bete produit de son vivant, en plus de son butin : l'oeuf, l'ecaille, le rayon de miel. */
    private record Extra(Item item, float chance) {
    }

    private static final Map<EntityType<?>, List<Extra>> EXTRAS = Map.of(
            EntityTypes.CHICKEN, List.of(new Extra(Items.EGG, 0.6f)),
            EntityTypes.ARMADILLO, List.of(new Extra(Items.ARMADILLO_SCUTE, 1.0f)),
            EntityTypes.TURTLE, List.of(new Extra(Items.TURTLE_SCUTE, 0.2f)),
            EntityTypes.SNIFFER, List.of(new Extra(Items.TORCHFLOWER_SEEDS, 0.3f), new Extra(Items.PITCHER_POD, 0.15f)),
            EntityTypes.BEE, List.of(new Extra(Items.HONEYCOMB, 0.5f)));

    private Dna() {
    }

    // -- Les betes ---------------------------------------------------------------------------

    /** Vrai si cette bete est paisible : un ADN peut s'en prelever. */
    public static boolean passive(EntityType<?> type) {
        return PASSIVE.contains(type.getCategory()) && !type.builtInRegistryHolder().is(NO_DNA);
    }

    /** Vrai si le clone de cette bete donnerait quelque chose : un butin ou une production. */
    public static boolean yields(ServerLevel level, EntityType<?> type) {
        if (EXTRAS.containsKey(type)) {
            return true;
        }
        return type.getDefaultLootTable()
                .map(key -> level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY)
                .orElse(false);
    }

    /** Ce que la bete produit de son vivant, tire au sort pour un cycle. */
    static List<ItemStack> extras(EntityType<?> type, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (Extra extra : EXTRAS.getOrDefault(type, List.of())) {
            if (random.nextFloat() < extra.chance()) {
                out.add(new ItemStack(extra.item()));
            }
        }
        return out;
    }

    // -- La fiole ------------------------------------------------------------------------------

    /** Une fiole d'ADN de cette bete, a ce grade. */
    public static ItemStack stack(EntityType<?> type, int grade) {
        int clamped = Math.max(1, Math.min(MAX_GRADE, grade));
        ItemStack stack = new ItemStack(DoHyCryo.DNA.get());
        stack.set(DoHyCryo.DNA_MOB.get(), BuiltInRegistries.ENTITY_TYPE.getKey(type));
        stack.set(DoHyCryo.DNA_GRADE.get(), clamped);
        // la teinte du liquide et celle de la bague, lues par la definition de l'objet
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(),
                List.of(color(type), GRADE_COLORS[clamped - 1])));
        return stack;
    }

    /** La bete d'une fiole, vide si ce n'est pas une fiole d'ADN ou si la bete n'existe plus. */
    public static Optional<EntityType<?>> type(ItemStack stack) {
        if (!stack.is(DoHyCryo.DNA.get())) {
            return Optional.empty();
        }
        Identifier id = stack.get(DoHyCryo.DNA_MOB.get());
        return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
    }

    /** Le grade d'une fiole, 1 par defaut. */
    public static int grade(ItemStack stack) {
        Integer grade = stack.get(DoHyCryo.DNA_GRADE.get());
        return grade == null ? 1 : Math.max(1, Math.min(MAX_GRADE, grade));
    }

    public static String roman(int grade) {
        return ROMAN[Math.max(1, Math.min(MAX_GRADE, grade)) - 1];
    }

    public static int gradeColor(int grade) {
        return GRADE_COLORS[Math.max(1, Math.min(MAX_GRADE, grade)) - 1];
    }

    /** La couleur d'une bete : la sienne, ou une teinte tiree de son nom pour les betes des autres mods. */
    public static int color(EntityType<?> type) {
        Integer known = COLORS.get(type);
        if (known != null) {
            return 0xFF000000 | known;
        }
        int hash = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().hashCode();
        return 0xFF000000 | hue((hash & 0xFFFF) / 65535.0f, 0.55f, 0.85f);
    }

    /** Une teinte, une saturation, une valeur : la couleur RVB. */
    private static int hue(float h, float s, float v) {
        float sector = (h - (float) Math.floor(h)) * 6.0f;
        int index = (int) sector;
        float f = sector - index;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r;
        float g;
        float b;
        switch (index) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255.0f) << 16) | (Math.round(g * 255.0f) << 8) | Math.round(b * 255.0f);
    }

    // -- La chambre --------------------------------------------------------------------------

    /** La duree d'un cycle, en ticks. */
    public static int cycle(int grade) {
        return CYCLE[Math.max(1, Math.min(MAX_GRADE, grade)) - 1];
    }

    /** Les tirages de butin d'un cycle. */
    public static int rolls(int grade) {
        return ROLLS[Math.max(1, Math.min(MAX_GRADE, grade)) - 1];
    }
}
