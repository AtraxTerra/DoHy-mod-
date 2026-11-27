package fr.atraxterra.dohy.palamod.fishing;

import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.fishing.FishingTable.Rarity;
import fr.atraxterra.dohy.palamod.hunter.PalaHunter;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La peche de PalaJobs avec les cannes en paladium et en endium (la canne en
 * bois reste vanilla, choix du joueur) : quand un poisson est remonte, le
 * butin vanilla est annule et le mini-jeu s'ouvre. Le curseur parcourt la
 * barre ; la barre espace l'arrete. Zone verte : un poisson, rouge : deux,
 * violette : le multiplicateur du « Boost », jaune : la rarete suivante (une
 * nouvelle manche). Hors des zones, ou le temps ecoule : le poisson s'echappe.
 */
@EventBusSubscriber
public final class FishingGame {

    /** Une partie en cours. */
    private static final class Session {
        final boolean endium;
        Rarity rarity;
        int stage;
        List<FishingView.Zone> zones = List.of();
        long deadline;

        Session(boolean endium) {
            this.endium = endium;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Une seconde de marge apres la fin de la manche (le ping, le dernier appui). */
    private static final long GRACE_MS = 1000L;

    private FishingGame() {
    }

    public static boolean isPalaRod(ItemStack stack) {
        return stack.is(PalaHunter.PALADIUM_FISHING_ROD.get()) || stack.is(PalaHunter.ENDIUM_FISHING_ROD.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack rod = isPalaRod(main) ? main : player.getItemInHand(InteractionHand.OFF_HAND);
        if (!isPalaRod(rod)) {
            return;
        }
        event.damageRodBy(1);
        event.setCanceled(true);
        start(player, rod.is(PalaHunter.ENDIUM_FISHING_ROD.get()));
    }

    /** Ouvre le mini-jeu, la rarete tiree selon la canne. */
    public static void start(ServerPlayer player, boolean endium) {
        Session session = new Session(endium);
        session.rarity = FishingTable.roll(endium, player.getRandom());
        SESSIONS.put(player.getUUID(), session);
        open(player, session);
    }

    private static void open(ServerPlayer player, Session session) {
        RandomSource random = player.getRandom();
        Rarity rarity = session.rarity;
        int layout = random.nextInt(100);
        boolean levelUp = rarity.levelUp > 0f && rarity.next() != null
                && (rarity.next() != Rarity.MYTHIC || session.endium) && layout < 15;
        boolean boost = !levelUp && rarity.boost > 0 && layout < 30;
        List<FishingView.Zone> zones = new ArrayList<>();
        float x1 = (boost || levelUp ? rarity.boostX1 : rarity.x1) / 100f;
        float x2 = rarity.x2 / 100f;
        // La zone verte au hasard, la rouge en son milieu, le boost au milieu de la rouge.
        float green = 0.08f + random.nextFloat() * (0.84f - x1);
        float center = green + x1 / 2f;
        zones.add(new FishingView.Zone(false, 1, FishingTable.GREEN, green, x1));
        zones.add(new FishingView.Zone(false, 2, FishingTable.RED, center - x2 / 2f, x2));
        if (boost) {
            float width = rarity.boostWidth / 100f;
            zones.add(new FishingView.Zone(false, rarity.boost, FishingTable.PURPLE, center - width / 2f, width));
        }
        if (levelUp) {
            // La zone jaune ailleurs sur la barre, loin de la verte.
            float width = rarity.levelUp / 100f;
            float at = green > 0.5f ? 0.04f + random.nextFloat() * (green - 0.12f) - width
                    : green + x1 + 0.06f + random.nextFloat() * (0.9f - green - x1 - 0.06f);
            zones.add(new FishingView.Zone(true, 0, FishingTable.YELLOW, Math.max(0.01f, Math.min(0.98f - width, at)),
                    width));
        }
        session.zones = zones;
        session.stage++;
        session.deadline = System.currentTimeMillis() + rarity.durationMs + GRACE_MS;
        String label = rarity.label + (boost ? " Boost" : levelUp ? " Level-Up" : "");
        float speed = 0.55f + 0.1f * rarity.ordinal() + random.nextFloat() * 0.15f;
        UiNet.show(player, new FishingView(session.stage, rarity.key, label, rarity.color, rarity.durationMs, speed,
                zones));
        player.level().playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.PLAYERS, 0.6f, 1.0f + 0.1f * rarity.ordinal());
    }

    /** L'appui sur la barre espace : {@code amount} est la position du curseur, en milliemes. */
    public static void handle(ServerPlayer player, UiNet.Action action) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !"stop".equals(action.verb())
                || !String.valueOf(session.stage).equals(action.argument())) {
            return;
        }
        float position = Math.max(0, Math.min(1000, action.amount())) / 1000f;
        FishingView.Zone hit = null;
        for (FishingView.Zone zone : session.zones) {
            if (position >= zone.start() && position <= zone.start() + zone.width()
                    && (hit == null || zone.width() < hit.width())) {
                hit = zone;
            }
        }
        if (hit == null || System.currentTimeMillis() > session.deadline) {
            escape(player);
            return;
        }
        if (hit.levelUp()) {
            session.rarity = session.rarity.next();
            player.sendSystemMessage(Component.literal("[Peche] La prise s'ameliore : " + session.rarity.label + " !")
                    .withStyle(ChatFormatting.YELLOW));
            open(player, session);
            return;
        }
        catchFish(player, session, hit.multiplier());
    }

    private static void catchFish(ServerPlayer player, Session session, int count) {
        SESSIONS.remove(player.getUUID());
        UiNet.close(player);
        Item item = FishingTable.loot(session.rarity, JobApi.level(player, Job.HUNTER), player.getRandom());
        ItemStack stack = new ItemStack(item, count);
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        long xp = FishingTable.XP.getOrDefault(id, 0L) * count;
        Component name = stack.getHoverName();
        CollectionEvents.recordCatch(player, stack.copy());
        fr.atraxterra.dohy.faction.marks.Marks.fished(player, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        if (xp > 0) {
            JobApi.grant(player, Job.HUNTER, xp);
        }
        player.sendSystemMessage(Component.literal("[Peche] " + session.rarity.label + " : " + count + " x ")
                .append(name).append(xp > 0 ? " (+" + MoneyFormat.exact(xp) + " XP de Chasseur)" : "")
                .withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void escape(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        UiNet.close(player);
        player.sendSystemMessage(Component.literal("[Peche] Le poisson s'est echappe.").withStyle(ChatFormatting.GRAY));
    }

    /** Une manche sans reponse, ou un joueur parti : le poisson s'echappe. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                SESSIONS.remove(id);
            } else if (now > SESSIONS.get(id).deadline) {
                escape(player);
            }
        }
    }
}
