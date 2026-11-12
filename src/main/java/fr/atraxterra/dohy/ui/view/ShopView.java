package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Le shop : le solde et toutes les offres du catalogue.
 *
 * <p>Tout le catalogue part d'un coup (moins de deux cents lignes) : la
 * categorie, la recherche et la selection se font cote client sans aller-retour.
 * Seuls l'achat et la vente passent par le serveur, qui renvoie la vue a jour.
 *
 * @param balance       solde du joueur, en centimes
 * @param courseMinutes minutes avant le prochain cours de l'heure
 */
public record ShopView(long balance, List<Offer> offers, int courseMinutes) implements UiView {

    public static final Identifier ID = UiNet.id("shop");

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
     */
    public record Offer(ItemStack stack, String category, long buy, int discount, long sell, int sellPercent,
                        long lockMissing, int owned, long collected, long nextStep, int course) {

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
                },
                buffer -> new Offer(ItemStack.STREAM_CODEC.decode(buffer), buffer.readUtf(), buffer.readVarLong(),
                        buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarLong(),
                        buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt()));

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
            ShopView::new);

    @Override
    public Identifier viewId() {
        return ID;
    }
}
