package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le lucky block de Noel ({@code luckyevents/christmas}) : dix-huit evenements.
 *
 * <p>Ecarts : la maison du Pere Noescroc, le village et les maquettes ne sont pas reconstruits a
 * l'identique (la maquette est une petite maison enneigee) ; la vache, le traineau et l'armee de
 * neige sont des creatures du jeu ; « Milkado » devient « Chocolat chaud » ; la carte de voeux
 * s'adresse a tout le serveur au lieu d'ouvrir un ecran d'ecriture.
 */
@EventBusSubscriber
public final class ChristmasEvents {

    private static final String MILKADO = "dohy_milkado";
    /** « Credit a la consommation » : les joueurs qui doivent encore donner un montant. */
    private static final Map<UUID, Long> CREDITS = new HashMap<>();
    /** Les cadeaux du Pere Noescroc ({@code InventoryChristmasSantaNoescrocTrade}). */
    private static final List<String> SCAM_GIFTS = List.of("Lingot d'endium", "Lingot de paladium", "Epee en endium",
            "Pioche en endium", "Hache en endium", "Totem d'endium", "Dynamite d'endium", "Pierre d'invocation",
            "Fleur d'endium", "Lucky block de Noel");

    private ChristmasEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.CHRISTMAS, "christmas_mockup", "Maquettiste", "christmas_mockup", false, 50,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.CHRISTMAS_MOCKUP.get(), 8)));
        c.season(LuckyType.CHRISTMAS, "ate_too_much", "Trop mange", "ate_too_much", true, 70, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 900, 0));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 900, 0));
            SeasonKit.fail(player, "Tu as trop mange et tu te sens mal.");
        });
        c.season(LuckyType.CHRISTMAS, "chocolate_milk", "Chocolat chaud", "milkado", false, 70,
                ChristmasEvents::chocolateCow);
        c.season(LuckyType.CHRISTMAS, "consumer_credit", "Credit a la consommation", "consumer_credit", false, 70,
                (player, level, pos) -> {
                    CREDITS.put(player.getUUID(), level.getGameTime() + 1200L);
                    SeasonKit.say(player, "De combien d'argent as-tu besoin pour acheter les cadeaux ? Ecris le montant"
                            + " dans le chat (10 000 $ au plus). Une chance sur deux de le gagner... ou de le perdre.");
                });
        c.season(LuckyType.CHRISTMAS, "drank_too_much", "Trop bu", "drunk_too_much", true, 70,
                ChristmasEvents::drunk);
        c.season(LuckyType.CHRISTMAS, "extreme_cold", "Grand froid", "extreme_cold", false, 70, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(SeasonEffects.EXTREME_COLD, 6000, 0));
            SeasonKit.say(player, "Grand froid : la neige tombe sous tes pas.");
        });
        c.season(LuckyType.CHRISTMAS, "forest_king", "Roi des forets", "forest_king", false, 70,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.CHRISTMAS_AXE.get())));
        c.season(LuckyType.CHRISTMAS, "private_christmas", "Prive de Noel", "private_christmas", true, 70,
                ChristmasEvents::privateChristmas);
        c.season(LuckyType.CHRISTMAS, "sleigh", "Tres-no", "sleigh", false, 70, ChristmasEvents::sleigh);
        c.season(LuckyType.CHRISTMAS, "wish_card", "Mes meilleurs voeux", "wish_card", false, 70,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.WISH_CARD.get())));
        c.season(LuckyType.CHRISTMAS, "freeze", "Il a Freeze il a tout compris", "freeze", true, 100,
                ChristmasEvents::freeze);
        c.season(LuckyType.CHRISTMAS, "snow_army", "Snow army", "snow_army", false, 100, ChristmasEvents::snowArmy);
        c.season(LuckyType.CHRISTMAS, "present", "K-Do", "present", false, 200,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.PRESENT.get())));
        c.season(LuckyType.CHRISTMAS, "noescroc_father", "Pere Noescroc", "noescroc_father", true, 300,
                ChristmasEvents::scamSanta);
        c.season(LuckyType.CHRISTMAS, "christmas_stone", "T'as ete sage", "christmas_stone", false, 400,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.CHRISTMAS_STONE.get())));
        c.season(LuckyType.CHRISTMAS, "present_bag", "Sac K-Dos", "present_bag", false, 500,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.PRESENT_BAG.get())));
        c.season(LuckyType.CHRISTMAS, "christmas_gift", "Cadeau block", "block_gift", false, 600,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.MULTICOLOR_GIFT.get())));
        c.season(LuckyType.CHRISTMAS, "christmas_village", "Village de Noel", "christmas_village", false, 700,
                (player, level, pos) -> fr.atraxterra.dohy.palamod.miner.GodVillager.spawn(player));
    }

    // -- Chocolat chaud -------------------------------------------------------------------------

    /** La vache de Noel ({@code EntityChristmasCow}) : traite au seau, elle donne du lait chocolate. */
    private static void chocolateCow(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob cow = SeasonKit.mob(level, EntityTypes.COW, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 1, 3)),
                "Vache de Noel");
        if (cow != null) {
            cow.addTag(MILKADO);
            SeasonKit.win(player, "Une vache de Noel ! Traite-la avec un seau.");
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!target.entityTags().contains(MILKADO) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(Items.BUCKET)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        held.shrink(1);
        SeasonKit.give(player, new ItemStack(SeasonItems.CHOCOLATE_MILK.get()));
        player.level().playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.COW_MILK,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    // -- Credit a la consommation ---------------------------------------------------------------

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Long until = CREDITS.get(player.getUUID());
        if (until == null) {
            return;
        }
        CREDITS.remove(player.getUUID());
        if (!(player.level() instanceof ServerLevel level) || level.getGameTime() > until) {
            return;
        }
        event.setCanceled(true);
        long amount;
        try {
            amount = Long.parseLong(event.getRawText().trim().replace(" ", ""));
        } catch (NumberFormatException e) {
            SeasonKit.fail(player, "Tu dois entrer un nombre.");
            return;
        }
        if (amount <= 0L) {
            SeasonKit.fail(player, "Tu dois entrer un nombre.");
            return;
        }
        if (amount > 10_000L) {
            SeasonKit.fail(player, "Tu ne peux pas demander plus que 10 000 $.");
            return;
        }
        if (Economy.balance(player) < amount) {
            SeasonKit.fail(player, "Tu n'as pas assez d'argent pour emprunter autant.");
            return;
        }
        if (level.getRandom().nextBoolean()) {
            Economy.credit(player, amount);
            SeasonKit.win(player, amount + " $ ont ete deposes sur ton compte : joyeux Noel !");
        } else {
            Economy.debit(player, amount);
            SeasonKit.fail(player, amount + " $ ont ete retires de ton compte : il faut bien rembourser...");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CREDITS.remove(event.getEntity().getUUID());
    }

    // -- Trop bu --------------------------------------------------------------------------------

    /** Trop bu ({@code EntityChristmasBall}) : des boules de Noel tournent autour du joueur une minute. */
    private static void drunk(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 1200, 0));
        SeasonKit.fail(player, "Tu as trop bu et tu vois des choses etranges.");
        int[] colors = {0xD62828, 0x2BA554, 0xF2C230, 0x3A6FD8};
        LuckyTasks.repeat(player, 0, 2, 600, step -> {
            for (int ball = 0; ball < colors.length; ball++) {
                double angle = Math.toRadians(step * 6.0d + ball * 90.0d);
                level.sendParticles(new DustParticleOptions(colors[ball], 1.6f), player.getX() + Math.cos(angle) * 1.6d,
                        player.getY() + 1.4d + Math.sin(step * 0.2d + ball) * 0.3d, player.getZ() + Math.sin(angle) * 1.6d,
                        2, 0.02d, 0.02d, 0.02d, 0.0d);
            }
        });
    }

    // -- Prive de Noel --------------------------------------------------------------------------

    /** Prive de Noel : un bloc d'obsidienne durcie de 5 x 6 x 5 autour du joueur, sa place laissee libre. */
    private static void privateChristmas(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos feet = player.blockPosition();
        LuckyWorld.box(player, level, feet.above(2), 5, 6, 5, SeasonKit.block("palamod:hardened_obsidian"),
                at -> at.equals(feet) || at.equals(feet.above()));
        SeasonKit.fail(player, "Prive de Noel ! Tu restes dans ta chambre.");
    }

    // -- Tres-no --------------------------------------------------------------------------------

    /** Le traineau ({@code EntityChristmasSleigh}) : des rails autour du joueur et un chariot. */
    private static void sleigh(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = player.blockPosition();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            if (level.getBlockState(at).isAir()
                    && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                LuckyWorld.place(player, level, at.immutable(), Blocks.RAIL.defaultBlockState());
            }
        }
        var sleigh = LuckyWorld.create(level, EntityTypes.MINECART, center.getX() + 1.5d, center.getY(),
                center.getZ() + 1.5d);
        if (sleigh != null) {
            sleigh.setCustomName(net.minecraft.network.chat.Component.literal("Traineau").withStyle(ChatFormatting.RED));
            level.addFreshEntity(sleigh);
        }
        SeasonKit.give(player, new ItemStack(Items.RAIL, 16));
        SeasonKit.win(player, "Le traineau est avance : en voiture !");
    }

    // -- Il a Freeze il a tout compris ----------------------------------------------------------

    /** Freeze ({@code ServerManager.addFreeze}) : trente secondes fige sur place. */
    private static void freeze(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 spot = player.position();
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 9, false, false));
        player.addEffect(new MobEffectInstance(PalaLucky.NO_JUMP, 600, 0, false, false));
        SeasonKit.fail(player, "Tu es completement freeze !");
        SeasonKit.challenge(player, "Tu es freeze", 30, true, (p, age) -> {
            if (p.position().distanceToSqr(spot) > 0.04d) {
                p.teleportTo(spot.x, spot.y, spot.z);
            }
            if (age % 10 == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0d, p.getZ(), 6, 0.4d, 0.8d, 0.4d,
                        0.0d);
            }
            return SeasonKit.Outcome.GOING;
        }, p -> SeasonKit.say(p, "Tu degeles enfin."), p -> {
        });
    }

    // -- Snow army ------------------------------------------------------------------------------

    /** L'armee de neige ({@code EntityChristmasSnowGolem}) : cinq soldats de neige et un tapis de neige. */
    private static void snowArmy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = player.blockPosition();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            if (level.getBlockState(at).isAir()
                    && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                LuckyWorld.place(player, level, at.immutable(), Blocks.SNOW.defaultBlockState());
            }
        }
        for (int index = 0; index < 5; index++) {
            SeasonKit.mob(level, EntityTypes.SNOW_GOLEM, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2, 4)),
                    "Soldat de neige");
        }
        SeasonKit.win(player, "L'armee de neige est a tes ordres !");
    }

    // -- Pere Noescroc --------------------------------------------------------------------------

    /**
     * Le Pere Noescroc ({@code EntitySantaNoescroc}) : il propose de choisir son cadeau... et donne
     * un bloc de terre qui en porte le nom.
     */
    private static void scamSanta(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob santa = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player, level,
                2, 3)), "Pere Noel", 300, (p, npc) -> SeasonKit.ask(p, "Pere Noel", "Clique sur l'objet pour choisir ton"
                + " cadeau :", SCAM_GIFTS, (q, choice) -> SeasonKit.ask(q, "Pere Noel", "Veux-tu recuperer cet objet : "
                + SCAM_GIFTS.get(choice) + " ?", List.of("Oui", "Non"), (r, answer) -> {
                    if (answer != 0) {
                        SeasonKit.say(r, "Comme tu voudras... Ho ho ho.");
                        return;
                    }
                    SeasonKit.give(r, SeasonKit.named(new ItemStack(Items.DIRT), SCAM_GIFTS.get(choice)));
                    SeasonKit.speak(r, "Pere Noel", "Ho ho ho ! Joyeux Noel !");
                    SeasonKit.fail(r, "C'etait le Pere Noescroc : ton cadeau n'est qu'un bloc de terre.");
                    SeasonKit.dismiss(npc);
                })));
        if (santa != null) {
            santa.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, SeasonKit.stack("minecraft:red_wool", 1));
            SeasonKit.speak(player, "Pere Noel", "Ho ho ho ! Viens choisir ton cadeau.");
        }
    }
}
