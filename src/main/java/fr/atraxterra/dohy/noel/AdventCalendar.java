package fr.atraxterra.dohy.noel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.spawn.SpawnApi;
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
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Le calendrier de l'Avent : une case par jour, du 1er au 24 decembre, a ouvrir au
 * spawn aupres du Pere Noel du parvis ({@link fr.atraxterra.dohy.spawn.SpawnChristmas}).
 *
 * <p>Les regles :
 * <ul>
 *   <li>la case du jour s'ouvre le jour meme, avec la bascule de 5 h des contrats (il est
 *       encore le 30 novembre a 4 h 59) ;</li>
 *   <li>une case manquee se rattrape, contre de l'argent ({@code catch_up_price}), jusqu'au
 *       6 janvier ;</li>
 *   <li>les vingt-quatre cases ouvertes, le <b>grand cadeau</b> (la 25e) se prend du 25
 *       decembre au 6 janvier ;</li>
 *   <li>une case ne s'ouvre qu'au spawn ; {@code /calendrier} la montre de partout ;</li>
 *   <li>le cadeau entier, ou rien : un sac trop plein le laisse a plus tard.</li>
 * </ul>
 *
 * <p>Le contenu des cases est dans {@code data/advent.json} (copie dans le jar en
 * {@code data/dohy/advent.json}), lu au demarrage du monde.
 */
public final class AdventCalendar {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "advent.json");

    /** Les cases du 1er au 24 decembre. */
    public static final int DOORS = 24;
    /** Le grand cadeau : la vingt-cinquieme case, pour qui a ouvert les vingt-quatre. */
    public static final int BONUS = 25;
    /** Le dernier jour de la saison : l'Epiphanie, le 6 janvier. */
    static final int LAST_JANUARY_DAY = 6;
    /** Le prix d'une case rattrapee si le fichier n'en dit rien. */
    private static final long DEFAULT_CATCH_UP = 25_000L;

    /** Un objet d'une case. */
    public record Entry(Identifier id, int count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(Entry::count)
        ).apply(instance, Entry::new));
    }

    /** Une case : son jour (25 pour le grand cadeau), son nom, son icone, l'argent et les objets. */
    public record Gift(int day, String name, Identifier icon, long money, List<Entry> items) {
        static final Codec<Gift> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, BONUS).fieldOf("day").forGetter(Gift::day),
                Codec.STRING.fieldOf("name").forGetter(Gift::name),
                Identifier.CODEC.fieldOf("icon").forGetter(Gift::icon),
                Codec.LONG.optionalFieldOf("money", 0L).forGetter(Gift::money),
                Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Gift::items)
        ).apply(instance, Gift::new));

        /** Les piles de la case : chaque objet connu, coupe a la taille de pile du jeu. */
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

        public ItemStack iconStack() {
            return BuiltInRegistries.ITEM.getOptional(icon).map(ItemStack::new)
                    .filter(stack -> !stack.isEmpty()).orElseGet(() -> new ItemStack(Items.CHEST));
        }
    }

    private record Config(List<Gift> days, Optional<Gift> bonus, long catchUpPrice) {
        static final Config FALLBACK = new Config(List.of(), Optional.empty(), DEFAULT_CATCH_UP);
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Gift.CODEC.listOf().fieldOf("days").forGetter(Config::days),
                Gift.CODEC.optionalFieldOf("bonus").forGetter(Config::bonus),
                Codec.LONG.optionalFieldOf("catch_up_price", DEFAULT_CATCH_UP).forGetter(Config::catchUpPrice)
        ).apply(instance, Config::new));
    }

    /**
     * Ce qu'un joueur a ouvert : la saison (l'annee de son mois de decembre) et un bit par
     * case, de 1 a 25. Une autre saison repart de zero.
     */
    public record Opened(int season, int mask) {
        public static final Opened NONE = new Opened(0, 0);
        public static final Codec<Opened> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("season").forGetter(Opened::season),
                Codec.INT.fieldOf("mask").forGetter(Opened::mask)
        ).apply(instance, Opened::new));

        public boolean isEmpty() {
            return mask == 0;
        }

        boolean has(int door) {
            return (mask & (1 << door)) != 0;
        }

        Opened with(int door) {
            return new Opened(season, mask | (1 << door));
        }

        /** Les cases de 1 a 24 ouvertes. */
        int count() {
            return Integer.bitCount(mask & ((1 << (DOORS + 1)) - 2));
        }
    }

    /** Ou en est une case pour un joueur. */
    public enum State {
        /** Deja ouverte. */
        OPENED,
        /** A ouvrir maintenant. */
        READY,
        /** Son jour est passe : elle se rattrape, contre de l'argent. */
        MISSED,
        /** Pas encore son jour (ou le grand cadeau sans les vingt-quatre). */
        LOCKED
    }

    private static volatile Config config = Config.FALLBACK;

    /**
     * Pour les captures de la CI seulement : une date imposee, reglee par reflexion. Elle
     * n'a ni commande ni option : en jeu, c'est toujours la date reelle.
     */
    private static volatile LocalDate forcedDay;

    private AdventCalendar() {
    }

    // -- Le fichier ------------------------------------------------------------------------

    static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Calendrier de l'Avent introuvable : data/{}/{}", LOCATION.getNamespace(),
                    LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Calendrier de l'Avent illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Config read = result.getOrThrow();
            Gift[] byDay = new Gift[DOORS + 1];
            for (Gift gift : read.days()) {
                if (gift.day() > DOORS || byDay[gift.day()] != null) {
                    DoHy.LOGGER.warn("Calendrier de l'Avent : case {} ecartee (hors du 1er au 24, ou en double)",
                            gift.day());
                    continue;
                }
                byDay[gift.day()] = gift;
                warnUnknown(gift);
            }
            List<Gift> days = new ArrayList<>();
            for (int day = 1; day <= DOORS; day++) {
                if (byDay[day] == null) {
                    DoHy.LOGGER.warn("Calendrier de l'Avent : pas de case pour le {} decembre", day);
                    days.add(new Gift(day, "Case du " + day, Identifier.withDefaultNamespace("chest"), 0L, List.of()));
                } else {
                    days.add(byDay[day]);
                }
            }
            read.bonus().ifPresent(AdventCalendar::warnUnknown);
            config = new Config(List.copyOf(days), read.bonus(), Math.max(0L, read.catchUpPrice()));
            DoHy.LOGGER.info("Calendrier de l'Avent : {} cases, grand cadeau {}, rattrapage {} $", days.size(),
                    read.bonus().isPresent() ? "oui" : "non", read.catchUpPrice());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement du calendrier de l'Avent", error);
            return false;
        }
    }

    private static void warnUnknown(Gift gift) {
        for (Entry entry : gift.items()) {
            if (BuiltInRegistries.ITEM.getOptional(entry.id()).isEmpty()) {
                DoHy.LOGGER.warn("Calendrier de l'Avent, case {} : objet inconnu {} ecarte (mod absent ?)", gift.day(),
                        entry.id());
            }
        }
    }

    /** La case d'un jour (1 a 24), ou le grand cadeau (25) ; {@code null} si le fichier n'en a pas. */
    public static Gift gift(int door) {
        Config current = config;
        if (door == BONUS) {
            return current.bonus().orElse(null);
        }
        return door >= 1 && door <= current.days().size() ? current.days().get(door - 1) : null;
    }

    public static long catchUpPrice() {
        return config.catchUpPrice();
    }

    // -- Le temps --------------------------------------------------------------------------

    /** Le jour du mod : la date reelle, avec la bascule de 5 h des contrats. */
    public static LocalDate today() {
        LocalDate forced = forcedDay;
        return forced != null ? forced : LocalDate.ofEpochDay(ContractApi.today());
    }

    /**
     * La saison d'un jour : l'annee de son mois de decembre, du 1er decembre au 6 janvier ;
     * 0 en dehors.
     */
    public static int season(LocalDate day) {
        if (day.getMonth() == Month.DECEMBER) {
            return day.getYear();
        }
        if (day.getMonth() == Month.JANUARY && day.getDayOfMonth() <= LAST_JANUARY_DAY) {
            return day.getYear() - 1;
        }
        return 0;
    }

    /** Vrai du 1er decembre au 6 janvier : le calendrier et le decor du spawn sont la. */
    public static boolean inSeason() {
        return season(today()) != 0;
    }

    /**
     * Le rang du jour dans la saison : 1 le 1er decembre, 24 le 24, 25 a Noel, 37 le 6
     * janvier ; 0 hors saison.
     */
    public static int dayIndex() {
        LocalDate day = today();
        int season = season(day);
        if (season == 0) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.of(season, Month.DECEMBER, 1), day) + 1;
    }

    // -- Les regles ------------------------------------------------------------------------

    /** Ce que le joueur a ouvert cette saison (rien d'une autre saison). */
    static Opened opened(ServerPlayer player) {
        Opened opened = player.getData(DoHyNoel.OPENED.get());
        int season = season(today());
        return season != 0 && opened.season() == season ? opened : new Opened(season, 0);
    }

    /** Les cases de 1 a 24 que le joueur a ouvertes cette saison. */
    public static int openedCount(ServerPlayer player) {
        return opened(player).count();
    }

    public static State state(ServerPlayer player, int door) {
        Opened opened = opened(player);
        if (opened.has(door)) {
            return State.OPENED;
        }
        int index = dayIndex();
        if (index == 0) {
            return State.LOCKED;
        }
        if (door == BONUS) {
            return index >= BONUS && opened.count() == DOORS ? State.READY : State.LOCKED;
        }
        if (door == index) {
            return State.READY;
        }
        return door < index ? State.MISSED : State.LOCKED;
    }

    /** La case du jour, si elle attend encore le joueur : de quoi le prevenir a la connexion. */
    public static int waitingToday(ServerPlayer player) {
        int index = dayIndex();
        return index >= 1 && index <= DOORS && state(player, index) == State.READY ? index : 0;
    }

    /** Les cases de 1 a 24 dont le jour est passe sans qu'elles soient ouvertes. */
    public static int missedCount(ServerPlayer player) {
        int missed = 0;
        for (int door = 1; door <= DOORS; door++) {
            if (state(player, door) == State.MISSED) {
                missed++;
            }
        }
        return missed;
    }

    /** Les cases du sac et de la barre d'action, sans l'armure ni la main gauche. */
    private static final int BAG_SLOTS = 36;

    private static int freeSlots(ServerPlayer player) {
        int free = 0;
        for (int slot = 0; slot < BAG_SLOTS; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** « 5 decembre », « le grand cadeau ». */
    public static String label(int door) {
        return door == BONUS ? "le grand cadeau" : (door == 1 ? "1er" : String.valueOf(door)) + " decembre";
    }

    /**
     * Ouvre une case ; {@code null} si elle est ouverte, sinon la raison du refus. Le cadeau
     * entier ou rien, et seulement au spawn.
     */
    public static String open(ServerPlayer player, int door) {
        if (!inSeason()) {
            return "Le calendrier de l'Avent ouvre le 1er decembre.";
        }
        Gift gift = gift(door);
        if (gift == null) {
            return "Cette case est vide cette annee.";
        }
        State state = state(player, door);
        switch (state) {
            case OPENED -> {
                return "La case du " + label(door) + " est deja ouverte.";
            }
            case LOCKED -> {
                return door == BONUS
                        ? "Le grand cadeau : des le 25 decembre, les 24 cases ouvertes."
                        : "Patience : la case du " + label(door) + " s'ouvre le " + label(door) + ".";
            }
            default -> {
            }
        }
        if (!SpawnApi.inSpawn(player)) {
            return "Les cases s'ouvrent au spawn (/spawn).";
        }
        if (fr.atraxterra.dohy.palamod.trixium.CombatTag.inCombat(player)) {
            return "Pas de cadeau en plein combat.";
        }
        List<ItemStack> stacks = gift.stacks();
        int free = freeSlots(player);
        if (free < stacks.size()) {
            return "Il faut " + stacks.size() + " cases libres dans ton sac (tu en as " + free + ").";
        }
        long price = state == State.MISSED ? catchUpPrice() : 0L;
        if (price > 0L && !Economy.tryDebit(player, price)) {
            return "Rattraper la case du " + label(door) + " coute " + MoneyFormat.exact(price) + " $.";
        }
        Opened opened = opened(player);
        player.setData(DoHyNoel.OPENED.get(), opened.with(door));
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        if (gift.money() > 0L) {
            Economy.credit(player, gift.money());
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.6f, 1.6f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.PLAYERS, 0.8f, 1.2f);
        String money = gift.money() > 0L ? (stacks.isEmpty() ? "" : " et ") + MoneyFormat.exact(gift.money()) + " $" : "";
        String objects = stacks.isEmpty() ? "" : stacks.size() + " objet(s)";
        String paid = price > 0L ? " (rattrapee pour " + MoneyFormat.exact(price) + " $)" : "";
        player.sendSystemMessage(Component.literal((door == BONUS ? "Le grand cadeau" : "Case du " + label(door))
                + " : " + gift.name() + ", " + objects + money + paid + ".").withStyle(ChatFormatting.GREEN));
        return null;
    }

    /** Pour les operateurs : un joueur repart sans aucune case ouverte. */
    static void reset(ServerPlayer player) {
        player.setData(DoHyNoel.OPENED.get(), Opened.NONE);
    }
}
