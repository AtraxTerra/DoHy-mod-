package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * L'onglet Acheter du commerce : le solde et tout ce que le shop vend.
 *
 * <p>Tout le catalogue part d'un coup (moins de deux cents lignes) : la
 * categorie, la recherche et la selection se font cote client sans aller-retour.
 * Seul l'achat passe par le serveur, qui renvoie la vue a jour. La vente a son
 * propre onglet.
 *
 * @param balance       solde du joueur
 * @param courseMinutes minutes avant le prochain cours de l'heure
 * @param subtitle      la phrase sous le titre : ce qu'on trouve ici
 * @param tabs          les onglets du commerce
 * @param elsewhere     ce qui s'achete ailleurs (boutique, hotel des ventes), pour la recherche
 */
public record ShopView(long balance, List<Offer> offers, int courseMinutes, String subtitle, List<MenuView.Tab> tabs,
                       List<Elsewhere> elsewhere) implements UiView {

    public static final Identifier ID = UiNet.id("shop");

    /**
     * Ce qui ne s'achete pas au shop mais ailleurs dans le commerce : une recherche
     * le trouve et dit ou aller.
     *
     * @param icon  l'objet
     * @param label son nom s'il n'est pas celui de l'objet (« Booster x2 »), vide sinon
     * @param tab   le chemin de l'onglet ou il s'achete
     * @param where le nom de cet onglet
     * @param price son prix, deja ecrit
     */
    public record Elsewhere(ItemStack icon, String label, String tab, String where, String price) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Elsewhere> STREAM_CODEC = StreamCodec.of(
                (buffer, entry) -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.icon());
                    buffer.writeUtf(entry.label());
                    buffer.writeUtf(entry.tab());
                    buffer.writeUtf(entry.where());
                    buffer.writeUtf(entry.price());
                },
                buffer -> new Elsewhere(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readUtf(),
                        buffer.readUtf(), buffer.readUtf(), buffer.readUtf()));
    }

    /**
     * Une offre.
     *
     * @param category    cle de {@code ShopCategory}
     * @param buy         prix d'achat unitaire remise comprise, 0 si l'objet n'est pas en vente
     * @param discount    remise d'achat en pour cent
     * @param sell        prix de vente unitaire courant, 0 si achat seul
     * @param sellPercent part du prix de base que vaut la vente aujourd'hui
     * @param lockMissing quantite a collecter avant de pouvoir vendre, 0 si ouvert
     * @param owned       exemplaires dans l'inventaire
     * @param collected   exemplaires deja collectes
     * @param nextStep    prochain palier de collection, 0 si complet
     * @param course      cours de l'heure en pour cent du prix de base, 0 si l'objet n'en suit pas
     * @param chart       la courbe des dernieres heures, plate pour un prix fixe
     */
    public record Offer(ItemStack stack, String category, long buy, int discount, long sell, int sellPercent,
                        long lockMissing, int owned, long collected, long nextStep, int course, PriceChart chart) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
                (buffer, offer) -> {
                    ItemStack.STREAM_CODEC.encode(buffer, offer.stack());
                    buffer.writeUtf(offer.category());
                    buffer.writeVarLong(offer.buy());
                    buffer.writeVarInt(offer.discount());
                    buffer.writeVarLong(offer.sell());
                    buffer.writeVarInt(offer.sellPercent());
                    buffer.writeVarLong(offer.lockMissing());
                    buffer.writeVarInt(offer.owned());
                    buffer.writeVarLong(offer.collected());
                    buffer.writeVarLong(offer.nextStep());
                    buffer.writeVarInt(offer.course());
                    PriceChart.OPTIONAL_STREAM_CODEC.encode(buffer, offer.chart());
                },
                buffer -> new Offer(ItemStack.STREAM_CODEC.decode(buffer), buffer.readUtf(), buffer.readVarLong(),
                        buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarLong(),
                        buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(),
                        PriceChart.OPTIONAL_STREAM_CODEC.decode(buffer)));

        public boolean sellable() {
            return sell > 0;
        }

        public boolean locked() {
            return lockMissing > 0;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ShopView::balance,
            Offer.STREAM_CODEC.apply(ByteBufCodecs.list()), ShopView::offers,
            ByteBufCodecs.VAR_INT, ShopView::courseMinutes,
            ByteBufCodecs.STRING_UTF8, ShopView::subtitle,
            MenuView.Tab.STREAM_CODEC.apply(ByteBufCodecs.list()), ShopView::tabs,
            Elsewhere.STREAM_CODEC.apply(ByteBufCodecs.list()), ShopView::elsewhere,
            ShopView::new);

    @Override
    public Identifier viewId() {
        return ID;
    }
}
