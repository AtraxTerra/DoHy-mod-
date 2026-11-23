package fr.atraxterra.dohy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.underground.DoHyUnderground;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.menu.Menus;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : le lot 23c (le laboratoire de recherche). */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> OWN = Set.of("dohy", "palamod", "palarpg");
    private static final Pattern RISKY_BLOCK = Pattern.compile(
            "tnt|dynamite|bomb|nuke|explos|lucky|portal|_fire|^fire|lava|landmine|detonat");
    private static final Pattern RISKY_ENTITY = Pattern.compile("missile|nuke|bomb|tnt|grenade|rocket|explos|shell"
            + "|bullet|arrow|projectile|warhead|strike|meteor|bolt|spear|fireball|ball|dynamite|potion|orb|laser|beam"
            + "|shot|lucky|firework|flare|dart|knife|shuriken|snowball|egg|pearl|trident|harpoon|hook|bobber");
    private static final Pattern GLITCH = Pattern.compile(
            "(?i)\\bnull\\b|\\bNaN\\b|Infinity|%[sdf]|\\{\\}|\\$\\{|\\b(item|block|entity|tooltip|message|gui|menu)"
                    + "\\.(dohy|palamod|palarpg)\\.[a-z0-9_.]+");
    private static final Pattern TRANSLATE = Pattern.compile("\"translate\":\"([^\"]+)\"");
    private static final String ALLOWED = "«»—·×";

    private static final int BX = 3200;
    private static final int BZ = 3200;
    private static final int EX = 3200;
    private static final int EZ = 3420;
    private static final int Y = -60;

    private static volatile UUID me;
    private static final int G = fr.atraxterra.dohy.farmland.FarmlandMap.G;
    private static final Map<BlockPos, String> PLACED = new LinkedHashMap<>();
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static int side;

    private ShotSamples() {
    }


    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-bug: ECHEC etape " + name, t);
                }
            });
            return null;
        });
    }

    private static ShotHarness.Shot client(String name, int extra, Runnable action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            try {
                action.run();
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC client " + name, t);
            }
            return null;
        });
    }

    private static void op(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player)
                .withPosition(player.position()).withLevel((ServerLevel) player.level()), command);
    }

    private static void as(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void log(String format, Object... args) {
        LOG.info("dohy-bug: " + format, args);
    }

    // -- Les textes ------------------------------------------------------------------------------

    /** Les cles de traduction sans texte, dans un composant et ses enfants. */
    private static void missing(Component component, Set<String> out) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (!Language.getInstance().has(translatable.getKey()) && translatable.getFallback() == null) {
                out.add(translatable.getKey());
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component inner) {
                    missing(inner, out);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            missing(sibling, out);
        }
    }

    /** Les signes d'un texte casse : « null », NaN, %s, cle brute, et les caracteres hors ASCII. */
    private static void inspect(String text, List<String> flags, Set<Character> exotic) {
        if (text == null) {
            flags.add("texte null");
            return;
        }
        if (!text.isEmpty() && text.charAt(0) == MenuView.TRANSLATED) {
            Matcher keys = TRANSLATE.matcher(text);
            while (keys.find()) {
                if (!Language.getInstance().has(keys.group(1))) {
                    flags.add("cle sans traduction " + keys.group(1));
                }
            }
            return;
        }
        Matcher glitch = GLITCH.matcher(text);
        if (glitch.find()) {
            flags.add("'" + glitch.group() + "' dans « " + (text.length() > 90 ? text.substring(0, 90) + "..." : text)
                    + " »");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127 && ALLOWED.indexOf(c) < 0) {
                exotic.add(c);
            }
        }
    }

    private static void views(ServerPlayer player, String label) {
        MinecraftServer server = player.level().getServer();
        int ok = 0;
        for (MenuController menu : Menus.all()) {
            String id = menu.id().toString();
            try {
                MenuView view = menu.view(player);
                if (view == null) {
                    log("vue {} [{}] : NULL", id, label);
                    continue;
                }
                List<String> flags = new ArrayList<>();
                Set<Character> exotic = new TreeSet<>();
                view.map(text -> {
                    inspect(text, flags, exotic);
                    return text == null ? "" : text;
                });
                int cards = 0;
                int tall = 0;
                for (MenuView.Section section : view.sections()) {
                    cards += section.cards().size();
                    for (MenuView.Card card : section.cards()) {
                        if (card.lines().size() > 3) {
                            tall++;
                            log("carte haute {} [{}] section « {} » (mise en page {}) : « {} » {} lignes {}", id, label,
                                    section.title(), section.layout(), card.title(), card.lines().size(), card.lines());
                        }
                    }
                }
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                UiNet.Show.STREAM_CODEC.encode(buffer, new UiNet.Show(view));
                int size = buffer.readableBytes();
                UiNet.Show.STREAM_CODEC.decode(buffer);
                buffer.release();
                ok++;
                log("vue {} [{}] : « {} » {} sections, {} cartes{}, {} octets{}{}", id, label, view.title(),
                        view.sections().size(), cards, tall > 0 ? " (" + tall + " a plus de 3 lignes)" : "", size,
                        flags.isEmpty() ? "" : " ; SIGNES " + flags, exotic.isEmpty() ? "" : " ; hors ASCII " + exotic);
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC vue " + id + " [" + label + "]", t);
            }
        }
        log("vues [{}] : {} / {} sans exception", label, ok, Menus.all().size());
    }

    private static void openMenu(ServerPlayer player, MenuController menu) {
        try {
            menu.open(player);
        } catch (Throwable t) {
            LOG.error("dohy-bug: ECHEC ouverture " + menu.id(), t);
        }
    }

    private static final Pattern RISKY_ACTION = Pattern.compile(
            "(?i)dissou|quitt|supprim|abandon|reset|reinit|disband|leave|delete|detrui|exil|renonc|bannir|kick|liquid");

    private static String key(String action) {
        int bar = action.indexOf('|');
        return bar < 0 ? action : action.substring(0, bar);
    }

    private static void clicks(ServerPlayer player) {
        int total = 0;
        int failed = 0;
        int skipped = 0;
        for (MenuController menu : Menus.all()) {
            MenuView view;
            try {
                view = menu.view(player);
            } catch (Throwable t) {
                LOG.error("dohy-bug: ECHEC vue avant clics " + menu.id(), t);
                continue;
            }
            List<String[]> actions = new ArrayList<>();
            for (MenuView.Section section : view.sections()) {
                for (MenuView.Card card : section.cards()) {
                    String text = card.title() + " " + card.confirm() + " " + card.action() + " " + card.rightAction();
                    if (RISKY_ACTION.matcher(text).find()) {
                        skipped++;
                        continue;
                    }
                    // Comme le client : ce qui suit la barre est le libelle de l'action, pas sa cle.
                    if (!card.action().isEmpty()) {
                        actions.add(new String[] {key(card.action()), "0"});
                        actions.add(new String[] {key(card.action()), "2"});
                    }
                    if (!card.rightAction().isEmpty()) {
                        actions.add(new String[] {key(card.rightAction()), "1"});
                    }
                }
            }
            int done = 0;
            for (String[] action : actions) {
                total++;
                try {
                    menu.handle(player, new UiNet.Action(menu.id(), "card", action[0], Integer.parseInt(action[1])));
                    done++;
                } catch (Throwable t) {
                    failed++;
                    LOG.error("dohy-bug: ECHEC clic " + menu.id() + " « " + action[0] + " » bouton " + action[1], t);
                }
            }
            UiNet.close(player);
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
            log("clics {} : {} / {} sans exception", menu.id(), done, actions.size());
        }
        log("clics : {} actions, {} en echec, {} cartes ecartees", total, failed, skipped);
    }

    private static MenuController menu(String path) {
        Identifier id = UiNet.id(path);
        for (MenuController menu : Menus.all()) {
            if (menu.id().equals(id)) {
                return menu;
            }
        }
        throw new IllegalStateException("menu inconnu " + path);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }


    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }


    // -- Le laboratoire de recherche (lot 23c) ------------------------------------------------------

    private static void teleport(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        ServerLevel world = player.level().getServer().overworld();
        world.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        player.teleport(new TeleportTransition(world, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch,
                TeleportTransition.DO_NOTHING));
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.setYRot(yaw);
                Minecraft.getInstance().player.setXRot(pitch);
            }
        });
    }

    /** Le regard d'une camera posee en (x, y des yeux, z) vers un point. */
    private static void look(ServerPlayer player, double x, double eyeY, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - eyeY;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        teleport(player, x, eyeY - 1.62d, z, yaw, pitch);
    }

    private static fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain(String id) {
        return fr.atraxterra.dohy.faction.tech.TechConfig.domain(id);
    }

    private static final BlockPos LAB = new BlockPos(3000, -60, 2996);
    private static final BlockPos LAB2 = new BlockPos(2996, -60, 2996);
    private static final BlockPos OUTSIDE = new BlockPos(3012, -60, 2996);

    private static fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity lab(ServerPlayer player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found
                ? found : null;
    }

    private static void labState(ServerPlayer player, BlockPos pos, String label) {
        fr.atraxterra.dohy.faction.tech.ResearchLabBlockEntity found = lab(player, pos);
        if (found == null) {
            log("labo [{}] {} : aucun ({})", label, pos.toShortString(),
                    BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()));
            return;
        }
        log("labo [{}] {} : etat {} ; energie {} FE ; cout {} FE/t ; combustible {} ticks ; reserve {} ; produits {} ;"
                        + " rythme brut {} pts/h ; x{} ; nation « {} » ; allume {}", label, pos.toShortString(),
                found.status(), found.energyStored(), found.cost(), found.fuel(), found.buffer(), found.produced(),
                Math.round(found.rawRate()), String.format(java.util.Locale.ROOT, "%.3f", found.multiplier()),
                found.nationName(), player.level().getBlockState(pos).getValue(
                        fr.atraxterra.dohy.faction.tech.ResearchLabBlock.LIT));
    }

    /** Les recherches en cours, leurs points, et la liste des laboratoires de la nation. */
    private static void state(ServerPlayer player, String label) {
        fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
        StringBuilder levels = new StringBuilder();
        for (fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain
                : fr.atraxterra.dohy.faction.tech.TechConfig.domains()) {
            levels.append(domain.id()).append('=').append(fr.atraxterra.dohy.faction.tech.Tech.level(data, domain));
            if (data.techRunning(domain.id())) {
                levels.append("(").append(data.techPoints(domain.id())).append(" pts)");
            }
            levels.append(' ');
        }
        log("etat [{}] : {}; labos {} (permis {}) ; rythme nation {} pts/h ; x{} ; pays +{} % ; banque {}", label, levels,
                data.labs(), fr.atraxterra.dohy.faction.tech.TechPerks.of(data).labs(),
                fr.atraxterra.dohy.faction.tech.Tech.rate(player),
                String.format(java.util.Locale.ROOT, "%.3f", fr.atraxterra.dohy.faction.tech.Tech.multiplier(player)),
                fr.atraxterra.dohy.faction.tech.Tech.countries(player,
                        fr.atraxterra.dohy.faction.tech.TechPerks.of(data)), data.bank());
    }

    private static void useOn(ServerPlayer player, BlockPos pos, ItemStack stack, boolean sneak) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(sneak);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0.0d, 0.5d, 0.0d), Direction.UP, pos, false));
        player.setShiftKeyDown(false);
        log("clic droit sur {} avec {} : {} ; au-dessus {}", pos.toShortString(), stack, result,
                BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos.above()).getBlock()));
    }

    private static void place(ServerPlayer player, BlockPos at) {
        useOn(player, at.below(), new ItemStack(fr.atraxterra.dohy.faction.tech.DoHyResearch.LAB_ITEM.get()), false);
    }

    private static void feedEnergy(ServerPlayer player, BlockPos pos, int amount) {
        var handler = net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK.getCapability(
                (ServerLevel) player.level(), pos, null, null, Direction.NORTH);
        if (handler == null) {
            log("energie : pas de capacite sur {}", pos.toShortString());
            return;
        }
        int total = 0;
        for (int i = 0; i < amount / 2000; i++) {
            try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                         net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int accepted = handler.insert(2000, transaction);
                transaction.commit();
                total += accepted;
            }
        }
        log("energie versee sur {} : {} FE (capacite {} FE)", pos.toShortString(), total, handler.getCapacityAsLong());
    }

    private static void codec(ServerPlayer player) {
        fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
        var encoded = fr.atraxterra.dohy.faction.FactionData.MAP_CODEC.codec()
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data);
        if (encoded.isError()) {
            log("codec : encodage en echec {}", encoded.error());
            return;
        }
        net.minecraft.nbt.Tag tag = encoded.getOrThrow();
        var decoded = fr.atraxterra.dohy.faction.FactionData.MAP_CODEC.codec()
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag);
        if (decoded.isError()) {
            log("codec : decodage en echec {}", decoded.error());
            return;
        }
        fr.atraxterra.dohy.faction.FactionData back = decoded.getOrThrow();
        boolean ok = data.labs().equals(back.labs());
        for (fr.atraxterra.dohy.faction.tech.TechConfig.Domain domain
                : fr.atraxterra.dohy.faction.tech.TechConfig.domains()) {
            ok &= data.techPoints(domain.id()) == back.techPoints(domain.id())
                    && data.techRunning(domain.id()) == back.techRunning(domain.id())
                    && fr.atraxterra.dohy.faction.tech.Tech.level(data, domain)
                    == fr.atraxterra.dohy.faction.tech.Tech.level(back, domain);
        }
        String text = tag.toString();
        int at = text.indexOf("tech:");
        log("codec : aller-retour {} ; extrait « {} »", ok ? "identique" : "DIFFERENT",
                at < 0 ? "pas de champ tech" : text.substring(at, Math.min(text.length(), at + 240)));
    }

    /** Une recherche du lot 23b : une fin prevue, pas de points. */
    @SuppressWarnings("unchecked")
    private static void legacy(ServerPlayer player, String id, long endsIn) {
        try {
            fr.atraxterra.dohy.faction.FactionData data = FactionApi.data(player);
            java.lang.reflect.Field running = data.getClass().getDeclaredField("techRunning");
            java.lang.reflect.Field points = data.getClass().getDeclaredField("techPoints");
            running.setAccessible(true);
            points.setAccessible(true);
            ((Map<String, Long>) running.get(data)).put(id, System.currentTimeMillis() + endsIn);
            ((Map<String, Long>) points.get(data)).remove(id);
            log("recherche du lot 23b simulee : {} finit dans {} min ; points {}", id, endsIn / 60000L,
                    data.techPoints(id));
        } catch (ReflectiveOperationException error) {
            log("recherche du lot 23b : {}", error.toString());
        }
    }

    private static void act(ServerPlayer player, MenuController menu, String key, int button) {
        menu.handle(player, new UiNet.Action(menu.id(), "card", key, button));
    }

    static void register() {
        ShotHarness.SHOTS.add(client("a0_reglages", 5, () -> Minecraft.getInstance().options.renderDistance().set(8)));
        ShotHarness.SHOTS.add(server("a1_depart", 100, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            player.setGameMode(GameType.SURVIVAL);
            teleport(player, 3000.5d, -60.0d, 3000.5d, 180.0f, 20.0f);
            Economy.set(player, 1_000_000L);
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            log("laboratoire : config {}", fr.atraxterra.dohy.faction.tech.ResearchLabConfig.config());
            String refusal = FactionApi.create(player, "Testeurs", "Le lot 23c", List.of(), 3);
            log("nation : {}", refusal == null ? "fondee" : refusal);
            log("claim : {}", FactionApi.claim(player));
            FactionApi.creditBank(player, 50_000_000L, "Essai");
            log("rechercher sans laboratoire : « {} »", fr.atraxterra.dohy.faction.tech.Tech.blocker(player,
                    domain("energie")));
            state(player, "nation fondee");
            place(player, OUTSIDE);
            log("hors claim : {}", BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(OUTSIDE).getBlock()));
            place(player, LAB);
            labState(player, LAB, "pose");
            state(player, "un laboratoire");
            place(player, LAB2);
            log("deuxieme laboratoire au Gouvernement 0 : {}", BuiltInRegistries.BLOCK.getKey(
                    player.level().getBlockState(LAB2).getBlock()));
        }));
        ShotHarness.SHOTS.add(server("a2_labo_eteint", 60, player -> {
            labState(player, LAB, "sans energie");
            look(player, 3002.4d, -58.35d, 2998.7d, 3000.5d, -59.45d, 2996.5d);
        }));
        ShotHarness.SHOTS.add(server("a3_combustible", 100, player -> {
            useOn(player, LAB, new ItemStack(Items.COAL, 64), false);
            labState(player, LAB, "charbon donne");
        }));
        ShotHarness.SHOTS.add(server("a4_labo_allume", 40, player -> {
            labState(player, LAB, "au charbon");
            look(player, 2998.8d, -58.35d, 2998.4d, 3000.5d, -59.45d, 2996.5d);
        }));
        ShotHarness.SHOTS.add(server("a5_labo_de_pres", 40, player ->
                look(player, 3000.5d, -58.38d, 2998.2d, 3000.5d, -59.3d, 2996.6d)));
        ShotHarness.SHOTS.add(server("a5b_labo_de_cote", 40, player ->
                look(player, 3002.6d, -58.38d, 2996.5d, 3000.5d, -59.4d, 2996.5d)));
        ShotHarness.SHOTS.add(server("a6_ecran_labo", 40, player -> {
            teleport(player, 3000.5d, -60.0d, 2998.5d, 180.0f, 30.0f);
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
        }));
        ShotHarness.SHOTS.add(server("a7_recherche", 200, player -> {
            fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player, domain("energie"));
            act(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE, "research", 0);
            state(player, "energie II lancee");
            feedEnergy(player, LAB, 100_000);
            labState(player, LAB, "plein d'energie");
        }));
        ShotHarness.SHOTS.add(server("a8_points", 40, player -> {
            labState(player, LAB, "10 s plus tard");
            state(player, "points verses");
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
        }));
        ShotHarness.SHOTS.add(server("a9_pays", 40, player -> {
            UiNet.close(player);
            double before = fr.atraxterra.dohy.faction.tech.Tech.multiplier(player);
            op(player, "f pays donner " + fr.atraxterra.dohy.faction.enterprise.Enterprises.countryName(
                    fr.atraxterra.dohy.faction.enterprise.Enterprises.here(player).country()));
            log("pays : {} ; multiplicateur {} -> {}", fr.atraxterra.dohy.earth.EarthNations.heldBy(player), before,
                    fr.atraxterra.dohy.faction.tech.Tech.multiplier(player));
            op(player, "f techno palier gouvernement 3");
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
            place(player, LAB2);
            labState(player, LAB2, "deuxieme, au Gouvernement III");
            feedEnergy(player, LAB2, 40_000);
            state(player, "deux laboratoires");
        }));
        ShotHarness.SHOTS.add(server("b0_deux_labos", 60, player -> {
            UiNet.close(player);
            labState(player, LAB2, "deuxieme en marche");
            look(player, 2998.5d, -58.38d, 2999.8d, 2998.5d, -59.4d, 2996.5d);
        }));
        ShotHarness.SHOTS.add(server("b1_technologies", 40, player -> {
            state(player, "avant l'ecran");
            openMenu(player, fr.atraxterra.dohy.faction.FactionTreeMenus.Tree.INSTANCE);
        }));
        ShotHarness.SHOTS.add(server("b2_energie", 40, player ->
                fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player, domain("energie"))));
        ShotHarness.SHOTS.add(server("b3_finie", 40, player -> {
            op(player, "f techno points 1500");
            state(player, "1 500 points de plus");
            labState(player, LAB, "apres energie II");
            fr.atraxterra.dohy.faction.FactionTreeMenus.Branch.INSTANCE.show(player, domain("energie"));
        }));
        ShotHarness.SHOTS.add(server("b4_casse", 40, player -> {
            UiNet.close(player);
            log("casser le deuxieme : {}", player.gameMode.destroyBlock(LAB2));
            state(player, "un laboratoire casse");
            legacy(player, "industrie", 3L * 3_600_000L);
            fr.atraxterra.dohy.faction.tech.Tech.complete(player, false);
            state(player, "recherche du lot 23b convertie");
            codec(player);
            op(player, "dohy reload");
            state(player, "apres /dohy reload");
        }));
        ShotHarness.SHOTS.add(server("b5_reserve", 60, player -> {
            op(player, "f techno finir");
            state(player, "plus de recherche");
            labState(player, LAB, "sans recherche");
            fr.atraxterra.dohy.faction.tech.ResearchLabMenu.open(player, LAB);
        }));
        ShotHarness.SHOTS.add(server("h0_vues", 10, player -> views(player, "lot 23c")));
        ShotHarness.SHOTS.add(server("h1_clics", 100, player -> {
            UiNet.close(player);
            clicks(player);
            state(player, "apres les clics");
        }));
    }
}
