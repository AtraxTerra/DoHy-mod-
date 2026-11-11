package fr.atraxterra.dohy.boost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.CrateView;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les caisses : le puits a argent du milieu de partie.
 *
 * <p>Une caisse est un pari : elle coute un montant fixe et rend une valeur
 * aleatoire. Les tables sont calibrees pour rendre un peu moins que leur prix en
 * moyenne — c'est ce qui en fait un sink et non une source
 * ({@code tools/crate_values.py} le verifie). Elles vivent dans le datapack,
 * donc le rendement se reequilibre sans recompilation.
 *
 * <p>L'ouverture : le serveur tire le lot et le donne tout de suite, puis
 * envoie au joueur une roulette ({@link CrateView}) qui s'arrete dessus. Le
 * message du gain attend la fin de l'animation, pour ne pas la gacher.
 */
@EventBusSubscriber
public final class Crates {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "crates.json");

    /** Les classes de lot, de la plus courante a la plus rare. */
    public static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary");
    private static final List<String> RARITY_NAMES = List.of("Commun", "Rare", "Epique", "Legendaire");
    private static final List<ChatFormatting> RARITY_COLORS = List.of(
            ChatFormatting.WHITE, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD);

    /** La longueur de la roulette, et la case ou elle s'arrete. */
    public static final int REEL_LENGTH = 46;
    public static final int REEL_WIN = 39;
    /** La duree de l'animation chez le client, plus une marge : le message du gain part apres. */
    public static final int REVEAL_TICKS = 115;

    private static volatile Config config = new Config(Map.of());
    /** Le premier objet de chaque tag, resolu une fois par chargement. */
    private static final Map<String, Optional<Item>> TAGS = new ConcurrentHashMap<>();
    /** Les messages de gain en attente, dans l'ordre : le delai est le meme pour tous. */
    private static final Deque<Reveal> REVEALS = new ArrayDeque<>();
    private static int spins;

    private Crates() {
    }

    public static Config config() {
        return config;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Caisses introuvables : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            // Les composants (enchantements) designent des entrees de registre :
            // il faut les registres du serveur pour les lire.
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            var result = Config.CODEC.parse(ops, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Caisses illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            TAGS.clear();
            List<String> summary = new ArrayList<>();
            config.crates().forEach((tier, crate) -> summary.add(tier + " " + crate.available().size()
                    + "/" + crate.entries().size()));
            DoHy.LOGGER.info("Caisses chargees (lots disponibles) : {}", summary);
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des caisses", error);
            return false;
        }
    }

    /** Achete une cle. Retourne la raison du refus, ou {@code null} si reussi. */
    public static String buyKey(ServerPlayer player, String tier) {
        Crate crate = config.crates().get(tier);
        if (crate == null) {
            return "Palier inconnu : " + tier;
        }
        if (!Economy.tryDebit(player, crate.price())) {
            return "Il te faut " + MoneyFormat.exact(crate.price()) + " en liquide.";
        }
        Boosters.data(player).addKeys(tier, 1);
        return null;
    }

    /**
     * Ouvre une caisse : tire le lot, le donne, et lance la roulette chez le
     * joueur. Retourne la raison du refus, ou {@code null} si reussi.
     */
    public static String open(ServerPlayer player, String tier) {
        Crate crate = config.crates().get(tier);
        if (crate == null) {
            return "Palier inconnu : " + tier;
        }
        List<Entry> pool = crate.available();
        if (pool.isEmpty()) {
            return "Table de butin vide pour " + tier + ".";
        }
        if (!Boosters.data(player).consumeKey(tier)) {
            return "Aucune cle " + tier + ". Achete-en une a la boutique.";
        }

        RandomSource random = player.getRandom();
        Entry won = roll(random, pool);
        long money = won.money(random);
        List<ItemStack> stacks = won.stacks();
        if (money > 0) {
            Economy.credit(player, money);
        }
        for (ItemStack stack : stacks) {
            give(player, stack.copy());
        }

        List<CrateView.Slot> reel = new ArrayList<>(REEL_LENGTH);
        for (int index = 0; index < REEL_LENGTH; index++) {
            if (index == REEL_WIN) {
                reel.add(slot(won, money, stacks));
            } else {
                Entry shown = roll(random, pool);
                reel.add(slot(shown, shown.money(random), shown.stacks()));
            }
        }
        int rarity = won.rarityIndex();
        Component name = name(won, money, stacks);
        String detail = detail(won, money, stacks);
        List<ItemStack> contents = won.prizes().size() > 1 ? icons(stacks) : List.of();
        BoostData data = Boosters.data(player);
        UiNet.show(player, new CrateView(tier, title(tier), data.keys(tier), crate.price(),
                Economy.balance(player) >= crate.price(), reel, REEL_WIN, MenuView.text(name), detail, contents,
                rarity, crate.odds(), ++spins));

        DoHy.LOGGER.info("Caisse {} ouverte par {} : {} ({})", tier, player.getName().getString(),
                name.getString(), RARITIES.get(rarity));
        schedule(player, tier, won, name, detail);
        return null;
    }

    /** « Caisse rare » : le nom affiche d'un palier. */
    public static String title(String tier) {
        return switch (tier) {
            case "common" -> "Caisse commune";
            case "rare" -> "Caisse rare";
            case "legendary" -> "Caisse legendaire";
            default -> "Caisse " + tier;
        };
    }

    /** Le nom d'un lot : la somme, le nom du lot groupe, ou celui de l'objet. */
    public static Component name(Entry entry, long money, List<ItemStack> stacks) {
        if (entry.name().isPresent()) {
            return Component.literal(entry.name().get());
        }
        if (!stacks.isEmpty()) {
            return stacks.get(0).getHoverName();
        }
        return Component.literal("+" + MoneyFormat.exact(money));
    }

    /** Le libelle d'un lot dans une liste : la somme ou sa fourchette, l'objet et sa quantite. */
    public static Component label(Entry entry) {
        List<ItemStack> stacks = entry.stacks();
        if (entry.name().isPresent()) {
            return Component.literal(entry.name().get() + " (" + stacks.size() + " objets)");
        }
        if (!stacks.isEmpty()) {
            ItemStack stack = stacks.get(0);
            return stack.getHoverName().copy().append(" x" + stack.getCount());
        }
        long low = Math.min(entry.moneyMin(), entry.moneyMax());
        long high = Math.max(entry.moneyMin(), entry.moneyMax());
        return Component.literal(high > low ? MoneyFormat.compact(low) + " a " + MoneyFormat.compact(high)
                : MoneyFormat.exact(low));
    }

    public static String rarityName(int rarity) {
        return RARITY_NAMES.get(Math.max(0, Math.min(rarity, RARITY_NAMES.size() - 1)));
    }

    public static ChatFormatting rarityColor(int rarity) {
        return RARITY_COLORS.get(Math.max(0, Math.min(rarity, RARITY_COLORS.size() - 1)));
    }

    private static String detail(Entry entry, long money, List<ItemStack> stacks) {
        if (entry.name().isPresent()) {
            return stacks.size() + " objets" + (money > 0 ? " et " + MoneyFormat.exact(money) : "");
        }
        if (!stacks.isEmpty()) {
            int count = stacks.get(0).getCount();
            return count > 1 ? "x" + count : "";
        }
        return "Verse en liquide";
    }

    private static CrateView.Slot slot(Entry entry, long money, List<ItemStack> stacks) {
        ItemStack icon = entry.icon()
                .flatMap(id -> BuiltInRegistries.ITEM.getOptional(id))
                .map(ItemStack::new)
                .orElseGet(() -> stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0).copyWithCount(1));
        String label;
        if (entry.name().isPresent()) {
            label = "LOT";
        } else if (!stacks.isEmpty()) {
            label = "x" + stacks.get(0).getCount();
        } else {
            label = MoneyFormat.compact(money);
        }
        return new CrateView.Slot(icon, entry.rarityIndex(), label);
    }

    /** Les objets d'un lot groupe, a l'unite, pour l'ecran (une pile par case, le nombre affiche). */
    private static List<ItemStack> icons(List<ItemStack> stacks) {
        List<ItemStack> icons = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            icons.add(stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
        return icons;
    }

    /** Donne une pile, decoupee a la taille maximale de l'objet ; ce qui ne rentre pas tombe au sol. */
    private static void give(ServerPlayer player, ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        int left = stack.getCount();
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(max, left));
            left -= part.getCount();
            // add() rend vrai des qu'une partie est rangee : on regarde ce qui reste.
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                player.drop(part, false);
            }
        }
    }

    private static Entry roll(RandomSource random, List<Entry> pool) {
        int total = pool.stream().mapToInt(entry -> Math.max(0, entry.weight())).sum();
        if (total <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        int pick = random.nextInt(total);
        for (Entry entry : pool) {
            pick -= Math.max(0, entry.weight());
            if (pick < 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // -- Message du gain ---------------------------------------------------------

    private record Reveal(UUID player, long due, Component message, Component broadcast) {
    }

    private static void schedule(ServerPlayer player, String tier, Entry won, Component name, String detail) {
        int rarity = won.rarityIndex();
        ChatFormatting color = rarityColor(rarity);
        MutableComponent prize = name.copy().withStyle(color);
        if (!detail.isEmpty() && !detail.startsWith("Verse")) {
            prize.append(Component.literal(" (" + detail + ")").withStyle(ChatFormatting.GRAY));
        }
        Component message = Component.literal(title(tier) + " : ").withStyle(ChatFormatting.GRAY)
                .append(prize)
                .append(Component.literal(" · " + rarityName(rarity)).withStyle(color));
        Component broadcast = rarity < 2 ? null : Component.literal("[Caisses] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(player.getName().getString() + " a obtenu ").withStyle(ChatFormatting.GRAY))
                .append(name.copy().withStyle(color))
                .append(Component.literal(" (" + rarityName(rarity) + ") dans une " + title(tier).toLowerCase()
                        + " !").withStyle(ChatFormatting.GRAY));
        REVEALS.addLast(new Reveal(player.getUUID(), player.level().getServer().getTickCount() + REVEAL_TICKS,
                message, broadcast));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        while (!REVEALS.isEmpty() && REVEALS.peekFirst().due() <= now) {
            Reveal reveal = REVEALS.pollFirst();
            ServerPlayer player = server.getPlayerList().getPlayer(reveal.player());
            if (player != null) {
                player.sendSystemMessage(reveal.message());
            }
            if (reveal.broadcast() != null) {
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (other != player) {
                        other.sendSystemMessage(reveal.broadcast());
                    }
                }
            }
        }
    }

    // -- Tables ------------------------------------------------------------------

    /** Le premier objet d'un tag ({@code c:ingots/lead}), ou rien si le tag est vide. */
    private static Optional<Item> firstInTag(String tag) {
        return TAGS.computeIfAbsent(tag, key -> {
            Identifier id = Identifier.tryParse(key.startsWith("#") ? key.substring(1) : key);
            if (id == null) {
                return Optional.empty();
            }
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), id);
            for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.listElements().toList()) {
                if (holder.is(tagKey)) {
                    return Optional.of(holder.value());
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Un objet a gagner : un identifiant ({@code item}) ou un tag ({@code tag},
     * le premier objet qu'il contient), une quantite, et des composants au
     * format des commandes — c'est ce qui permet de mettre en caisse un outil
     * enchante ou un gant en endium deja garni.
     */
    public record Prize(Optional<Identifier> item, Optional<String> tag, int count,
                        Optional<DataComponentPatch> components) {
        static final MapCodec<Prize> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("item").forGetter(Prize::item),
                Codec.STRING.optionalFieldOf("tag").forGetter(Prize::tag),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Prize::count),
                DataComponentPatch.CODEC.optionalFieldOf("components").forGetter(Prize::components)
        ).apply(instance, Prize::new));
        static final Codec<Prize> CODEC = MAP_CODEC.codec();

        boolean declared() {
            return item.isPresent() || tag.isPresent();
        }

        /** La pile, ou une pile vide si l'objet n'existe pas dans cette partie. */
        public ItemStack stack() {
            Item resolved = item.isPresent()
                    ? BuiltInRegistries.ITEM.getOptional(item.get()).orElse(null)
                    : tag.flatMap(Crates::firstInTag).orElse(null);
            if (resolved == null || resolved == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(resolved, Math.max(1, count));
            components.ifPresent(stack::applyComponents);
            return stack;
        }
    }

    /**
     * Une entree de caisse : de l'argent ({@code money_min}/{@code money_max}),
     * un objet (les champs de {@link Prize} a plat), ou un lot de plusieurs
     * objets ({@code items}, avec un {@code name} et une {@code icon}).
     * {@code rarity} donne la couleur et le son de l'ouverture ; la chance vient
     * du poids.
     */
    public record Entry(int weight, String rarity, Optional<String> name, Optional<Identifier> icon, Prize single,
                        List<Prize> items, long moneyMin, long moneyMax) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.STRING.optionalFieldOf("rarity", "common").forGetter(Entry::rarity),
                Codec.STRING.optionalFieldOf("name").forGetter(Entry::name),
                Identifier.CODEC.optionalFieldOf("icon").forGetter(Entry::icon),
                Prize.MAP_CODEC.forGetter(Entry::single),
                Prize.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Entry::items),
                Codec.LONG.optionalFieldOf("money_min", 0L).forGetter(Entry::moneyMin),
                Codec.LONG.optionalFieldOf("money_max", 0L).forGetter(Entry::moneyMax)
        ).apply(instance, Entry::new));

        public List<Prize> prizes() {
            if (!single.declared()) {
                return items;
            }
            List<Prize> all = new ArrayList<>(items.size() + 1);
            all.add(single);
            all.addAll(items);
            return all;
        }

        /** Les piles du lot ; une pile vide pour un objet absent de la partie. */
        public List<ItemStack> stacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (Prize prize : prizes()) {
                stacks.add(prize.stack());
            }
            return stacks;
        }

        /** Tirable : chaque objet existe dans la partie, et l'entree donne quelque chose. */
        public boolean available() {
            List<ItemStack> stacks = stacks();
            if (stacks.stream().anyMatch(ItemStack::isEmpty)) {
                return false;
            }
            return !stacks.isEmpty() || moneyMin > 0 || moneyMax > 0;
        }

        public int rarityIndex() {
            return Math.max(0, RARITIES.indexOf(rarity));
        }

        long money(RandomSource random) {
            if (moneyMax <= 0) {
                return Math.max(0L, moneyMin);
            }
            long low = Math.min(moneyMin, moneyMax);
            long high = Math.max(moneyMin, moneyMax);
            // Arrondi a la centaine : « 1 234 500 $ » se lit mieux que « 1 234 567 $ ».
            long amount = low + (long) (random.nextDouble() * (high - low));
            return amount >= 10_000 ? amount / 100 * 100 : amount;
        }
    }

    public record Crate(long price, List<Entry> entries) {
        static final Codec<Crate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("price").forGetter(Crate::price),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Crate::entries)
        ).apply(instance, Crate::new));

        /** Les entrees tirables dans cette partie : sans NTM NEXT, ses lots sortent du tirage. */
        public List<Entry> available() {
            return entries.stream().filter(Entry::available).toList();
        }

        /** La chance de chaque classe parmi les entrees tirables, en pour mille. */
        public List<Integer> odds() {
            List<Entry> pool = available();
            int total = pool.stream().mapToInt(entry -> Math.max(0, entry.weight())).sum();
            int[] weights = new int[RARITIES.size()];
            for (Entry entry : pool) {
                weights[entry.rarityIndex()] += Math.max(0, entry.weight());
            }
            List<Integer> odds = new ArrayList<>(weights.length);
            for (int weight : weights) {
                odds.add(total <= 0 ? 0 : Math.round(1000.0f * weight / total));
            }
            return odds;
        }
    }

    public record Config(Map<String, Crate> crates) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Crate.CODEC).optionalFieldOf("crates", Map.of())
                        .forGetter(Config::crates)
        ).apply(instance, Config::new));
    }
}
