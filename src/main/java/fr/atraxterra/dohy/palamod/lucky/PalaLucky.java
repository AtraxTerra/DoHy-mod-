package fr.atraxterra.dohy.palamod.lucky;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les lucky blocks de PalaMod ({@code modules.luckyblock}) : les trois blocs,
 * le faux bloc de paladium de « Ouaiche t'es relou », les effets des
 * evenements et les donnees du joueur.
 *
 * <p>Ce que dit le code d'origine ({@code BlockLuckyBlock}) : materiau roche,
 * durete 1, resistance 1000, et il se recolte a la main
 * ({@code canHarvestBlock} toujours vrai). Casse, il se rend tel quel. Il fume
 * de poussiere rouge ({@code randomDisplayTick}, {@code reddust}).
 */
public final class PalaLucky {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    /** L'evenement deja tire d'un lucky block casse avant d'etre ouvert : il ne se retire pas. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> EVENT =
            COMPONENTS.registerComponentType("lucky_event",
                    builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Un lucky block ne de « Quelle dinguerie ! » ({@code setVersion(1)}) : il ne peut pas en ressortir. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CHAINED =
            COMPONENTS.registerComponentType("lucky_chained",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    /** L'armure de « Arc en armure » ({@code rainbow} du NBT) : sa couleur tourne quand on la porte. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RAINBOW =
            COMPONENTS.registerComponentType("lucky_rainbow",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredBlock<LuckyBlock> PALADIUM_LUCKY_BLOCK = BLOCKS.registerBlock(
            "paladium_lucky_block", properties -> new LuckyBlock(LuckyType.PALADIUM, properties), PalaLucky::lucky);
    public static final DeferredBlock<LuckyBlock> ENDIUM_LUCKY_BLOCK = BLOCKS.registerBlock(
            "endium_lucky_block", properties -> new LuckyBlock(LuckyType.ENDIUM, properties), PalaLucky::lucky);
    public static final DeferredBlock<LuckyBlock> FINDIUM_LUCKY_BLOCK = BLOCKS.registerBlock(
            "findium_lucky_block", properties -> new LuckyBlock(LuckyType.FINDIUM, properties), PalaLucky::lucky);

    /**
     * Le faux bloc de paladium ({@code BlockFakePaladium}) : la texture du vrai,
     * durete 6, son de metal. Il saute quinze fois avant de ceder un vrai bloc.
     */
    public static final DeferredBlock<FakePaladiumBlock> PALADIUM_FAKE_BLOCK = BLOCKS.registerBlock(
            "paladium_fake_block", FakePaladiumBlock::new,
            properties -> properties.strength(6.0f, 6.0f).sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> PALADIUM_LUCKY_BLOCK_ITEM = ITEMS.registerItem(
            "paladium_lucky_block", properties -> new BlockItem(PALADIUM_LUCKY_BLOCK.get(),
                    properties.useBlockDescriptionPrefix()));
    public static final DeferredItem<BlockItem> ENDIUM_LUCKY_BLOCK_ITEM = ITEMS.registerItem(
            "endium_lucky_block", properties -> new BlockItem(ENDIUM_LUCKY_BLOCK.get(),
                    properties.useBlockDescriptionPrefix()));
    public static final DeferredItem<BlockItem> FINDIUM_LUCKY_BLOCK_ITEM = ITEMS.registerItem(
            "findium_lucky_block", properties -> new BlockItem(FINDIUM_LUCKY_BLOCK.get(),
                    properties.useBlockDescriptionPrefix()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LuckyBlockEntity>> LUCKY_ENTITY =
            BLOCK_ENTITIES.register("lucky_block", () -> new BlockEntityType<>(LuckyBlockEntity::new, Set.of(
                    PALADIUM_LUCKY_BLOCK.get(), ENDIUM_LUCKY_BLOCK.get(), FINDIUM_LUCKY_BLOCK.get())));

    // Les effets des evenements ({@code CustomPotion} de PLuckyBlock). Leur
    // comportement vit dans LuckyWorldEvents (serveur) et LuckyClientEvents (client).

    /** SuperMan : on vole deux minutes. */
    public static final DeferredHolder<MobEffect, LuckyEffect> SUPER_MAN =
            EFFECTS.register("super_man", () -> new LuckyEffect(MobEffectCategory.BENEFICIAL, 0x3D8BFF));
    /** Walk in Music : des notes sous les pieds. */
    public static final DeferredHolder<MobEffect, LuckyEffect> WALK_IN_MUSIC =
            EFFECTS.register("walk_in_music", () -> new LuckyEffect(MobEffectCategory.BENEFICIAL, 0xEC4FB5));
    /** Tout feu tout flamme : des flammes autour du joueur. */
    public static final DeferredHolder<MobEffect, LuckyEffect> FIRE_MAN =
            EFFECTS.register("fire_man", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xFF6A00));
    /** Bunny Life ({@code GROS_RELOU}) : on saute tout seul. */
    public static final DeferredHolder<MobEffect, LuckyEffect> BUNNY_LIFE =
            EFFECTS.register("bunny_life", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xE8E8E8));
    /** Ou donner de la tete ({@code HIDE_HEAD}) : la tete disparait. */
    public static final DeferredHolder<MobEffect, LuckyEffect> HIDE_HEAD =
            EFFECTS.register("hide_head", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x5A5A5A));
    /** Sol glace ({@code ICE_GROUND}) : on glisse partout. */
    public static final DeferredHolder<MobEffect, LuckyEffect> FROZEN_GROUND =
            EFFECTS.register("frozen_ground", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x9AD8FF));
    /** Sonic ({@code CANCEL_JUMP}) : plus de saut. */
    public static final DeferredHolder<MobEffect, LuckyEffect> NO_JUMP =
            EFFECTS.register("no_jump", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x8B8E95));
    /** Prendre du recul ({@code VIEW_TWO_PERSON}) : la camera reste derriere le joueur. */
    public static final DeferredHolder<MobEffect, LuckyEffect> THIRD_PERSON =
            EFFECTS.register("third_person", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x7A5CFF));
    /** Pret a tout ({@code ANVIL_MONEY}) : chaque enclume recue rapporte 1 000 $. */
    public static final DeferredHolder<MobEffect, LuckyEffect> ANVIL_MONEY =
            EFFECTS.register("anvil_money", () -> new LuckyEffect(MobEffectCategory.BENEFICIAL, 0xFAC400));

    // Le deuxieme lot : leurs effets vivent dans LuckyEffectEvents (serveur),
    // LuckyClientEvents et LuckyOverlays (client).

    /** T'as un probleme nan ? ({@code RAYMAN}) : le corps disparait, bras et jambes flottent. */
    public static final DeferredHolder<MobEffect, LuckyEffect> RAYMAN =
            EFFECTS.register("rayman", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xC9A0FF));
    /** Fantome ({@code GHOST}) : il ne reste que la tete. */
    public static final DeferredHolder<MobEffect, LuckyEffect> GHOST =
            EFFECTS.register("ghost", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xDDE6EE));
    /** Le monde a l'envers ({@code ROTATE}) : le joueur se voit la tete en bas. */
    public static final DeferredHolder<MobEffect, LuckyEffect> ROTATE =
            EFFECTS.register("rotate", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x5E8BFF));
    /** Steve Jobs ({@code STEVE}) : le skin par defaut. */
    public static final DeferredHolder<MobEffect, LuckyEffect> STEVE =
            EFFECTS.register("steve", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x3FA7C9));
    /** Sans lunette ({@code FLOU}) : un voile gris sur l'ecran. */
    public static final DeferredHolder<MobEffect, LuckyEffect> FLOU =
            EFFECTS.register("flou", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xC9CBCF));
    /** I love cats ({@code CAT}) : un chat au milieu de l'ecran. */
    public static final DeferredHolder<MobEffect, LuckyEffect> CAT =
            EFFECTS.register("cat", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xE79A3C));
    /** Vision nocturne ({@code GREEN}) : un filtre vert. */
    public static final DeferredHolder<MobEffect, LuckyEffect> GREEN =
            EFFECTS.register("green", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x9AFF69));
    /** #TeamTree ({@code TREE}) : on seme des pousses de chene en marchant. */
    public static final DeferredHolder<MobEffect, LuckyEffect> TREE =
            EFFECTS.register("tree", () -> new LuckyEffect(MobEffectCategory.BENEFICIAL, 0x4E9A2E));
    /** Boom les mobs ({@code BOOM_MOBS}) : un coup envoie le monstre en l'air. */
    public static final DeferredHolder<MobEffect, LuckyEffect> BOOM_MOBS =
            EFFECTS.register("boom_mobs", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xFF4E4E));
    /** Explosif ({@code EXPLOSE}) : s'accroupir fait exploser. */
    public static final DeferredHolder<MobEffect, LuckyEffect> EXPLOSE =
            EFFECTS.register("explose", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xFF8A1E));
    /** Inversion ({@code PacketUseInversion}) : avant, arriere, gauche, droite, saut et accroupi inverses. */
    public static final DeferredHolder<MobEffect, LuckyEffect> INVERSION =
            EFFECTS.register("inversion", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xB04BD6));
    /** Divise XP ({@code DiviseXP.isXpDivised}) : l'XP de metier est divisee par deux. */
    public static final DeferredHolder<MobEffect, LuckyEffect> HALF_JOB_XP =
            EFFECTS.register("half_job_xp", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x7B6A3A));
    /** On voit pas le fond du bol ({@code pumpkinBlur}) : la vue de la citrouille reste, meme sans elle. */
    public static final DeferredHolder<MobEffect, LuckyEffect> PUMPKIN_BLUR =
            EFFECTS.register("pumpkin_blur", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0xE38A1D));

    /** Les evenements vus et le Lucky Pass. Gardes a la mort. */
    public static final Supplier<AttachmentType<LuckyData>> DATA = ATTACHMENTS.register("lucky",
            () -> AttachmentType.builder(LuckyData::new)
                    .serialize(LuckyData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            PALADIUM_LUCKY_BLOCK_ITEM, ENDIUM_LUCKY_BLOCK_ITEM, FINDIUM_LUCKY_BLOCK_ITEM);

    private PalaLucky() {
    }

    /** Roche, durete 1, resistance 1000 : une explosion ne l'ouvre pas. */
    private static BlockBehaviour.Properties lucky(BlockBehaviour.Properties properties) {
        return properties.strength(1.0f, 1200.0f).sound(SoundType.STONE);
    }

    public static DeferredItem<BlockItem> item(LuckyType type) {
        return switch (type) {
            case PALADIUM -> PALADIUM_LUCKY_BLOCK_ITEM;
            case ENDIUM -> ENDIUM_LUCKY_BLOCK_ITEM;
            case FINDIUM -> FINDIUM_LUCKY_BLOCK_ITEM;
        };
    }

    public static DeferredBlock<LuckyBlock> block(LuckyType type) {
        return switch (type) {
            case PALADIUM -> PALADIUM_LUCKY_BLOCK;
            case ENDIUM -> ENDIUM_LUCKY_BLOCK;
            case FINDIUM -> FINDIUM_LUCKY_BLOCK;
        };
    }

    public static LuckyData data(net.minecraft.world.entity.player.Player player) {
        return player.getData(DATA);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
