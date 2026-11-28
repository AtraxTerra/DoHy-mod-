package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.agri.AgriInv;
import fr.atraxterra.dohy.agri.Climate;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le laboratoire Genis d'un joueur, tel que le gere le centre de controle ({@code BotanistModuleManager}
 * de JobsReborn).
 *
 * <p>Dans JobsReborn, le laboratoire est un monde a part, fait de modules (des salles, chacune a son
 * climat) ou l'on pose ses pots ; le centre de controle les debloque, les ameliore (plus de places
 * pour des pots), les booste une fois par jour (pousse acceleree, mutations plus probables) et y
 * teleporte. Ici, les pots se posent dans le monde : un module est un <b>climat</b>, et il regroupe
 * les pots du joueur dont la plante est de ce climat. Le centre de controle les suit (ou ils sont,
 * ce qu'ils reclament), les booste une fois par jour, et les ameliore : une amelioration ne donne pas
 * de places (les pots ne sont pas limites ici) mais de la pousse, 10 % par niveau.
 *
 * <p>Les durees (construction, boost) suivent l'horloge reelle ({@link RealClock}) : elles avancent
 * le joueur deconnecte, comme le reste de ce qui avance hors ligne sur ce serveur. Le boost du jour se
 * renouvelle a 5 h (heure de Paris).
 */
@EventBusSubscriber
public final class BotanistLab {

    /** Les modules debloques avec le pot (Fermier 10). */
    public static final int UNLOCK_LEVEL = 10;
    public static final int MAX_LEVEL = 3;
    /** +10 % de pousse par niveau d'amelioration. */
    public static final double GROWTH_PER_LEVEL = 0.10d;
    /** Le boost : pousse x1,5 et +2 points de chance de muter, pendant 30 minutes, une fois par jour. */
    public static final double BOOST_GROWTH = 1.5d;
    public static final double BOOST_MUTATION = 0.02d;
    public static final long BOOST_MINUTES = 30L;
    public static final int BOOSTS_PER_DAY = 1;
    public static final int RESET_HOUR = 5;
    public static final long BOOST_MONEY = 5_000L;
    /** Les ameliorations, niveau par niveau (le niveau vise moins un). */
    private static final long[] UPGRADE_MONEY = {50_000L, 200_000L, 750_000L};
    private static final long[] UPGRADE_MINUTES = {60L, 240L, 720L};
    private static final int[] UPGRADE_FARMER = {25, 45, 70};
    private static final int[] UPGRADE_XP = {500, 1_500, 4_000};
    /** Les pots suivis : au plus tant par joueur (au-dela, les plus anciens ne sont plus listes). */
    private static final int MAX_POTS = 256;

    /**
     * Les objets des couts, lus au premier usage : cette classe est chargee avec les evenements, avant
     * que les objets ne soient sous la main.
     */
    private static final class Prices {
        static final Cost[] BOOST = {new Cost(Items.BONE_MEAL, 4)};
        static final Cost[][] UPGRADE = {
                {new Cost(Items.BONE_MEAL, 16)},
                {new Cost(Items.GLOWSTONE_DUST, 16), new Cost(Items.EMERALD, 4)},
                {new Cost(Items.DIAMOND, 2), new Cost(null, 8)}};
    }

    /** Les objets du boost : quatre poudres d'os. */
    public static Cost[] boostItems() {
        return Prices.BOOST;
    }

    /** Un objet a fournir ; {@code item} nul : le lingot de paladium (enregistre plus tard que ce fichier). */
    public record Cost(Item item, int count) {
        public Item resolved() {
            return item != null ? item : fr.atraxterra.dohy.palamod.PalaItems.PALADIUM_INGOT.get();
        }

        public String label() {
            return count + " " + new ItemStack(resolved()).getHoverName().getString();
        }
    }

    /** Un module : un climat. */
    public enum Module {
        NORMAL("normal", "Module normal", Climate.TEMPERATE, Mutation.IRONWHEAT, 0xFF53AE10),
        GLACIAL("glacial", "Module glacial", Climate.COLD, Mutation.MOONFROST, 0xFF6FD8FF),
        DESERT("desert", "Module desertique", Climate.HOT, Mutation.SAND_CORAL, 0xFFF2A01C);

        private final String key;
        private final String label;
        private final Climate climate;
        private final Mutation icon;
        private final int color;

        Module(String key, String label, Climate climate, Mutation icon, int color) {
            this.key = key;
            this.label = label;
            this.climate = climate;
            this.icon = icon;
            this.color = color;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public Climate climate() {
            return climate;
        }

        public Mutation icon() {
            return icon;
        }

        public int color() {
            return color;
        }

        public static Module of(Climate climate) {
            for (Module module : values()) {
                if (module.climate == climate) {
                    return module;
                }
            }
            return NORMAL;
        }

        public static Module byKey(String key) {
            for (Module module : values()) {
                if (module.key.equals(key)) {
                    return module;
                }
            }
            return null;
        }
    }

    /** L'etat d'un module ({@code BotanistModuleState}). */
    public enum State {
        LOCKED("VERROUILLE", 0xFF6B6B78),
        BUILDING("CONSTRUCTION", 0xFFFCD34B),
        ACTION("ACTION REQUISE", 0xFFFF5A3C),
        BOOST("BOOST ACTIF", 0xFFB07CFF),
        ACTIVE("ACTIF", 0xFF53AE10),
        FREE("LIBRE", 0xFF9A9AA6);

        private final String label;
        private final int color;

        State(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public int color() {
            return color;
        }
    }

    /** Ce que le centre de controle sait d'un pot : la derniere fois que le pot l'a dit. */
    public record Pot(String mutation, String stage, String need, boolean mutated, int permille) {
        public static final Codec<Pot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mutation").forGetter(Pot::mutation),
                Codec.STRING.fieldOf("stage").forGetter(Pot::stage),
                Codec.STRING.optionalFieldOf("need", "").forGetter(Pot::need),
                Codec.BOOL.optionalFieldOf("mutated", false).forGetter(Pot::mutated),
                Codec.INT.optionalFieldOf("permille", 0).forGetter(Pot::permille)
        ).apply(instance, Pot::new));

        public Mutation plant() {
            return Mutation.byId(mutation);
        }
    }

    /** Le laboratoire d'un joueur, garde sur lui (mort comprise). */
    public record Data(Map<String, Integer> levels, Map<String, Long> building, Map<String, Long> boostUntil,
                       Map<String, Long> boostDay, Map<String, Pot> pots) {
        public static final Data EMPTY = new Data(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(Data::levels),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("building", Map.of())
                        .forGetter(Data::building),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("boost_until", Map.of())
                        .forGetter(Data::boostUntil),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("boost_day", Map.of())
                        .forGetter(Data::boostDay),
                Codec.unboundedMap(Codec.STRING, Pot.CODEC).optionalFieldOf("pots", Map.of()).forGetter(Data::pots)
        ).apply(instance, Data::new));

        public int level(Module module) {
            return Math.max(0, Math.min(MAX_LEVEL, levels.getOrDefault(module.key(), 0)));
        }
    }

    private BotanistLab() {
    }

    public static Data data(ServerPlayer player) {
        return player.getData(PalaBotanist.LAB.get());
    }

    private static void save(ServerPlayer player, Data data) {
        player.setData(PalaBotanist.LAB.get(), data);
    }

    // -- Les effets sur les pots ------------------------------------------------------------------

    public static boolean unlocked(ServerPlayer player) {
        return JobApi.level(player, Job.FARMER) >= UNLOCK_LEVEL;
    }

    public static boolean boosted(ServerPlayer player, Module module) {
        return data(player).boostUntil().getOrDefault(module.key(), 0L) > RealClock.epochMinutes();
    }

    /** La pousse des pots de ce climat pour ce joueur : ameliorations et boost. */
    public static double growth(ServerPlayer player, Climate climate) {
        Module module = Module.of(climate);
        double growth = 1.0d + GROWTH_PER_LEVEL * data(player).level(module);
        return boosted(player, module) ? growth * BOOST_GROWTH : growth;
    }

    /** Les points de chance de muter en plus pendant un boost ({@code getMutationBoost}). */
    public static double mutationBonus(ServerPlayer player, Climate climate) {
        return boosted(player, Module.of(climate)) ? BOOST_MUTATION : 0.0d;
    }

    // -- Les pots suivis ----------------------------------------------------------------------------

    public static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    /** Un pot dit ou il en est ; vide, il n'est plus suivi. */
    static void track(ServerPlayer owner, ServerLevel level, BlockPos pos, Pot pot) {
        Data data = data(owner);
        String key = key(level, pos);
        if (pot == null) {
            if (!data.pots().containsKey(key)) {
                return;
            }
            Map<String, Pot> pots = new HashMap<>(data.pots());
            pots.remove(key);
            save(owner, new Data(data.levels(), data.building(), data.boostUntil(), data.boostDay(), Map.copyOf(pots)));
            return;
        }
        if (pot.equals(data.pots().get(key))) {
            return;
        }
        if (!data.pots().containsKey(key) && data.pots().size() >= MAX_POTS) {
            return;
        }
        Map<String, Pot> pots = new HashMap<>(data.pots());
        pots.put(key, pot);
        save(owner, new Data(data.levels(), data.building(), data.boostUntil(), data.boostDay(), Map.copyOf(pots)));
    }

    /** Oublie les pots qui ne sont plus la (charges, mais casses ou vides, ou a un autre). */
    public static void prune(ServerPlayer player) {
        Data data = data(player);
        Map<String, Pot> pots = new HashMap<>(data.pots());
        boolean changed = false;
        for (String key : List.copyOf(pots.keySet())) {
            String[] parts = key.split("\\|");
            if (parts.length != 4) {
                pots.remove(key);
                changed = true;
                continue;
            }
            ServerLevel level = levelOf(player, parts[0]);
            BlockPos pos;
            try {
                pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (NumberFormatException malformed) {
                pots.remove(key);
                changed = true;
                continue;
            }
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof BotanistPotBlockEntity pot) || pot.stage() == PotStage.EMPTY
                    || !player.getUUID().equals(pot.ownerId())) {
                pots.remove(key);
                changed = true;
            }
        }
        if (changed) {
            save(player, new Data(data.levels(), data.building(), data.boostUntil(), data.boostDay(), Map.copyOf(pots)));
        }
    }

    private static ServerLevel levelOf(ServerPlayer player, String dimension) {
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    /** Les pots suivis d'un module : cle -> ce qu'on en sait. */
    public static Map<String, Pot> pots(ServerPlayer player, Module module) {
        Map<String, Pot> found = new HashMap<>();
        for (Map.Entry<String, Pot> entry : data(player).pots().entrySet()) {
            Mutation plant = entry.getValue().plant();
            if (plant != null && Module.of(plant.climate()) == module) {
                found.put(entry.getKey(), entry.getValue());
            }
        }
        return found;
    }

    /** Les besoins en attente d'un module. */
    public static int needs(Map<String, Pot> pots) {
        int needs = 0;
        for (Pot pot : pots.values()) {
            if (!pot.need().isEmpty()) {
                needs++;
            }
        }
        return needs;
    }

    /** Les fleurs a recolter, et parmi elles celles qui ont mute. */
    public static int blooms(Map<String, Pot> pots, boolean mutatedOnly) {
        int blooms = 0;
        for (Pot pot : pots.values()) {
            if (pot.stage().equals(PotStage.BLOOMING.getSerializedName()) && (!mutatedOnly || pot.mutated())) {
                blooms++;
            }
        }
        return blooms;
    }

    /** La pousse moyenne ({@code getGrowth}) : l'avancement des plantes, une fleur comptant pour 100 %. */
    public static int averageGrowth(Map<String, Pot> pots) {
        int total = 0;
        int counted = 0;
        for (Pot pot : pots.values()) {
            if (pot.stage().equals(PotStage.WILTED.getSerializedName())) {
                continue;
            }
            total += pot.stage().equals(PotStage.BLOOMING.getSerializedName()) ? 1000 : pot.permille();
            counted++;
        }
        return counted == 0 ? 0 : total / counted;
    }

    public static State state(ServerPlayer player, Module module) {
        if (!unlocked(player)) {
            return State.LOCKED;
        }
        Data data = data(player);
        if (data.building().getOrDefault(module.key(), 0L) > RealClock.epochMinutes()) {
            return State.BUILDING;
        }
        Map<String, Pot> pots = pots(player, module);
        if (needs(pots) > 0) {
            return State.ACTION;
        }
        if (boosted(player, module)) {
            return State.BOOST;
        }
        return pots.isEmpty() ? State.FREE : State.ACTIVE;
    }

    // -- Le boost du jour -------------------------------------------------------------------------

    public static int boostsLeft(ServerPlayer player, Module module) {
        long day = data(player).boostDay().getOrDefault(module.key(), -1L);
        return day == RealClock.dayKey(RESET_HOUR) ? 0 : BOOSTS_PER_DAY;
    }

    public static long boostMinutesLeft(ServerPlayer player, Module module) {
        return Math.max(0L, data(player).boostUntil().getOrDefault(module.key(), 0L) - RealClock.epochMinutes());
    }

    /** Ce qui empeche de booster, ou {@code null}. */
    public static String boostMissing(ServerPlayer player, Module module) {
        if (!unlocked(player)) {
            return "Niveau " + UNLOCK_LEVEL + " de Fermier requis";
        }
        if (boosted(player, module)) {
            return "Boost actif";
        }
        if (boostsLeft(player, module) <= 0) {
            return "Boost du jour deja utilise";
        }
        return missing(player, BOOST_MONEY, boostItems());
    }

    public static boolean boost(ServerPlayer player, Module module) {
        String missing = boostMissing(player, module);
        if (missing != null) {
            refuse(player, missing);
            return false;
        }
        if (!pay(player, BOOST_MONEY, boostItems())) {
            refuse(player, "Il te manque " + MoneyFormat.exact(BOOST_MONEY - Economy.balance(player)) + " $.");
            return false;
        }
        Data data = data(player);
        Map<String, Long> until = new HashMap<>(data.boostUntil());
        Map<String, Long> day = new HashMap<>(data.boostDay());
        until.put(module.key(), RealClock.epochMinutes() + BOOST_MINUTES);
        day.put(module.key(), RealClock.dayKey(RESET_HOUR));
        save(player, new Data(data.levels(), data.building(), Map.copyOf(until), Map.copyOf(day), data.pots()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                0.8f, 1.4f);
        say(player, Component.literal(module.label() + " booste : pousse x1,5 et +2 % de chance de muter pendant "
                + BOOST_MINUTES + " minutes.").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    // -- Les ameliorations ------------------------------------------------------------------------

    public static long buildingMinutesLeft(ServerPlayer player, Module module) {
        return Math.max(0L, data(player).building().getOrDefault(module.key(), 0L) - RealClock.epochMinutes());
    }

    public static boolean building(ServerPlayer player, Module module) {
        return buildingMinutesLeft(player, module) > 0L;
    }

    public static long upgradeMoney(int next) {
        return UPGRADE_MONEY[next - 1];
    }

    public static Cost[] upgradeItems(int next) {
        return Prices.UPGRADE[next - 1];
    }

    public static long upgradeMinutes(int next) {
        return UPGRADE_MINUTES[next - 1];
    }

    public static int upgradeFarmer(int next) {
        return UPGRADE_FARMER[next - 1];
    }

    public static int upgradeXp(int next) {
        return UPGRADE_XP[next - 1];
    }

    /** Ce qui empeche d'ameliorer, ou {@code null}. */
    public static String upgradeMissing(ServerPlayer player, Module module) {
        if (!unlocked(player)) {
            return "Niveau " + UNLOCK_LEVEL + " de Fermier requis";
        }
        int level = data(player).level(module);
        if (level >= MAX_LEVEL) {
            return "Niveau maximum atteint";
        }
        if (building(player, module)) {
            return "Amelioration en construction";
        }
        int next = level + 1;
        if (JobApi.level(player, Job.FARMER) < upgradeFarmer(next)) {
            return "Niveau " + upgradeFarmer(next) + " de Fermier requis";
        }
        return missing(player, upgradeMoney(next), upgradeItems(next));
    }

    public static boolean upgrade(ServerPlayer player, Module module) {
        // Une construction finie depuis le dernier passage compte d'abord : sinon la suivante
        // repartirait du meme niveau et la premiere serait perdue.
        update(player);
        String missing = upgradeMissing(player, module);
        if (missing != null) {
            refuse(player, missing);
            return false;
        }
        int next = data(player).level(module) + 1;
        if (!pay(player, upgradeMoney(next), upgradeItems(next))) {
            refuse(player, "Il te manque " + MoneyFormat.exact(upgradeMoney(next) - Economy.balance(player)) + " $.");
            return false;
        }
        Data data = data(player);
        Map<String, Long> building = new HashMap<>(data.building());
        building.put(module.key(), RealClock.epochMinutes() + upgradeMinutes(next));
        save(player, new Data(data.levels(), Map.copyOf(building), data.boostUntil(), data.boostDay(), data.pots()));
        player.level().playSound(null, player.blockPosition(), PalaBotanist.CONTROL_CONSTRUCTION.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        say(player, Component.literal("Amelioration du " + module.label().toLowerCase(java.util.Locale.ROOT)
                + " lancee : niveau " + next + " dans " + duration(upgradeMinutes(next)) + ".")
                .withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Les constructions finies : le niveau monte, l'XP tombe ({@code ModuleUpgradeJobExperience}). */
    public static void update(ServerPlayer player) {
        Data data = data(player);
        if (data.building().isEmpty()) {
            return;
        }
        long now = RealClock.epochMinutes();
        Map<String, Long> building = new HashMap<>(data.building());
        Map<String, Integer> levels = new HashMap<>(data.levels());
        List<Module> done = new ArrayList<>();
        for (Module module : Module.values()) {
            Long end = building.get(module.key());
            if (end != null && end <= now) {
                building.remove(module.key());
                levels.put(module.key(), Math.min(MAX_LEVEL, data.level(module) + 1));
                done.add(module);
            }
        }
        if (done.isEmpty()) {
            return;
        }
        save(player, new Data(Map.copyOf(levels), Map.copyOf(building), data.boostUntil(), data.boostDay(),
                data.pots()));
        for (Module module : done) {
            int level = Math.min(MAX_LEVEL, data.level(module) + 1);
            int xp = upgradeXp(level);
            JobApi.grant(player, Job.FARMER, xp);
            say(player, Component.literal(module.label() + " ameliore : niveau " + level + ", +"
                    + Math.round(GROWTH_PER_LEVEL * level * 100) + " % de pousse (+" + xp + " XP de Fermier).")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    // -- Couts ------------------------------------------------------------------------------------

    private static String missing(ServerPlayer player, long money, Cost[] items) {
        List<String> lacking = new ArrayList<>();
        if (Economy.balance(player) < money) {
            lacking.add(MoneyFormat.exact(money - Economy.balance(player)) + " $");
        }
        for (Cost cost : items) {
            int have = AgriInv.count(player, cost.resolved());
            if (have < cost.count()) {
                lacking.add(new Cost(cost.resolved(), cost.count() - have).label());
            }
        }
        return lacking.isEmpty() ? null : "Il te manque " + String.join(", ", lacking);
    }

    private static boolean pay(ServerPlayer player, long money, Cost[] items) {
        if (missing(player, money, items) != null || !Economy.tryDebit(player, money)) {
            return false;
        }
        for (Cost cost : items) {
            AgriInv.take(player, cost.resolved(), cost.count());
        }
        return true;
    }

    /** « 5 000 $ et 4 Poudre d'os ». */
    public static String costLabel(long money, Cost[] items) {
        StringBuilder label = new StringBuilder(MoneyFormat.exact(money) + " $");
        for (Cost cost : items) {
            label.append(" et ").append(cost.label());
        }
        return label.toString();
    }

    /** « 45 min », « 4 h », « 2 h 15 ». */
    public static String duration(long minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + (rest < 10 ? "0" : "") + rest;
    }

    private static void refuse(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED), true);
    }

    private static void say(ServerPlayer player, Component message) {
        player.sendSystemMessage(Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN).append(message));
    }

    // -- Les constructions finissent meme sans ouvrir le centre de controle ------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 0) {
            update(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            update(player);
        }
    }
}
