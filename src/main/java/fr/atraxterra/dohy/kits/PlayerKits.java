package fr.atraxterra.dohy.kits;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.grade.Grades;
import fr.atraxterra.dohy.grade.PlayerGrade;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les kits, comme sur Paladium : des lots d'objets a prendre avec {@code /kit} ou aupres de
 * l'intendant du spawn. Le kit de depart arrive de lui-meme a la premiere connexion, une
 * seule fois ; les autres reviennent apres un delai en heures reelles, et ceux des grades
 * (Heros, Legende, Divinite, Premium) demandent le grade.
 *
 * <p>Le contenu et les delais sont dans {@code data/dohy/kits.json}, lu au demarrage du monde :
 * l'equilibrage se change sans recompiler ({@code docs/BALANCE.md}, section Kits).
 */
public final class PlayerKits {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "kits.json");
    private static final long HOUR_MS = 3_600_000L;

    /** Un objet d'un kit. */
    public record Entry(Identifier id, int count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Entry::count)
        ).apply(instance, Entry::new));
    }

    /** « 22E558 » : une couleur sans canal alpha. */
    private static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16) & 0xFFFFFF);
        } catch (NumberFormatException error) {
            return DataResult.error(() -> "couleur illisible : " + text);
        }
    }, value -> String.format("%06X", value & 0xFFFFFF));

    /**
     * Un kit.
     *
     * @param once          une seule prise par joueur, pour toujours
     * @param firstJoin     donne de lui-meme a la premiere connexion d'un nouveau joueur
     * @param cooldownHours heures reelles entre deux prises (sans effet si {@code once})
     * @param grade         le grade qu'il faut ({@code ""} : aucun) ; un grade plus haut suffit
     * @param premium       il faut Premium en cours
     * @param money         dollars verses en liquide avec les objets
     */
    public record Kit(String id, String name, Identifier icon, int color, boolean once, boolean firstJoin,
                      int cooldownHours, String grade, boolean premium, long money, List<Entry> items) {
        static final Codec<Kit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Kit::id),
                Codec.STRING.fieldOf("name").forGetter(Kit::name),
                Identifier.CODEC.optionalFieldOf("icon", Identifier.withDefaultNamespace("chest")).forGetter(Kit::icon),
                COLOR.optionalFieldOf("color", 0xFAC400).forGetter(Kit::color),
                Codec.BOOL.optionalFieldOf("once", false).forGetter(Kit::once),
                Codec.BOOL.optionalFieldOf("first_join", false).forGetter(Kit::firstJoin),
                Codec.intRange(1, 24 * 365).optionalFieldOf("cooldown_hours", 24).forGetter(Kit::cooldownHours),
                Codec.STRING.optionalFieldOf("grade", "").forGetter(Kit::grade),
                Codec.BOOL.optionalFieldOf("premium", false).forGetter(Kit::premium),
                Codec.LONG.optionalFieldOf("money", 0L).forGetter(Kit::money),
                Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Kit::items)
        ).apply(instance, Kit::new));

        public PlayerGrade requiredGrade() {
            return PlayerGrade.byId(grade);
        }

        /** Le delai affiche : « 1 fois », « 24 h », « 7 j ». */
        public String delay() {
            if (once) {
                return "1 fois";
            }
            return cooldownHours % 24 == 0 && cooldownHours >= 48 ? cooldownHours / 24 + " j" : cooldownHours + " h";
        }

        /** Les piles du kit : chaque objet connu, coupe a la taille de pile du jeu. */
        public List<ItemStack> stacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (Entry entry : items) {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(entry.id());
                if (item.isEmpty() || item.get() == Items.AIR) {
                    continue;
                }
                int left = entry.count();
                while (left > 0) {
                    ItemStack stack = new ItemStack(item.get());
                    int count = Math.min(left, stack.getMaxStackSize());
                    stack.setCount(count);
                    stacks.add(stack);
                    left -= count;
                }
            }
            return stacks;
        }
    }

    private record Config(List<Kit> kits) {
        static final Config FALLBACK = new Config(List.of());
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kit.CODEC.listOf().fieldOf("kits").forGetter(Config::kits)
        ).apply(instance, Config::new));
    }

    /** Ce qu'un joueur a pris : l'identifiant d'un kit, et quand (millisecondes de l'horloge reelle). */
    public record Claims(Map<String, Long> taken) {
        public static final Claims NONE = new Claims(Map.of());
        public static final Codec<Claims> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
                .xmap(Claims::new, Claims::taken);

        public boolean isEmpty() {
            return taken.isEmpty();
        }

        Claims with(String id, long when) {
            Map<String, Long> copy = new HashMap<>(taken);
            copy.put(id, when);
            return new Claims(Map.copyOf(copy));
        }
    }

    private static volatile Config config = Config.FALLBACK;

    private PlayerKits() {
    }

    public static List<Kit> kits() {
        return config.kits();
    }

    public static Kit find(String id) {
        for (Kit kit : config.kits()) {
            if (kit.id().equals(id)) {
                return kit;
            }
        }
        return null;
    }

    static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Kits introuvables : data/{}/{}", LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Kits illisibles : {}", result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            List<Kit> kits = new ArrayList<>();
            for (Kit kit : result.getOrThrow().kits()) {
                if (!kit.id().matches("[a-z0-9_]+") || find(kits, kit.id()) != null) {
                    DoHy.LOGGER.warn("Kit ecarte : identifiant « {} » invalide ou en double", kit.id());
                    continue;
                }
                for (Entry entry : kit.items()) {
                    if (BuiltInRegistries.ITEM.getOptional(entry.id()).isEmpty()) {
                        DoHy.LOGGER.warn("Kit {} : objet inconnu {} ecarte (mod absent ?)", kit.id(), entry.id());
                    }
                }
                kits.add(kit);
            }
            config = new Config(List.copyOf(kits));
            DoHy.LOGGER.info("Kits : {} ({})", kits.size(), String.join(", ", kits.stream().map(Kit::id).toList()));
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des kits", error);
            return false;
        }
    }

    private static Kit find(List<Kit> kits, String id) {
        for (Kit kit : kits) {
            if (kit.id().equals(id)) {
                return kit;
            }
        }
        return null;
    }

    // -- Les regles --------------------------------------------------------------------------

    static Claims claims(ServerPlayer player) {
        return player.getData(DoHyKits.CLAIMS.get());
    }

    /** Le joueur a-t-il ce qu'il faut (grade, Premium) pour ce kit ? */
    public static boolean allowed(ServerPlayer player, Kit kit) {
        return Grades.grade(player).atLeast(kit.requiredGrade()) && (!kit.premium() || Grades.premium(player));
    }

    /** Vrai si le kit, pris une seule fois, l'a deja ete. */
    public static boolean spent(ServerPlayer player, Kit kit) {
        return kit.once() && claims(player).taken().containsKey(kit.id());
    }

    /** Ce qu'il reste a attendre avant de reprendre le kit, en millisecondes ; 0 s'il est pret. */
    public static long remaining(ServerPlayer player, Kit kit, long now) {
        Long last = claims(player).taken().get(kit.id());
        if (last == null || kit.once()) {
            return 0L;
        }
        return Math.max(0L, last + kit.cooldownHours() * HOUR_MS - now);
    }

    /** Pret a prendre tout de suite ? */
    public static boolean ready(ServerPlayer player, Kit kit, long now) {
        return allowed(player, kit) && !spent(player, kit) && remaining(player, kit, now) == 0L;
    }

    /** Combien de kits attendent le joueur : la pastille de la barre laterale. */
    public static int readyCount(ServerPlayer player) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Kit kit : config.kits()) {
            if (ready(player, kit, now)) {
                count++;
            }
        }
        return count;
    }

    /** « 5 h 12 min », « 3 j 4 h », « 12 min ». */
    public static String duration(long millis) {
        long minutes = (millis + 59_999L) / 60_000L;
        if (minutes >= 48 * 60) {
            return minutes / (24 * 60) + " j " + minutes % (24 * 60) / 60 + " h";
        }
        return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
    }

    /** Ce qu'il faut pour un kit refuse faute de grade : « Grade Heros », « Premium ». */
    public static String requirement(Kit kit) {
        if (kit.premium()) {
            return "Premium";
        }
        return "Grade " + kit.requiredGrade().label();
    }

    /** Les cases du sac et de la barre d'action, sans l'armure ni la main gauche. */
    private static final int BAG_SLOTS = 36;

    /** Les cases vides du sac : le kit n'est donne que s'il y tient en entier. */
    private static int freeSlots(ServerPlayer player) {
        int free = 0;
        for (int slot = 0; slot < BAG_SLOTS; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * Prend un kit ; {@code null} s'il est donne, sinon la raison du refus. Le kit entier, ou
     * rien : un sac trop plein le laisse a plus tard plutot que d'en jeter une partie par terre.
     */
    public static String claim(ServerPlayer player, String id) {
        Kit kit = find(id);
        if (kit == null) {
            return "Kit inconnu : " + id + ". /kit pour la liste.";
        }
        long now = System.currentTimeMillis();
        if (!allowed(player, kit)) {
            return kit.name() + " : " + (kit.premium() ? "il faut Premium." : "il faut le grade "
                    + kit.requiredGrade().label() + ".");
        }
        if (spent(player, kit)) {
            return kit.name() + " : deja pris, il ne se prend qu'une fois.";
        }
        long wait = remaining(player, kit, now);
        if (wait > 0L) {
            return kit.name() + " : de retour dans " + duration(wait) + ".";
        }
        if (fr.atraxterra.dohy.palamod.trixium.CombatTag.inCombat(player)) {
            return "Pas de kit en plein combat.";
        }
        List<ItemStack> stacks = kit.stacks();
        int free = freeSlots(player);
        if (free < stacks.size()) {
            return kit.name() + " : il faut " + stacks.size() + " cases libres dans ton sac (tu en as " + free + ").";
        }
        give(player, kit, stacks, now);
        return null;
    }

    private static void give(ServerPlayer player, Kit kit, List<ItemStack> stacks, long now) {
        player.setData(DoHyKits.CLAIMS.get(), claims(player).with(kit.id(), now));
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        if (kit.money() > 0L) {
            Economy.credit(player, kit.money());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.5f, 1.4f);
        String extra = kit.money() > 0L ? " et " + MoneyFormat.exact(kit.money()) + " $" : "";
        player.sendSystemMessage(Component.literal(kit.name() + " recu : " + stacks.size() + " objet(s)" + extra
                + ".").withStyle(ChatFormatting.GREEN));
    }

    /**
     * La premiere connexion : les kits {@code first_join} pas encore pris arrivent d'eux-memes,
     * s'ils tiennent dans le sac ; sinon le joueur est prevenu qu'ils l'attendent.
     */
    static void welcome(ServerPlayer player) {
        long now = System.currentTimeMillis();
        for (Kit kit : config.kits()) {
            if (!kit.firstJoin() || !ready(player, kit, now)) {
                continue;
            }
            List<ItemStack> stacks = kit.stacks();
            if (freeSlots(player) >= stacks.size()) {
                give(player, kit, stacks, now);
                player.sendSystemMessage(Component.literal("Les autres kits : /kit, ou l'intendant du spawn.")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                remind(player, kit);
            }
        }
    }

    /** Un kit de depart jamais pris par un ancien joueur : un mot a la connexion. */
    static void remind(ServerPlayer player, Kit kit) {
        player.sendSystemMessage(Component.literal(kit.name() + " t'attend : /kit " + kit.id() + ".")
                .withStyle(ChatFormatting.GOLD));
    }

    /** Pour les operateurs : un joueur repart sans aucun kit pris. */
    public static void reset(ServerPlayer player) {
        player.setData(DoHyKits.CLAIMS.get(), Claims.NONE);
    }
}
