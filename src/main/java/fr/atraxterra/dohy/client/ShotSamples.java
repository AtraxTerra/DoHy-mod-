package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.earth.mine.EarthMines;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.Sieges;
import fr.atraxterra.dohy.faction.war.Soldier;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.faction.war.WarMenu;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : la riposte contre les nations (lot 22l-2). */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile UUID me;
    private static EarthBodies.Body mine;
    private static int cx;
    private static int cz;
    private static int bx;
    private static int bz;
    private static int nation = -1;
    private static int given;
    private static int first = -1;
    private static int second = -1;

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
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
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
                LOG.error("dohy-shots: ECHEC client " + name, t);
            }
            return null;
        });
    }

    /** Une commande du serveur, avec le joueur pour entite (les commandes d'operateur passent). */
    private static void op(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player)
                .withPosition(player.position()).withLevel((ServerLevel) player.level()), command);
        log("/{}", command);
    }

    /** Une commande tapee par le joueur lui-meme. */
    private static void as(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        log("(joueur) /{}", command);
    }

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

    private static void log(String format, Object... args) {
        LOG.info("dohy-shots: [riposte] " + format, args);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow();
    }

    private static void scroll(double amount) {
        try {
            var screen = Minecraft.getInstance().gui.screen();
            var field = screen.getClass().getDeclaredField("content");
            field.setAccessible(true);
            Object content = field.get(screen);
            var method = content.getClass().getMethod("mouseScrolled", double.class, double.class, double.class);
            method.setAccessible(true);
            method.invoke(content, 0.0, 0.0, amount);
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC defilement", t);
        }
    }

    private static void openChat() {
        try {
            Screen chat = null;
            for (var constructor : net.minecraft.client.gui.screens.ChatScreen.class.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 1 && types[0] == String.class) {
                    chat = (Screen) constructor.newInstance("");
                } else if (types.length == 2 && types[0] == String.class && types[1] == boolean.class) {
                    chat = (Screen) constructor.newInstance("", false);
                }
            }
            if (chat != null) {
                Minecraft.getInstance().gui.setScreen(chat);
            }
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC chat", t);
        }
    }

    private static void closeScreen() {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
    }

    /** Une methode de paquet de la guerre, par reflexion (le banc d'essai n'est pas dans le paquet). */
    private static Object call(String type, String name, Class<?>[] types, Object... args) {
        try {
            var method = Class.forName("fr.atraxterra.dohy.faction.war." + type).getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC reflexion " + type + "." + name, t);
            return null;
        }
    }

    private static boolean fortUp() {
        return me != null && call("Strongholds", "of", new Class<?>[] {UUID.class}, me) != null;
    }

    private static WarData war(ServerPlayer player) {
        return player.getData(Factions.WAR.get());
    }

    private static String name(int index) {
        return index < 0 ? "-" : NationTraits.of(index).name();
    }

    private static void state(ServerPlayer player, String when) {
        WarData data = war(player);
        List<String> lines = new ArrayList<>();
        for (WarData.Relation relation : data.relations()) {
            if (relation.nation() == nation || relation.nation() == first || relation.nation() == second) {
                lines.add(relation.traits().name() + "{" + relation.stance() + ", hostilite "
                        + Math.round(relation.hostility()) + ", tresor " + relation.treasury() + ", armee "
                        + relation.army() + ", doit " + relation.owed() + " $, pays " + relation.retaken()
                        + ", mines " + relation.retakenMines() + ", declaree " + relation.declared() + ", vaincue "
                        + relation.beaten(System.currentTimeMillis()) + ", allie " + name(relation.ally())
                        + ", lecons " + relation.lessons().assaults() + " " + relation.lessons().causes() + " "
                        + relation.lessons().deaths().size() + " morts, lecon '" + relation.lessons().lesson() + "'}");
            }
        }
        List<String> held = new ArrayList<>();
        for (int country : EarthNations.heldBy(player)) {
            held.add(EarthNations.countryName(country));
        }
        List<String> mines = new ArrayList<>();
        for (var concession : EarthMines.of(player)) {
            mines.add(concession.mine());
        }
        log("{} : banque {} $ ; pays {} ; mines {} ; siege {} ; {}", when, FactionApi.data(player).bank(), held, mines,
                Sieges.status(player.getUUID()), lines);
    }

    private static List<Soldier> soldiers(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        return level.getEntitiesOfClass(Soldier.class, new AABB(bx - 160, -80, bz - 160, bx + 160, 40, bz + 160));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 120, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty easy");
            op(player, "kill @e[type=minecraft:slime]");
            player.setGameMode(GameType.SURVIVAL);
            mine = EarthMines.find("sadiola");
            if (mine == null) {
                for (EarthBodies.Body body : EarthBodies.all()) {
                    if (body.kind() == EarthBodies.Kind.ORE && body.tier() == 3) {
                        mine = body;
                        break;
                    }
                }
            }
            MinecraftServer server = player.level().getServer();
            cx = (int) Math.floor(mine.centerX());
            cz = (int) Math.floor(mine.centerZ());
            nation = EarthMines.nation(server, mine);
            first = (nation + 1) % NationTraits.count();
            second = (nation + 2) % NationTraits.count();
            // La base : 120 blocs au nord de la mine, au coeur d'un chunk.
            bx = ((cx >> 4) << 4) + 8;
            bz = (((cz - 120) >> 4) << 4) + 8;
            log("Terre {} ; mine {} en {} {} ({}), nation {} ({}) ; coalition {} + {} ; base {} {}", EarthNations.active(),
                    mine.name(), cx, cz, EarthMines.countryName(mine), nation, name(nation), name(first),
                    name(second), bx, bz);
            teleport(player, bx + 0.5d, -60.0d, bz + 0.5d, 180.0f, 20.0f);
            player.getInventory().clearContent();
            Economy.set(player, 1_000_000L);
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            String refusal = FactionApi.create(player, "Lynx", "La riposte", List.of(), 3);
            log("faction : {} ; existe {}", refusal == null ? "fondee" : refusal, FactionApi.has(player));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    teleport(player, bx + dx * 16 + 0.5d, -60.0d, bz + dz * 16 + 0.5d, 180.0f, 20.0f);
                    String claim = FactionApi.claim(player);
                    if (claim != null) {
                        log("claim {} {} : {}", dx, dz, claim);
                    }
                }
            }
            teleport(player, bx + 0.5d, -60.0d, bz + 0.5d, 180.0f, 20.0f);
            FactionApi.creditBank(player, 400_000L, "Essai");
            as(player, "f pays capitale");
            as(player, "guerre coffre");
            // Un pays de sa region, et la mine : ce qu'elle viendra reprendre.
            List<List<Integer>> holdings = EarthNations.rivalHoldings(server);
            int capital = EarthNations.nation(player).capital();
            for (int country : holdings.get(nation)) {
                if (country != capital) {
                    given = country;
                    break;
                }
            }
            op(player, "f pays donner " + EarthNations.countryName(given));
            op(player, "mines donner " + mine.id());
            log("capitale {} ; donne {} ; en jeu face a {} : pays {}, mine {}", EarthNations.countryName(capital),
                    EarthNations.countryName(given), name(nation), EarthNations.countryName(
                            EarthNations.stake(player, nation)), EarthMines.stake(player, nation) == null ? "-"
                            : EarthMines.stake(player, nation).name());
            state(player, "depart");
        }));
        // Un assaut reussi de la nation, sans bataille : elle prend 30 000 $, le pays et la mine.
        ShotHarness.SHOTS.add(server("b1_pillage", 40, player -> {
            teleport(player, bx + 0.5d, -60.0d, bz + 0.5d, 180.0f, 20.0f);
            log("en jeu face a {} : pays {}, mine {}", name(nation), EarthNations.countryName(
                    EarthNations.stake(player, nation)), EarthMines.stake(player, nation) == null ? "-"
                    : EarthMines.stake(player, nation).name());
            op(player, "guerre admin pillage " + (nation + 1) + " 30000");
            state(player, "apres le pillage");
            WarMenu.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(client("b2_guerre_bas", 25, () -> scroll(-12.0)));
        // Ta contre-offensive : depuis l'ecran, comme un clic sur sa carte.
        ShotHarness.SHOTS.add(server("c1_declarer", 40, player -> {
            WarMenu.INSTANCE.handle(player, new UiNet.Action(Identifier.parse("dohy:guerre"), "card",
                    "declare:" + nation, 0));
            // Sa place forte, plus pres que d'ordinaire pour l'essai.
            op(player, "guerre admin placeforte " + (nation + 1) + " 260");
            UiNet.close(player);
            state(player, "guerre declaree");
        }));
        ShotHarness.SHOTS.add(client("c2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("c3_refus", 20, player -> {
            closeScreen();
            as(player, "guerre declarer " + (first + 1));
        }));
        ShotHarness.SHOTS.add(client("c4_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("d1_placeforte", 40, player -> {
            closeScreen();
            ShotHarness.until = ShotSamples::fortUp;
            log("attente de la place forte");
        }));
        ShotHarness.SHOTS.add(server("d2_guerre", 40, player -> {
            state(player, "place forte debout");
            WarMenu.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("d3_vue", 160, player -> {
            UiNet.close(player);
            op(player, "gamemode spectator");
            // La place forte, vue d'en haut a quelques dizaines de blocs (en spectateur : ses gardes l'ignorent).
            Object fort = call("Strongholds", "of", new Class<?>[] {UUID.class}, me);
            try {
                var field = fort.getClass().getDeclaredField("center");
                field.setAccessible(true);
                BlockPos center = (BlockPos) field.get(fort);
                double vx = center.getX() + 34.0d;
                double vz = center.getZ() + 34.0d;
                float yaw = (float) Math.toDegrees(Math.atan2(center.getZ() - vz, center.getX() - vx)) - 90.0f;
                teleport(player, vx, center.getY() + 24.0d, vz, yaw, 28.0f);
                log("place forte en {} {} {} ({} blocs du coffre)", center.getX(), center.getY(), center.getZ(),
                        Math.round(Math.sqrt(Math.pow(center.getX() - bx, 2) + Math.pow(center.getZ() - bz, 2))));
            } catch (Throwable t) {
                LOG.error("dohy-shots: ECHEC vue de la place forte", t);
            }
        }));
        ShotHarness.SHOTS.add(server("e1_rase", 60, player -> {
            op(player, "gamemode survival");
            teleport(player, bx + 0.5d, -60.0d, bz + 0.5d, 180.0f, 20.0f);
            long before = FactionApi.data(player).bank();
            op(player, "guerre admin rase");
            log("place forte rasee : banque {} -> {} (+{})", before, FactionApi.data(player).bank(),
                    FactionApi.data(player).bank() - before);
            state(player, "apres la chute");
            log("{} aux mains de {} ; {} tenue par la faction : {}", EarthNations.countryName(given),
                    EarthNations.holder(player.level().getServer(), given).name(), mine.name(),
                    EarthMines.ownedBy(player, mine));
        }));
        ShotHarness.SHOTS.add(client("e2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("e3_refus", 20, player -> {
            closeScreen();
            as(player, "guerre declarer " + (nation + 1));
        }));
        ShotHarness.SHOTS.add(client("e4_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("e5_guerre", 40, player -> {
            WarMenu.INSTANCE.open(player);
        }));
        // La coalition.
        ShotHarness.SHOTS.add(server("f1_coalition", 30, player -> {
            UiNet.close(player);
            op(player, "guerre admin coalition " + (first + 1) + " " + (second + 1));
            state(player, "coalition");
        }));
        ShotHarness.SHOTS.add(client("f2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("f3_guerre", 40, player -> {
            WarMenu.INSTANCE.open(player);
        }));
        // Une lecon apprise, puis l'assaut de la coalition.
        ShotHarness.SHOTS.add(server("g1_assaut", 40, player -> {
            UiNet.close(player);
            op(player, "guerre admin lecon " + (first + 1) + " lame 6");
            op(player, "gamemode creative");
            teleport(player, bx + 0.5d, -60.0d, bz + 0.5d, 180.0f, 20.0f);
            op(player, "guerre admin assaut " + (first + 1));
            state(player, "assaut lance");
            ShotHarness.until = () -> {
                String status = Sieges.status(me);
                return status != null && status.startsWith("Camp");
            };
        }));
        ShotHarness.SHOTS.add(client("g2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("g3_soldats", 30, player -> {
            closeScreen();
            log("siege : {}", Sieges.status(player.getUUID()));
            // Le camp : on se poste entre lui et le coffre, face au camp, pour voir sortir la premiere vague.
            Object siege = call("Sieges", "of", new Class<?>[] {UUID.class}, me);
            try {
                var field = siege.getClass().getDeclaredField("camp");
                field.setAccessible(true);
                BlockPos camp = (BlockPos) field.get(siege);
                double angle = Math.atan2(bz - camp.getZ(), bx - camp.getX());
                double vx = camp.getX() + Math.cos(angle) * 15.0d;
                double vz = camp.getZ() + Math.sin(angle) * 15.0d;
                float yaw = (float) Math.toDegrees(Math.atan2(camp.getZ() - vz, camp.getX() - vx)) - 90.0f;
                teleport(player, vx, camp.getY() + 2.0d, vz, yaw, 6.0f);
                log("camp en {} {} {}", camp.getX(), camp.getY(), camp.getZ());
            } catch (Throwable t) {
                LOG.error("dohy-shots: ECHEC vue du camp", t);
            }
            ShotHarness.until = () -> {
                String status = Sieges.status(me);
                return status != null && !status.startsWith("Camp") && !status.startsWith("Leur commandant");
            };
        }));
        ShotHarness.SHOTS.add(server("g4_troupe", 100, player -> {
            List<Soldier> all = soldiers(player);
            int theirs = 0;
            double sx = 0;
            double sz = 0;
            for (Soldier soldier : all) {
                if (soldier.nation() == second) {
                    theirs++;
                }
                sx += soldier.getX();
                sz += soldier.getZ();
            }
            log("soldats deployes : {} dont {} {} ; siege {}", all.size(), theirs, NationTraits.of(second).de(),
                    Sieges.status(player.getUUID()));
            if (!all.isEmpty()) {
                sx /= all.size();
                sz /= all.size();
                double angle = Math.atan2(sz - bz, sx - bx);
                double vx = sx - Math.cos(angle) * 13.0d;
                double vz = sz - Math.sin(angle) * 13.0d;
                float yaw = (float) Math.toDegrees(Math.atan2(sz - vz, sx - vx)) - 90.0f;
                teleport(player, vx, -58.0d, vz, yaw, 9.0f);
            }
        }));
        ShotHarness.SHOTS.add(server("g4b_guerre", 40, WarMenu.INSTANCE::open));
        ShotHarness.SHOTS.add(server("g5_tues", 40, player -> {
            UiNet.close(player);
            List<Soldier> all = soldiers(player);
            int killed = 0;
            ServerLevel level = (ServerLevel) player.level();
            for (Soldier soldier : all) {
                if (killed >= 3 || soldier.role().name().equals("COMMANDANT") || soldier.role().name().equals("TRESORIER")) {
                    continue;
                }
                soldier.hurtServer(level, player.damageSources().playerAttack(player), 1000.0f);
                killed++;
            }
            log("soldats tues a la lame : {}", killed);
            op(player, "guerre admin fin");
            state(player, "assaut arrete");
        }));
        ShotHarness.SHOTS.add(client("g6_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("h1_guerre", 40, player -> {
            op(player, "gamemode survival");
            WarMenu.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(client("h2_guerre_bas", 25, () -> scroll(-14.0)));
        ShotHarness.SHOTS.add(client("h3_guerre_fin", 25, () -> scroll(-60.0)));
        ShotHarness.SHOTS.add(server("i1_fiche", 30, player -> {
            UiNet.close(player);
            fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "guerre");
        }));
        ShotHarness.SHOTS.add(client("i2_fiche_bas", 25, () -> scroll(-14.0)));
        ShotHarness.SHOTS.add(client("i3_fiche_fin", 25, () -> scroll(-12.0)));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> {
            UiNet.close(player);
            op(player, "guerre admin etat");
            state(player, "fin");
        }));
    }
}
