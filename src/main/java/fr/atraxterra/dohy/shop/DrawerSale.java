package fr.atraxterra.dohy.shop;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Vendre le contenu d'un tiroir de Storage Drawers ({@code storagedrawers}) sans le vider a la main,
 * et le recuperer apres. Berskar n'en depend pas : tout passe par les donnees de l'objet.
 *
 * <p>Un tiroir casse plein garde son contenu (mode {@code KEEP}, celui du mod par defaut) : l'objet
 * porte les donnees de son bloc ({@code BLOCK_ENTITY_DATA}). Un tiroir simple y range une liste
 * {@code Drawers}, une entree par case ({@code Item}, {@code Count}, et {@code Missing} si la case a
 * ete retiree) ; un compacteur, un {@code Drawers} avec son reservoir commun ({@code Count}, compte
 * dans la plus petite forme) et ses formes ({@code Items} : {@code Item}, {@code Slot}, {@code Conv},
 * le nombre de petites unites par piece). Une case retiree du bloc ({@code detached_drawer_full})
 * porte la sienne dans le composant {@code detached_drawer_content}.
 *
 * <p>Ce qui se vend : ce que le catalogue rachete et dont la vente est ouverte (collection). Le
 * reste demeure dans le tiroir. Vide et sans amelioration, le tiroir redevient un tiroir neuf, qui
 * s'empile ; une case se vide comme le mod la vide (elle oublie son objet, sauf verrou de la cle).
 * Un compacteur vend d'abord la forme qui paie le mieux par petite unite.
 *
 * <p>Refuses : le tiroir ferme par la cle personnelle d'un autre, et celui qui porte l'amelioration
 * de distribution creative (un stock sans fin).
 */
public final class DrawerSale {

    static final String NAMESPACE = "storagedrawers";
    private static final String VENDING = "storagedrawers:creative_vending_upgrade";
    private static final Identifier DETACHED_FULL = Identifier.fromNamespaceAndPath(NAMESPACE, "detached_drawer_full");
    private static final Identifier DETACHED_EMPTY = Identifier.fromNamespaceAndPath(NAMESPACE, "detached_drawer");
    private static final Identifier DETACHED_CONTENT =
            Identifier.fromNamespaceAndPath(NAMESPACE, "detached_drawer_content");
    /** {@code LockAttribute.LOCK_POPULATED} : une case verrouillee garde son objet, vide. */
    private static final int LOCK_POPULATED = 1;

    private static boolean warned;

    private DrawerSale() {
    }

    /** Pourquoi un tiroir ne se vend pas du tout. */
    public enum Refusal {
        NONE,
        /** Ferme par la cle personnelle d'un autre joueur. */
        OWNER,
        /** Amelioration de distribution creative : un stock sans fin. */
        VENDING
    }

    /**
     * Ce qu'un tiroir rapporte, et ce qu'il redevient une fois vendu. {@link #apply()} modifie la
     * pile (ou en rend une autre, pour une case retiree) : a n'appeler qu'une fois, la vente faite.
     */
    public static final class Plan {

        private final ItemStack stack;
        private final Map<Item, Integer> sold;
        private final boolean leftover;
        private final Refusal refusal;
        private final Supplier<ItemStack> after;
        private boolean applied;

        private Plan(ItemStack stack, Map<Item, Integer> sold, boolean leftover, Refusal refusal,
                     Supplier<ItemStack> after) {
            this.stack = stack;
            this.sold = sold;
            this.leftover = leftover;
            this.refusal = refusal;
            this.after = after;
        }

        private static Plan refused(Refusal refusal, ItemStack stack) {
            return new Plan(stack, Map.of(), true, refusal, () -> stack);
        }

        /** Ce qui se vend : objet, quantite. Vide si rien ne se vend. */
        public Map<Item, Integer> sold() {
            return sold;
        }

        public int count() {
            int total = 0;
            for (int amount : sold.values()) {
                total += amount;
            }
            return total;
        }

        /** Vrai s'il restera quelque chose dans le tiroir. */
        public boolean leftover() {
            return leftover;
        }

        public Refusal refusal() {
            return refusal;
        }

        /**
         * Retire du tiroir ce qui est vendu ; rend la pile a remettre a sa place (la meme, sauf
         * pour une case retiree, qui change d'objet une fois vide). Rien a vendre : la pile intacte.
         */
        public ItemStack apply() {
            if (sold.isEmpty()) {
                return stack;
            }
            if (applied) {
                throw new IllegalStateException("tiroir deja vide");
            }
            applied = true;
            return after.get();
        }
    }

    /** Vrai si la pile est un tiroir de Storage Drawers qui porte un contenu (ou ses donnees). */
    public static boolean isDrawer(ItemStack stack) {
        if (stack.isEmpty() || !NAMESPACE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            return data.copyTagWithoutId().contains("Drawers");
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(DETACHED_FULL);
    }

    /**
     * Le plan de vente d'un tiroir : rien si la pile n'en est pas un. {@code wanted} restreint aux
     * objets voulus (une ligne de l'onglet Vendre) ; {@code room}, ce qui peut encore s'ajouter de
     * chaque objet a la vente en cours (un total doit tenir dans un {@code int}).
     */
    public static Optional<Plan> plan(ServerPlayer player, ItemStack stack, Predicate<Item> wanted,
                                      ToIntFunction<Item> room) {
        if (!isDrawer(stack)) {
            return Optional.empty();
        }
        try {
            if (stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
                return Optional.of(block(player, stack, wanted, room));
            }
            return Optional.ofNullable(detached(player, stack, wanted, room));
        } catch (RuntimeException error) {
            // Un format de donnees inattendu (une autre version du mod) : le tiroir reste tel quel.
            if (!warned) {
                warned = true;
                DoHy.LOGGER.warn("Tiroir de Storage Drawers illisible, laisse tel quel : {}", stack, error);
            }
            return Optional.empty();
        }
    }

    /** Toute la vente possible, sans limite de quantite. */
    public static Optional<Plan> plan(ServerPlayer player, ItemStack stack) {
        return plan(player, stack, item -> true, item -> Integer.MAX_VALUE);
    }

    // -- Tiroir entier (le bloc casse) ---------------------------------------------------------

    private static Plan block(ServerPlayer player, ItemStack stack, Predicate<Item> wanted, ToIntFunction<Item> room) {
        TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        CompoundTag tag = data.copyTagWithoutId();
        String owner = tag.getStringOr("Own", "");
        if (!owner.isEmpty() && !owner.equals(player.getUUID().toString())) {
            return Plan.refused(Refusal.OWNER, stack);
        }
        ListTag upgrades = tag.getListOrEmpty("Upgrades");
        for (int index = 0; index < upgrades.size(); index++) {
            if (VENDING.equals(upgrades.getCompoundOrEmpty(index).getStringOr("id", ""))) {
                return Plan.refused(Refusal.VENDING, stack);
            }
        }
        boolean locked = (tag.getIntOr("Lock", 0) & LOCK_POPULATED) != 0;
        Map<Item, Integer> sold = new LinkedHashMap<>();
        boolean leftover;
        Tag drawers = tag.get("Drawers");
        if (drawers instanceof ListTag slots) {
            leftover = standard(player, slots, locked, wanted, room, sold);
        } else if (drawers instanceof CompoundTag pool) {
            leftover = compacting(player, pool, locked, wanted, room, sold);
        } else {
            return Plan.refused(Refusal.NONE, stack);
        }
        boolean upgraded = !upgrades.isEmpty();
        return new Plan(stack, sold, leftover, Refusal.NONE, () -> {
            if (!leftover && !upgraded) {
                // Comme le mod casse un tiroir vide : un tiroir neuf, sans donnees, qui s'empile.
                reset(stack, DataComponents.BLOCK_ENTITY_DATA);
            } else {
                stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(data.type(), tag));
            }
            if (!leftover) {
                reset(stack, DataComponents.MAX_STACK_SIZE);
            }
            return stack;
        });
    }

    /** Les cases d'un tiroir simple ; vrai s'il en reste une pleine (ou retiree du bloc). */
    private static boolean standard(ServerPlayer player, ListTag slots, boolean locked, Predicate<Item> wanted,
                                    ToIntFunction<Item> room, Map<Item, Integer> sold) {
        boolean leftover = false;
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag slot = slots.getCompoundOrEmpty(index);
            if (slot.getBooleanOr("Missing", false)) {
                leftover = true;
                continue;
            }
            int count = slot.getIntOr("Count", 0);
            Item item = item(slot.getCompoundOrEmpty("Item"));
            if (count <= 0 || item == null) {
                continue;
            }
            int take = eligible(player, item, wanted) ? Math.min(count, roomLeft(item, room, sold)) : 0;
            if (take > 0) {
                sold.merge(item, take, Integer::sum);
                count -= take;
                if (count == 0 && !locked) {
                    slot.remove("Item");
                    slot.remove("Count");
                } else {
                    slot.putInt("Count", count);
                }
            }
            leftover |= count > 0;
        }
        return leftover;
    }

    /** Une forme d'un compacteur : l'objet, et combien de petites unites fait une piece. */
    private record Form(Item item, int conversion, double valuePerUnit) {
    }

    /**
     * Le reservoir d'un compacteur, vendu forme par forme, la plus payante par petite unite
     * d'abord ; ce qui ne fait pas une piece entiere d'une forme vendable reste.
     */
    private static boolean compacting(ServerPlayer player, CompoundTag pool, boolean locked, Predicate<Item> wanted,
                                      ToIntFunction<Item> room, Map<Item, Integer> sold) {
        int units = pool.getIntOr("Count", 0);
        ListTag forms = pool.getListOrEmpty("Items");
        List<Form> sellable = new ArrayList<>();
        for (int index = 0; index < forms.size(); index++) {
            CompoundTag form = forms.getCompoundOrEmpty(index);
            Item item = item(form.getCompoundOrEmpty("Item"));
            int conversion = form.getIntOr("Conv", 0);
            if (item == null || conversion <= 0 || !eligible(player, item, wanted)) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            PriceEntry entry = Prices.book().entry(id).orElseThrow();
            sellable.add(new Form(item, conversion, DynamicPrice.unitPrice(player, id, entry) / (double) conversion));
        }
        sellable.sort(Comparator.comparingDouble((Form form) -> -form.valuePerUnit())
                .thenComparingInt(form -> -form.conversion()));
        int before = units;
        for (Form form : sellable) {
            int take = Math.min(units / form.conversion(), roomLeft(form.item(), room, sold));
            if (take > 0) {
                sold.merge(form.item(), take, Integer::sum);
                units -= take * form.conversion();
            }
        }
        if (units != before) {
            pool.putInt("Count", units);
            if (units == 0 && !locked) {
                pool.put("Items", new ListTag());
            }
        }
        return units > 0;
    }

    // -- Case retiree du bloc --------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Plan detached(ServerPlayer player, ItemStack stack, Predicate<Item> wanted, ToIntFunction<Item> room) {
        DataComponentType<Object> type = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(DETACHED_CONTENT).orElse(null);
        Item empty = BuiltInRegistries.ITEM.getOptional(DETACHED_EMPTY).orElse(null);
        Object value = type == null ? null : stack.get(type);
        if (value == null || empty == null) {
            return null;
        }
        Codec<Object> codec = type.codecOrThrow();
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag content = (CompoundTag) codec.encodeStart(ops, value).getOrThrow();
        int count = content.getIntOr("itemCount", 0);
        Item item = item(content.getCompoundOrEmpty("item"));
        Map<Item, Integer> sold = new LinkedHashMap<>();
        int take = count > 0 && item != null && eligible(player, item, wanted)
                ? Math.min(count, roomLeft(item, room, sold)) : 0;
        if (take <= 0) {
            return new Plan(stack, sold, count > 0, Refusal.NONE, () -> stack);
        }
        sold.put(item, take);
        int left = count - take;
        return new Plan(stack, sold, left > 0, Refusal.NONE, () -> {
            CompoundTag legacy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ItemStack result;
            if (left > 0) {
                content.putInt("itemCount", left);
                legacy.putInt("Count", left);
                result = stack;
            } else {
                // Comme le mod tire une case vide du bloc : la case vide, sans objet, empilable.
                content.remove("item");
                content.putInt("itemCount", 0);
                content.putBoolean("heavy", false);
                legacy.remove("Item");
                legacy.remove("Heavy");
                legacy.putInt("Count", 0);
                result = new ItemStack(empty);
            }
            result.set(type, codec.parse(ops, content).getOrThrow());
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(legacy));
            return result;
        });
    }

    // -- Outils ----------------------------------------------------------------------------------

    /** Vrai si l'objet est voulu, rachete par le catalogue et sa vente ouverte pour ce joueur. */
    private static boolean eligible(ServerPlayer player, Item item, Predicate<Item> wanted) {
        if (!wanted.test(item)) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        return entry != null && entry.sellable() && CollectionApi.isUnlocked(player, id, entry);
    }

    private static int roomLeft(Item item, ToIntFunction<Item> room, Map<Item, Integer> sold) {
        return Math.max(0, room.applyAsInt(item) - sold.getOrDefault(item, 0));
    }

    /** L'objet d'une entree {@code ItemStack.CODEC} ({@code id}), ou rien. */
    private static Item item(CompoundTag stack) {
        Identifier id = Identifier.tryParse(stack.getStringOr("id", ""));
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /**
     * Rend a la pile la valeur de son prototype. {@code remove} ne suffit pas : sur un composant
     * que l'objet porte par defaut (la taille de pile), il noterait une absence, et le tiroir ne
     * s'empilerait plus du tout.
     */
    private static <T> void reset(ItemStack stack, DataComponentType<T> type) {
        stack.set(type, new ItemStack(stack.getItem()).get(type));
    }
}
