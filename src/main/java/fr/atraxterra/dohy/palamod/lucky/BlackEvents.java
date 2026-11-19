package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Le lucky block noir ({@code luckyevents/black}) : seize evenements.
 *
 * <p>Ecarts : « Lucky Luck », « Sonic Boots » et « Gogo Gadgeto Grip » renvoyaient a des personnages
 * connus : ils deviennent « Pistolet a eau », « Bottes supersoniques » et « Bras extensible », avec
 * des images redessinees ; le clone du joueur de « Petit Chien Chien » est un chien apprivoise ;
 * l'enigme se repond dans le chat (pas sur un panneau) ; les 25 PB d'« Easy Cash » deviennent
 * 25 000 $ et le cosmetique de « Cosmetico », un chapeau de gala.
 */
@EventBusSubscriber
public final class BlackEvents {

    private static final String GOLDEN_OFFER = "dohy_golden_offer";
    /** L'enigme en cours : sa reponse, et la fin des dix minutes. */
    private static final Map<UUID, Riddle> RIDDLES = new HashMap<>();
    private static final List<Riddle> ALL_RIDDLES = List.of(
            new Riddle("Le fabricant n'en veut pas. L'acheteur ne s'en sert pas. Et l'utilisateur ne le voit pas. Que"
                    + " suis-je ?", "cercueil", 0L),
            new Riddle("Je suis le seul aliment qu'on met au frigo, mais qui reste toujours chaud. Qui suis-je ?",
                    "piment", 0L),
            new Riddle("Qu'est-ce qu'on peut ecraser avec le pied droit mais pas avec le pied gauche ?", "pied gauche",
                    0L),
            new Riddle("Qu'est-ce qui peut remplir une piece entiere sans prendre de place ?", "lumiere", 0L),
            new Riddle("Je n'ai que deux mots mais des milliers de lettres. Qui suis-je ?", "poste", 0L),
            new Riddle("Qui a deux aiguilles, mais ne pique pas ?", "montre", 0L),
            new Riddle("Qu'est-ce qui t'appartient mais que tout le monde utilise ?", "nom", 0L),
            new Riddle("Je parle sans bouche et j'entends sans oreilles. Je n'ai pas de corps, mais je m'anime grace au"
                    + " vent. Qui suis-je ?", "echo", 0L));

    private BlackEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.BLACK, "tchu", "Tchu Tchu", "tchutchu", false, 45,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.WHISTLE.get())));
        c.season(LuckyType.BLACK, "sherpa", "Sherpa", "sherpa", false, 50, BlackEvents::sherpa);
        c.season(LuckyType.BLACK, "ni_guemeu", "Hey ! Ni guemeu", "hey_ni_guemeu", false, 75, BlackEvents::riddle);
        c.season(LuckyType.BLACK, "luckyluke", "Pistolet a eau", "luckyluke", false, 100,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.WATER_PISTOL.get())));
        c.season(LuckyType.BLACK, "petit_chien", "Petit Chien Chien", "petit_chien", false, 100, BlackEvents::puppy);
        c.season(LuckyType.BLACK, "sonic_boots", "Bottes supersoniques", "bottes_supersoniques", false, 100,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.SUPERSONIC_BOOTS.get())));
        c.season(LuckyType.BLACK, "missile_sa", "Missile Sol-Air", "missile_sol_air", false, 110,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.WEATHER_MISSILE.get())));
        c.season(LuckyType.BLACK, "solitaire", "Solitaire", "solitaire", true, 150, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(SeasonEffects.SOLITAIRE, 2400, 0));
            SeasonKit.fail(player, "Te voila seul au monde : plus aucun joueur a l'horizon.");
        });
        c.season(LuckyType.BLACK, "cauchemardesque", "Cauchemardesque", "cauchemardesque", true, 200,
                BlackEvents::nightmare);
        c.season(LuckyType.BLACK, "gogo_grip", "Bras extensible", "bras_extensible", false, 250,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.EXTENDING_ARM.get())));
        c.season(LuckyType.BLACK, "killer_dauphin", "Killer Dauphin", "killer_dauphin", true, 350,
                BlackEvents::killerDolphin);
        c.season(LuckyType.BLACK, "alchi", "Alchi rapide", "alchirapide", false, 500,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.ALCHEMIST_FLASK.get())));
        c.season(LuckyType.BLACK, "partoutladium", "Pa-rtout-ladium", "partoutladium", false, 500,
                BlackEvents::paladiumEverywhere);
        c.season(LuckyType.BLACK, "golden_offer", "Une offre en or", "une_offre_en_or", false, 750,
                BlackEvents::goldenOffer);
        c.season(LuckyType.BLACK, "easy_cash", "Easy Cash", "easy_cash", false, 2000, (player, level, pos) -> {
            Economy.credit(player, 25_000L);
            SeasonKit.win(player, "Tu as gagne 25 000 $ !");
        });
        c.season(LuckyType.BLACK, "cosmetico", "Cosmetico", "cosmetico", false, 5000, (player, level, pos) -> {
            ItemStack hat = new ItemStack(Items.LEATHER_HELMET);
            hat.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1A1A1A));
            SeasonKit.give(player, SeasonKit.named(hat, "Couleurs Palarae", "Un chapeau de gala rarissime"));
            SeasonKit.win(player, "Un cosmetique rarissime : le chapeau de gala !");
        });
    }

    // -- Sherpa ---------------------------------------------------------------------------------

    /** Sherpa : d'une a dix vaches, toutes en laisse derriere le joueur. */
    private static void sherpa(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int count = 1 + level.getRandom().nextInt(10);
        for (int index = 0; index < count; index++) {
            Vec3 at = player.position().add(level.getRandom().nextInt(5) - 2.0d, 0.0d, level.getRandom().nextInt(5) - 2.0d);
            Mob cow = SeasonKit.mob(level, EntityTypes.COW, at, null);
            if (cow != null) {
                cow.setLeashedTo(player, true);
            }
        }
        SeasonKit.say(player, "Te voila sherpa : " + count + " vache" + (count > 1 ? "s" : "") + " a guider !");
    }

    // -- Hey ! Ni guemeu ------------------------------------------------------------------------

    private record Riddle(String question, String answer, long until) {
    }

    /** Une enigme : dix minutes pour repondre dans le chat ; 10 000 $ si c'est juste, 5 000 $ de moins sinon. */
    private static void riddle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Riddle picked = ALL_RIDDLES.get(level.getRandom().nextInt(ALL_RIDDLES.size()));
        RIDDLES.put(player.getUUID(), new Riddle(picked.question(), picked.answer(), level.getGameTime() + 12_000L));
        SeasonKit.give(player, SeasonKit.named(new ItemStack(Items.BOOK), "Une petite enigme ?", picked.question()));
        SeasonKit.speak(player, "???", "Une petite enigme ? Tu as 10 minutes pour repondre dans le chat.");
        SeasonKit.speak(player, "???", picked.question());
        SeasonKit.say(player, "Si tu reponds correctement, tu seras dument recompense. Si tu te trompes, fais"
                + " attention a ce qui peut se passer...");
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Riddle riddle = RIDDLES.remove(player.getUUID());
        if (riddle == null || !(player.level() instanceof ServerLevel level) || level.getGameTime() > riddle.until()) {
            return;
        }
        event.setCanceled(true);
        String answer = Normalizer.normalize(event.getRawText(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        if (answer.contains(riddle.answer())) {
            Economy.credit(player, 10_000L);
            SeasonKit.win(player, "Bonne reponse ! 10 000 $ ont ete deposes sur ton compte.");
        } else {
            long taken = Economy.debit(player, 5_000L);
            SeasonKit.fail(player, "Mauvaise reponse (c'etait : " + riddle.answer() + "). " + taken
                    + " $ ont ete retires de ton compte.");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RIDDLES.remove(event.getEntity().getUUID());
    }

    // -- Petit Chien Chien ----------------------------------------------------------------------

    /** Petit Chien Chien ({@code EntityClone}) : un compagnon apprivoise, qui suit et defend son maitre. */
    private static void puppy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob dog = SeasonKit.mob(level, EntityTypes.WOLF, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 1, 2)),
                "Petit chien chien");
        if (dog instanceof net.minecraft.world.entity.TamableAnimal wolf) {
            wolf.tame(player);
            SeasonKit.win(player, "Un petit chien chien te suit partout !");
        }
    }

    // -- Cauchemardesque ------------------------------------------------------------------------

    /** Cauchemardesque ({@code EntityPhantom}) : de trois a cinq cauchemars volants. */
    private static void nightmare(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int count = 3 + level.getRandom().nextInt(3);
        for (int index = 0; index < count; index++) {
            Vec3 at = player.position().add(level.getRandom().nextInt(7) - 3.0d, 3.0d, level.getRandom().nextInt(7) - 3.0d);
            Mob phantom = SeasonKit.mob(level, EntityTypes.PHANTOM, at, "Cauchemar");
            if (phantom != null) {
                SeasonKit.stat(phantom, Attributes.ATTACK_DAMAGE, 3.0d);
                phantom.setTarget(player);
            }
        }
        SeasonKit.fail(player, "Cauchemardesque !");
    }

    // -- Killer Dauphin -------------------------------------------------------------------------

    /** Le dauphin tueur ({@code EntityDolphin}) : un dauphin qui attaque, et frappe fort. */
    private static void killerDolphin(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob dolphin = SeasonKit.mob(level, EntityTypes.DOLPHIN, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2,
                4)), "Killer Dauphin");
        if (dolphin != null) {
            SeasonKit.stat(dolphin, Attributes.ATTACK_DAMAGE, 6.0d);
            dolphin.setTarget(player);
            SeasonKit.fail(player, "Attention, ce dauphin n'est pas commode !");
        }
    }

    // -- Pa-rtout-ladium ------------------------------------------------------------------------

    /** Pa-rtout-ladium : 135 blocs pleins autour du joueur deviennent du minerai de paladium. */
    private static void paladiumEverywhere(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState ore = SeasonKit.block("palamod:paladium_ore");
        BlockPos center = player.blockPosition();
        int[] done = {0};
        SeasonKit.win(player, "Du paladium partout !");
        LuckyTasks.repeat(player, 0, 1, 90, step -> {
            for (int attempt = 0; attempt < 6 && done[0] < 135; attempt++) {
                BlockPos at = center.offset(7 - level.getRandom().nextInt(15),
                        level.getRandom().nextInt(3) - level.getRandom().nextInt(3) - 1, 7 - level.getRandom().nextInt(15));
                BlockState state = level.getBlockState(at);
                if (!state.isAir() && !state.is(Blocks.BEDROCK) && state.isCollisionShapeFullBlock(level, at)
                        && LuckyWorld.place(player, level, at, ore)) {
                    done[0]++;
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5d, at.getY() + 1.0d,
                            at.getZ() + 0.5d, 2, 0.3d, 0.2d, 0.3d, 0.0d);
                }
            }
        });
    }

    // -- Une offre en or ------------------------------------------------------------------------

    /**
     * Une offre en or ({@code EntityLBVillager}) : un marchand propose de la terre contre 32 blocs de
     * paladium ; une fois sur dix, touche, il ajoute deux fragments d'endium. Il part au bout de dix
     * minutes.
     */
    private static void goldenOffer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
        if (trader == null) {
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2, 3));
        trader.setPos(at.x, at.y, at.z);
        trader.setCustomName(Component.literal("Villageois").withStyle(ChatFormatting.GOLD));
        trader.setCustomNameVisible(true);
        trader.setDespawnDelay(12_000);
        trader.addTag(GOLDEN_OFFER);
        trader.getOffers().clear();
        trader.getOffers().add(new MerchantOffer(new ItemCost(SeasonKit.stack("palamod:paladium_block", 1).getItem(), 32),
                new ItemStack(Items.DIRT), 1, 0, 0.0f));
        level.addFreshEntity(trader);
        SeasonKit.say(player, "Un villageois a une offre en or pour toi !");
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!event.getAbstractVillager().entityTags().contains(GOLDEN_OFFER)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.closeContainer();
        if (player.getRandom().nextInt(10) == 0) {
            LuckyWorld.tell(player, "Villageois > Tu as vraiment ete sympa avec moi, je te donne un petit quelque chose.",
                    ChatFormatting.GOLD);
            SeasonKit.give(player, SeasonKit.stack("palamod:endium_fragment", 2));
        }
    }
}
