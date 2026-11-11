package fr.atraxterra.dohy.ui.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * L'ouverture d'une caisse : la roulette, deja tiree par le serveur.
 *
 * <p>Le serveur tire le lot, le donne, puis envoie cette vue : une bande de
 * cases tirees au hasard dans la table, et le lot gagne a la case {@code win}.
 * Le client ne fait que la faire defiler jusqu'a cette case. Fermer l'ecran en
 * cours de route ne change rien au lot.
 *
 * @param title      le nom du palier, « Caisse rare »
 * @param keys       les cles qui restent pour ce palier
 * @param affordable assez de liquide pour acheter une cle de plus
 * @param prize      le nom du lot (texte marque par {@code MenuView.text} pour un objet)
 * @param detail     la ligne sous le nom : la quantite, le nombre d'objets d'un lot
 * @param contents   les objets du lot, pour un lot groupe ; vide sinon
 * @param rarity     la classe du lot, de 0 (commun) a 3 (legendaire)
 * @param odds       la chance de chaque classe, en pour mille
 * @param spin       un numero d'ouverture : une nouvelle vue relance la roulette
 */
public record CrateView(String tier, String title, int keys, long price, boolean affordable, List<Slot> reel,
                        int win, String prize, String detail, List<ItemStack> contents, int rarity,
                        List<Integer> odds, int spin) implements UiView {

    public static final Identifier ID = UiNet.id("crate");

    @Override
    public Identifier viewId() {
        return ID;
    }

    /** La meme vue, le nom du lot passe par {@code text} (voir {@code MenuView.text}). */
    public CrateView map(UnaryOperator<String> text) {
        return new CrateView(tier, title, keys, price, affordable, reel, win, text.apply(prize), detail, contents,
                rarity, odds, spin);
    }

    /**
     * Une case de la roulette.
     *
     * @param icon  l'objet, a l'unite ; vide pour de l'argent
     * @param label la quantite, la somme ou « LOT », sous l'objet
     */
    public record Slot(ItemStack icon, int rarity, String label) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.of(
                (buffer, slot) -> {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, slot.icon());
                    buffer.writeVarInt(slot.rarity());
                    buffer.writeUtf(slot.label());
                },
                buffer -> new Slot(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt(),
                        buffer.readUtf()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CrateView> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeUtf(view.tier());
                buffer.writeUtf(view.title());
                buffer.writeVarInt(view.keys());
                buffer.writeVarLong(view.price());
                buffer.writeBoolean(view.affordable());
                buffer.writeVarInt(view.reel().size());
                for (Slot slot : view.reel()) {
                    Slot.STREAM_CODEC.encode(buffer, slot);
                }
                buffer.writeVarInt(view.win());
                buffer.writeUtf(view.prize());
                buffer.writeUtf(view.detail());
                buffer.writeVarInt(view.contents().size());
                for (ItemStack stack : view.contents()) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
                }
                buffer.writeVarInt(view.rarity());
                buffer.writeVarInt(view.odds().size());
                for (int odd : view.odds()) {
                    buffer.writeVarInt(odd);
                }
                buffer.writeVarInt(view.spin());
            },
            buffer -> {
                String tier = buffer.readUtf();
                String title = buffer.readUtf();
                int keys = buffer.readVarInt();
                long price = buffer.readVarLong();
                boolean affordable = buffer.readBoolean();
                int size = buffer.readVarInt();
                List<Slot> reel = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    reel.add(Slot.STREAM_CODEC.decode(buffer));
                }
                int win = buffer.readVarInt();
                String prize = buffer.readUtf();
                String detail = buffer.readUtf();
                int count = buffer.readVarInt();
                List<ItemStack> contents = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    contents.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                }
                int rarity = buffer.readVarInt();
                int classes = buffer.readVarInt();
                List<Integer> odds = new ArrayList<>(classes);
                for (int index = 0; index < classes; index++) {
                    odds.add(buffer.readVarInt());
                }
                int spin = buffer.readVarInt();
                return new CrateView(tier, title, keys, price, affordable, reel, win, prize, detail, contents,
                        rarity, odds, spin);
            });
}
