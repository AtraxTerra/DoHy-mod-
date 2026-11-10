package fr.atraxterra.dohy.faction.reserve;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionPerks;
import fr.atraxterra.dohy.faction.FactionStats;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.reserve.PalaReserve;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * La reserve d'or (lot 22e), d'apres le wiki « Investissement Reserve d'or ».
 *
 * <p><b>Le cube</b> : 5, 6 ou 7 de cote (palier 1, 2 ou 3, debloques par la
 * branche Economie de l'arbre). Aretes, dessus et dessous en coffres-forts de
 * faction ; le centre de chaque flanc en vitres de coffre-fort ; le coeur en
 * or liquide (ou vide). Le palier 1 fait 62 coffres-forts, 36 vitres et jusqu'a
 * 27 or liquide, le 3 : 118, 100 et 125. Tout doit etre dans tes claims.
 *
 * <p><b>Investir</b> : chaque or liquide du coeur permet de placer 400 000 $
 * (jusqu'a 800 000 $ avec l'arbre). L'argent sort de la banque de faction et y
 * revient a l'echeance, avec des interets d'autant plus gros que le placement
 * est long (1 % sur un jour, 4 % sur trois, 10 % sur sept, 25 % sur quatorze),
 * multiplies par l'arbre (x2, x3). Avant l'echeance, on peut retirer sa mise,
 * moins 5 %.
 *
 * <p><b>Le risque</b> : la reserve ne craint aucune explosion, seulement les
 * withers. Si l'un d'eux en casse un bloc pendant qu'il y a de l'argent placé,
 * tout l'argent investi est perdu. A la main, on ne la demonte pas tant qu'elle
 * porte des placements.
 */
@EventBusSubscriber
public final class GoldReserves {

    /** Les durees de placement et leur rendement de base, en pour mille. */
    public enum Term {
        DAY(1, 10, "1 jour"),
        THREE_DAYS(3, 40, "3 jours"),
        WEEK(7, 100, "7 jours"),
        FORTNIGHT(14, 250, "14 jours");

        private final int days;
        private final int permille;
        private final String label;

        Term(int days, int permille, String label) {
            this.days = days;
            this.permille = permille;
            this.label = label;
        }

        public int days() {
            return days;
        }

        public int permille() {
            return permille;
        }

        public String label() {
            return label;
        }
    }

    public static final int MAX_INVESTMENTS = 6;
    public static final long MIN_AMOUNT = 10_000L;
    public static final int PENALTY_PERCENT = 5;

    private GoldReserves() {
    }

    public static GoldReserve of(ServerPlayer player) {
        return player.getData(Factions.RESERVE.get());
    }

    private static void set(ServerPlayer player, GoldReserve reserve) {
        player.setData(Factions.RESERVE.get(), reserve);
    }

    /** Ce que la reserve permet de placer : or liquide x valeur de l'arbre. */
    public static long capacity(ServerPlayer player, GoldReserve reserve) {
        return reserve.gold() * FactionApi.perks(player).goldValue();
    }

    // -- Le cube ----------------------------------------------------------------------

    private record Cube(BlockPos origin, int size, int gold) {
    }

    private static boolean isSafe(BlockState state) {
        return state.is(PalaReserve.FACTION_SAFE.get());
    }

    private static boolean isReserveBlock(BlockState state) {
        return isSafe(state) || state.is(PalaReserve.VAULT_GLASS.get()) || state.is(PalaReserve.LIQUID_GOLD.get());
    }

    /** Le plus grand cube de reserve dont ce coffre-fort fait partie, ou {@code null}. */
    private static Cube find(Level level, BlockPos clicked) {
        for (int n = 7; n >= 5; n--) {
            for (int dx = 0; dx < n; dx++) {
                for (int dy = 0; dy < n; dy++) {
                    for (int dz = 0; dz < n; dz++) {
                        if (role(dx, dy, dz, n) != Role.WALL) {
                            continue;
                        }
                        BlockPos origin = clicked.offset(-dx, -dy, -dz);
                        int gold = match(level, origin, n);
                        if (gold >= 0) {
                            return new Cube(origin, n, gold);
                        }
                    }
                }
            }
        }
        return null;
    }

    private enum Role { WALL, WINDOW, CORE }

    private static Role role(int x, int y, int z, int n) {
        boolean ix = x > 0 && x < n - 1;
        boolean iy = y > 0 && y < n - 1;
        boolean iz = z > 0 && z < n - 1;
        if (ix && iy && iz) {
            return Role.CORE;
        }
        if (iy && (ix || iz) && (!ix || !iz)) {
            return Role.WINDOW;
        }
        return Role.WALL;
    }

    /** L'or liquide du cube s'il est bien forme, sinon -1. */
    private static int match(Level level, BlockPos origin, int n) {
        int gold = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    BlockState state = level.getBlockState(pos.setWithOffset(origin, x, y, z));
                    switch (role(x, y, z, n)) {
                        case WALL -> {
                            if (!isSafe(state)) {
                                return -1;
                            }
                        }
                        case WINDOW -> {
                            if (!state.is(PalaReserve.VAULT_GLASS.get())) {
                                return -1;
                            }
                        }
                        case CORE -> {
                            if (state.is(PalaReserve.LIQUID_GOLD.get())) {
                                gold++;
                            } else if (!state.isAir()) {
                                return -1;
                            }
                        }
                    }
                }
            }
        }
        return gold;
    }

    private static final int UNLOADED = -2;
    private static final int BROKEN = -1;

    /** Releve la reserve enregistree : son or, {@link #BROKEN} si elle ne tient plus, {@link #UNLOADED} si loin. */
    private static int survey(ServerPlayer player, GoldReserve reserve) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (FactionApi.dimension(candidate).equals(reserve.dimension())) {
                level = candidate;
            }
        }
        if (level == null) {
            return BROKEN;
        }
        BlockPos origin = reserve.origin();
        int last = reserve.size() - 1;
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + last) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + last) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return UNLOADED;
                }
            }
        }
        int gold = match(level, origin, reserve.size());
        return gold < 0 ? BROKEN : gold;
    }

    private static boolean insideOwnClaims(ServerPlayer player, Level level, Cube cube) {
        int last = cube.size() - 1;
        for (int x = 0; x <= last; x += last) {
            for (int z = 0; z <= last; z += last) {
                ClaimOwner owner = FactionApi.claimAt(level, cube.origin().offset(x, 0, z));
                if (!owner.ownedBy(player.getUUID())) {
                    return false;
                }
            }
        }
        return true;
    }

    // -- Ouvrir -------------------------------------------------------------------------

    /** Un clic droit mains nues sur un coffre-fort : reconnait le cube, l'enregistre, ouvre la reserve. */
    public static void use(ServerPlayer player, BlockPos pos) {
        if (!FactionApi.has(player)) {
            FactionApi.tell(player, "Fonde d'abord ta faction.", ChatFormatting.RED);
            return;
        }
        Level level = player.level();
        Cube cube = find(level, pos);
        if (cube == null) {
            FactionApi.tell(player, "Ce coffre-fort n'appartient a aucune reserve complete : un cube de 5, 6 ou 7 "
                    + "de cote, flancs vitres, coeur d'or liquide (voir /f reserve).", ChatFormatting.RED);
            ReserveMenu.INSTANCE.open(player);
            return;
        }
        int tier = cube.size() - 4;
        if (tier > FactionApi.perks(player).reserveTier()) {
            FactionApi.tell(player, "La reserve de palier " + tier + " est verrouillee : branche Economie de "
                    + "l'arbre de faction.", ChatFormatting.RED);
            return;
        }
        if (!insideOwnClaims(player, level, cube)) {
            FactionApi.tell(player, "La reserve doit etre entierement dans tes claims.", ChatFormatting.RED);
            return;
        }
        GoldReserve current = of(player);
        String dimension = FactionApi.dimension(level);
        boolean same = current.exists() && current.dimension().equals(dimension)
                && current.origin().equals(cube.origin()) && current.size() == cube.size();
        if (!same && !current.investments().isEmpty()) {
            FactionApi.tell(player, "Ta reserve est en " + current.origin().toShortString() + " et porte des "
                    + "placements : recolte-les ou retire-les avant d'en changer.", ChatFormatting.RED);
            return;
        }
        set(player, new GoldReserve(dimension, cube.origin(), cube.size(), cube.gold(),
                same ? current.investments() : java.util.List.of()));
        long built = FactionApi.data(player).stat(FactionStats.RESERVE_TIER);
        if (tier > built) {
            FactionApi.stat(player, FactionStats.RESERVE_TIER, tier - built);
        }
        if (!same) {
            FactionApi.tell(player, "Reserve d'or de palier " + tier + " reconnue : " + cube.gold()
                    + " or liquide, " + MoneyFormat.exact(capacity(player, of(player))) + " $ a placer.",
                    ChatFormatting.GOLD);
        }
        ReserveMenu.INSTANCE.open(player);
    }

    /** Releve l'or de la reserve avant d'afficher : rien ne change si elle est loin. */
    static void refresh(ServerPlayer player) {
        GoldReserve reserve = of(player);
        if (!reserve.exists()) {
            return;
        }
        int gold = survey(player, reserve);
        if (gold == BROKEN) {
            destroyed(player, reserve);
        } else if (gold >= 0 && gold != reserve.gold()) {
            set(player, reserve.withGold(gold));
        }
    }

    // -- Placer, recolter, retirer -------------------------------------------------------

    /** Place {@code wanted} (0 : le plus possible) pour {@code term} ; {@code null} si c'est fait. */
    public static String invest(ServerPlayer player, long wanted, Term term) {
        GoldReserve reserve = of(player);
        if (!reserve.exists()) {
            return "Tu n'as pas de reserve d'or : bats-en une, puis clic droit mains nues sur un coffre-fort";
        }
        int gold = survey(player, reserve);
        if (gold == UNLOADED) {
            return "Ta reserve est trop loin : approche-toi pour placer de l'argent";
        }
        if (gold == BROKEN) {
            destroyed(player, reserve);
            return "Ta reserve d'or n'est plus entiere";
        }
        reserve = reserve.withGold(gold);
        set(player, reserve);
        if (reserve.investments().size() >= MAX_INVESTMENTS) {
            return "Six placements au plus : recolte-en un d'abord";
        }
        long room = capacity(player, reserve) - reserve.invested();
        long bank = FactionApi.data(player).bank();
        long amount = wanted <= 0 ? Math.min(room, bank) : wanted;
        if (amount > room) {
            return "Plus que " + MoneyFormat.exact(Math.max(0L, room)) + " $ de place : ajoute de l'or liquide";
        }
        if (amount > bank) {
            return "La banque de faction n'a que " + MoneyFormat.exact(bank) + " $";
        }
        if (amount < MIN_AMOUNT) {
            return "Il faut placer au moins " + MoneyFormat.exact(MIN_AMOUNT) + " $ (place : "
                    + MoneyFormat.exact(Math.max(0L, room)) + " $, banque : " + MoneyFormat.exact(bank) + " $)";
        }
        if (!FactionApi.payFromBank(player, amount, "Reserve d'or : placement " + term.label())) {
            return "La banque de faction n'a pas assez";
        }
        int permille = term.permille() * Math.max(1, FactionApi.perks(player).interest());
        set(player, reserve.with(new GoldReserve.Investment(amount, System.currentTimeMillis(), term.days(),
                permille)));
        FactionApi.stat(player, FactionStats.RESERVE_INVESTS, 1);
        return null;
    }

    /** Recolte un placement arrive a echeance : mise et interets a la banque. */
    public static String harvest(ServerPlayer player, int index) {
        GoldReserve reserve = of(player);
        if (index < 0 || index >= reserve.investments().size()) {
            return "Ce placement n'existe plus";
        }
        GoldReserve.Investment investment = reserve.investments().get(index);
        if (!investment.ripe(System.currentTimeMillis())) {
            return "Pas encore a echeance";
        }
        if (survey(player, reserve) == BROKEN) {
            destroyed(player, reserve);
            return "Ta reserve d'or n'est plus entiere";
        }
        set(player, reserve.without(index));
        FactionApi.creditBank(player, investment.payout(), "Reserve d'or : placement recolte");
        FactionApi.tell(player, "+" + MoneyFormat.exact(investment.payout()) + " $ a la banque de faction (dont "
                + MoneyFormat.exact(investment.payout() - investment.amount()) + " $ d'interets).", ChatFormatting.GREEN);
        return null;
    }

    /** Retire un placement avant l'echeance : la mise, moins 5 %, sans interets. */
    public static String withdraw(ServerPlayer player, int index) {
        GoldReserve reserve = of(player);
        if (index < 0 || index >= reserve.investments().size()) {
            return "Ce placement n'existe plus";
        }
        GoldReserve.Investment investment = reserve.investments().get(index);
        if (investment.ripe(System.currentTimeMillis())) {
            return harvest(player, index);
        }
        if (survey(player, reserve) == BROKEN) {
            destroyed(player, reserve);
            return "Ta reserve d'or n'est plus entiere";
        }
        long back = investment.amount() * (100 - PENALTY_PERCENT) / 100L;
        set(player, reserve.without(index));
        FactionApi.creditBank(player, back, "Reserve d'or : placement retire avant l'echeance");
        return null;
    }

    // -- Le risque -----------------------------------------------------------------------

    private static void destroyed(ServerPlayer player, GoldReserve reserve) {
        long lost = reserve.invested();
        set(player, GoldReserve.EMPTY);
        FactionApi.tell(player, lost > 0
                ? "Ta reserve d'or a ete detruite : " + MoneyFormat.exact(lost) + " $ de placements sont perdus."
                : "Ta reserve d'or a ete detruite.", ChatFormatting.RED);
    }

    /** A la main, une reserve qui porte des placements ne se demonte pas. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !isReserveBlock(event.getState())) {
            return;
        }
        GoldReserve reserve = of(player);
        if (!reserve.investments().isEmpty()
                && reserve.contains(FactionApi.dimension(player.level()), event.getPos())) {
            event.setCanceled(true);
            FactionApi.tell(player, "Ta reserve porte des placements : recolte-les ou retire-les avant de la "
                    + "demonter.", ChatFormatting.RED);
        }
    }

    /** Un wither casse un bloc de reserve : si son proprietaire a de l'argent place, il est perdu. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWither(LivingDestroyBlockEvent event) {
        if (event.isCanceled() || !isReserveBlock(event.getState())
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = FactionApi.claimAt(level, event.getPos()).owner().orElse(null);
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && of(player).contains(FactionApi.dimension(level), event.getPos())) {
            destroyed(player, of(player));
        }
    }

    /** Une fois par minute : les reserves chargees qui portent des placements tiennent-elles encore ? */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 1200 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            GoldReserve reserve = of(player);
            if (!reserve.investments().isEmpty() && survey(player, reserve) == BROKEN) {
                destroyed(player, reserve);
            }
        }
    }
}
