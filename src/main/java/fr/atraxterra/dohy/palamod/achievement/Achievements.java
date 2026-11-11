package fr.atraxterra.dohy.palamod.achievement;

import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.palamod.boss.VolcaniaDragon;
import fr.atraxterra.dohy.palamod.smeltery.GrinderModifier;
import fr.atraxterra.dohy.palamod.smeltery.PalaSmeltery;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les succes (lot 23b, module {@code achievements} de PalaMod et succes de
 * PalaJobs) : les compteurs, le controle et la recompense.
 *
 * <p><b>Ce qui mesure</b> : autant que possible les statistiques que le jeu
 * tient deja (objets fabriques, casses, utilises ; creatures tuees ; distance
 * parcourue), lues a la demande comme le fait l'arbre de faction. Le reste
 * passe par les compteurs de {@link AchievementData} :
 * <ul>
 *   <li>les blocs casses, <b>hors blocs poses par un joueur</b> (on ne valide
 *       pas « casser 500 000 minerais » en posant et cassant le meme) ;</li>
 *   <li>ce que les machines et les objets de PalaMod signalent par
 *       {@link #count} (seve, ames, totem, captures, boss...) ;</li>
 *   <li>ce qui se constate en regardant le joueur toutes les cinq secondes :
 *       les dimensions visitees, les objets possedes, l'armure portee, l'Unclaim
 *       Finder tenu en main.</li>
 * </ul>
 *
 * <p>Un succes valide verse sa recompense une fois, avec un message ; plusieurs
 * a la fois (a la premiere connexion, par exemple) donnent un seul bilan.
 */
@EventBusSubscriber
public final class Achievements {

    /** La categorie « minerais » de la convention commune ({@code c:ores}). */
    public static final TagKey<Block> ORES = TagKey.create(BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath("c", "ores"));

    // -- Cles des compteurs --------------------------------------------------------
    public static final String MINED_ORES = "mine:ores";
    public static final String HARVEST = "harvest";
    public static final String SPAWNER_WITH_FINDER = "spawner:finder";
    public static final String SOLD = "shop:sold";
    public static final String SALES = "shop:sales";
    public static final String PURCHASES = "shop:purchases";
    public static final String SPENT = "money:spent";
    public static final String UNCLAIM_SECONDS = "unclaim:seconds";
    public static final String HOOD_SECONDS = "hood:seconds";
    public static final String SPAWNER_FINDER_SECONDS = "spawner_finder:seconds";
    public static final String HAMMER_FORTUNE = "hammer:fortune";
    public static final String HAMMER_SMELT = "hammer:smelt";
    public static final String ENCHANTED_PICKAXE = "own:enchanted_paladium_pickaxe";
    public static final String ENDIUM_SET = "wear:endium_set";

    /** Les objets dont on note la plus grosse quantite portee a la fois ({@link Progress#owned}). */
    static final Set<String> WATCHED = new java.util.HashSet<>();

    private Achievements() {
    }

    public static AchievementData data(ServerPlayer player) {
        return player.getData(PalaAchievements.DATA.get());
    }

    /** Ajoute a un compteur ; les objets et machines de PalaMod l'appellent. */
    public static void count(ServerPlayer player, String key, long amount) {
        if (amount > 0) {
            data(player).add(key, amount);
        }
    }

    /** Une vente au shop : une vente de plus, et son montant. */
    public static void sold(ServerPlayer player, long money) {
        count(player, SALES, 1);
        count(player, SOLD, money);
    }

    // -- Controle ------------------------------------------------------------------

    /** Valide ce qui est rempli et verse les recompenses. */
    public static void check(ServerPlayer player) {
        AchievementData data = data(player);
        List<Achievement> fresh = new ArrayList<>();
        for (Achievement achievement : AchievementCatalogue.all()) {
            if (!data.isDone(achievement.id()) && achievement.done(player)) {
                data.done.add(achievement.id());
                fresh.add(achievement);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        long total = 0L;
        for (Achievement achievement : fresh) {
            total += achievement.reward();
        }
        if (total > 0) {
            Economy.credit(player, total);
        }
        if (fresh.size() <= 3) {
            for (Achievement achievement : fresh) {
                tell(player, "Succes valide : " + achievement.name() + " (+" + MoneyFormat.exact(achievement.reward())
                        + " $)", ChatFormatting.GOLD);
            }
        } else {
            tell(player, fresh.size() + " succes valides d'un coup : +" + MoneyFormat.exact(total) + " $. Detail : /succes",
                    ChatFormatting.GOLD);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.6f, 1.3f);
    }

    private static void tell(ServerPlayer player, String message, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[Succes] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(message).withStyle(color)));
    }

    // -- Ce qui se constate ------------------------------------------------------

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            everySecond(player);
        }
        if (player.tickCount % 100 == 0) {
            survey(player);
            check(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            survey(player);
            check(player);
        }
    }

    private static void everySecond(ServerPlayer player) {
        AchievementData data = data(player);
        if (isUnclaimFinder(player.getMainHandItem()) || isUnclaimFinder(player.getOffhandItem())) {
            data.add(UNCLAIM_SECONDS, 1);
        }
        if (is(player.getItemBySlot(EquipmentSlot.HEAD), "palamod:hood_helmet")) {
            data.add(HOOD_SECONDS, 1);
        }
        if (is(player.getMainHandItem(), "palamod:spawner_finder") || is(player.getOffhandItem(), "palamod:spawner_finder")) {
            data.add(SPAWNER_FINDER_SECONDS, 1);
        }
    }

    private static boolean isUnclaimFinder(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("unclaim_finder");
    }

    private static boolean is(ItemStack stack, String id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }

    /** Toutes les cinq secondes : la dimension, l'inventaire, l'armure. */
    private static void survey(ServerPlayer player) {
        // Le catalogue declare les objets a surveiller ({@link #WATCHED}) en se construisant.
        AchievementCatalogue.all();
        AchievementData data = data(player);
        data.atLeast("dim:" + player.level().dimension().identifier(), 1);

        Map<String, Long> carried = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (WATCHED.contains(id)) {
                carried.merge(id, (long) stack.getCount(), Long::sum);
            }
            if (id.equals("palamod:paladium_pickaxe") && stack.isEnchanted()) {
                data.atLeast(ENCHANTED_PICKAXE, 1);
            }
            if (id.equals("palamod:paladium_hammer")) {
                var modifiers = PalaSmeltery.modifiers(stack);
                data.atLeast(HAMMER_FORTUNE, modifiers.level(GrinderModifier.FORTUNE));
                data.atLeast(HAMMER_SMELT, modifiers.level(GrinderModifier.SMELT));
            }
        }
        carried.forEach((id, count) -> data.atLeast("own:" + id, count));

        if (is(player.getItemBySlot(EquipmentSlot.HEAD), "palamod:endium_helmet")
                && is(player.getItemBySlot(EquipmentSlot.CHEST), "palamod:endium_chestplate")
                && is(player.getItemBySlot(EquipmentSlot.LEGS), "palamod:endium_leggings")
                && is(player.getItemBySlot(EquipmentSlot.FEET), "palamod:endium_boots")) {
            data.atLeast(ENDIUM_SET, 1);
        }
    }

    /** Egg Hunt : frapper Volcania ({@code EggHuntActionAchievement}, action 1). */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof VolcaniaDragon && event.getSource().getEntity() instanceof ServerPlayer player) {
            count(player, "egghunt:hit", 1);
        }
    }

    // -- Blocs casses ------------------------------------------------------------

    /**
     * Minerais et recoltes, hors blocs poses ; et les spawners casses avec un
     * Spawner Finder dans l'inventaire ({@code BreakSpawnerFinderAchievement}).
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        boolean exempt = state.is(CollectionEvents.PLACEMENT_EXEMPT);
        if (PlacedBlocks.wasPlayerPlaced(event) && !exempt) {
            return;
        }
        AchievementData data = data(player);
        if (state.is(ORES)) {
            data.add(MINED_ORES, 1);
            data.add("mine:" + BuiltInRegistries.BLOCK.getKey(state.getBlock()), 1);
        }
        if (state.getBlock() instanceof CropBlock crop ? crop.isMaxAge(state)
                : state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            data.add(HARVEST, 1);
        }
        if (state.is(Blocks.SPAWNER) && carries(player, "palamod:spawner_finder")) {
            data.add(SPAWNER_WITH_FINDER, 1);
        }
    }

    private static boolean carries(ServerPlayer player, String id) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (is(inventory.getItem(slot), id)) {
                return true;
            }
        }
        return false;
    }

    // -- /succes ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        for (String name : new String[]{"succes", "achievements"}) {
            event.getDispatcher().register(Commands.literal(name).executes(ctx -> {
                AchievementMenu.INSTANCE.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
        }
    }

    /** Le nombre de succes du catalogue. */
    public static int total() {
        return AchievementCatalogue.all().size();
    }

    /** Une recompense lisible : « 250 000 $ ». */
    static String money(long amount) {
        return MoneyFormat.exact(amount) + " $";
    }

    /** L'objet d'un identifiant, ou de l'air s'il n'existe pas. */
    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }
}
