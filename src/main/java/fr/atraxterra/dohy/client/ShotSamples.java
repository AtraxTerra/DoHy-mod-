package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.earth.mine.DoHyMines;
import fr.atraxterra.dohy.earth.mine.EarthMines;
import fr.atraxterra.dohy.earth.mine.MineMenu;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.FactionVault;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import fr.atraxterra.dohy.ui.view.UiNet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Temporaire : les mines de la Terre. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static EarthBodies.Body mine;
    private static int cx;
    private static int cz;
    private static volatile UUID me;
    private static int nation = -1;

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
        LOG.info("dohy-shots: [mines] " + format, args);
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

    private static InteractionResult click(ServerPlayer player, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.UP, 0.5d), Direction.UP, pos,
                false);
        InteractionResult result = player.gameMode.useItemOn(player, player.level(), held, InteractionHand.MAIN_HAND,
                hit);
        log("clic droit sur {} {} {} avec {} -> {}", pos.getX(), pos.getY(), pos.getZ(),
                held.isEmpty() ? "rien" : BuiltInRegistries.ITEM.getKey(held.getItem()).getPath(), result);
        return result;
    }

    private static void holdBeacon(ServerPlayer player) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(DoHyMines.BEACON_ITEM.get())) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return;
            }
        }
        log("pas de balise dans l'inventaire");
    }

    private static int countOf(ServerPlayer player, String id) {
        Item wanted = item(id);
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(wanted)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String vault(ServerPlayer player) {
        List<String> items = new ArrayList<>();
        for (ItemStack stack : FactionVault.of(player).items()) {
            items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "x" + stack.getCount());
        }
        return items.toString();
    }

    private static void state(ServerPlayer player, String when) {
        List<String> held = new ArrayList<>();
        for (var concession : EarthMines.of(player)) {
            held.add(concession.mine());
        }
        log("{} : mines {} ; par heure {} $ ; capture [{}] ; banque {} $ ; coffre {} ({} cases) ; argent {}", when, held,
                EarthMines.moneyPerHour(player), EarthMines.captureState(player.getUUID()),
                FactionApi.data(player).bank(), vault(player), FactionVault.of(player).used(), Economy.balance(player));
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 100, player -> {
            me = player.getUUID();
            op(player, "time set noon");
            op(player, "weather clear");
            op(player, "gamerule doDaylightCycle false");
            op(player, "gamerule advance_time false");
            op(player, "gamerule doMobSpawning false");
            op(player, "gamerule spawn_mobs false");
            op(player, "difficulty peaceful");
            op(player, "kill @e[type=minecraft:slime]");
            player.setGameMode(GameType.SURVIVAL);
            log("carte de la Terre : {} ; {} gisements geants charges", EarthMines.active(), EarthBodies.all().size());
            mine = EarthMines.find("sadiola");
            if (mine == null) {
                for (EarthBodies.Body body : EarthBodies.all()) {
                    if (body.kind() == EarthBodies.Kind.ORE && body.tier() == 3) {
                        mine = body;
                        break;
                    }
                }
            }
            if (mine == null) {
                log("aucune mine de rang 3 : rien a essayer");
                return;
            }
            cx = (int) Math.floor(mine.centerX());
            cz = (int) Math.floor(mine.centerZ());
            nation = EarthMines.nation(player.level().getServer(), mine);
            log("mine d'essai : {} ({}, rang {}) en {} {}, pays {}, nation {} ({}) ; verse {}", mine.name(),
                    mine.resource(), mine.tier(), cx, cz, EarthMines.countryName(mine), nation,
                    EarthMines.nationName(nation), EarthMines.output(mine));
            teleport(player, cx + 0.5d, -60.0d, cz + 7.5d, 180.0f, 25.0f);
            player.getInventory().clearContent();
            Economy.set(player, 1_000_000L);
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 64));
            player.getInventory().add(new ItemStack(item("palamod:paladium_ingot"), 36));
            String refusal = FactionApi.create(player, "Mineurs", "Les mines de la Terre", List.of(), 3);
            log("faction : {} ; existe {}", refusal == null ? "fondee" : refusal, FactionApi.has(player));
            state(player, "depart");
        }));
        ShotHarness.SHOTS.add(server("a1_boutique", 30, BoutiqueController.INSTANCE::open));
        ShotHarness.SHOTS.add(client("a2_boutique", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(client("a3_boutique", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(client("a4_boutique", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("b1_pose", 40, player -> {
            UiNet.close(player);
            long before = Economy.balance(player);
            String refusal = EarthMines.buyBeacon(player);
            log("achat de la balise : {} ; argent {} -> {}", refusal == null ? "ok" : refusal, before,
                    Economy.balance(player));
            holdBeacon(player);
            BlockPos outside = new BlockPos(cx + 60, -61, cz);
            log("refus hors d'une mine : {}", EarthMines.refusal(player, outside.above()));
            click(player, outside);
            BlockPos ground = new BlockPos(cx, -61, cz);
            log("refus au centre : {}", EarthMines.refusal(player, ground.above()));
            click(player, ground);
            log("bloc au centre : {}", player.level().getBlockState(ground.above()));
            state(player, "balise posee");
        }));
        ShotHarness.SHOTS.add(server("b2_capture", 60, player -> state(player, "capture en cours")));
        ShotHarness.SHOTS.add(server("b3_pause", 100, player -> {
            state(player, "avant de s'eloigner");
            teleport(player, cx + 40.5d, -60.0d, cz + 0.5d, 90.0f, 15.0f);
        }));
        ShotHarness.SHOTS.add(server("b4_retour", 20, player -> {
            state(player, "loin de la balise (en pause)");
            teleport(player, cx + 0.5d, -60.0d, cz + 7.5d, 180.0f, 25.0f);
        }));
        ShotHarness.SHOTS.add(server("b5_capturee", 20, player -> {
            ShotHarness.until = () -> EarthMines.captureState(me).isEmpty();
            state(player, "de retour pres de la balise");
        }));
        ShotHarness.SHOTS.add(server("b6_bilan", 20, player -> {
            state(player, "capture finie");
            ServerLevel level = (ServerLevel) player.level();
            log("balise : {} ; claim au centre : {} ; mine en jeu face a la nation : {}",
                    level.getBlockState(new BlockPos(cx, -60, cz)), FactionApi.claimAt(level, new BlockPos(cx, -60, cz))
                            .claimed(), EarthMines.stake(player, nation) == null ? "aucune"
                            : EarthMines.stake(player, nation).name());
            log("refus dans sa propre mine : {}", EarthMines.refusal(player, new BlockPos(cx, -60, cz)));
        }));
        ShotHarness.SHOTS.add(client("b7_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("c1_versement", 30, player -> {
            closeScreen();
            state(player, "avant 5 h");
            op(player, "mines avancer 5");
            state(player, "apres 5 h");
        }));
        ShotHarness.SHOTS.add(server("c2_banque", 30, FactionMenus.Bank.INSTANCE::open));
        ShotHarness.SHOTS.add(client("c3_banque_bas", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("c4_tout_prendre", 30, player -> {
            String metal = mine == null ? "minecraft:raw_gold" : "minecraft:raw_gold";
            int before = countOf(player, metal);
            FactionMenus.Bank.INSTANCE.handle(player, new UiNet.Action(Identifier.parse("dohy:faction_bank"), "card",
                    "vault_all", 0));
            log("tout prendre : or brut dans l'inventaire {} -> {}", before, countOf(player, metal));
            state(player, "coffre vide");
        }));
        ShotHarness.SHOTS.add(server("d1_mines", 30, player -> {
            UiNet.close(player);
            MineMenu.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(client("d2_mines_bas", 20, () -> scroll(-40.0)));
        ShotHarness.SHOTS.add(server("e1_absence", 20, player -> {
            UiNet.close(player);
            long before = FactionApi.data(player).bank();
            op(player, "mines avancer 30");
            log("30 h d'absence : banque +{} $", FactionApi.data(player).bank() - before);
            before = FactionApi.data(player).bank();
            op(player, "mines avancer 200");
            log("200 h d'absence (plafond 168 h) : banque +{} $", FactionApi.data(player).bank() - before);
            state(player, "apres les absences");
        }));
        ShotHarness.SHOTS.add(client("e2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("f1_gisements", 20, player -> {
            closeScreen();
            as(player, "gisements");
        }));
        ShotHarness.SHOTS.add(client("f2_chat", 15, ShotSamples::openChat));
        ShotHarness.SHOTS.add(server("g1_faction", 30, player -> {
            closeScreen();
            FactionMenus.Overview.INSTANCE.open(player);
        }));
        ShotHarness.SHOTS.add(server("h1_reprise", 30, player -> {
            UiNet.close(player);
            log("avant la reprise : en jeu face a {} : {}", EarthMines.nationName(nation),
                    EarthMines.stake(player, nation) == null ? "rien" : EarthMines.stake(player, nation).name());
            op(player, "guerre admin reprise " + (nation + 1));
            state(player, "apres la reprise");
        }));
        ShotHarness.SHOTS.add(server("i1_fiche", 30, player ->
                fr.atraxterra.dohy.guide.EncyclopediaMenu.openEntry(player, "mines_terre")));
        ShotHarness.SHOTS.add(server("z_fin", 10, player -> state(player, "fin")));
    }
}
