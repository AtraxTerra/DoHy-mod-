package fr.atraxterra.dohy.palamod.mount;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les regles de {@code ServerListener} et la commande {@code /mount}.
 *
 * <p><b>XP partagee</b> : seules les orbes ramassees comptent, que la monture
 * soit invoquee ou non. La monture prend {@code orbe x partage / 100}, le
 * joueur le reste, arrondi vers le bas : l'orbe est reecrite a sa part avant
 * Raccommodage.
 *
 * <p><b>En selle</b> : tout coup recu fait tomber le cavalier et annule une
 * invocation en cours ; on ne ramasse ni ne jette d'objet.
 *
 * <p><b>Ecarts</b> : rien ne tombe a la mort du joueur (l'original lachait
 * l'inventaire de la monture <i>rangee</i> et gardait celui de la monture
 * invoquee, l'inverse de l'intention) ; {@code /mount unequip}, qui detruisait
 * la monture, n'est pas porte ; le nom et le partage d'XP, qui se tapaient
 * dans le menu, ont leur sous-commande.
 */
@EventBusSubscriber
public final class MountEvents {

    /**
     * Les orbes reecrites pendant ce tick, et leur valeur d'origine. Une orbe
     * peut en contenir plusieurs (elles fusionnent) : chacune est ramassee
     * l'une apres l'autre avec la meme valeur, qu'il faut donc rendre.
     */
    private static final Map<ExperienceOrb, Integer> REWRITTEN = new IdentityHashMap<>();

    /**
     * {@code onPlayerPickupXPEvent} : l'orbe est reecrite a la part du joueur
     * avant que Raccommodage ou le joueur ne la prennent, comme dans
     * l'original.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MountData data = MountManager.data(player);
        if (!data.isEquipped() || data.sharedXp() <= 0) {
            return;
        }
        ExperienceOrb orb = event.getOrb();
        int value = REWRITTEN.computeIfAbsent(orb, ExperienceOrb::getValue);
        float mountXp = value * data.sharedXp() / 100.0f;
        orb.setValue((int) (value - mountXp));
        MountManager.addXp(player, mountXp);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        REWRITTEN.forEach((orb, value) -> {
            if (orb.isAlive()) {
                orb.setValue(value);
            }
        });
        REWRITTEN.clear();
        MountManager.tickCountdowns(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MountManager.cancel(player);
            if (player.getVehicle() instanceof Dancarok) {
                player.stopRiding();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MountManager.unsummon(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MountManager.unsummon(player, false);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MountManager.unsummon(player, true);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer().getVehicle() instanceof Dancarok) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** L'objet jete revient dans l'inventaire ; s'il n'y tient plus, il tombe. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(player.getVehicle() instanceof Dancarok)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem().copy();
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            event.setCanceled(true);
        } else {
            event.getEntity().setItem(stack);
        }
        player.inventoryMenu.broadcastChanges();
    }

    // -- /mount ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mount")
                .executes(ctx -> menu(ctx.getSource()))
                .then(Commands.literal("summon").executes(ctx -> summon(ctx.getSource())))
                .then(Commands.literal("nom").then(Commands.argument("nom", StringArgumentType.greedyString())
                        .executes(ctx -> rename(ctx.getSource(), StringArgumentType.getString(ctx, "nom")))))
                .then(Commands.literal("xp").then(Commands.argument("pourcentage", IntegerArgumentType.integer())
                        .executes(ctx -> sharedXp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "pourcentage")))))
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> give(ctx.getSource(), 1))
                        .then(Commands.argument("niveau", IntegerArgumentType.integer(1, MountLevels.MAX_LEVEL))
                                .executes(ctx -> give(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "niveau"))))));
    }

    private static int menu(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MountMenus.openHome(source.getPlayerOrException());
        return 1;
    }

    private static int summon(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MountManager.requestSummon(source.getPlayerOrException());
        return 1;
    }

    /** {@code CSMountName} : 4 a 16 caracteres. */
    private static int rename(CommandSourceStack source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MountData data = MountManager.data(player);
        if (!data.isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return 0;
        }
        String trimmed = name.trim();
        if (trimmed.length() < 4 || trimmed.length() > 16) {
            MountTexts.error(player, "name_length");
            return 0;
        }
        data.setName(trimmed);
        MountTexts.success(player, "renamed", trimmed);
        return 1;
    }

    /** {@code CSMountSharedXP} : 0 a 100 %. */
    private static int sharedXp(CommandSourceStack source, int percent)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MountData data = MountManager.data(player);
        if (!data.isEquipped()) {
            MountTexts.error(player, "none_equipped");
            return 0;
        }
        if (percent < 0 || percent > 100) {
            MountTexts.error(player, "shared_xp_range");
            return 0;
        }
        data.setSharedXp(percent);
        MountTexts.success(player, "shared_xp", percent);
        return 1;
    }

    /**
     * {@code /mount give} : une gemme apprivoisee au nom de l'appelant. Le
     * niveau en option est un ajout, pour essayer la monture sans des heures de
     * ramassage d'orbes.
     */
    private static int give(CommandSourceStack source, int level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        float xp = MountLevels.minXp(level);
        MountGem gem = new MountGem(UUID.randomUUID(), Optional.of(player.getUUID()), MountGem.DEFAULT_NAME,
                MountLevels.cap(xp), MountData.DEFAULT_SHARED_XP, MountData.MAX_FOOD, 0.0f, -1L, -1L, -1L);
        MountManager.give(player, MountGemstoneItem.stackOf(gem));
        MountTexts.success(player, "given", gem.level());
        return 1;
    }
}
