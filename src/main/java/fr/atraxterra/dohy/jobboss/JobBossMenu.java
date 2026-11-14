package fr.atraxterra.dohy.jobboss;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.dungeon.JobBosses;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'autel des offrandes : les quatre gardiens, l'offrande de celui qu'on
 * regarde, et ce qu'il rapporte. Ouvert depuis la borne AUTEL du spawn, on peut
 * deposer ; ouvert d'ailleurs (menu principal, {@code /autel}), on ne fait
 * que regarder et combattre.
 */
public final class JobBossMenu extends MenuController {

    public static final JobBossMenu INSTANCE = new JobBossMenu();

    private record Context(Job job, boolean altar) {
    }

    private final Map<UUID, Context> context = new ConcurrentHashMap<>();

    private JobBossMenu() {
        super("autel");
    }

    /** Depuis la borne de l'autel : les depots sont permis. */
    public void openAtAltar(ServerPlayer player) {
        Context here = context.get(player.getUUID());
        context.put(player.getUUID(), new Context(here == null ? Job.FARMER : here.job(), true));
        open(player);
    }

    /** D'ailleurs : regarder, et combattre un gardien arrive. */
    public void openFromAfar(ServerPlayer player) {
        Context here = context.get(player.getUUID());
        context.put(player.getUUID(), new Context(here == null ? Job.FARMER : here.job(), false));
        open(player);
    }

    private static int color(Job job) {
        return switch (job) {
            case FARMER -> 0xFF5BC236;
            case MINER -> 0xFFB8C2CC;
            case HUNTER -> 0xFFEF3826;
            case ALCHEMIST -> 0xFF4FB3FF;
        };
    }

    private static String status(ServerPlayer player, Job job) {
        return switch (JobBossApi.state(player, job)) {
            case OFFERING -> "Offrande : " + Math.round(JobBossApi.progress(player, job) * 100) + " %";
            case COMING -> "Arrive dans " + JobBossApi.when(JobBossApi.remaining(player, job));
            case READY -> "T'attend dans l'arene";
        };
    }

    @Override
    public MenuView view(ServerPlayer player) {
        Context here = context.getOrDefault(player.getUUID(), new Context(Job.FARMER, false));
        Job selected = here.job();
        JobBossData data = JobBossApi.data(player);

        List<Card> guardians = new ArrayList<>();
        int ready = 0;
        for (Job job : Job.values()) {
            JobBossApi.State state = JobBossApi.state(player, job);
            int level = data.guardian(job).level();
            Card card = Card.of(new ItemStack(JobBosses.icon(job)), JobBosses.name(job), List.of(
                            "Niveau " + level + " · " + job.display(), status(player, job)))
                    .accent(color(job))
                    .tooltip(List.of("Son arene : le decor de " + JobBosses.arena(job).display(),
                            "Victoires : " + data.guardian(job).kills(),
                            state == JobBossApi.State.READY ? "Clic : entrer dans l'arene" : "Clic : voir son offrande"));
            if (state == JobBossApi.State.READY) {
                ready++;
                card = card.badge("PRET").action("fight:" + job.name() + "|combattre");
            } else {
                card = card.badge(state == JobBossApi.State.COMING ? "EN ROUTE" : job == selected ? "VU" : "")
                        .action("select:" + job.name() + "|voir l'offrande");
            }
            if (job == selected && state != JobBossApi.State.READY) {
                card = card.progress(Math.round(JobBossApi.progress(player, job) * 100), 100);
            }
            guardians.add(card);
        }

        JobBossData.Guardian guardian = data.guardian(selected);
        JobBossApi.State state = JobBossApi.state(player, selected);
        List<Card> offering = new ArrayList<>();
        for (JobBossApi.Need need : JobBossApi.needs(selected, guardian.level())) {
            int deposited = state == JobBossApi.State.OFFERING ? Math.min(need.count(), guardian.deposited(need.id()))
                    : need.count();
            int held = JobBossApi.held(player, need.item());
            ItemStack icon = new ItemStack(need.item());
            Card card = Card.of(icon, MenuView.text(icon.getHoverName()), List.of(
                            deposited + " / " + need.count(), held + " dans ton sac"))
                    .badge(MoneyFormat.exact(need.count()))
                    .progress(deposited, need.count());
            offering.add(deposited >= need.count() ? card.state(MenuView.DONE) : card);
        }

        List<Card> actions = new ArrayList<>();
        if (state == JobBossApi.State.OFFERING) {
            Card give = Card.of(new ItemStack(Items.CHEST), "Offrir", List.of(
                    "Deposer ce que tu as de l'offrande", "Le reste plus tard, autant de fois qu'il faut"))
                    .accent(color(selected));
            actions.add(here.altar() ? give.action("offer|offrir")
                    : give.state(MenuView.DISABLED).tooltip(List.of("Seulement a l'autel du spawn :",
                    "la borne AUTEL, sur le parvis")));
        } else if (state == JobBossApi.State.COMING) {
            actions.add(Card.of(new ItemStack(Items.CLOCK), "En route", List.of(
                    "Arrive dans " + JobBossApi.when(JobBossApi.remaining(player, selected)),
                    "L'autel te previendra dans le chat")).accent(color(selected)));
        } else {
            actions.add(Card.of(new ItemStack(Items.NETHERITE_SWORD), "Combattre", List.of(
                            "Il t'attend dans l'arene", "Une defaite ne coute rien"))
                    .accent(0xFFFAC400).badge("PRET").action("fight:" + selected.name() + "|combattre"));
        }

        int level = guardian.level();
        List<Card> rewards = List.of(
                Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "XP de " + selected.display(), List.of(
                        "Des que le gardien tombe")).badge("+" + MoneyFormat.exact(JobBossApi.xpReward(level))),
                Card.of(new ItemStack(Items.GOLD_INGOT), "Argent", List.of("Des que le gardien tombe"))
                        .badge("+" + MoneyFormat.exact(JobBossApi.moneyReward(level)) + " $"),
                Card.of(new ItemStack(Items.ENDER_CHEST), "Coffre du gardien", List.of(
                        "Au centre de l'arene : la reserve de " + JobBosses.arena(selected).display()
                                + " et deux tirages de la table des boss, au palier " + JobBosses.palier(level)))
                        .badge("PALIER " + JobBosses.palier(level)),
                Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "Niveau suivant", List.of(
                        level >= JobBosses.MAX_LEVEL ? "Niveau maximum : il reste a 10"
                                : "Niveau " + (level + 1) + " : offrande x" + String.format("%.1f",
                                1.0d + 0.5d * level).replace('.', ',') + ", gardien plus fort")));

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("VICTOIRES", String.valueOf(data.totalKills()), 0xFFFFFFFF),
                new MenuView.Stat("PRETS", String.valueOf(ready), ready > 0 ? 0xFFFAC400 : 0xFF8C8B93),
                new MenuView.Stat("NIVEAU", level + " / " + JobBosses.MAX_LEVEL, color(selected)));
        String name = JobBosses.name(selected).toUpperCase(java.util.Locale.ROOT);
        return new MenuView(id(), "Autel des offrandes", here.altar() ? "Une offrande, une heure, un gardien"
                : "Les offrandes se deposent a l'autel du spawn", stats, List.of(
                new MenuView.Section("LES GARDIENS", "armor", MenuView.GRID, guardians),
                new MenuView.Section("OFFRANDE · " + name, "resources", MenuView.TILES, offering),
                new MenuView.Section("", "", MenuView.GRID, actions),
                new MenuView.Section("CE QU'IL RAPPORTE", "upgrade", MenuView.ROWS, rewards)), false);
    }

    @Override
    protected void back(ServerPlayer player) {
        UiNet.close(player);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        Context here = context.getOrDefault(player.getUUID(), new Context(Job.FARMER, false));
        if (key.startsWith("select:")) {
            Job job = job(key.substring(7));
            if (job != null) {
                context.put(player.getUUID(), new Context(job, here.altar()));
            }
        } else if ("offer".equals(key)) {
            if (!here.altar()) {
                failure(player);
                return;
            }
            String refusal = JobBossApi.offer(player, here.job());
            if (refusal == null) {
                success(player);
            } else {
                report(player, refusal, null);
            }
        } else if (key.startsWith("fight:")) {
            Job job = job(key.substring(6));
            if (job == null) {
                return;
            }
            String refusal = JobBossApi.fight(player, job);
            if (refusal == null) {
                UiNet.close(player);
                success(player);
            } else {
                report(player, refusal, null);
            }
        }
    }

    private static Job job(String name) {
        for (Job job : Job.values()) {
            if (job.name().equals(name)) {
                return job;
            }
        }
        return null;
    }
}
