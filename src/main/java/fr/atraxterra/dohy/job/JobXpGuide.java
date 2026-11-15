package fr.atraxterra.dohy.job;

import fr.atraxterra.dohy.agri.AgriConfig;
import fr.atraxterra.dohy.agri.Bread;
import fr.atraxterra.dohy.agri.DoHyAgri;
import fr.atraxterra.dohy.jobboss.JobBossApi;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.alchemist.AlchemistEvents;
import fr.atraxterra.dohy.palamod.alchemist.CauldronCoreBlockEntity;
import fr.atraxterra.dohy.palamod.alchemist.PalaAlchemist;
import fr.atraxterra.dohy.palamod.alchemist.PalaWoods;
import fr.atraxterra.dohy.palamod.alchemist.PortalTier;
import fr.atraxterra.dohy.palamod.alchemist.Sap;
import fr.atraxterra.dohy.palamod.defense.Amulets;
import fr.atraxterra.dohy.palamod.dungeon.CandyItem;
import fr.atraxterra.dohy.palamod.dungeon.Dungeon;
import fr.atraxterra.dohy.palamod.dungeon.DungeonManager;
import fr.atraxterra.dohy.palamod.dungeon.DungeonMode;
import fr.atraxterra.dohy.palamod.dungeon.JobBosses;
import fr.atraxterra.dohy.palamod.dungeon.JobBottleItem;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.palamod.fishing.FishingTable;
import fr.atraxterra.dohy.palamod.hunter.PalaHunter;
import fr.atraxterra.dohy.palamod.hunter.SpawnerEvents;
import fr.atraxterra.dohy.palamod.legendary.LegendaryStones;
import fr.atraxterra.dohy.palamod.legendary.PalaLegendary;
import fr.atraxterra.dohy.palamod.machine.PalaMachines;
import fr.atraxterra.dohy.palamod.miner.GodVillager;
import fr.atraxterra.dohy.palamod.miner.MinerXpEvents;
import fr.atraxterra.dohy.ui.view.JobDetailView;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * L'onglet XP de la fiche d'un metier : ce qui rapporte de l'XP, combien, et a
 * partir de quel niveau.
 *
 * <p>Rien n'est recopie a la main : les blocs et les creatures viennent de
 * {@code data/job_progression.json}, les gestes PalaMod des constantes de leurs
 * classes, l'agriculture de sa configuration, la peche de {@link FishingTable}.
 * L'onglet ne peut donc pas annoncer autre chose que ce qui est donne. Les gains
 * de PalaJobs (alchimie, God Villager, spawner) sont affiches convertis, a leur
 * valeur moyenne : {@link MinerXpEvents#convert} tire la fraction au sort.
 *
 * <p>Le niveau d'une ligne est le premier ou le gain devient possible : le seuil
 * du gain lui-meme, ou le verrou de {@code data/jobs.json} sur le bloc, la graine,
 * la canne ou la machine qu'il demande, le plus haut des deux. Un verrou d'un autre metier
 * ne change pas le niveau : il est ecrit dans la bulle.
 *
 * <p>Les valeurs sont celles de base. Les bonus d'XP (ameliorations, amulettes)
 * s'appliquent a toutes de la meme facon, dans {@link JobApi#grant} ; le premier
 * titre de section donne celui du joueur.
 */
public final class JobXpGuide {

    /** Libelles des groupes de blocs du datapack ; les autres prennent le nom de leurs blocs. */
    private static final Map<String, String> TAG_TITLES = Map.of(
            "#minecraft:base_stone_overworld", "Pierre et roches de surface",
            "#dohy:mining_xp/low", "Minerais d'autres mods (communs)",
            "#dohy:mining_xp/mid", "Minerais d'autres mods (moyens)",
            "#dohy:mining_xp/high", "Minerais d'autres mods (rares)",
            "#dohy:mining_xp/elite", "Minerais d'autres mods (tres rares)");

    /** Noms des blocs d'un groupe montres dans la bulle, avant le « et N autres ». */
    private static final int TAG_NAMES = 5;

    private JobXpGuide() {
    }

    public static List<JobDetailView.Row> rows(ServerPlayer player, Job job) {
        List<Section> sections = switch (job) {
            case MINER -> miner();
            case HUNTER -> hunter();
            case FARMER -> farmer();
            case ALCHEMIST -> alchemist();
        };
        sections.add(rewards(job));

        int level = JobApi.level(player, job);
        long bonus = Math.round((UpgradeApi.bonus(player, UpgradeApi.JOB_XP_BONUS)
                + Amulets.xpBonus(player, job)) * 100.0d);
        List<JobDetailView.Row> rows = new ArrayList<>();
        boolean first = true;
        for (Section section : sections) {
            if (section.entries.isEmpty()) {
                continue;
            }
            List<String> notes = new ArrayList<>();
            String badge = "";
            if (first) {
                notes.add("Valeurs de base, avant les bonus d'XP");
                if (bonus > 0) {
                    badge = "BONUS +" + bonus + " %";
                    notes.add("Ton bonus actuel : +" + bonus + " % sur chaque gain");
                }
                first = false;
            }
            notes.addAll(section.notes);
            rows.add(new JobDetailView.Row(ItemStack.EMPTY, section.title, badge, 0, JobDetailView.HEADER, notes));

            // Du plus accessible au plus lointain, puis du plus gros gain au plus petit.
            section.entries.sort((a, b) -> a.level() != b.level() ? Integer.compare(a.level(), b.level())
                    : Double.compare(b.sort(), a.sort()));
            for (Entry entry : section.entries) {
                boolean open = level >= entry.level();
                List<String> lines = new ArrayList<>();
                lines.add(open ? "Disponible a ton niveau" : "Il manque " + (entry.level() - level) + " niveau(x)");
                lines.addAll(entry.lines());
                rows.add(new JobDetailView.Row(entry.icon(), entry.title(), entry.detail(), entry.level(),
                        open ? JobDetailView.ACQUIRED : JobDetailView.LOCKED, lines));
            }
        }
        return rows;
    }

    // -- Les quatre metiers ---------------------------------------------------------------------

    private static List<Section> miner() {
        Section blocks = new Section("BLOCS MINES", "Un bloc pose par un joueur ne rapporte rien");
        blocks(blocks, Job.MINER, "par bloc", Items.STONE);

        Section cobble = new Section("COBBLEBREAKER", "Prises a la main dans la machine, par clic ou maj-clic",
                "Un entonnoir qui vide la machine ne donne pas d'XP");
        int machine = Math.max(MinerXpEvents.PARTICLE_XP_LEVEL,
                gate(Job.MINER, JobGate.of(PalaMachines.COBBLEBREAKER_BLOCK.get().asItem())));
        for (Map.Entry<Item, Double> particle : MinerXpEvents.particles()) {
            double xp = MinerXpEvents.exact(particle.getValue());
            ItemStack stack = new ItemStack(particle.getKey());
            cobble.add(stack, MenuView.text(stack.getHoverName()), plus(xp), "par particule", machine, xp);
        }

        Section gestures = new Section("AUTRES GESTES");
        double obsidian = MinerXpEvents.exact(MinerXpEvents.PALAJOBS_XP);
        gestures.add(new ItemStack(Items.OBSIDIAN), "Obsidienne sous l'effet Durete", plus(obsidian), "par bloc", 0,
                obsidian, "Sous l'effet Durete, sans toucher de soie");
        double god = MinerXpEvents.exact(GodVillager.PALAJOBS_XP);
        gestures.add(new ItemStack(PalaItems.ENDIUM_NUGGET.get()), "Echange avec le God Villager", plus(god),
                "par echange", GodVillager.REQUIRED_LEVEL, god,
                "Apparait tres rarement pres d'un Mineur niveau " + GodVillager.REQUIRED_LEVEL,
                "64 blocs de paladium contre une pepite d'endium");
        return sections(blocks, cobble, gestures);
    }

    private static List<Section> hunter() {
        Section creatures = new Section("CREATURES TUEES");
        entities(creatures, Job.HUNTER, "par creature", Items.BONE);

        // Le niveau d'un poisson tient compte de la canne en paladium, la canne d'entree : sans
        // elle, pas de mini-jeu. La canne en endium, de fin de partie, est dite dans les bulles.
        int paladiumRod = gate(Job.HUNTER, JobGate.of(PalaHunter.PALADIUM_FISHING_ROD.get()));
        int endiumRod = gate(Job.HUNTER, JobGate.of(PalaHunter.ENDIUM_FISHING_ROD.get()));
        Section fishing = new Section("PECHE AU MINI-JEU",
                paladiumRod > 0 ? "Le niveau tient compte de la canne en paladium (Chasseur " + paladiumRod + ")"
                        : "La canne en paladium ne demande pas de niveau",
                endiumRod > 0 ? "La canne en endium demande Chasseur " + endiumRod
                        : "La canne en endium ne demande pas de niveau",
                "La canne en bois ne lance pas le mini-jeu et ne rapporte rien",
                "La zone touchee multiplie la prise et son XP (x1 a x5)",
                "Un poisson au-dessus de ton niveau ne mord pas");
        for (Map.Entry<String, Long> fish : FishingTable.XP.entrySet()) {
            Item item = item(fish.getKey());
            if (item == null || fish.getValue() <= 0) {
                continue;
            }
            int own = FishingTable.LEVEL.getOrDefault(fish.getKey(), 0);
            int shown = Math.max(own, paladiumRod);
            List<String> lines = new ArrayList<>();
            if (own > 0) {
                lines.add("Mord a partir du niveau " + own);
            }
            if (Math.max(own, endiumRod) < shown) {
                lines.add("Avec la canne en endium : des le niveau " + Math.max(own, endiumRod));
            }
            ItemStack stack = new ItemStack(item);
            fishing.add(stack, MenuView.text(stack.getHoverName()), plus(fish.getValue()), "par prise", shown,
                    fish.getValue(), lines);
        }

        Section gestures = new Section("AUTRES GESTES");
        double spawner = MinerXpEvents.exact(SpawnerEvents.SPAWNER_XP);
        gestures.add(new ItemStack(Items.SPAWNER), "Spawner casse", plus(spawner), "par spawner",
                SpawnerEvents.SPAWNER_XP_LEVEL, spawner, "Un spawner de monstres, casse par toi");
        return sections(creatures, fishing, gestures);
    }

    private static List<Section> farmer() {
        Section crops = new Section("RECOLTES", "Une culture ne compte qu'une fois mure",
                "Un bloc pose par un joueur ne rapporte rien");
        blocks(crops, Job.FARMER, "par recolte", Items.WHEAT);

        Section breeding = new Section("ELEVAGE");
        entities(breeding, Job.FARMER, "par animal", Items.LEATHER);

        Section machines = new Section("MACHINES ET OGM",
                "Moulin et machine a pain : a la main seulement, l'automate ne donne pas d'XP");
        AgriConfig.Config agri = AgriConfig.config();
        long mill = agri.machines().millXp();
        if (mill > 0) {
            ItemStack stack = new ItemStack(DoHyAgri.FLOUR_MILL_ITEM.get());
            machines.add(stack, MenuView.text(stack.getHoverName()), plus(mill), "par farine moulue",
                    gate(Job.FARMER, JobGate.of(stack.getItem())), mill);
        }
        int oven = gate(Job.FARMER, JobGate.of(DoHyAgri.BREAD_MACHINE_ITEM.get()));
        for (AgriConfig.Batch batch : agri.breads()) {
            Bread bread = Bread.byKey(batch.id());
            if (bread == null || batch.xp() <= 0) {
                continue;
            }
            ItemStack stack = new ItemStack(DoHyAgri.bread(bread));
            machines.add(stack, MenuView.text(stack.getHoverName()), plus(batch.xp()), "par fournee",
                    Math.max(batch.minLevel(), oven), batch.xp(),
                    "A la machine a pain : " + batch.count() + " pain(s) par fournee");
        }
        AgriConfig.Gmo gmo = agri.gmo();
        int lab = Math.max(gmo.minLevel(), gate(Job.FARMER, JobGate.of(DoHyAgri.GMO_MACHINE_ITEM.get())));
        ItemStack gmoIcon = new ItemStack(DoHyAgri.GMO_MACHINE_ITEM.get());
        if (gmo.discoveryXp() > 0) {
            machines.add(gmoIcon, "Recette OGM decouverte", plus(gmo.discoveryXp()), "par recette", lab,
                    gmo.discoveryXp(), "Une fois par recette trouvee a la machine OGM");
        }
        if (gmo.craftXp() > 0) {
            machines.add(gmoIcon, "Graines OGM fabriquees", plus(gmo.craftXp()), "par fabrication", lab,
                    gmo.craftXp(), gmo.seedsPerCraft() + " graines par fabrication reussie");
        }
        return sections(crops, breeding, machines);
    }

    private static List<Section> alchemist() {
        Section harvest = new Section("RECOLTES ET BLOCS", "Une culture ne compte qu'une fois mure",
                "Un bloc pose par un joueur ne rapporte rien");
        blocks(harvest, Job.ALCHEMIST, "par bloc", Items.NETHER_WART);
        double log = MinerXpEvents.exact(AlchemistEvents.LOG_XP);
        harvest.add(new ItemStack(PalaWoods.JACARANDA_LOG.get()), "Buches de jacaranda, judeecercis, erable",
                plus(log), "par buche", 0, log, "Rien pour l'ostrya");

        Section creatures = new Section("CREATURES TUEES");
        entities(creatures, Job.ALCHEMIST, "par creature", Items.BLAZE_POWDER);

        Section brewing = new Section("ALAMBIC ET MACHINES");
        long potion = sources(Job.ALCHEMIST).actions().getOrDefault("potion_brewed", 0L);
        if (potion > 0) {
            brewing.add(new ItemStack(Items.BREWING_STAND), "Potion terminee a l'alambic", plus(potion), "par potion",
                    0, potion, "Chaque potion qui sort de l'alambic");
        }

        ItemStack extractor = new ItemStack(PalaAlchemist.EXTRACTOR_ITEM.get());
        int extractorGate = gate(Job.ALCHEMIST, JobGate.of(extractor.getItem()));
        for (Sap sap : Sap.values()) {
            if (sap.extractionXp() <= 0) {
                continue;
            }
            double xp = MinerXpEvents.exact(sap.extractionXp());
            brewing.add(extractor, MenuView.text(Component.literal("Extracteur : ").append(sap.displayName())),
                    plus(xp), "par fiole pleine", Math.max(sap.extractionLevel(), extractorGate), xp,
                    "Une fiole en partie remplie en donne la part");
        }

        for (PortalTier tier : PortalTier.values()) {
            if (tier.xp() <= 0) {
                continue;
            }
            double xp = MinerXpEvents.exact(tier.xp());
            ItemStack output = new ItemStack(tier.output());
            int flasks = Math.max(1, tier.required() / Sap.FLASK_UNITS);
            brewing.add(output, MenuView.text(Component.literal("Portail : ").append(output.getHoverName())),
                    plus(xp), "par objet rendu", tier.xpLevel(), xp,
                    MenuView.text(Component.literal(flasks + " fiole(s) par objet : ").append(tier.sap().displayName())));
        }

        int cauldron = gate(Job.ALCHEMIST, JobGate.of(PalaAlchemist.CAULDRON_CORE_ITEM.get()));
        Map<Double, MutableComponent> flowers = new TreeMap<>();
        for (Map.Entry<Item, Double> flower : CauldronCoreBlockEntity.FLOWER_XP.entrySet()) {
            MutableComponent names = flowers.computeIfAbsent(flower.getValue(), ignored -> Component.empty());
            if (!names.getSiblings().isEmpty()) {
                names.append(", ");
            }
            names.append(new ItemStack(flower.getKey()).getHoverName());
        }
        if (!flowers.isEmpty()) {
            List<String> lines = new ArrayList<>();
            double low = Double.MAX_VALUE;
            double high = 0.0d;
            for (Map.Entry<Double, MutableComponent> group : flowers.entrySet()) {
                double xp = MinerXpEvents.exact(group.getKey());
                low = Math.min(low, xp);
                high = Math.max(high, xp);
                lines.add(MenuView.text(Component.literal(plus(xp) + " : ").append(group.getValue())));
            }
            if (cauldron > 0) {
                lines.add("Le coeur du chaudron demande le niveau " + cauldron);
            }
            brewing.add(new ItemStack(Items.POPPY), "Fleurs jetees dans le chaudron",
                    "+" + xp(low) + " a " + xp(high) + " XP", "par fleur",
                    Math.max(CauldronCoreBlockEntity.FLOWER_XP_LEVEL, cauldron), high, lines);
        }
        double paladiumFlower = MinerXpEvents.exact(CauldronCoreBlockEntity.PALADIUM_FLOWER_XP);
        ItemStack flower = new ItemStack(PalaAlchemist.PALADIUM_FLOWER_ITEM.get());
        brewing.add(flower, MenuView.text(flower.getHoverName()), plus(paladiumFlower), "par fleur",
                Math.max(CauldronCoreBlockEntity.PALADIUM_FLOWER_XP_LEVEL, cauldron), paladiumFlower,
                "Jetee dans le chaudron");

        double lightning = MinerXpEvents.exact(AlchemistEvents.LIGHTNING_XP);
        ItemStack bolt = new ItemStack(PalaAlchemist.LIGHTNING_POTION.get());
        brewing.add(bolt, MenuView.text(bolt.getHoverName()), plus(lightning), "par potion", 0, lightning,
                "Sous la pluie, casque en fer ou en or, fiole vide en main",
                "La foudre tombe en moyenne au bout de 40 secondes");
        return sections(harvest, creatures, brewing);
    }

    /**
     * Ce que tous les metiers partagent : leur donjon, leur gardien, et les objets d'XP. Tout y est
     * au niveau 0 ; le rang ({@code sort}) les garde dans cet ordre plutot que par gain.
     */
    private static Section rewards(Job job) {
        Section section = new Section("DONJONS ET RECOMPENSES");
        for (Dungeon dungeon : Dungeon.values()) {
            if (dungeon.job() != job) {
                continue;
            }
            long low = Long.MAX_VALUE;
            long high = 0L;
            StringBuilder modes = new StringBuilder();
            for (DungeonMode mode : DungeonMode.values()) {
                low = Math.min(low, DungeonManager.jobXp(1, mode));
                high = Math.max(high, DungeonManager.jobXp(DungeonManager.MAX_PALIER, mode));
                modes.append(modes.isEmpty() ? "" : ", ").append(mode.label()).append(" x").append(xp(mode.rewards()));
            }
            long base = DungeonManager.jobXp(0, DungeonMode.NORMAL);
            long step = DungeonManager.jobXp(1, DungeonMode.NORMAL) - base;
            section.add(new ItemStack(Items.WITHER_SKELETON_SKULL), "Donjon " + dungeon.display() + " reussi",
                    "+" + MoneyFormat.exact(low) + " a " + MoneyFormat.exact(high) + " XP", "par palier", 0, 6,
                    MoneyFormat.exact(base) + " XP, plus " + step + " par palier (1 a " + DungeonManager.MAX_PALIER + ")",
                    "Selon le mode : " + modes);
        }

        long bossLow = JobBossApi.xpReward(1);
        long bossHigh = JobBossApi.xpReward(JobBosses.MAX_LEVEL);
        section.add(new ItemStack(JobBosses.icon(job)), "Gardien : " + JobBosses.name(job),
                "+" + MoneyFormat.exact(bossLow) + " a " + MoneyFormat.exact(bossHigh) + " XP", "par victoire", 0,
                5, MoneyFormat.exact(bossLow) + " XP par niveau du gardien (1 a " + JobBosses.MAX_LEVEL + ")",
                "Appele par une offrande a l'autel du spawn",
                "Repos de " + JobBossApi.REST / 3_600_000L + " h apres chaque victoire");

        String candySource = "Dans les bonbons a deballer : recompense du niveau 95, boss de donjon des le palier 50";
        ItemStack candy = new ItemStack(candy(job));
        section.add(candy, MenuView.text(candy.getHoverName()), plus(CandyItem.XP), "par bonbon", 0, 4,
                candySource, "Accroupi : toute la pile d'un coup");
        ItemStack rainbow = new ItemStack(PalaDungeon.CANDY_RAINBOW.get());
        section.add(rainbow, MenuView.text(rainbow.getHoverName()), plus(CandyItem.XP), "aux quatre metiers", 0,
                3, candySource, "Un bonbon a deballer sur dix");

        ItemStack bottle = new ItemStack(bottle(job));
        section.add(bottle, MenuView.text(bottle.getHoverName()), plus(JobBottleItem.DEFAULT_XP), "par bouteille", 0,
                2, "Dans le butin des boss de donjon",
                "Celles du marche noir en contiennent davantage");

        ItemStack stone = new ItemStack(PalaLegendary.JOBS.get());
        section.add(stone, MenuView.text(stone.getHoverName()),
                "+" + MoneyFormat.exact(LegendaryStones.JOBS_XP_MIN) + " a "
                        + MoneyFormat.exact(LegendaryStones.JOBS_XP_MAX) + " XP", "a un metier", 0,
                1, "Le metier est tire au hasard parmi les quatre");
        return section;
    }

    // -- Lecture des sources --------------------------------------------------------------------

    private static JobProgression.Sources sources(Job job) {
        return JobProgression.config().def(job).sources()
                .orElse(new JobProgression.Sources(Map.of(), Map.of(), Map.of()));
    }

    /** Les blocs du datapack : un par identifiant, un par groupe non vide. */
    private static void blocks(Section section, Job job, String unit, Item fallback) {
        Map<String, Long> table = sources(job).blocks();
        for (Map.Entry<String, Long> source : table.entrySet()) {
            long xp = source.getValue();
            if (xp <= 0) {
                continue;
            }
            String key = source.getKey();
            if (key.startsWith("#")) {
                group(section, table, key, xp, unit, fallback);
                continue;
            }
            Identifier id = JobConfig.parse(key);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            JobConfig.Unlock unlock = JobGate.of(block.defaultBlockState());
            if (unlock == null && block.asItem() != Items.AIR) {
                unlock = JobGate.of(block.asItem());
            }
            int level = gate(job, unlock);
            Job owner = unlock == null ? null : Job.byKey(unlock.job());
            if (owner != null && owner != job) {
                lines.add("Demande " + owner.display() + " niveau " + unlock.level());
            }
            section.add(icon(block, fallback), MenuView.text(block.getName()), plus(xp), unit, level, xp, lines);
        }
    }

    /**
     * Un groupe de blocs ({@code #tag}) : ses blocs sont lus au moment de
     * l'ouverture, comme dans {@code JobXpEvents}. Un bloc qui a sa propre ligne
     * en est retire (elle passe avant le groupe), un groupe vide n'est pas montre.
     */
    private static void group(Section section, Map<String, Long> table, String key, long xp, String unit,
                              Item fallback) {
        Identifier tagId = JobConfig.parse(key.substring(1));
        if (tagId == null) {
            return;
        }
        TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(), tagId);
        List<Block> members = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().is(tag)
                    && !table.containsKey(BuiltInRegistries.BLOCK.getKey(block).toString())) {
                members.add(block);
            }
        }
        if (members.isEmpty()) {
            return;
        }
        MutableComponent names = Component.empty();
        for (int index = 0; index < Math.min(TAG_NAMES, members.size()); index++) {
            if (index > 0) {
                names.append(", ");
            }
            names.append(members.get(index).getName());
        }
        if (members.size() > TAG_NAMES) {
            names.append(" et " + (members.size() - TAG_NAMES) + " autre(s)");
        }
        String title = TAG_TITLES.get(key);
        section.add(icon(members.get(0), fallback), title != null ? title : MenuView.text(names), plus(xp), unit, 0,
                xp, MenuView.text(Component.literal("Comprend : ").append(names)));
    }

    private static void entities(Section section, Job job, String unit, Item fallback) {
        for (Map.Entry<String, Long> source : sources(job).entities().entrySet()) {
            long xp = source.getValue();
            Identifier id = JobConfig.parse(source.getKey());
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null || xp <= 0) {
                continue;
            }
            Item egg = BuiltInRegistries.ITEM.getOptional(
                    Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg")).orElse(fallback);
            section.add(new ItemStack(egg), MenuView.text(type.getDescription()), plus(xp), unit, 0, xp);
        }
    }

    /** Le niveau d'un verrou de ce metier ; 0 sans verrou ou pour le verrou d'un autre metier. */
    private static int gate(Job job, JobConfig.Unlock unlock) {
        if (unlock == null) {
            return 0;
        }
        return Job.byKey(unlock.job()) == job ? unlock.level() : 0;
    }

    private static ItemStack icon(Block block, Item fallback) {
        Item item = block.asItem();
        return new ItemStack(item == Items.AIR ? fallback : item);
    }

    private static Item item(String raw) {
        Identifier id = JobConfig.parse(raw);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static Item candy(Job job) {
        return switch (job) {
            case FARMER -> PalaDungeon.CANDY_GREEN.get();
            case MINER -> PalaDungeon.CANDY_YELLOW.get();
            case HUNTER -> PalaDungeon.CANDY_BLUE.get();
            case ALCHEMIST -> PalaDungeon.CANDY_PURPLE.get();
        };
    }

    private static Item bottle(Job job) {
        return switch (job) {
            case FARMER -> PalaDungeon.FARMER_BOTTLE.get();
            case MINER -> PalaDungeon.MINER_BOTTLE.get();
            case HUNTER -> PalaDungeon.HUNTER_BOTTLE.get();
            case ALCHEMIST -> PalaDungeon.ALCHEMIST_BOTTLE.get();
        };
    }

    // -- Mise en forme --------------------------------------------------------------------------

    private static String plus(double value) {
        return "+" + xp(value) + " XP";
    }

    /**
     * Un gain lisible : entier au-dessus de 10, une decimale au-dessus de 1, deux
     * en dessous — les gains de PalaJobs convertis descendent a 0,27.
     */
    static String xp(double value) {
        if (value >= 10.0d) {
            return MoneyFormat.exact(Math.round(value));
        }
        String text = String.format(Locale.ROOT, value >= 1.0d ? "%.1f" : "%.2f", value).replace('.', ',');
        while (text.contains(",") && (text.endsWith("0") || text.endsWith(","))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static List<Section> sections(Section... sections) {
        return new ArrayList<>(List.of(sections));
    }

    /** Une section de l'onglet : un titre, des remarques pour sa bulle, ses lignes. */
    private static final class Section {
        private final String title;
        private final List<String> notes;
        private final List<Entry> entries = new ArrayList<>();

        Section(String title, String... notes) {
            this.title = title;
            this.notes = List.of(notes);
        }

        void add(ItemStack icon, String title, String amount, String unit, int level, double sort, String... lines) {
            add(icon, title, amount, unit, level, sort, List.of(lines));
        }

        void add(ItemStack icon, String title, String amount, String unit, int level, double sort,
                 List<String> lines) {
            entries.add(new Entry(icon, title, unit.isEmpty() ? amount : amount + " " + unit, level, sort,
                    List.copyOf(lines)));
        }
    }

    /** Une ligne : {@code sort} range les gains d'un meme niveau, du plus gros au plus petit. */
    private record Entry(ItemStack icon, String title, String detail, int level, double sort, List<String> lines) {
    }
}
