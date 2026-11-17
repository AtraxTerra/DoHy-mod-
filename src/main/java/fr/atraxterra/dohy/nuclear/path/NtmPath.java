package fr.atraxterra.dohy.nuclear.path;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Toasts;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le parcours NTM : les premieres machines de NTM NEXT, une etape apres
 * l'autre, de l'enclume a la centrifugeuse ({@code data/dohy/ntm_path.json}).
 *
 * <p>Chaque etape a un objectif que le serveur verifie au moment de valider :
 * des objets dans le sac ({@code have}, rien n'est pris) ou une machine posee
 * pres du joueur ({@code near}). La recompense est versee une seule fois. Sans
 * NTM NEXT, le parcours est inerte : le menu le dit, et rien ne se verifie.
 */
public final class NtmPath {

    /** L'identifiant de mod de NTM NEXT. */
    public static final String MOD = "hbm";

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "ntm_path.json");

    private static volatile Book book = Book.EMPTY;
    private static volatile List<Located> steps = List.of();
    private static volatile Map<String, Located> byId = Map.of();

    private NtmPath() {
    }

    // -- Le fichier -----------------------------------------------------------------------

    /** Un objet de recompense. */
    public record Prize(Identifier id, int count) {
        static final Codec<Prize> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(Prize::id),
                Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Prize::count)
        ).apply(instance, Prize::new));
    }

    /** Ce que rapporte une etape. */
    public record Reward(long money, List<Prize> items) {
        static final Reward NONE = new Reward(0L, List.of());
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("money", 0L).forGetter(Reward::money),
                Prize.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Reward::items)
        ).apply(instance, Reward::new));
    }

    /**
     * L'objectif : {@code have} (avoir {@code count} objets parmi {@code items},
     * ou un de chaque avec {@code all}) ou {@code near} (une de ces machines posee
     * a moins de {@link #radius()} blocs).
     */
    public record Goal(String type, List<Identifier> items, int count, boolean all, String label) {
        static final Codec<Goal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Goal::type),
                Identifier.CODEC.listOf().fieldOf("items").forGetter(Goal::items),
                Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Goal::count),
                Codec.BOOL.optionalFieldOf("all", false).forGetter(Goal::all),
                Codec.STRING.fieldOf("label").forGetter(Goal::label)
        ).apply(instance, Goal::new));

        public boolean near() {
            return "near".equals(type);
        }
    }

    public record Step(String id, String title, Identifier icon, Goal goal, List<String> text, Reward reward) {
        static final Codec<Step> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Step::id),
                Codec.STRING.fieldOf("title").forGetter(Step::title),
                Identifier.CODEC.fieldOf("icon").forGetter(Step::icon),
                Goal.CODEC.fieldOf("goal").forGetter(Step::goal),
                Codec.STRING.listOf().optionalFieldOf("text", List.of()).forGetter(Step::text),
                Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(Step::reward)
        ).apply(instance, Step::new));

        public ItemStack iconStack() {
            return stack(icon, 1);
        }
    }

    public record Chapter(String id, String name, String color, List<Step> steps) {
        static final Codec<Chapter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Chapter::id),
                Codec.STRING.fieldOf("name").forGetter(Chapter::name),
                Codec.STRING.optionalFieldOf("color", "22E558").forGetter(Chapter::color),
                Step.CODEC.listOf().fieldOf("steps").forGetter(Chapter::steps)
        ).apply(instance, Chapter::new));

        /** La couleur du chapitre, opaque. */
        public int accent() {
            try {
                return 0xFF000000 | Integer.parseInt(color, 16);
            } catch (NumberFormatException malformed) {
                return 0xFF22E558;
            }
        }
    }

    record Book(int radius, List<Chapter> chapters) {
        static final Book EMPTY = new Book(8, List.of());
        static final Codec<Book> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 16).optionalFieldOf("radius", 8).forGetter(Book::radius),
                Chapter.CODEC.listOf().fieldOf("chapters").forGetter(Book::chapters)
        ).apply(instance, Book::new));
    }

    /** Une etape a sa place : son chapitre, le rang du chapitre et son numero dans le parcours (1, 2, ...). */
    public record Located(Step step, Chapter chapter, int chapterIndex, int number) {
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Parcours NTM introuvable : data/{}/{}", LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Book.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Parcours NTM illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Book loaded = result.getOrThrow();
            List<Located> flat = new ArrayList<>();
            Map<String, Located> index = new LinkedHashMap<>();
            int number = 0;
            for (int chapter = 0; chapter < loaded.chapters().size(); chapter++) {
                Chapter current = loaded.chapters().get(chapter);
                for (Step step : current.steps()) {
                    Located located = new Located(step, current, chapter, ++number);
                    flat.add(located);
                    if (index.put(step.id(), located) != null) {
                        DoHy.LOGGER.warn("Parcours NTM : etape en double « {} »", step.id());
                    }
                }
            }
            book = loaded;
            steps = List.copyOf(flat);
            byId = index;
            if (installed()) {
                report(flat);
            }
            DoHy.LOGGER.info("Parcours NTM : {} etapes en {} chapitres{}", flat.size(), loaded.chapters().size(),
                    installed() ? "" : " (NTM NEXT absent : inerte)");
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement du parcours NTM", error);
            return false;
        }
    }

    /** Ce que le fichier nomme et que cette version de NTM n'a pas : l'etape ne se validerait jamais. */
    private static void report(List<Located> flat) {
        for (Located located : flat) {
            Goal goal = located.step().goal();
            for (Identifier id : goal.items()) {
                boolean exists = goal.near() ? BuiltInRegistries.BLOCK.getOptional(id).isPresent()
                        : BuiltInRegistries.ITEM.getOptional(id).isPresent();
                if (!exists) {
                    DoHy.LOGGER.warn("Parcours NTM : etape « {} » : {} inconnu de cette version de NTM",
                            located.step().id(), id);
                }
            }
        }
    }

    // -- Lecture --------------------------------------------------------------------------

    /** NTM NEXT est-il installe ? Sans lui, le parcours ne fait rien. */
    public static boolean installed() {
        return ModList.get().isLoaded(MOD);
    }

    public static List<Located> steps() {
        return steps;
    }

    public static List<Chapter> chapters() {
        return book.chapters();
    }

    public static int radius() {
        return book.radius();
    }

    public static Located byId(String id) {
        return byId.get(id);
    }

    public static NtmPathData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.NTM_PATH.get());
    }

    /** L'etape en cours : la premiere qui n'est pas validee, ou {@code null} si tout est fait. */
    public static Located current(ServerPlayer player) {
        NtmPathData data = data(player);
        for (Located located : steps) {
            if (!data.done(located.step().id())) {
                return located;
            }
        }
        return null;
    }

    public static int doneCount(ServerPlayer player) {
        NtmPathData data = data(player);
        int done = 0;
        for (Located located : steps) {
            if (data.done(located.step().id())) {
                done++;
            }
        }
        return done;
    }

    /** Une pile d'un objet, ou du papier si l'objet n'existe pas dans cette partie. */
    public static ItemStack stack(Identifier id, int count) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? new ItemStack(Items.PAPER) : new ItemStack(item, Math.max(1, count));
    }

    // -- L'objectif -----------------------------------------------------------------------

    /**
     * Ou en est un objectif : {@code have} sur {@code need}, et la phrase qui le
     * dit (« 12 / 24 dans ton sac », « Posee a 3 blocs »).
     */
    public record Status(long have, long need, boolean met, String line) {
    }

    public static Status status(ServerPlayer player, Step step) {
        Goal goal = step.goal();
        if (goal.near()) {
            int distance = nearest(player, goal.items(), radius());
            if (distance >= 0) {
                return new Status(1, 1, true, distance <= 1 ? "Posee juste a cote de toi" : "Posee a " + distance
                        + " blocs de toi");
            }
            return new Status(0, 1, false, "Pose-la, puis valide a moins de " + radius() + " blocs");
        }
        if (goal.all()) {
            int owned = 0;
            for (Identifier id : goal.items()) {
                if (count(player, List.of(id)) >= goal.count()) {
                    owned++;
                }
            }
            boolean met = owned >= goal.items().size();
            return new Status(owned, goal.items().size(), met,
                    owned + " / " + goal.items().size() + " dans ton sac");
        }
        long owned = count(player, goal.items());
        return new Status(Math.min(owned, goal.count()), goal.count(), owned >= goal.count(),
                Math.min(owned, goal.count()) + " / " + goal.count() + " dans ton sac");
    }

    /** Combien le joueur porte de ces objets, tous ensemble. */
    private static long count(ServerPlayer player, List<Identifier> ids) {
        List<Item> items = new ArrayList<>();
        for (Identifier id : ids) {
            BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).ifPresent(items::add);
        }
        if (items.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && items.contains(stack.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * La distance (en blocs, la plus grande des trois coordonnees) du bloc de ces
     * machines le plus proche, ou -1 s'il n'y en a aucun dans le rayon. Une
     * machine de NTM en plusieurs blocs compte par n'importe lequel des siens.
     */
    private static int nearest(ServerPlayer player, List<Identifier> ids, int radius) {
        List<Block> blocks = new ArrayList<>();
        for (Identifier id : ids) {
            BuiltInRegistries.BLOCK.getOptional(id).filter(block -> block != Blocks.AIR).ifPresent(blocks::add);
        }
        if (blocks.isEmpty()) {
            return -1;
        }
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int best = -1;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distance = Math.max(Math.abs(dy), Math.max(Math.abs(dx), Math.abs(dz)));
                    if (best >= 0 && distance >= best) {
                        continue;
                    }
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    Block block = level.getBlockState(cursor).getBlock();
                    if (blocks.contains(block)) {
                        best = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Ce bloc est-il une machine de l'objectif de l'etape en cours du joueur ? */
    static boolean placesCurrent(ServerPlayer player, Block block) {
        Located current = current(player);
        if (current == null || !current.step().goal().near()) {
            return false;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return current.step().goal().items().contains(id);
    }

    // -- La recompense --------------------------------------------------------------------

    /** « 5 000 $ + 2 moteurs », a traduire par le client (les noms d'objets). */
    public static Component rewardText(Reward reward) {
        MutableComponent text = Component.literal(MoneyFormat.exact(reward.money()) + " $");
        for (Prize prize : reward.items()) {
            text.append(" + " + prize.count() + " ").append(Gui.displayName(prize.id()));
        }
        return text;
    }

    /**
     * Valide l'etape en cours si son objectif est atteint : l'argent et les
     * objets sont verses, l'etape suivante s'ouvre.
     * @return la raison du refus, ou {@code null} si l'etape est validee
     */
    public static String claim(ServerPlayer player) {
        if (!installed()) {
            return "NTM NEXT n'est pas installe sur ce serveur.";
        }
        Located current = current(player);
        if (current == null) {
            return steps.isEmpty() ? "Le parcours n'a pas pu etre lu : voir le journal du serveur."
                    : "Le parcours est termine : il n'y a plus d'etape a valider.";
        }
        Step step = current.step();
        Status status = status(player, step);
        if (!status.met()) {
            return step.goal().near()
                    ? "Aucune machine de l'etape a moins de " + radius() + " blocs : pose-la, puis valide pres d'elle."
                    : "Il te manque encore de quoi valider : " + step.goal().label() + " (" + status.line() + ").";
        }
        NtmPathData data = data(player);
        Reward reward = step.reward();
        if (!data.complete(step.id(), reward.money())) {
            return "Cette etape est deja validee.";
        }
        if (reward.money() > 0L) {
            Economy.credit(player, reward.money());
        }
        for (Prize prize : reward.items()) {
            give(player, stack(prize.id(), prize.count()));
        }
        NtmPathEvents.forget(player);

        Located next = current(player);
        player.sendSystemMessage(Component.literal("[NTM] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Etape " + current.number() + " validee : " + step.title() + ". ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("+").append(rewardText(reward)).withStyle(ChatFormatting.GOLD)));
        if (next != null) {
            player.sendSystemMessage(Component.literal("[NTM] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Etape suivante : " + next.step().title() + ". ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(link("[Voir]")));
        } else {
            player.sendSystemMessage(Component.literal("[NTM] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Parcours termine : toute la premiere industrie est montee. Bravo !")
                            .withStyle(ChatFormatting.GOLD)));
        }
        Toasts.send(player, step.iconStack(), next == null ? "PARCOURS NTM TERMINE" : "ETAPE VALIDEE", step.title(),
                "+" + MoneyFormat.exact(reward.money()) + " $", current.chapter().accent(), "");
        player.level().playSound(null, player.blockPosition(),
                next == null ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.6f, 1.0f);
        return null;
    }

    /** Donne une pile, decoupee a sa taille maximale ; ce qui ne rentre pas tombe aux pieds du joueur. */
    private static void give(ServerPlayer player, ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        int left = stack.getCount();
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(max, left));
            left -= part.getCount();
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                player.drop(part, false);
            }
        }
    }

    /** Le lien cliquable qui ouvre le parcours. */
    static MutableComponent link(String label) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/parcours"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/parcours"))));
    }

    /** Pour l'administration : remet a zero le parcours d'un joueur. */
    public static void reset(ServerPlayer player) {
        data(player).reset();
        NtmPathEvents.forget(player);
    }

    /** Un identifiant d'etape valide, pour la commande d'administration. */
    public static Optional<Located> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
