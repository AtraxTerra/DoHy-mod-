package fr.atraxterra.dohy.palamod.clicker;

import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran du clicker, calcule par le serveur.
 *
 * <p>Le serveur ne renvoie pas une vue a chaque clic : entre deux vues, le
 * client fait avancer le compteur lui-meme, de {@code rps} par seconde et de
 * {@code click} par clic. {@code clicks} dit combien de clics le serveur a
 * recus : le client retire ceux-la de ceux qu'il a faits pour savoir combien
 * sont encore en route.
 *
 * @param points    les ClicCoins
 * @param rps       la production par seconde
 * @param click     ce que rapporte un clic
 * @param clicks    les clics recus par le serveur pendant cette session
 * @param clickItem l'aliment que l'on clique : celui de la derniere amelioration du clic
 * @param day       le jour de la saison (l'age du clicker)
 * @param owned     les ameliorations possedees, et leur nombre total
 * @param more      il reste des batiments caches apres le dernier montre
 */
public record ClickerView(double points, double rps, double click, int clicks, ItemStack clickItem, int day,
                          int owned, int upgradeCount, List<Building> buildings, boolean more,
                          List<Upgrade> upgrades, List<Shelf> shop) implements UiView {

    public static final Identifier ID = UiNet.id("clicker");

    @Override
    public Identifier viewId() {
        return ID;
    }

    /**
     * Un batiment de la colonne de droite.
     *
     * @param price    le prix du prochain exemplaire
     * @param base     la production de base d'un exemplaire
     * @param rps      la production actuelle de tous les exemplaires
     * @param produced tout ce que ce batiment a produit
     * @param bonuses  le nom des ameliorations qui s'y appliquent
     * @param unlocks  les offres du Clic Shop qu'il ouvre
     */
    public record Building(String id, String name, ItemStack icon, int count, double price, double base, double rps,
                           double produced, String categories, List<String> bonuses, List<ItemStack> unlocks) {
    }

    /**
     * Une amelioration a acheter.
     *
     * @param badge      deux lettres au coin de l'icone, qui disent son type : plusieurs ameliorations
     *                   d'un batiment portent son icone
     * @param ready      toutes ses conditions sont remplies
     * @param effect     ce qu'elle fait
     * @param conditions ses conditions ; le premier caractere dit si elle est remplie ({@code +}) ou non ({@code -})
     */
    public record Upgrade(String id, String name, ItemStack icon, String badge, double price, boolean ready,
                          String effect, List<String> conditions) {
    }

    /** Une offre du Clic Shop. {@code left} : les achats qui restent dans cette rotation. */
    public record Offer(String id, ItemStack stack, double price, int left) {
    }

    /** Un rayon du Clic Shop. {@code seconds} : le temps avant le prochain tirage. */
    public record Shelf(String type, String title, long seconds, List<Offer> offers) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ClickerView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeDouble(view.points());
                buffer.writeDouble(view.rps());
                buffer.writeDouble(view.click());
                buffer.writeVarInt(view.clicks());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, view.clickItem());
                buffer.writeVarInt(view.day());
                buffer.writeVarInt(view.owned());
                buffer.writeVarInt(view.upgradeCount());
                buffer.writeVarInt(view.buildings().size());
                for (Building building : view.buildings()) {
                    buffer.writeUtf(building.id());
                    buffer.writeUtf(building.name());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, building.icon());
                    buffer.writeVarInt(building.count());
                    buffer.writeDouble(building.price());
                    buffer.writeDouble(building.base());
                    buffer.writeDouble(building.rps());
                    buffer.writeDouble(building.produced());
                    buffer.writeUtf(building.categories());
                    writeStrings(buffer, building.bonuses());
                    buffer.writeVarInt(building.unlocks().size());
                    for (ItemStack stack : building.unlocks()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
                    }
                }
                buffer.writeBoolean(view.more());
                buffer.writeVarInt(view.upgrades().size());
                for (Upgrade upgrade : view.upgrades()) {
                    buffer.writeUtf(upgrade.id());
                    buffer.writeUtf(upgrade.name());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, upgrade.icon());
                    buffer.writeUtf(upgrade.badge());
                    buffer.writeDouble(upgrade.price());
                    buffer.writeBoolean(upgrade.ready());
                    buffer.writeUtf(upgrade.effect());
                    writeStrings(buffer, upgrade.conditions());
                }
                buffer.writeVarInt(view.shop().size());
                for (Shelf shelf : view.shop()) {
                    buffer.writeUtf(shelf.type());
                    buffer.writeUtf(shelf.title());
                    buffer.writeVarLong(shelf.seconds());
                    buffer.writeVarInt(shelf.offers().size());
                    for (Offer offer : shelf.offers()) {
                        buffer.writeUtf(offer.id());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, offer.stack());
                        buffer.writeDouble(offer.price());
                        buffer.writeVarInt(offer.left());
                    }
                }
            },
            buffer -> {
                double points = buffer.readDouble();
                double rps = buffer.readDouble();
                double click = buffer.readDouble();
                int clicks = buffer.readVarInt();
                ItemStack clickItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                int day = buffer.readVarInt();
                int owned = buffer.readVarInt();
                int upgradeCount = buffer.readVarInt();
                int size = buffer.readVarInt();
                List<Building> buildings = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    String id = buffer.readUtf();
                    String name = buffer.readUtf();
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                    int count = buffer.readVarInt();
                    double price = buffer.readDouble();
                    double base = buffer.readDouble();
                    double production = buffer.readDouble();
                    double produced = buffer.readDouble();
                    String categories = buffer.readUtf();
                    List<String> bonuses = readStrings(buffer);
                    int unlockCount = buffer.readVarInt();
                    List<ItemStack> unlocks = new ArrayList<>(unlockCount);
                    for (int unlock = 0; unlock < unlockCount; unlock++) {
                        unlocks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                    }
                    buildings.add(new Building(id, name, icon, count, price, base, production, produced, categories,
                            bonuses, unlocks));
                }
                boolean more = buffer.readBoolean();
                int upgradeSize = buffer.readVarInt();
                List<Upgrade> upgrades = new ArrayList<>(upgradeSize);
                for (int index = 0; index < upgradeSize; index++) {
                    upgrades.add(new Upgrade(buffer.readUtf(), buffer.readUtf(),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readUtf(), buffer.readDouble(),
                            buffer.readBoolean(), buffer.readUtf(), readStrings(buffer)));
                }
                int shelfCount = buffer.readVarInt();
                List<Shelf> shop = new ArrayList<>(shelfCount);
                for (int index = 0; index < shelfCount; index++) {
                    String type = buffer.readUtf();
                    String title = buffer.readUtf();
                    long seconds = buffer.readVarLong();
                    int offerCount = buffer.readVarInt();
                    List<Offer> offers = new ArrayList<>(offerCount);
                    for (int offer = 0; offer < offerCount; offer++) {
                        offers.add(new Offer(buffer.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                                buffer.readDouble(), buffer.readVarInt()));
                    }
                    shop.add(new Shelf(type, title, seconds, offers));
                }
                return new ClickerView(points, rps, click, clicks, clickItem, day, owned, upgradeCount, buildings,
                        more, upgrades, shop);
            });

    private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> strings) {
        buffer.writeVarInt(strings.size());
        for (String string : strings) {
            buffer.writeUtf(string);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> out = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            out.add(buffer.readUtf());
        }
        return out;
    }
}
