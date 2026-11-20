package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Les grades : la donnee du joueur et les quatre insignes qui les representent dans les menus.
 *
 * <p>Les insignes ne se gagnent pas : ce sont les icones des grades (cartes de la boutique,
 * barre laterale, encyclopedie). Leurs dessins sont faits par {@code tools/grade_assets.py}.
 */
public final class DoHyGrades {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);

    /** Le grade, le Premium, les homes : conserves a la mort, comme tout ce qui s'achete. */
    public static final Supplier<AttachmentType<GradeData>> DATA = ATTACHMENTS.register("grade",
            () -> AttachmentType.builder(() -> GradeData.EMPTY)
                    .serialize(GradeData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    public static final DeferredItem<GradeBadgeItem> HEROS = ITEMS.registerItem("grade_heros", GradeBadgeItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<GradeBadgeItem> LEGENDE = ITEMS.registerItem("grade_legende",
            GradeBadgeItem::new, properties -> properties.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<GradeBadgeItem> DIVINITE = ITEMS.registerItem("grade_divinite",
            GradeBadgeItem::new, properties -> properties.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<GradeBadgeItem> PREMIUM = ITEMS.registerItem("grade_premium",
            GradeBadgeItem::new, properties -> properties.stacksTo(1).rarity(Rarity.RARE));

    private DoHyGrades() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
    }

    /** L'insigne d'un grade ; celui de Premium pour aucun grade. */
    public static ItemStack badge(PlayerGrade grade) {
        return new ItemStack(switch (grade) {
            case HEROS -> HEROS.get();
            case LEGENDE -> LEGENDE.get();
            case DIVINITE -> DIVINITE.get();
            case NONE -> PREMIUM.get();
        });
    }

    public static ItemStack premiumBadge() {
        return new ItemStack(PREMIUM.get());
    }
}
