package fr.atraxterra.dohy.boost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les caisses : le puits a argent du milieu de partie.
 *
 * <p>Une caisse est un pari : elle coute un montant fixe et rend une valeur
 * aleatoire. Les tables sont calibrees pour rendre un peu moins que leur prix en
 * moyenne — c'est ce qui en fait un sink et non une source. Elles vivent dans le
 * datapack, donc le rendement se reequilibre sans recompilation.
 */
@EventBusSubscriber
public final class Crates {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "crates.json");

    private static volatile Config config = new Config(Map.of());

    private Crates() {
    }

    public static Config config() {
        return config;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Caisses introuvables : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Caisses illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Caisses chargees : {}", config.crates().keySet());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des caisses", error);
            return false;
        }
    }

    /** Achete une cle. Retourne la raison du refus, ou {@code null} si reussi. */
    public static String buyKey(ServerPlayer player, String tier) {
        Crate crate = config.crates().get(tier);
        if (crate == null) {
            return "Palier inconnu : " + tier;
        }
        if (!Economy.tryDebit(player, crate.price())) {
            return "Il te faut " + MoneyFormat.exact(crate.price()) + " en liquide.";
        }
        Boosters.data(player).addKeys(tier, 1);
        return null;
    }

    /** Ouvre une caisse. Retourne la raison du refus, ou {@code null} si reussi. */
    public static String open(ServerPlayer player, String tier) {
        Crate crate = config.crates().get(tier);
        if (crate == null) {
            return "Palier inconnu : " + tier;
        }
        BoostData data = Boosters.data(player);
        if (!data.consumeKey(tier)) {
            return "Aucune cle " + tier + ". Achete-en une avec /crate buy " + tier + ".";
        }

        Entry entry = roll(player, crate);
        if (entry == null) {
            return "Table de butin vide pour " + tier + ".";
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 1.1f);

        long money = entry.money(player);
        if (money > 0) {
            long balance = Economy.credit(player, money);
            player.sendSystemMessage(Component.literal("Caisse " + tier + " : ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyFormat.exact(money)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        entry.item().ifPresent(id -> {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                DoHy.LOGGER.warn("Caisse {} : item inconnu {}", tier, id);
                return;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, entry.count()));
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.sendSystemMessage(Component.literal("Caisse " + tier + " : ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(name.copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" x" + Math.max(1, entry.count()))
                            .withStyle(ChatFormatting.WHITE)));
        });

        return null;
    }

    private static Entry roll(ServerPlayer player, Crate crate) {
        int total = crate.entries().stream().mapToInt(Entry::weight).sum();
        if (total <= 0) {
            return null;
        }
        int pick = player.getRandom().nextInt(total);
        for (Entry entry : crate.entries()) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry;
            }
        }
        return crate.entries().get(crate.entries().size() - 1);
    }

    public record Entry(int weight, Optional<Identifier> item, int count,
                        long moneyMin, long moneyMax) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Identifier.CODEC.optionalFieldOf("item").forGetter(Entry::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count),
                Codec.LONG.optionalFieldOf("money_min", 0L).forGetter(Entry::moneyMin),
                Codec.LONG.optionalFieldOf("money_max", 0L).forGetter(Entry::moneyMax)
        ).apply(instance, Entry::new));

        long money(ServerPlayer player) {
            if (moneyMax <= 0) {
                return Math.max(0L, moneyMin);
            }
            long low = Math.min(moneyMin, moneyMax);
            long high = Math.max(moneyMin, moneyMax);
            return low + (long) (player.getRandom().nextDouble() * (high - low));
        }
    }

    public record Crate(long price, List<Entry> entries) {
        static final Codec<Crate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("price").forGetter(Crate::price),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Crate::entries)
        ).apply(instance, Crate::new));
    }

    public record Config(Map<String, Crate> crates) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Crate.CODEC).optionalFieldOf("crates", Map.of())
                        .forGetter(Config::crates)
        ).apply(instance, Config::new));
    }
}
