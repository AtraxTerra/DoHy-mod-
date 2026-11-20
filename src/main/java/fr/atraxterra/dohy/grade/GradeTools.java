package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthJump;
import fr.atraxterra.dohy.mine.MineService;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import fr.atraxterra.dohy.palamod.trixium.CombatTag;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.SpawnApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Ce que font les commandes des grades, appelees depuis le chat ou depuis les cartes de la page
 * des grades : les homes, {@code /rtp}, et chacune des {@link GradeCommand}.
 */
@EventBusSubscriber
public final class GradeTools {

    /** Cinq minutes entre deux {@code /feed} : rassasier, pas supprimer la faim. */
    static final long FEED_MILLIS = 5 * 60_000L;
    static final String DEFAULT_HOME = "home";
    private static final Pattern HOME_NAME = Pattern.compile("[a-z0-9_-]{1,16}");
    /** Le {@code /rtp} sans carte de la Terre : un point entre 800 et 4 000 blocs du spawn du monde. */
    private static final int RTP_MIN = 800;
    private static final int RTP_SPREAD = 3_200;
    private static final int RTP_ATTEMPTS = 10;

    private static final Map<UUID, Long> FED = new ConcurrentHashMap<>();
    /** Ce que donnent neuf exemplaires d'un objet a l'etabli ; vide s'il n'y a pas de recette. */
    private static final Map<Item, ItemStack> CONDENSED = new ConcurrentHashMap<>();

    private GradeTools() {
    }

    /** Une commande de grade : refusee sans le grade, sinon executee. */
    public static void run(ServerPlayer player, GradeCommand command) {
        Component refusal = Grades.refusal(player, command);
        if (refusal != null) {
            player.sendSystemMessage(refusal);
            return;
        }
        switch (command) {
            case CRAFT -> craft(player);
            case MINAGE -> minage(player);
            case FEED -> feed(player);
            case FURNACE -> furnace(player);
            case RANGEMENT -> sort(player);
            case TPTOGGLE -> tpToggle(player);
            case CONDENSE -> condense(player);
            case HAT -> hat(player);
            case DEATHLOCATE -> deathLocate(player);
            case EC -> enderChest(player);
            case FLY -> fly(player);
            case NV -> nightVision(player);
        }
    }

    // -- Les homes -----------------------------------------------------------------------------

    /** Le nom d'un home en minuscules ; {@code null} s'il est invalide. */
    static String homeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_HOME;
        }
        String name = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return HOME_NAME.matcher(name).matches() ? name : null;
    }

    static void setHome(ServerPlayer player, String raw) {
        String name = homeName(raw);
        if (name == null) {
            tell(player, "Nom de home invalide : lettres, chiffres, - et _, seize au plus.", ChatFormatting.RED);
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !GradeTeleports.homeLevel(level)) {
            tell(player, "Un home se pose dans l'Overworld, le Nether ou l'End, pas ici.", ChatFormatting.RED);
            return;
        }
        GradeData data = Grades.data(player);
        boolean moved = data.homes().containsKey(name);
        int max = Grades.homes(player);
        if (!moved && data.homes().size() >= max) {
            player.sendSystemMessage(Component.literal("Tu as deja " + data.homes().size() + " homes sur " + max
                    + " : /delhome pour en liberer un, ou un grade pour en avoir plus. ")
                    .withStyle(ChatFormatting.RED).append(Grades.link("[Voir les grades]")));
            return;
        }
        Grades.save(player, data.withHome(name, ReturnPoint.of(player)));
        int count = Grades.data(player).homes().size();
        tell(player, "Home « " + name + " » " + (moved ? "deplace ici" : "pose ici") + " (" + count + " / "
                + max + "). /home " + (name.equals(DEFAULT_HOME) ? "" : name) + " pour y revenir.",
                ChatFormatting.GREEN);
    }

    static void home(ServerPlayer player, String raw) {
        Map<String, ReturnPoint> homes = Grades.data(player).sortedHomes();
        if (homes.isEmpty()) {
            tell(player, "Aucun home : /sethome pose le premier la ou tu es.", ChatFormatting.RED);
            return;
        }
        String name = raw == null && homes.size() == 1 ? homes.keySet().iterator().next() : homeName(raw);
        ReturnPoint point = name == null ? null : homes.get(name);
        if (point == null) {
            player.sendSystemMessage(Component.literal("Pas de home « " + (name == null ? raw : name)
                    + " ». ").withStyle(ChatFormatting.RED).append(list(homes)));
            return;
        }
        GradeTeleports.schedule(player, "au home " + name, () -> {
            player.closeContainer();
            player.fallDistance = 0.0f;
            point.send(player);
        });
    }

    static void delHome(ServerPlayer player, String raw) {
        String name = homeName(raw);
        GradeData data = Grades.data(player);
        if (name == null || !data.homes().containsKey(name)) {
            tell(player, "Pas de home « " + raw + " ».", ChatFormatting.RED);
            return;
        }
        Grades.save(player, data.withHome(name, null));
        tell(player, "Home « " + name + " » supprime.", ChatFormatting.GRAY);
    }

    static void homes(ServerPlayer player) {
        Map<String, ReturnPoint> homes = Grades.data(player).sortedHomes();
        int max = Grades.homes(player);
        if (homes.isEmpty()) {
            tell(player, "Aucun home (" + max + " possibles) : /sethome [nom] pose un home la ou tu es.",
                    ChatFormatting.GRAY);
            return;
        }
        player.sendSystemMessage(Component.literal("Tes homes (" + homes.size() + " / " + max + ") : ")
                .withStyle(ChatFormatting.GOLD).append(list(homes)));
    }

    /** Les homes, chacun cliquable. */
    private static MutableComponent list(Map<String, ReturnPoint> homes) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String name : homes.keySet()) {
            if (!first) {
                out.append(Component.literal(" "));
            }
            first = false;
            out.append(GradeTeleports.button("[" + name + "]", "/home " + name, ChatFormatting.AQUA));
        }
        return out;
    }

    // -- Les teleportations --------------------------------------------------------------------

    static void rtp(ServerPlayer player) {
        int left = Grades.rtpLeft(player);
        if (left <= 0) {
            player.sendSystemMessage(Component.literal("Plus de /rtp ce mois-ci (" + Grades.rtpPerMonth(player)
                    + " par mois). ").withStyle(ChatFormatting.RED).append(Grades.link("[Voir les grades]")));
            return;
        }
        GradeTeleports.schedule(player, "au hasard", () -> {
            if (!randomTeleport(player)) {
                tell(player, "Aucun endroit sur n'a ete trouve : reessaie, ce /rtp n'est pas compte.",
                        ChatFormatting.RED);
                return;
            }
            GradeData data = Grades.data(player);
            int month = Grades.month();
            int used = data.rtpMonth() == month ? data.rtpUsed() : 0;
            Grades.save(player, data.withRtp(month, used + 1));
            int remaining = Grades.rtpLeft(player);
            tell(player, "Teleporte au hasard. " + (remaining > 0 ? "Encore " + remaining + " /rtp ce mois-ci."
                    : "C'etait le dernier /rtp du mois."), ChatFormatting.GREEN);
        });
    }

    /** Un sol sec tire au hasard : sur la carte de la Terre s'il y en a une, sinon autour du spawn du monde. */
    private static boolean randomTeleport(ServerPlayer player) {
        if (EarthData.get() != null && EarthJump.jump(player)) {
            return true;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.overworld();
        BlockPos center = level.getRespawnData().pos();
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < RTP_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            int distance = RTP_MIN + random.nextInt(RTP_SPREAD);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
                continue;
            }
            level.getChunk(x >> 4, z >> 4);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (surface <= level.getMinY() + 1) {
                continue;
            }
            BlockState ground = level.getBlockState(new BlockPos(x, surface - 1, z));
            if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES) || ground.is(Blocks.MAGMA_BLOCK)
                    || ground.is(Blocks.CACTUS) || ground.is(Blocks.POWDER_SNOW)) {
                continue;
            }
            player.closeContainer();
            player.fallDistance = 0.0f;
            player.teleport(new TeleportTransition(level, new Vec3(x + 0.5d, surface, z + 0.5d), Vec3.ZERO,
                    player.getYRot(), 0.0f, TeleportTransition.DO_NOTHING));
            return true;
        }
        return false;
    }

    /** {@code /minage} : la Mine sans passer par le spawn ; la borne RETOUR ramene ou l'on etait. */
    static void minage(ServerPlayer player) {
        if (MineService.inMine(player)) {
            tell(player, "Tu es deja a la Mine.", ChatFormatting.GRAY);
            return;
        }
        GradeTeleports.schedule(player, "vers la Mine", () -> {
            if (!SpawnApi.inSpawn(player)) {
                // comme si l'on etait passe par le spawn : /spawn, puis la borne RETOUR, ramenent ici
                player.setData(DoHySpawn.RETURN.get(), ReturnPoint.of(player));
            }
            MineService.enter(player);
        });
    }

    // -- Les commandes de grade ----------------------------------------------------------------

    static void craft(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        player.openMenu(new SimpleMenuProvider((containerId, inventory, viewer) ->
                new CraftingMenu(containerId, inventory, ContainerLevelAccess.create(player.level(), pos)) {
                    @Override
                    public boolean stillValid(Player user) {
                        return true;
                    }
                }, Component.translatable("container.crafting")));
    }

    static void feed(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = FED.get(player.getUUID());
        if (last != null && now - last < FEED_MILLIS) {
            long minutes = (FEED_MILLIS - (now - last) + 59_999L) / 60_000L;
            tell(player, "Encore " + minutes + " min avant le prochain /feed.", ChatFormatting.RED);
            return;
        }
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(10.0f);
        FED.put(player.getUUID(), now);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f,
                1.0f);
        tell(player, "Rassasie. Prochain /feed dans 5 min.", ChatFormatting.GREEN);
    }

    /** {@code /furnace} : la pile en main, cuite d'un coup, sans XP (ni de metier ni classique). */
    static void furnace(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            tell(player, "Prends en main ce que tu veux cuire.", ChatFormatting.RED);
            return;
        }
        SingleRecipeInput input = new SingleRecipeInput(held.copyWithCount(1));
        ItemStack result = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> holder.value().assemble(input))
                .orElse(ItemStack.EMPTY);
        if (result.isEmpty()) {
            tell(player, "Ce que tu tiens ne se cuit pas.", ChatFormatting.RED);
            return;
        }
        int count = held.getCount();
        long total = (long) result.getCount() * count;
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        give(player, result, total);
        player.level().playSound(null, player.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS,
                1.0f, 1.0f);
        player.sendSystemMessage(Component.literal(count + " cuit" + (count > 1 ? "s" : "") + " : " + total + " x ")
                .withStyle(ChatFormatting.GREEN).append(result.getHoverName()));
    }

    /** {@code /rangement} : les 27 cases du sac regroupees et triees ; la barre d'action ne bouge pas. */
    static void sort(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> merged = new ArrayList<>();
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot).copy();
            for (ItemStack target : merged) {
                if (stack.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(target, stack) && target.getCount() < target.getMaxStackSize()) {
                    int move = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
                    target.grow(move);
                    stack.shrink(move);
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }
        merged.sort(Comparator.comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .thenComparing(stack -> -stack.getCount()));
        for (int slot = 9; slot < 36; slot++) {
            int index = slot - 9;
            inventory.setItem(slot, index < merged.size() ? merged.get(index) : ItemStack.EMPTY);
        }
        player.inventoryMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f,
                0.8f);
        tell(player, "Sac range : " + merged.size() + " piles.", ChatFormatting.GREEN);
    }

    static void tpToggle(ServerPlayer player) {
        GradeData data = Grades.data(player);
        boolean closed = !data.tpaClosed();
        Grades.save(player, data.withTpaClosed(closed));
        tell(player, closed ? "Tu refuses desormais les demandes de teleportation."
                : "Tu acceptes de nouveau les demandes de teleportation.", ChatFormatting.GREEN);
    }

    /** {@code /condense} : chaque objet que l'etabli compresse par neuf (lingots, minerais...) passe en blocs. */
    static void condense(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Inventory inventory = player.getInventory();
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getComponentsPatch().isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        MutableComponent made = Component.empty();
        int kinds = 0;
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            int nine = entry.getValue() / 9;
            if (nine == 0) {
                continue;
            }
            ItemStack result = condensed(level, entry.getKey());
            if (result.isEmpty()) {
                continue;
            }
            remove(inventory, entry.getKey(), nine * 9);
            long total = (long) nine * result.getCount();
            give(player, result, total);
            made.append(Component.literal((kinds > 0 ? ", " : "") + total + " x ")).append(result.getHoverName());
            kinds++;
        }
        if (kinds == 0) {
            tell(player, "Rien a condenser : il faut neuf exemplaires d'un objet qui se compresse"
                            + " (lingots, minerais...).",
                    ChatFormatting.GRAY);
            return;
        }
        player.inventoryMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("Condense : ").withStyle(ChatFormatting.GREEN)
                .append(made.withStyle(ChatFormatting.WHITE)));
    }

    /** La recette de neuf exemplaires en carre, si elle donne un seul objet different. */
    private static ItemStack condensed(ServerLevel level, Item item) {
        return CONDENSED.computeIfAbsent(item, key -> {
            CraftingInput input = CraftingInput.of(3, 3, Collections.nCopies(9, new ItemStack(key)));
            ItemStack result = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(holder -> holder.value().assemble(input))
                    .orElse(ItemStack.EMPTY);
            return result.getCount() == 1 && !result.is(key) ? result : ItemStack.EMPTY;
        }).copy();
    }

    private static void remove(Inventory inventory, Item item, int amount) {
        for (int slot = 0; slot < 36 && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
    }

    /** {@code /hat} : l'objet en main sur la tete ; le casque d'avant revient dans le sac. */
    static void hat(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            tell(player, "Prends en main ce que tu veux porter sur la tete.", ChatFormatting.RED);
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty() && EnchantmentHelper.has(head, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            tell(player, "Ton casque est maudit : il ne s'enleve pas.", ChatFormatting.RED);
            return;
        }
        ItemStack worn = hand.split(1);
        player.setItemSlot(EquipmentSlot.HEAD, worn);
        if (!head.isEmpty() && !player.getInventory().add(head)) {
            player.drop(head, false);
        }
        player.sendSystemMessage(Component.literal("Sur ta tete : ").withStyle(ChatFormatting.GREEN)
                .append(worn.getHoverName()));
    }

    static void deathLocate(ServerPlayer player) {
        Optional<GlobalPos> death = player.getLastDeathLocation();
        if (death.isEmpty()) {
            tell(player, "Aucune mort enregistree.", ChatFormatting.GRAY);
            return;
        }
        GlobalPos where = death.get();
        BlockPos pos = where.pos();
        tell(player, "Ta derniere mort : " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " ("
                + dimensionName(where) + ").", ChatFormatting.GOLD);
    }

    private static String dimensionName(GlobalPos where) {
        String id = where.dimension().identifier().toString();
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> id;
        };
    }

    static void enderChest(ServerPlayer player) {
        if (CombatTag.inCombat(player)) {
            tell(player, "Pas d'Ender Chest en plein combat.", ChatFormatting.RED);
            return;
        }
        GradeEnder.open(player, 3, null, Component.translatable("container.enderchest"));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS,
                0.5f, 1.0f);
    }

    static void fly(ServerPlayer player) {
        GradeData data = Grades.data(player);
        boolean on = !data.fly();
        Grades.save(player, data.withFly(on));
        GradeEvents.updateFlight(player);
        tell(player, !on ? "Vol au spawn coupe." : SpawnApi.inSpawn(player)
                ? "Vol au spawn active : saute deux fois pour t'envoler."
                : "Vol au spawn active : il marchera des que tu y seras.", ChatFormatting.GREEN);
    }

    static void nightVision(ServerPlayer player) {
        GradeData data = Grades.data(player);
        boolean on = !data.nightVision();
        Grades.save(player, data.withNightVision(on));
        GradeEvents.applyNightVision(player);
        tell(player, on ? "Vision nocturne activee." : "Vision nocturne coupee.", ChatFormatting.GREEN);
    }

    // -- Outils --------------------------------------------------------------------------------

    /** Donne un total d'objets en piles pleines ; ce qui ne tient pas tombe aux pieds. */
    private static void give(ServerPlayer player, ItemStack template, long total) {
        int max = Math.max(1, template.getMaxStackSize());
        while (total > 0) {
            int count = (int) Math.min(max, total);
            ItemStack stack = template.copyWithCount(count);
            total -= count;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        GradeTeleports.tell(player, text, color);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FED.clear();
        CONDENSED.clear();
    }
}
