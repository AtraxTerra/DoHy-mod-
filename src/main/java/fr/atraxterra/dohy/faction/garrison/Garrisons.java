package fr.atraxterra.dohy.faction.garrison;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.war.Sieges;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La garnison en jeu : deployer les soldats quand tu es la, les rappeler quand tu pars, payer la
 * solde, recruter, donner les ordres.
 *
 * <p><b>Ou sont les soldats.</b> En garde ou au repos, autour de la caserne, chacun a son poste,
 * tant que tu es a moins de {@value #NEAR} blocs d'elle (un siege ne vient jamais en ton absence :
 * ta base n'a besoin d'eux que quand tu es la) ; ils rentrent au-dela de {@value #FAR} blocs. Pendant
 * un assaut, ceux qui gardent la base se postent autour de ton coffre, et le tiennent comme tes
 * golems. En marche, derriere toi, partout ou tu vas (sauf au spawn) ; ils te rejoignent d'un saut
 * quand tu t'eloignes, et a l'arrivee dans une autre dimension. En conge, nulle part. Deconnecte, ils
 * sont rappeles.
 *
 * <p><b>La solde</b> se paie chaque jour reel (bascule de 5 h, comme les contrats), depuis la banque
 * de faction, le premier moment ou tu es en ligne ce jour-la : pas de rattrapage des jours d'absence.
 * Banque sous scelle (un siege), elle attend. Deux jours sans solde, un soldat deserte chaque jour.
 *
 * <p><b>Les blesses</b> rappeles se soignent a la caserne (une demi-vie par seconde, tant que tu es
 * en ligne) ; deployes, ils pansent leurs plaies a leur poste quand rien ne les attaque.
 */
@EventBusSubscriber
public final class Garrisons {

    /** En garde, deployes a moins de tant de blocs de la caserne... */
    static final int NEAR = 64;
    /** ... et rappeles au-dela de tant : pas de va-et-vient a la limite. */
    static final int FAR = 80;
    /** Les jours sans solde avant la premiere desertion. */
    static final int PATIENCE = 2;
    /** La vie que reprend chaque seconde un soldat rappele. */
    private static final float REST_HEAL = 0.5f;

    private static final String[] NAMES = {"Marcel", "Louis", "Jules", "Gaston", "Andre", "Henri", "Lucien", "Emile",
            "Raymond", "Albert", "Leon", "Maurice", "Fernand", "Rene", "Paul", "Marius", "Victor", "Edmond", "Armand",
            "Gustave", "Felix", "Hugo", "Arthur", "Theo", "Rose", "Alice", "Jeanne", "Louise", "Marthe", "Odette",
            "Simone", "Lucie", "Yvonne", "Colette", "Suzanne", "Madeleine"};

    /** Les soldats en jeu de chaque joueur, par soldat de la garnison. */
    private static final Map<UUID, Map<UUID, GarrisonSoldier>> DEPLOYED = new HashMap<>();

    private Garrisons() {
    }

    public static Garrison of(ServerPlayer player) {
        return player.getData(DoHyGarrison.GARRISON.get());
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    /** La couleur de la faction (sa banniere) : le cuir des soldats. */
    static DyeColor color(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        return data.exists() ? DyeColor.byId(Math.floorMod(data.banner(), 16)) : DyeColor.BLUE;
    }

    /** Un bloc ou un soldat tient debout : un sol, deux blocs libres, pas d'eau. */
    static boolean standable(LevelReader level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        return below.isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }

    /** La vie pleine d'un soldat de cette garnison. */
    static float maxHealth(Garrison garrison, Garrison.Member member) {
        return (float) Math.max(1.0d, member.role().health() * garrison.tier().health());
    }

    // -- Deployer, rappeler ------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        Garrison garrison = of(player);
        if (garrison.members().isEmpty() && !DEPLOYED.containsKey(player.getUUID())
                && garrison.barracks().isEmpty()) {
            return;
        }
        pay(player, garrison);
        upkeep(player, garrison);
    }

    /** Chaque seconde : ce qui doit etre en jeu l'est, au bon endroit ; la vie des soldats est gardee. */
    static void upkeep(ServerPlayer player, Garrison garrison) {
        Map<UUID, GarrisonSoldier> live = DEPLOYED.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Iterator<Map.Entry<UUID, GarrisonSoldier>> it = live.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GarrisonSoldier> entry = it.next();
            GarrisonSoldier soldier = entry.getValue();
            Garrison.Member member = garrison.member(entry.getKey());
            if (soldier.isRemoved() || !soldier.isAlive() || member == null) {
                if (member == null && !soldier.isRemoved()) {
                    soldier.discard();
                }
                it.remove();
                continue;
            }
            member.health = soldier.getHealth() >= soldier.getMaxHealth() ? -1.0f : soldier.getHealth();
        }
        rest(garrison, live);
        ServerLevel here = (ServerLevel) player.level();
        checkBarracks(player, garrison);
        Garrison.Order order = garrison.order();
        if (order == Garrison.Order.CONGE || player.isSpectator() || garrison.members().isEmpty()) {
            recall(player, garrison);
            return;
        }
        if (order == Garrison.Order.SUIVRE) {
            follow(player, garrison, live, here);
            return;
        }
        // en garde ou au repos : autour de la caserne (du coffre, pendant un assaut), quand le joueur est la
        GlobalPos vault = order == Garrison.Order.GARDE ? Sieges.defendedVault(player.getUUID()) : null;
        Optional<GlobalPos> home = vault != null ? Optional.of(vault) : garrison.barracks();
        int limit = live.isEmpty() ? NEAR : FAR;
        if (home.isEmpty() || home.get().dimension() != here.dimension()
                || home.get().pos().distSqr(player.blockPosition()) > (double) limit * limit) {
            recall(player, garrison);
            return;
        }
        BlockPos center = home.get().pos();
        List<Garrison.Member> members = garrison.members();
        for (int index = 0; index < members.size(); index++) {
            Garrison.Member member = members.get(index);
            BlockPos post = post(here, center, index, members.size(), vault != null);
            GarrisonSoldier soldier = live.get(member.id());
            if (soldier == null) {
                if (here.isLoaded(post)) {
                    spawn(player, garrison, member, here, post, post);
                }
                continue;
            }
            soldier.setOrder(order, post);
            // trop loin de son poste a courir apres un ennemi : il lache et revient
            if (soldier.distanceToSqr(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d) > 40.0d * 40.0d) {
                soldier.setTarget(null);
            }
        }
    }

    /** En marche : chacun pres du joueur, dans sa dimension ; refait a l'arrivee dans une autre. */
    private static void follow(ServerPlayer player, Garrison garrison, Map<UUID, GarrisonSoldier> live,
                               ServerLevel here) {
        if (SpawnApi.inSpawn(player)) {
            // au spawn, rien ne se bat : ils attendent dehors
            recall(player, garrison);
            return;
        }
        for (Garrison.Member member : garrison.members()) {
            GarrisonSoldier soldier = live.get(member.id());
            if (soldier != null && soldier.level() != here) {
                member.health = soldier.getHealth() >= soldier.getMaxHealth() ? -1.0f : soldier.getHealth();
                soldier.discard();
                live.remove(member.id());
                soldier = null;
            }
            if (soldier == null) {
                if (here.isLoaded(player.blockPosition())) {
                    GarrisonSoldier fresh = spawn(player, garrison, member, here, player.blockPosition(),
                            BlockPos.ZERO);
                    if (fresh != null) {
                        fresh.catchUp(player);
                    }
                }
                continue;
            }
            soldier.setOrder(Garrison.Order.SUIVRE, BlockPos.ZERO);
            if (soldier.distanceToSqr(player) > 48.0d * 48.0d) {
                soldier.setTarget(null);
                soldier.catchUp(player);
            }
        }
    }

    /** Les blesses rappeles se soignent a la caserne. */
    private static void rest(Garrison garrison, Map<UUID, GarrisonSoldier> live) {
        for (Garrison.Member member : garrison.members()) {
            if (member.health < 0.0f || live.containsKey(member.id())) {
                continue;
            }
            float next = member.health + REST_HEAL;
            member.health = next >= maxHealth(garrison, member) ? -1.0f : next;
        }
    }

    /** La caserne a disparu (cassee sans son proprietaire) : la garnison le sait. */
    private static void checkBarracks(ServerPlayer player, Garrison garrison) {
        Optional<GlobalPos> barracks = garrison.barracks();
        if (barracks.isEmpty() || player.tickCount % 100 != 0) {
            return;
        }
        ServerLevel level = player.level().getServer().getLevel(barracks.get().dimension());
        if (level == null) {
            lostBarracks(player, barracks.get());
            return;
        }
        if (level.isLoaded(barracks.get().pos()) && !level.getBlockState(barracks.get().pos())
                .is(DoHyGarrison.BARRACKS.get())) {
            lostBarracks(player, barracks.get());
        }
    }

    /**
     * Le poste du soldat {@code index} : un cercle autour de la caserne (deux rangs, a 4 et 6 blocs),
     * ou du coffre pendant un assaut (a 1 et 3 blocs : le premier rang le tient), sur un sol libre.
     */
    static BlockPos post(ServerLevel level, BlockPos center, int index, int count, boolean vault) {
        double angle = Math.PI * 2.0d * index / Math.max(1, count);
        int radius = vault ? 1 + (index % 2) * 2 : 4 + (index % 2) * 2;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
        if (level.isLoaded(new BlockPos(x, center.getY(), z))) {
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (standable(level, feet)) {
                    return feet;
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            if (level.isLoaded(center) && standable(level, center.above(dy))) {
                return center.above(dy);
            }
        }
        return center.above();
    }

    private static GarrisonSoldier spawn(ServerPlayer player, Garrison garrison, Garrison.Member member,
                                         ServerLevel level, BlockPos at, BlockPos post) {
        GarrisonSoldier soldier = DoHyGarrison.SOLDIER.get().create(level, EntitySpawnReason.EVENT);
        if (soldier == null) {
            return null;
        }
        soldier.snapTo(at.getX() + 0.5d, at.getY(), at.getZ() + 0.5d, level.getRandom().nextFloat() * 360.0f, 0.0f);
        soldier.enlist(player.getUUID(), garrison, member, color(player), post);
        if (!level.addFreshEntity(soldier)) {
            return null;
        }
        DEPLOYED.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(member.id(), soldier);
        return soldier;
    }

    /** Rappelle les soldats du joueur : leur vie est gardee, ils quittent le monde. */
    static void recall(ServerPlayer player, Garrison garrison) {
        Map<UUID, GarrisonSoldier> live = DEPLOYED.remove(player.getUUID());
        if (live == null) {
            return;
        }
        for (Map.Entry<UUID, GarrisonSoldier> entry : live.entrySet()) {
            Garrison.Member member = garrison.member(entry.getKey());
            GarrisonSoldier soldier = entry.getValue();
            if (member != null && soldier.isAlive()) {
                member.health = soldier.getHealth() >= soldier.getMaxHealth() ? -1.0f : soldier.getHealth();
            }
            soldier.discard();
        }
    }

    /** Redeploie tout (nouvel equipement, nouvelle caserne) : rappel, puis la seconde suivante. */
    static void refresh(ServerPlayer player) {
        recall(player, of(player));
    }

    /** Les soldats en jeu du joueur. */
    public static List<GarrisonSoldier> deployed(UUID player) {
        Map<UUID, GarrisonSoldier> live = DEPLOYED.get(player);
        return live == null ? List.of() : new ArrayList<>(live.values());
    }

    /** Ce soldat de la garnison, s'il est en jeu. */
    static GarrisonSoldier deployed(UUID player, UUID member) {
        Map<UUID, GarrisonSoldier> live = DEPLOYED.get(player);
        GarrisonSoldier soldier = live == null ? null : live.get(member);
        return soldier != null && soldier.isAlive() && !soldier.isRemoved() ? soldier : null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recall(player, of(player));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && of(player).order() != Garrison.Order.SUIVRE) {
            recall(player, of(player));
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        DEPLOYED.clear();
    }

    // -- La solde ------------------------------------------------------------------------------------

    private static void pay(ServerPlayer player, Garrison garrison) {
        long today = ContractApi.today();
        if (garrison.payDay == Long.MIN_VALUE || garrison.members().isEmpty()) {
            garrison.payDay = today;
            garrison.unpaid = 0;
            return;
        }
        if (garrison.payDay >= today || Sieges.besieged(player.getUUID())) {
            return;
        }
        garrison.payDay = today;
        long due = garrison.dailyPay();
        if (FactionApi.payFromBank(player, due, "Solde de la garnison")) {
            garrison.unpaid = 0;
            tell(player, "Garnison : solde du jour payee, " + MoneyFormat.exact(due) + " $ pris dans la banque.",
                    ChatFormatting.GRAY);
            return;
        }
        garrison.unpaid++;
        if (garrison.unpaid < PATIENCE) {
            tell(player, "Garnison : la banque ne peut pas payer la solde (" + MoneyFormat.exact(due)
                    + " $). Demain, un soldat desertera.", ChatFormatting.RED);
            return;
        }
        Garrison.Member gone = garrison.members().get(garrison.members().size() - 1);
        dismiss(player, garrison, gone);
        tell(player, "Garnison : " + gone.title() + " deserte, la solde n'est plus payee depuis "
                + garrison.unpaid + " jours.", ChatFormatting.RED);
        DoHy.LOGGER.info("Garnison de {} : {} deserte ({} jours sans solde)", player.getName().getString(),
                gone.title(), garrison.unpaid);
    }

    // -- Les actions -----------------------------------------------------------------------------------

    /** Pourquoi ce soldat ne se recrute pas ({@code null} : il se recrute), sans rien payer. */
    static String recruitRefusal(ServerPlayer player, GarrisonRole role) {
        Garrison garrison = of(player);
        if (!FactionApi.has(player)) {
            return "Il faut une nation (et sa banque) pour entretenir une garnison : /f.";
        }
        if (garrison.barracks().isEmpty()) {
            return "Il faut d'abord une caserne, posee dans tes claims.";
        }
        if (garrison.members().size() >= garrison.capacity()) {
            return "La caserne est pleine (" + garrison.capacity() + " soldats) : agrandis-la.";
        }
        if (role.max() > 0 && garrison.count(role) >= role.max()) {
            return "Un seul " + role.label().toLowerCase(Locale.ROOT) + " par garnison.";
        }
        if (FactionApi.data(player).bank() < role.cost()) {
            return "Il faut " + MoneyFormat.exact(role.cost()) + " $ dans la banque de la nation.";
        }
        return null;
    }

    /** Recrute un soldat ; {@code null} s'il arrive, sinon la raison du refus. */
    static String recruit(ServerPlayer player, GarrisonRole role) {
        String refusal = recruitRefusal(player, role);
        if (refusal != null) {
            return refusal;
        }
        Garrison garrison = of(player);
        if (!FactionApi.payFromBank(player, role.cost(), "Recrutement : " + role.label())) {
            return "La banque de la nation ne peut pas payer ce recrutement.";
        }
        Garrison.Member member = new Garrison.Member(UUID.randomUUID(), freshName(garrison, player), role, -1.0f, 0);
        garrison.members.add(member);
        if (garrison.payDay == Long.MIN_VALUE) {
            garrison.payDay = ContractApi.today();
        }
        tell(player, member.title() + " rejoint ta garnison (solde : " + MoneyFormat.exact(role.pay())
                + " $ par jour).", ChatFormatting.GREEN);
        return null;
    }

    /** Un prenom que personne ne porte encore dans la garnison, si possible. */
    private static String freshName(Garrison garrison, ServerPlayer player) {
        for (int attempt = 0; attempt < 12; attempt++) {
            String name = NAMES[player.getRandom().nextInt(NAMES.length)];
            boolean taken = false;
            for (Garrison.Member member : garrison.members()) {
                if (member.name().equals(name)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return name;
            }
        }
        return NAMES[player.getRandom().nextInt(NAMES.length)];
    }

    /** Renvoie un soldat : il quitte la garnison, sans remboursement. */
    static void dismiss(ServerPlayer player, Garrison garrison, Garrison.Member member) {
        Map<UUID, GarrisonSoldier> live = DEPLOYED.get(player.getUUID());
        if (live != null) {
            GarrisonSoldier soldier = live.remove(member.id());
            if (soldier != null) {
                soldier.discard();
            }
        }
        garrison.members.remove(member);
    }

    /** Fait venir pres du joueur un soldat en jeu (coince, ou perdu de vue). */
    static String call(ServerPlayer player, UUID id) {
        GarrisonSoldier soldier = deployed(player.getUUID(), id);
        if (soldier == null) {
            return "Ce soldat n'est pas deploye en ce moment.";
        }
        if (soldier.level() != player.level()) {
            return "Ce soldat est dans une autre dimension.";
        }
        if (!soldier.catchUp(player)) {
            return "Pas de place libre a cote de toi.";
        }
        soldier.setTarget(null);
        return null;
    }

    static void order(ServerPlayer player, Garrison.Order order) {
        String refusal = orderRefusal(player, order);
        if (refusal != null) {
            tell(player, refusal, ChatFormatting.RED);
            return;
        }
        Garrison garrison = of(player);
        garrison.order = order;
        for (GarrisonSoldier soldier : deployed(player.getUUID())) {
            soldier.setOrder(order, soldier.post());
        }
        tell(player, "Garnison : " + order.label().toLowerCase(Locale.ROOT) + ".", ChatFormatting.GOLD);
    }

    static String orderRefusal(ServerPlayer player, Garrison.Order order) {
        if (order != Garrison.Order.SUIVRE && order != Garrison.Order.CONGE && of(player).barracks().isEmpty()) {
            return "Sans caserne, la garnison ne peut que te suivre ou partir en conge.";
        }
        return null;
    }

    static String upgradeLevelRefusal(ServerPlayer player) {
        Garrison garrison = of(player);
        if (garrison.level() >= Garrison.CAPACITY.length) {
            return "La caserne est deja a son plus haut niveau.";
        }
        if (garrison.barracks().isEmpty()) {
            return "Il faut d'abord une caserne, posee dans tes claims.";
        }
        long cost = Garrison.LEVEL_COST[garrison.level()];
        if (FactionApi.data(player).bank() < cost) {
            return "Il faut " + MoneyFormat.exact(cost) + " $ dans la banque de la nation.";
        }
        return null;
    }

    static String upgradeLevel(ServerPlayer player) {
        String refusal = upgradeLevelRefusal(player);
        if (refusal != null) {
            return refusal;
        }
        Garrison garrison = of(player);
        long cost = Garrison.LEVEL_COST[garrison.level()];
        if (!FactionApi.payFromBank(player, cost, "Caserne : niveau " + (garrison.level() + 1))) {
            return "La banque de la nation ne peut pas payer l'agrandissement.";
        }
        garrison.level++;
        tell(player, "Caserne : niveau " + garrison.level() + ", " + garrison.capacity() + " soldats.",
                ChatFormatting.GREEN);
        return null;
    }

    static String upgradeTierRefusal(ServerPlayer player) {
        Garrison garrison = of(player);
        GarrisonTier next = garrison.tier().next();
        if (next == null) {
            return "La garnison porte deja le meilleur equipement.";
        }
        if (!FactionApi.has(player)) {
            return "Il faut une nation (et sa banque) pour equiper une garnison : /f.";
        }
        if (FactionApi.data(player).bank() < next.cost()) {
            return "Il faut " + MoneyFormat.exact(next.cost()) + " $ dans la banque de la nation.";
        }
        return null;
    }

    static String upgradeTier(ServerPlayer player) {
        String refusal = upgradeTierRefusal(player);
        if (refusal != null) {
            return refusal;
        }
        Garrison garrison = of(player);
        GarrisonTier next = garrison.tier().next();
        if (!FactionApi.payFromBank(player, next.cost(), "Garnison : equipement " + next.label())) {
            return "La banque de la nation ne peut pas payer l'equipement.";
        }
        garrison.tier = next;
        refresh(player);
        tell(player, "Garnison : equipement en " + next.label().toLowerCase(Locale.ROOT) + ".", ChatFormatting.GREEN);
        return null;
    }

    // -- La caserne -------------------------------------------------------------------------------------

    /** Pourquoi la caserne ne se pose pas la ({@code null} : elle se pose). */
    static String barracksRefusal(ServerPlayer player, Level level, BlockPos pos) {
        if (!FactionApi.has(player)) {
            return "Une caserne demande une nation : /f.";
        }
        if (!FactionApi.claimAt(level, pos).ownedBy(player.getUUID())) {
            return "Une caserne se pose dans tes claims.";
        }
        Optional<GlobalPos> current = of(player).barracks();
        if (current.isPresent() && !(current.get().dimension() == level.dimension()
                && current.get().pos().equals(pos))) {
            ServerLevel other = player.level().getServer().getLevel(current.get().dimension());
            // une lecture de bloc charge le chunk : la caserne d'avant existe-t-elle encore ?
            if (other != null && other.getBlockState(current.get().pos()).is(DoHyGarrison.BARRACKS.get())) {
                return "Tu as deja une caserne, en " + current.get().pos().toShortString() + ".";
            }
        }
        return null;
    }

    /** La caserne posee : la garnison s'y installe. */
    static void placeBarracks(ServerPlayer player, Level level, BlockPos pos) {
        Garrison garrison = of(player);
        garrison.barracks = Optional.of(GlobalPos.of(level.dimension(), pos.immutable()));
        refresh(player);
        tell(player, "Caserne posee : ta garnison y loge. Ouvre-la (ou /garnison) pour recruter.",
                ChatFormatting.GREEN);
    }

    /**
     * Le proprietaire ouvre une caserne que sa garnison ne connait plus (posee avant la perte de la
     * precedente, ou oubliee) : si elle est valable, la garnison s'y reinstalle.
     */
    static void adopt(ServerPlayer player, Level level, BlockPos pos) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos.immutable());
        if (of(player).barracks().map(here::equals).orElse(false)) {
            return;
        }
        if (barracksRefusal(player, level, pos) == null) {
            placeBarracks(player, level, pos);
        }
    }

    /** La caserne cassee : la garnison n'a plus de poste. */
    static void lostBarracks(ServerPlayer player, GlobalPos at) {
        Garrison garrison = of(player);
        if (!garrison.barracks().map(at::equals).orElse(false)) {
            return;
        }
        garrison.barracks = Optional.empty();
        if (garrison.order() == Garrison.Order.GARDE || garrison.order() == Garrison.Order.REPOS) {
            garrison.order = Garrison.Order.CONGE;
        }
        recall(player, garrison);
        tell(player, "Ta caserne est tombee : la garnison est en conge jusqu'a la prochaine.", ChatFormatting.RED);
    }

    // -- Les combats ------------------------------------------------------------------------------------

    /** Qui frappe vraiment : le tireur d'une fleche. */
    private static Entity attacker(Entity direct, Entity source) {
        if (source != null) {
            return source;
        }
        return direct instanceof Projectile projectile ? projectile.getOwner() : direct;
    }

    /** Ni toi ni les tiens ne blessent ta garnison ; ta garnison ne te blesse pas. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity attacker = attacker(event.getSource().getDirectEntity(), event.getSource().getEntity());
        if (attacker == null || attacker == event.getEntity()) {
            return;
        }
        if (event.getEntity() instanceof GarrisonSoldier soldier && soldier.ally(attacker)) {
            event.setCanceled(true);
        } else if (attacker instanceof GarrisonSoldier soldier && soldier.ally(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity killer = attacker(event.getSource().getDirectEntity(), event.getSource().getEntity());
        if (killer instanceof GarrisonSoldier soldier && soldier.owner() != null) {
            ServerPlayer owner = soldier.ownerPlayer();
            if (owner != null) {
                Garrison.Member member = of(owner).member(soldier.memberId());
                if (member != null) {
                    member.kills++;
                }
            }
        }
        if (event.getEntity() instanceof GarrisonSoldier soldier && soldier.owner() != null) {
            ServerPlayer owner = soldier.ownerPlayer();
            if (owner == null) {
                return;
            }
            Garrison garrison = of(owner);
            Garrison.Member member = garrison.member(soldier.memberId());
            if (member != null) {
                garrison.members.remove(member);
                Map<UUID, GarrisonSoldier> live = DEPLOYED.get(owner.getUUID());
                if (live != null) {
                    live.remove(member.id());
                }
                tell(owner, "Garnison : " + member.title() + " est tombe au combat (" + member.kills()
                        + " ennemi(s) abattu(s)).", ChatFormatting.RED);
                DoHy.LOGGER.info("Garnison de {} : {} tombe en {}", owner.getName().getString(), member.title(),
                        soldier.blockPosition().toShortString());
            }
        }
    }

    // -- La commande -------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("garnison").executes(context -> {
            GarrisonMenu.INSTANCE.open(context.getSource().getPlayerOrException());
            return 1;
        });
        for (Garrison.Order order : Garrison.Order.values()) {
            root.then(Commands.literal(order.id()).executes(context -> {
                order(context.getSource().getPlayerOrException(), order);
                return 1;
            }));
        }
        event.getDispatcher().register(root);
    }
}
