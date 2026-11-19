package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.defense.PalaDefense;
import fr.atraxterra.dohy.palamod.fluid.PalaFluids;
import fr.atraxterra.dohy.palamod.golem.GolemData;
import fr.atraxterra.dohy.palamod.golem.GolemUpgrade;
import fr.atraxterra.dohy.palamod.golem.GuardianGolem;
import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.palamod.hunter.CapturedEntity;
import fr.atraxterra.dohy.palamod.hunter.PalaHunter;
import fr.atraxterra.dohy.palamod.machine.PalaMachines;
import fr.atraxterra.dohy.palamod.machine.TotemBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les evenements du troisieme lot pour le lucky block en findium : ceux qui
 * s'appuient sur ce que le mod a deja (golems et boite a golem, pierre de capture,
 * totem de fertilite, obsidienne durcie, Lucky Pass) ou sur le jeu.
 */
final class FindiumContent {

    /** Tete d'enclume : onze vagues ({@code times > 10} arrete la tache apres la onzieme), tous les 15 tics. */
    private static final int ANVIL_WAVES = 11;
    private static final int ANVIL_PERIOD = 15;
    /** {@code CresusManager.withdrawPlayerAsync(..., 5000.0D, ...)}. */
    static final long MILLION_COST = 5000L;
    /** Les trois mini golems de « Jackpot » : niveau 12 ({@code setLevel(12)}). */
    private static final int JACKPOT_LEVEL = 12;
    /** {@code setFuel(new ItemStack(PALADIUM_INGOT, 64))} : le totem de « Je t'aime tot ». */
    private static final int TOTEM_FUEL = 64;
    /** {@code y < 255} : la colonne de bambou de « Bou-bam ». */
    private static final int BAMBOO_TOP = 255;

    private FindiumContent() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.findium("iloveyouearly", "Je t'aime tot", "je_t_aime_tot", false, 100, FindiumContent::totem);
        c.findium("boubam", "Bou-bam", "bou_bam", false, 150, FindiumContent::bamboo);
        c.findium("voyant", "Boule de Crystal", "crystal", false, 200, FindiumContent::seer);
        c.findium("palastrange", "Paladium Bizarre", "palastrange", false, 200, FindiumContent::strange);
        c.findium("coucou", "Coucou", "coucou", false, 300, FindiumContent::coucou);
        c.findium("donttellanyone", "N'en parle a personne", "dossier_secret", false, 300, FindiumContent::leak);
        c.findium("onemillionplayer", "1 000 000 joueurs", "onemillionplayer", false, 300, FindiumContent::million);
        c.findium("anvilhead", "Tete d'enclume", "tete_d_enclume", true, 500, FindiumContent::anvils);
        c.findium("creepesque", "Creepesque", "creepesque", false, 500, FindiumContent::creepesque);
        c.findium("unrank", "Declassement", "unrank", true, 600, FindiumContent::unrank);
        c.findium("enigme", "Pere Fouras", "pere_fouras", false, 800, FindiumContent::riddle);
        c.findium("roulette", "Roulette", "roulette", false, 800, (player, level, pos) -> {
            LuckyPass.giveBonus(player);
            LuckyPass.open(player);
        });
        c.findium("abandonedbase", "Base abandonnee", "base_abandone", false, 1000, FindiumContent::abandonedBase);
        c.findium("jackpot", "Jackpot", "jackpot", false, 1300, FindiumContent::jackpot);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** {@code <Wilderness> LuckyMan :}, en jaune, vert, rouge et gris : le prefixe de LuckyMan. */
    static Component luckyMan() {
        return Component.literal("<").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Wilderness").withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal("> ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("LuckyMan").withStyle(ChatFormatting.RED))
                .append(Component.literal(" :").withStyle(ChatFormatting.GRAY));
    }

    /** {@code Pere Fouras:}, en rose : le prefixe du gardien de l'enigme. */
    static Component fouras() {
        return Component.literal("Pere Fouras: ").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    // -- Les evenements ---------------------------------------------------------

    /** Un totem de fertilite, plein de 64 lingots de paladium ({@code BlockTotem}, {@code setFuel}). */
    private static void totem(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!LuckyWorld.place(player, level, pos, PalaMachines.TOTEM_BLOCK.get().defaultBlockState())
                || !(level.getBlockEntity(pos) instanceof TotemBlockEntity totem)) {
            return;
        }
        ItemStack ingot = new ItemStack(PalaItems.PALADIUM_INGOT.get());
        for (int index = 0; index < TOTEM_FUEL; index++) {
            totem.addFuel(ingot);
        }
    }

    /** Une colonne de bambou jusqu'a la hauteur 255, tant qu'il y a de l'air ({@code BAMBOO}). */
    private static void bamboo(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState bamboo = Blocks.BAMBOO.defaultBlockState();
        for (int y = pos.getY(); y < BAMBOO_TOP; y++) {
            BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.getBlockState(at).isAir() || !LuckyWorld.canEdit(player, level, at)) {
                return;
            }
            // Sans mise a jour des voisins : la tige tient debout, meme sur de la pierre.
            level.setBlock(at, bamboo, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * La boule de cristal ({@code EntityGodVillager}) : un lancer de Lucky Pass offert, et un
     * voyant qui ouvre le Lucky Pass d'un clic.
     */
    private static void seer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyPass.giveBonus(player);
        var seer = LuckyWorld.create(level, EntityTypes.VILLAGER, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (seer == null) {
            LuckyPass.open(player);
            return;
        }
        seer.setVillagerData(seer.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.CLERIC));
        seer.setCustomName(Component.literal("Voyant").withStyle(ChatFormatting.LIGHT_PURPLE));
        seer.setCustomNameVisible(true);
        seer.setNoAi(true);
        seer.setInvulnerable(true);
        seer.setSilent(true);
        seer.setPersistenceRequired();
        seer.setData(PalaLucky.SEER, true);
        level.addFreshEntity(seer);
        LuckyWorld.bar(player, "Le voyant a vu ton avenir : parle-lui !", ChatFormatting.LIGHT_PURPLE);
    }

    /** Un lingot de paladium marque {@code STRANGE}. */
    private static void strange(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack ingot = new ItemStack(PalaItems.PALADIUM_INGOT.get());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("STRANGE", true);
        ingot.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        ingot.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Il a l'air bizarre...").withStyle(ChatFormatting.DARK_PURPLE))));
        LuckyWorld.drop(level, pos, ingot);
    }

    /**
     * Coucou : un golem de niveau 40 au joueur, avec 64 blocs de paladium dans son
     * inventaire, et ce message : « Tue moi si tu l'oses ».
     */
    private static void coucou(ServerPlayer player, ServerLevel level, BlockPos pos) {
        GuardianGolem golem = LuckyWorld.create(level, PalaGolem.GUARDIAN_GOLEM.get(), pos.getX() + 0.5d, pos.getY(),
                pos.getZ() + 0.5d);
        if (golem == null) {
            return;
        }
        golem.load(new GolemData(Optional.of(player.getUUID()), player.getName().getString(), GuardianGolem.MAX_LEVEL,
                0, GuardianGolem.MAX_LEVEL, List.of(GolemUpgrade.GOLEMANCY, GolemUpgrade.UTILS, GolemUpgrade.INNOVATIVE,
                GolemUpgrade.GREEDY, GolemUpgrade.STOCKAGE), Map.of(), List.of(), 0, GolemData.MINI_GOLEM_DURABILITY,
                Optional.empty()));
        golem.storage().setItem(0, new ItemStack(PalaBlocks.PALADIUM_BLOCK.get(), 64));
        level.addFreshEntity(golem);
        player.sendSystemMessage(Component.literal("[").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("LuckyBlock").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("] Tue moi si tu l'oses").withStyle(ChatFormatting.GOLD)));
    }

    /** « N'en parle a personne » : LuckyMan partage un dossier qui a fuite... vide. */
    private static void leak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.sendSystemMessage(luckyMan().copy().append(Component.literal(" Psss, j'ai un dossier qui a fuite, tiens"
                + " je te le partage gratuitement mais en parle a personne ;)").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(luckyMan());
    }

    /**
     * 1 000 000 joueurs : un ecran de gagnant ({@code GuiOneMillionPlayer}), et 5 000 $
     * de moins. A la fermeture, LuckyMan avoue l'arnaque.
     */
    private static void million(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Economy.debit(player, MILLION_COST);
        OneMillionView.show(player);
    }

    /**
     * Tete d'enclume : onze vagues, toutes les trois quarts de seconde, d'enclumes qui
     * tombent de dix blocs au-dessus du joueur, sur 10 x 10 blocs, trois cases sur dix.
     * <b>Ecart</b> : une enclume n'apparait que dans l'air, sans remplacer de bloc.
     */
    private static void anvils(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        BlockState anvil = Blocks.ANVIL.defaultBlockState();
        LuckyTasks.repeat(player, 0, ANVIL_PERIOD, ANVIL_WAVES, step -> {
            int x = (int) player.getX();
            int y = (int) player.getY();
            int z = (int) player.getZ();
            for (int dx = x - 5; dx < x + 5; dx++) {
                for (int dz = z - 5; dz < z + 5; dz++) {
                    BlockPos at = new BlockPos(dx, y + 10, dz);
                    if (random.nextDouble() > 0.7d && level.getBlockState(at).isAir()) {
                        LuckyWorld.place(player, level, at, anvil);
                    }
                }
            }
        });
    }

    /** Creepesque : une pierre de capture qui garde un creeper. */
    private static void creepesque(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack stone = new ItemStack(PalaHunter.CAPTURE_STONE.get());
        var creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.EVENT);
        if (creeper != null) {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    level.registryAccess());
            creeper.saveWithoutId(output);
            CompoundTag data = output.buildResult();
            data.remove("UUID");
            stone.set(PalaHunter.CAPTURED.get(), new CapturedEntity(BuiltInRegistries.ENTITY_TYPE.getKey(
                    EntityTypes.CREEPER), data, creeper.getName()));
            creeper.discard();
        }
        LuckyWorld.drop(level, pos, stone);
    }

    /**
     * Declassement : chaque piece d'armure tombe au sol, et une piece en cuir, aux memes
     * enchantements et au meme nom, prend sa place (les degats de la piece ne suivent pas).
     */
    private static void unrank(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Map<EquipmentSlot, Item> leather = Map.of(EquipmentSlot.FEET, Items.LEATHER_BOOTS, EquipmentSlot.LEGS,
                Items.LEATHER_LEGGINGS, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, EquipmentSlot.HEAD,
                Items.LEATHER_HELMET);
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST,
                EquipmentSlot.HEAD)) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isEmpty()) {
                continue;
            }
            LuckyWorld.drop(level, pos, worn.copy());
            ItemStack copy = new ItemStack(leather.get(slot));
            copy.applyComponents(worn.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE));
            player.setItemSlot(slot, copy);
        }
    }

    /** Pere Fouras : trois essais pour trouver le mot ; la reponse, dans le chat ({@code handleChat}). */
    private static void riddle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.sendSystemMessage(fouras().copy().append(Component.literal("Repondez a cette enigme, vous n'avez")
                .withStyle(ChatFormatting.GREEN)));
        LuckyWorld.tell(player, "que 3 essais.", ChatFormatting.GREEN);
        LuckyWorld.tell(player, "Elle nous obsede sans arret.", ChatFormatting.YELLOW);
        LuckyWorld.tell(player, "Donne des reponses ou des idees,", ChatFormatting.YELLOW);
        LuckyWorld.tell(player, "Sans elle, pas d'indice gagne !", ChatFormatting.YELLOW);
        LuckyWorld.tell(player, "Qui est-elle ?", ChatFormatting.YELLOW);
        LuckyContentEvents.startRiddle(player);
    }

    /**
     * Base abandonnee : un sol de 10 x 10 en obsidienne durcie sous le joueur, et quatre
     * murs de onze blocs de haut, de six blocs sous ses pieds a quatre au-dessus de sa tete.
     */
    private static void abandonedBase(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState hard = PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (int i = x - 5; i < x + 5; i++) {
            for (int k = z - 5; k < z + 5; k++) {
                LuckyWorld.place(player, level, new BlockPos(i, y - 1, k), hard);
            }
        }
        for (int i = x - 5; i < x + 5; i++) {
            for (int j = y - 6; j < y + 5; j++) {
                LuckyWorld.place(player, level, new BlockPos(i, j, z - 5), hard);
                LuckyWorld.place(player, level, new BlockPos(i, j, z + 5), hard);
            }
        }
        for (int k = z - 5; k < z + 5; k++) {
            for (int j = y - 6; j < y + 5; j++) {
                LuckyWorld.place(player, level, new BlockPos(x - 5, j, k), hard);
                LuckyWorld.place(player, level, new BlockPos(x + 5, j, k), hard);
            }
        }
    }

    /**
     * Jackpot : une boite a golem toute montee (25 cadres, l'eau angelique au centre, la
     * boite sur une face) et trois mini golems dedans.
     *
     * <p><b>Ecart</b> : chez PalaMod, les golems recevaient les ameliorations Farmeur et
     * Palado-createur, mais pas Golem en boite, sans laquelle une boite ne les fait pas
     * travailler. Ici ils ont les deux branches jusqu'au bout : ils produisent.
     */
    private static void jackpot(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!LuckyWorld.canEdit(player, level, pos)) {
            return;
        }
        BlockState frame = PalaGolem.GOLEM_BOX_FRAME.get().defaultBlockState();
        for (int i = -1; i <= 1; i++) {
            for (int j = 0; j <= 2; j++) {
                for (int k = -1; k <= 1; k++) {
                    LuckyWorld.place(player, level, pos.offset(i, j, k), frame);
                }
            }
        }
        LuckyWorld.place(player, level, pos.above(), PalaFluids.ANGELIC_WATER_BLOCK.get().defaultBlockState());
        BlockPos box = pos.offset(1, 1, 0);
        if (!LuckyWorld.place(player, level, box, PalaGolem.GOLEM_BOX.get().defaultBlockState())
                || !(level.getBlockEntity(box) instanceof Container container)) {
            return;
        }
        GolemData data = new GolemData(Optional.of(player.getUUID()), player.getName().getString(), JACKPOT_LEVEL, 0,
                0, List.of(GolemUpgrade.GOLEMANCY, GolemUpgrade.UTILS, GolemUpgrade.INNOVATIVE, GolemUpgrade.GREEDY,
                GolemUpgrade.MECAGOLEM, GolemUpgrade.INBOX, GolemUpgrade.FARMER, GolemUpgrade.PACIFICATION,
                GolemUpgrade.INDUSTRIAL_REGIME, GolemUpgrade.IRON_MAKER, GolemUpgrade.AMETHYST_MAKER,
                GolemUpgrade.TITANE_MAKER, GolemUpgrade.PALADIUM_MAKER), Map.of(), List.of(), 0,
                GolemData.MINI_GOLEM_DURABILITY, Optional.empty());
        for (int slot = 0; slot < 3; slot++) {
            ItemStack golem = new ItemStack(PalaGolem.MINI_GOLEM.get());
            golem.set(PalaGolem.GOLEM_DATA.get(), data);
            container.setItem(slot, golem);
        }
        container.setChanged();
    }
}
