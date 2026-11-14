package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Les regles de la faction solo : creation, identite, claims, banque, ELO.
 *
 * <p>Chaque methode qui peut echouer rend la raison du refus, ou {@code null}
 * quand tout s'est bien passe : les ecrans et les commandes l'affichent tel quel.
 */
public final class FactionApi {

    /** {@code Java | Gestion d'une faction} : 100 lingots de paladium. */
    public static final int CREATION_COST = 100;

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 16;
    public static final int TEXT_MAX = 60;

    /** « Les caracteres speciaux ne sont pas supportes » : lettres, chiffres et ponctuation simple. */
    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9 _'-]+");
    private static final Pattern TEXT = Pattern.compile("[\\p{L}0-9 _'.,!?:()+-]*");

    /** On ne revendique que les trois dimensions du jeu : ni donjon, ni dimension du Mineur. */
    private static final Set<String> CLAIMABLE = Set.of("minecraft:overworld", "minecraft:the_nether",
            "minecraft:the_end");

    private FactionApi() {
    }

    public static FactionData data(ServerPlayer player) {
        return player.getData(Factions.FACTION.get());
    }

    public static boolean has(ServerPlayer player) {
        return data(player).exists();
    }

    public static FactionPerks perks(ServerPlayer player) {
        return FactionPerks.of(data(player));
    }

    public static MutableComponent prefix() {
        return Component.literal("[Faction] ").withStyle(ChatFormatting.GOLD);
    }

    public static void tell(ServerPlayer player, String message, ChatFormatting color) {
        player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(color)));
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    // -- Identite --------------------------------------------------------------

    /** Le refus d'un nom, ou {@code null}. */
    public static String checkName(String name) {
        String trimmed = name.strip();
        if (trimmed.length() < NAME_MIN || trimmed.length() > NAME_MAX) {
            return "Le nom fait de " + NAME_MIN + " a " + NAME_MAX + " caracteres";
        }
        if (!NAME.matcher(trimmed).matches()) {
            return "Le nom n'accepte que des lettres, des chiffres, des espaces, _ ' et -";
        }
        return null;
    }

    public static String checkText(String text, String what) {
        if (text.length() > TEXT_MAX) {
            return what + " : " + TEXT_MAX + " caracteres au plus";
        }
        if (!TEXT.matcher(text).matches()) {
            return what + " : caracteres speciaux non acceptes";
        }
        return null;
    }

    private static String checkTags(List<String> tags) {
        if (tags.size() > FactionTags.MAX) {
            return "Deux tags au plus";
        }
        for (String tag : tags) {
            if (!FactionTags.ALL.contains(tag)) {
                return "Tag inconnu : " + tag;
            }
        }
        return null;
    }

    private static int countIngots(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(PalaItems.PALADIUM_INGOT.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean canAfford(ServerPlayer player) {
        return player.getAbilities().instabuild || countIngots(player.getInventory()) >= CREATION_COST;
    }

    private static void takeIngots(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        int left = CREATION_COST;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(PalaItems.PALADIUM_INGOT.get())) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** Fonde la faction : 100 lingots de paladium, un nom, une description, deux tags, un blason. */
    public static String create(ServerPlayer player, String name, String description, List<String> tags, int banner) {
        FactionData data = data(player);
        if (data.exists()) {
            return "Tu as deja une faction";
        }
        String refusal = checkName(name);
        if (refusal == null) {
            refusal = checkText(description, "Description");
        }
        if (refusal == null) {
            refusal = checkTags(tags);
        }
        if (refusal == null && !canAfford(player)) {
            refusal = "Il faut " + CREATION_COST + " lingots de paladium sur toi";
        }
        if (refusal != null) {
            return refusal;
        }
        takeIngots(player);
        data.name = name.strip();
        data.description = description.strip();
        data.tags.clear();
        data.tags.addAll(tags);
        data.banner = Math.floorMod(banner, 16);
        data.created = System.currentTimeMillis();
        data.ranking = new FactionData.Ranking(0L, 0L, 0, 0, FactionWeekly.currentMonday());
        player.level().getServer().getPlayerList().broadcastSystemMessage(prefix().append(Component.literal(
                player.getGameProfile().name() + " a fonde la faction " + data.name + ".")
                .withStyle(ChatFormatting.YELLOW)), false);
        return null;
    }

    /** Change le nom, la description, le MOTD, les tags et le blason. */
    public static String edit(ServerPlayer player, String name, String description, String motd, List<String> tags,
                              int banner) {
        FactionData data = data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        String refusal = checkName(name);
        if (refusal == null) {
            refusal = checkText(description, "Description");
        }
        if (refusal == null) {
            refusal = checkText(motd, "MOTD");
        }
        if (refusal == null) {
            refusal = checkTags(tags);
        }
        if (refusal != null) {
            return refusal;
        }
        data.name = name.strip();
        data.description = description.strip();
        data.motd = FactionPerks.of(data).motd() ? motd.strip() : "";
        data.tags.clear();
        data.tags.addAll(tags);
        data.banner = Math.floorMod(banner, 16);
        fr.atraxterra.dohy.earth.EarthNations.renamed(player);
        return null;
    }

    /** Pose (ou retire, avec une pile vide) le blason personnalise : une banniere a motifs. */
    public static String setCustomBanner(ServerPlayer player, ItemStack stack) {
        FactionData data = data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        if (!FactionPerks.of(data).customBanner()) {
            return "Le blason personnalise se debloque au palier 4 de la branche Globale";
        }
        if (stack.isEmpty()) {
            data.bannerItem = ItemStack.EMPTY;
            return null;
        }
        if (!(stack.getItem() instanceof net.minecraft.world.item.BannerItem)) {
            return "Tiens une banniere en main";
        }
        data.bannerItem = stack.copyWithCount(1);
        return null;
    }

    /** Dissout la faction : claims rendus, banque perdue, arbre remis a zero. */
    public static void disband(ServerPlayer player) {
        FactionData data = data(player);
        if (!data.exists()) {
            return;
        }
        unclaimAll(player);
        player.setData(Factions.ENEMY_BASE.get(), fr.atraxterra.dohy.faction.base.EnemyBase.EMPTY);
        fr.atraxterra.dohy.earth.EarthNations.release(player);
        String name = data.name;
        data.clear();
        tell(player, "La faction " + name + " est dissoute.", ChatFormatting.RED);
    }

    // -- Claims ----------------------------------------------------------------

    private static LevelChunk chunkAt(LevelAccessor level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        ChunkAccess chunk = level.getChunk(pos);
        return chunk instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    /** Le proprietaire du chunk ou se trouve cette position. */
    public static ClaimOwner claimAt(LevelAccessor level, BlockPos pos) {
        LevelChunk chunk = chunkAt(level, pos);
        return chunk == null ? ClaimOwner.NONE : chunk.getData(Factions.CLAIM.get());
    }

    /** Les claims permis : ceux de l'arbre, un par pays tenu, et ceux gagnes en finissant premier de la notation. */
    public static int claimCap(ServerPlayer player) {
        return perks(player).claims() + fr.atraxterra.dohy.earth.EarthNations.claimBonus(player)
                + (int) data(player).stat(FactionNotation.PRESTIGE_CLAIMS);
    }

    public static boolean claimable(Level level) {
        return CLAIMABLE.contains(dimension(level));
    }

    /** Revendique le chunk ou se tient le joueur. */
    public static String claim(ServerPlayer player) {
        FactionData data = data(player);
        if (!data.exists()) {
            return "Fonde d'abord ta faction";
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!claimable(level)) {
            return "On ne revendique pas dans cette dimension";
        }
        LevelChunk chunk = chunkAt(level, player.blockPosition());
        if (chunk == null) {
            return "Chunk introuvable";
        }
        ClaimOwner owner = chunk.getData(Factions.CLAIM.get());
        ChunkPos pos = chunk.getPos();
        if (owner.ownedBy(player.getUUID())) {
            return "Ce chunk est deja a toi";
        }
        if (owner.claimed()) {
            return "Ce chunk appartient a la faction " + owner.faction();
        }
        int cap = claimCap(player);
        if (data.claims.size() >= cap) {
            return "Plus de claim disponible (" + cap + "). L'arbre de technologie en debloque d'autres";
        }
        chunk.setData(Factions.CLAIM.get(), ClaimOwner.player(player.getUUID(), data.name, true));
        chunk.markUnsaved();
        data.claims.add(new FactionData.Claim(dimension(level), pos.x(), pos.z(), true));
        return null;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Rend un claim de la faction. */
    public static String unclaim(ServerPlayer player, String dimension, int x, int z) {
        FactionData data = data(player);
        FactionData.Claim found = null;
        for (FactionData.Claim claim : data.claims) {
            if (claim.is(dimension, x, z)) {
                found = claim;
            }
        }
        if (found == null) {
            return "Ce chunk n'est pas a toi";
        }
        data.claims.remove(found);
        ServerLevel level = level(player.level().getServer(), dimension);
        if (level != null) {
            LevelChunk chunk = level.getChunk(x, z);
            if (chunk.getData(Factions.CLAIM.get()).ownedBy(player.getUUID())) {
                chunk.removeData(Factions.CLAIM.get());
                chunk.markUnsaved();
            }
        }
        // Un home dans un chunk rendu n'a plus de sens : il part avec.
        data.homes.removeIf(home -> home.dimension().equals(dimension)
                && ((int) Math.floor(home.x())) >> 4 == x && ((int) Math.floor(home.z())) >> 4 == z);
        return null;
    }

    public static String unclaimHere(ServerPlayer player) {
        ChunkPos pos = player.chunkPosition();
        return unclaim(player, dimension(player.level()), pos.x(), pos.z());
    }

    public static int unclaimAll(ServerPlayer player) {
        FactionData data = data(player);
        int count = 0;
        for (FactionData.Claim claim : List.copyOf(data.claims)) {
            if (unclaim(player, claim.dimension(), claim.x(), claim.z()) == null) {
                count++;
            }
        }
        return count;
    }

    /** Les monstres apparaissent-ils dans ce claim ? Bascule et rend le nouvel etat. */
    public static boolean toggleMonsters(ServerPlayer player, int index) {
        FactionData data = data(player);
        if (index < 0 || index >= data.claims.size()) {
            return false;
        }
        FactionData.Claim claim = data.claims.get(index);
        FactionData.Claim next = new FactionData.Claim(claim.dimension(), claim.x(), claim.z(), !claim.monsters());
        data.claims.set(index, next);
        ServerLevel level = level(player.level().getServer(), claim.dimension());
        if (level != null) {
            LevelChunk chunk = level.getChunk(claim.x(), claim.z());
            ClaimOwner owner = chunk.getData(Factions.CLAIM.get());
            if (owner.ownedBy(player.getUUID())) {
                chunk.setData(Factions.CLAIM.get(), new ClaimOwner(owner.owner(), owner.faction(), next.monsters(), owner.enemy()));
                chunk.markUnsaved();
            }
        }
        return next.monsters();
    }

    // -- Banque ----------------------------------------------------------------

    public static String deposit(ServerPlayer player, long amount) {
        FactionData data = data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        if (amount <= 0) {
            return "Rien a deposer";
        }
        if (!Economy.tryDebit(player, amount)) {
            return "Il te manque " + MoneyFormat.exact(amount - Economy.balance(player)) + " $";
        }
        data.bank += amount;
        data.log(amount, "Depot");
        return null;
    }

    public static String withdraw(ServerPlayer player, long amount) {
        FactionData data = data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        if (amount <= 0 || data.bank <= 0) {
            return "La banque de faction est vide";
        }
        if (fr.atraxterra.dohy.faction.war.Sieges.besieged(player.getUUID())) {
            return "Ta base est assiegee : rien ne sort de ta banque tant que dure l'assaut";
        }
        long taken = Math.min(amount, data.bank);
        data.bank -= taken;
        Economy.credit(player, taken);
        // Un retrait credite le joueur comme un gain : la notation le deduit de son critere d'economie.
        FactionNotation.withdrawn(data, taken);
        data.log(-taken, "Retrait");
        return null;
    }

    /** Verse a la banque de faction : recompenses du lundi, events. */
    public static void creditBank(ServerPlayer player, long amount, String label) {
        FactionData data = data(player);
        if (!data.exists() || amount <= 0) {
            return;
        }
        data.bank += amount;
        data.log(amount, label);
    }

    /** Paie depuis la banque de faction : l'arbre de technologie. Rien ne sort d'une banque assiegee. */
    public static boolean payFromBank(ServerPlayer player, long amount, String label) {
        FactionData data = data(player);
        if (!data.exists() || data.bank < amount) {
            return false;
        }
        if (fr.atraxterra.dohy.faction.war.Sieges.besieged(player.getUUID())) {
            tell(player, "Ta base est assiegee : rien ne sort de ta banque tant que dure l'assaut",
                    ChatFormatting.RED);
            return false;
        }
        data.bank -= amount;
        data.log(-amount, label);
        return true;
    }

    /** Ce qu'un tresorier ennemi emporte de la banque pendant un siege (lot 22l). */
    public static boolean seize(ServerPlayer player, long amount, String label) {
        FactionData data = data(player);
        if (!data.exists() || amount <= 0 || data.bank < amount) {
            return false;
        }
        data.bank -= amount;
        data.log(-amount, label);
        return true;
    }

    // -- ELO -------------------------------------------------------------------

    /** Ajoute de l'ELO (event, raid). Une ligue franchie se gagne tout de suite. */
    public static void addElo(ServerPlayer player, long amount, String source) {
        FactionData data = data(player);
        if (!data.exists() || amount <= 0) {
            return;
        }
        FactionData.Ranking ranking = data.ranking;
        long elo = ranking.elo() + amount;
        FactionLeague before = FactionLeague.of(ranking.league());
        FactionLeague after = FactionLeague.forElo(elo).ordinal() > before.ordinal() ? FactionLeague.forElo(elo) : before;
        data.ranking = new FactionData.Ranking(elo, ranking.weekElo() + amount, ranking.weekWins() + 1,
                after.ordinal(), ranking.lastMonday());
        tell(player, "+" + MoneyFormat.exact(amount) + " ELO (" + source + ")", ChatFormatting.GREEN);
        if (after != before) {
            tell(player, "Ta faction monte en ligue " + after.label() + " !", ChatFormatting.AQUA);
        }
    }

    // -- Stats de l'arbre --------------------------------------------------------

    public static void stat(ServerPlayer player, String key, long amount) {
        // Les succes lisent les memes compteurs, faction ou pas.
        fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, key, amount);
        FactionData data = data(player);
        if (data.exists()) {
            data.addStat(key, amount);
        }
    }
}
