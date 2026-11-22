package fr.atraxterra.dohy.faction.org;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Un traite signe avec une des douze nations (lot 24a).
 *
 * @param nation l'indice de la nation
 * @param type   le genre de traite ({@link Type})
 * @param since  sa signature (ms)
 * @param until  son echeance (ms) : il se renouvelle avant, ou il tombe
 */
public record Treaty(int nation, String type, long since, long until) {

    public static final Codec<Treaty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("nation").forGetter(Treaty::nation),
            Codec.STRING.fieldOf("type").forGetter(Treaty::type),
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(Treaty::since),
            Codec.LONG.optionalFieldOf("until", 0L).forGetter(Treaty::until)
    ).apply(instance, Treaty::new));

    private static final long DAY_MS = 86_400_000L;

    /**
     * Les trois traites. Une nation ne signe que si son hostilite est sous le seuil ; l'alliance
     * demande un pacte ou un accord deja en cours.
     *
     * @param threshold l'hostilite au-dela de laquelle elle refuse
     * @param price     ce que coute la signature (le cadeau d'usage), en $
     * @param days      sa duree
     * @param trust     ce qu'il retire a l'hostilite qu'elle tend a avoir
     * @param tax       ce qu'il retire a la taxe du marche international (en points)
     */
    public enum Type {
        PACTE("Pacte de non-agression", "Ni ultimatum ni guerre tant qu'il court ; elle ne se coalise pas contre toi",
                () -> new ItemStack(Items.WHITE_BANNER), 0xFFFFFFFF, 50.0d, 20_000L, 14, 10.0d, 0.0d),
        ACCORD("Accord commercial", "Taxe du marche international -1 point ; ses pays rapporteront plus (lot 24b)",
                () -> new ItemStack(Items.EMERALD), 0xFF22E558, 40.0d, 15_000L, 14, 6.0d, 0.01d),
        ALLIANCE("Alliance", "Pas de guerre, taxe -2 points, son hostilite fond ; un pacte ou un accord avant",
                () -> new ItemStack(Items.GOLDEN_HELMET), 0xFFFAC400, 25.0d, 60_000L, 30, 18.0d, 0.02d);

        private final String label;
        private final String effect;
        private final Supplier<ItemStack> icon;
        private final int color;
        private final double threshold;
        private final long price;
        private final int days;
        private final double trust;
        private final double tax;

        Type(String label, String effect, Supplier<ItemStack> icon, int color, double threshold, long price, int days,
             double trust, double tax) {
            this.label = label;
            this.effect = effect;
            this.icon = icon;
            this.color = color;
            this.threshold = threshold;
            this.price = price;
            this.days = days;
            this.trust = trust;
            this.tax = tax;
        }

        public String label() {
            return label;
        }

        public String effect() {
            return effect;
        }

        public ItemStack icon() {
            return icon.get();
        }

        public int color() {
            return color;
        }

        public double threshold() {
            return threshold;
        }

        public long price() {
            return price;
        }

        public int days() {
            return days;
        }

        public long duration() {
            return days * DAY_MS;
        }

        public double trust() {
            return trust;
        }

        public double tax() {
            return tax;
        }

        public static Type of(String name) {
            for (Type type : values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    public Type kind() {
        return Type.of(type);
    }

    public boolean active(long now) {
        return now < until && kind() != null;
    }
}
