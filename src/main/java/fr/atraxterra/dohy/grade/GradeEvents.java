package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.spawn.SpawnApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les avantages des grades qui ne passent pas par une commande : le pseudo en couleur, le chat,
 * l'XP en plus et gardee a la mort, le coffre de l'Ender agrandi, la vision nocturne et le vol au
 * spawn de Premium, l'arrivee annoncee, et la fin du Premium.
 *
 * <p>Les bonus d'XP de metier et du pass, la taxe du marche international et les annonces de
 * l'hotel des ventes sont lus la ou ils s'appliquent ({@code JobApi}, {@code PassApi},
 * {@code NationRank}, {@code AuctionSales}), par {@link Grades}.
 */
@EventBusSubscriber
public final class GradeEvents {

    /** Le vol que ce module a donne : on ne retire que celui-la. */
    private static final String FLIGHT_TAG = "dohy_grade_fly";
    /** La vision nocturne que ce module a donnee. */
    private static final String VISION_TAG = "dohy_grade_nv";

    private record Kept(int level, float progress, int total) {
    }

    /** L'XP des morts qui la gardent, entre la mort et la reapparition. */
    private static final Map<UUID, Kept> KEPT = new ConcurrentHashMap<>();

    private GradeEvents() {
    }

    // -- Le pseudo et le chat ------------------------------------------------------------------

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayname(Grades.decorate(player, event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player && Grades.color(player) != 0) {
            Component base = event.getDisplayName() != null ? event.getDisplayName() : player.getName();
            event.setDisplayName(Grades.decorate(player, base));
        }
    }

    /**
     * Couleurs et emojis. Avant les effets des lucky blocks (canard, pirate), qui remplacent le
     * message entier : le leur l'emporte.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        boolean colors = Grades.chatColors(player);
        boolean emojis = Grades.emojis(player);
        String raw = event.getRawText();
        boolean hasCodes = colors && raw.indexOf('&') >= 0;
        boolean hasEmojis = emojis && raw.indexOf(':') >= 0;
        if (!hasCodes && !hasEmojis) {
            return;
        }
        String text = hasEmojis ? ChatStyle.emojis(raw) : raw;
        event.setMessage(colors ? ChatStyle.colors(text) : Component.literal(text));
    }

    // -- L'XP ----------------------------------------------------------------------------------

    /** L'XP classique en plus : une fraction d'orbe se tire au sort, pour que les petites orbes comptent. */
    @SubscribeEvent
    public static void onXp(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) {
            return;
        }
        double bonus = Grades.vanillaXpBonus(player);
        if (bonus <= 0.0d) {
            return;
        }
        double extra = event.getAmount() * bonus;
        int whole = (int) extra;
        if (player.getRandom().nextDouble() < extra - whole) {
            whole++;
        }
        event.setAmount(event.getAmount() + whole);
    }

    /** Un grade garde l'XP a la mort. En dernier : apres un totem, apres un donjon qui annule la mort. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Grades.keepsXp(player)) {
            KEPT.put(player.getUUID(), new Kept(player.experienceLevel, player.experienceProgress,
                    player.totalExperience));
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && KEPT.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Kept kept = KEPT.remove(player.getUUID());
        if (kept != null) {
            player.experienceLevel = kept.level();
            player.experienceProgress = kept.progress();
            player.totalExperience = kept.total();
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.removeTag(VISION_TAG);
            applyNightVision(player);
            updateFlight(player);
        }
    }

    // -- Le coffre de l'Ender ------------------------------------------------------------------

    /** Un coffre de l'Ender du jeu ouvre le coffre agrandi, pour qui a un grade ou Premium. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.ENDER_CHEST) || Grades.enderRows(player) <= 3) {
            return;
        }
        if (player.isSecondaryUseActive()
                && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) {
            return;
        }
        if (level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        EnderChestBlockEntity chest = level.getBlockEntity(pos) instanceof EnderChestBlockEntity found ? found : null;
        GradeEnder.open(player, 3, chest, Component.translatable("container.enderchest"));
        player.awardStat(Stats.OPEN_ENDERCHEST);
    }

    // -- Premium : l'arrivee, la vision nocturne, le vol, la fin -------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        expireIfDue(player);
        Grades.refreshNames(player);
        applyNightVision(player);
        updateFlight(player);
        if (!Grades.premium(player)) {
            return;
        }
        // Le message de connexion de Premium : pour tous les joueurs en ligne.
        player.level().getServer().getPlayerList().broadcastSystemMessage(Component.empty()
                .append(Grades.decorate(player, player.getName()))
                .append(Component.literal(" vient d'arriver.").withColor(Premium.COLOR & 0xFFFFFF)), false);
        int days = Grades.premiumDays(player);
        if (days <= 3) {
            player.sendSystemMessage(Component.literal("Ton Premium se termine dans " + days
                    + (days > 1 ? " jours. " : " jour. ")).withStyle(ChatFormatting.GOLD)
                    .append(Grades.link("[Le prolonger]")));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            expireIfDue(player);
            updateFlight(player);
        }
    }

    /** Le Premium arrive a son terme : on le dit, et ses avantages s'en vont. */
    private static void expireIfDue(ServerPlayer player) {
        GradeData data = Grades.data(player);
        if (data.premiumUntil() <= 0L || data.premiumUntil() > System.currentTimeMillis()) {
            return;
        }
        Grades.save(player, data.withPremiumUntil(0L));
        player.sendSystemMessage(Component.literal("Ton Premium est termine. ").withStyle(ChatFormatting.GOLD)
                .append(Grades.link("[Le reprendre]")));
        premiumChanged(player);
    }

    /** Apres un changement de Premium : le pseudo, la vision nocturne et le vol suivent. */
    static void premiumChanged(ServerPlayer player) {
        Grades.refreshNames(player);
        applyNightVision(player);
        updateFlight(player);
    }

    /** La vision nocturne de {@code /nv} : sans fin, sans particules ; retiree quand elle n'a plus lieu d'etre. */
    static void applyNightVision(ServerPlayer player) {
        boolean wanted = Grades.data(player).nightVision() && Grades.can(player, GradeCommand.NV);
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (wanted) {
            if (current == null || !current.isInfiniteDuration()) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0,
                        false, false, true));
            }
            player.addTag(VISION_TAG);
            return;
        }
        if (player.entityTags().contains(VISION_TAG)) {
            player.removeTag(VISION_TAG);
            if (current != null && current.isInfiniteDuration()) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }

    /** Le vol de {@code /fly} : au spawn seulement ; il s'en va des qu'on en sort. */
    static void updateFlight(ServerPlayer player) {
        boolean active = Grades.data(player).fly() && Grades.can(player, GradeCommand.FLY) && SpawnApi.inSpawn(player)
                && !player.isCreative() && !player.isSpectator();
        Abilities abilities = player.getAbilities();
        if (active) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
                player.addTag(FLIGHT_TAG);
            }
            return;
        }
        if (!player.entityTags().contains(FLIGHT_TAG)) {
            return;
        }
        player.removeTag(FLIGHT_TAG);
        if (!player.isCreative() && !player.isSpectator()) {
            boolean flying = abilities.flying;
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
            if (flying) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, true));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        KEPT.clear();
    }
}
