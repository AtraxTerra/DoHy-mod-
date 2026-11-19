package fr.atraxterra.dohy.palamod.lucky;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Les outils communs des lucky blocks saisonniers ({@code monthly/}) : PalaMod y avait un
 * ecran de dialogue ({@code CustomDialog}), un compte a rebours en haut de l'ecran
 * ({@code startTimedHeliosEvent}), des PNJ ({@code EntityPlayerNPC}) et des tickables
 * ({@code ATickable}).
 *
 * <p>Ici :
 * <ul>
 *   <li>le dialogue s'affiche dans le chat, chaque reponse est un lien a cliquer
 *       ({@code /dohy_lucky_reponse}) ;</li>
 *   <li>le compte a rebours est une barre de boss, propre au joueur ;</li>
 *   <li>les PNJ sont des creatures du jeu, immobiles, invulnerables et nommees : un clic
 *       droit sur elles ouvre leur dialogue ;</li>
 *   <li>les defis tournent au tic du serveur, et s'arretent si le joueur part.</li>
 * </ul>
 * Rien de tout cela n'est sauve : un defi en cours se perd au redemarrage, comme dans
 * PalaMod.
 */
@EventBusSubscriber
public final class SeasonKit {

    /** Ce que rend une etape de defi. */
    public enum Outcome { GOING, WON, LOST }

    /** Une etape de defi, appelee a chaque tic : {@code age} compte les tics depuis le debut. */
    @FunctionalInterface
    public interface Step {
        Outcome tick(ServerPlayer player, int age);
    }

    private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
    private static final List<Challenge> ADDED = new ArrayList<>();
    private static final Map<UUID, Question> QUESTIONS = new HashMap<>();
    private static final Map<UUID, Npc> NPCS = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> LOOT = new HashMap<>();
    private static int nextToken = 1;

    private SeasonKit() {
    }

    // -- Messages et objets ---------------------------------------------------------------------

    public static void say(ServerPlayer player, String text) {
        LuckyWorld.tell(player, text, ChatFormatting.YELLOW);
    }

    public static void win(ServerPlayer player, String text) {
        LuckyWorld.tell(player, text, ChatFormatting.GREEN);
    }

    public static void fail(ServerPlayer player, String text) {
        LuckyWorld.tell(player, text, ChatFormatting.RED);
        LuckyWorld.sound((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), "entity.villager.no",
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Une replique de PNJ : « [Nom] texte ». */
    public static void speak(ServerPlayer player, String speaker, String text) {
        player.sendSystemMessage(Component.literal("[" + speaker + "] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    /** {@code InventoryUtils.giveOrDropitems} : dans l'inventaire, sinon aux pieds. */
    public static void give(ServerPlayer player, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                player.drop(copy, false);
            }
        }
    }

    /**
     * Un objet par son nom ({@code palamod:green_paladium_ingot}) : les objets de PalaMod sont
     * ranges dans une dizaine de registres, le nom les trouve tous. {@code tools/lucky_assets.py
     * --check} verifie que chaque nom ecrit ainsi existe.
     */
    public static ItemStack stack(String id, int count) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse(id)), count);
    }

    /**
     * Un bloc du jeu par son nom ({@code white_concrete}) : les blocs de couleur n'ont plus de
     * constante chacun dans {@code Blocks}.
     */
    public static BlockState block(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.parse(id.contains(":") ? id : "minecraft:" + id)).defaultBlockState();
    }

    /** Un objet renomme, avec ses lignes de description. */
    public static ItemStack named(ItemStack stack, String name, String... lore) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    /** Le sol sous une colonne : la premiere case libre au-dessus du relief. */
    public static BlockPos ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Une case au hasard autour du joueur, au sol, entre {@code min} et {@code max} blocs. */
    public static BlockPos around(ServerPlayer player, ServerLevel level, int min, int max) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0d;
        double distance = min + random.nextDouble() * Math.max(0, max - min);
        return ground(level, (int) Math.floor(player.getX() + Math.cos(angle) * distance),
                (int) Math.floor(player.getZ() + Math.sin(angle) * distance));
    }

    // -- Structures -----------------------------------------------------------------------------

    /** Remplit un pave, de {@code from} a {@code to} compris, la ou le joueur peut construire. */
    public static void fill(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            LuckyWorld.place(player, level, pos.immutable(), state);
        }
    }

    /** Les murs, le sol et le plafond d'un pave, vide dedans. */
    public static void shell(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to, BlockState wall) {
        int x0 = Math.min(from.getX(), to.getX());
        int x1 = Math.max(from.getX(), to.getX());
        int y0 = Math.min(from.getY(), to.getY());
        int y1 = Math.max(from.getY(), to.getY());
        int z0 = Math.min(from.getZ(), to.getZ());
        int z1 = Math.max(from.getZ(), to.getZ());
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            boolean edge = pos.getX() == x0 || pos.getX() == x1 || pos.getY() == y0 || pos.getY() == y1
                    || pos.getZ() == z0 || pos.getZ() == z1;
            LuckyWorld.place(player, level, pos.immutable(), edge ? wall : Blocks.AIR.defaultBlockState());
        }
    }

    /** Un coffre garni ; les objets se rangent dans des cases au hasard. */
    public static boolean chest(ServerPlayer player, ServerLevel level, BlockPos pos, List<ItemStack> loot) {
        if (!LuckyWorld.place(player, level, pos, Blocks.CHEST.defaultBlockState())
                || !(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return false;
        }
        RandomSource random = level.getRandom();
        for (ItemStack stack : loot) {
            int slot = random.nextInt(chest.getContainerSize());
            for (int tries = 0; tries < chest.getContainerSize() && !chest.getItem(slot).isEmpty(); tries++) {
                slot = (slot + 1) % chest.getContainerSize();
            }
            if (chest.getItem(slot).isEmpty()) {
                chest.setItem(slot, stack.copy());
            } else {
                LuckyWorld.drop(level, pos.above(), stack);
            }
        }
        return true;
    }

    // -- Salle a code ({@code BiicodeStructure}) -------------------------------------------------

    private static final Map<UUID, CodeRoom> ROOMS = new HashMap<>();

    /**
     * La salle a code des coffres du mois ({@code BiicodeStructure}) : une piece de 6 blocs de
     * bedrock autour du joueur, un coffre au milieu, trois chiffres devant le mur du nord, a
     * hauteur des yeux, et une lumiere invisible au plafond. Le code tape dans le chat ouvre la
     * porte ; au bout de cinq minutes la piece disparait de toute facon. Le coffre reste.
     */
    public static void codeRoom(ServerPlayer player, ServerLevel level, String title, int[] code,
                                List<ItemStack> rewards) {
        BlockPos center = player.blockPosition();
        if (!roomFits(player, level, center)) {
            center = ground(level, center.getX(), center.getZ());
        }
        if (!roomFits(player, level, center)) {
            fail(player, "Tu ne peux pas faire apparaitre la structure ici.");
            return;
        }
        BlockPos from = center.offset(-3, -1, -3);
        BlockPos to = center.offset(2, 4, 2);
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            boolean edge = pos.getX() == from.getX() || pos.getX() == to.getX() || pos.getY() == from.getY()
                    || pos.getY() == to.getY() || pos.getZ() == from.getZ() || pos.getZ() == to.getZ();
            if (edge && level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3)) {
                walls.add(pos.immutable());
            }
        }
        BlockPos light = center.offset(0, 3, -1);
        if (level.setBlock(light, Blocks.LIGHT.defaultBlockState(), 3)) {
            walls.add(light);
        }
        chest(player, level, center.offset(0, 0, 1), rewards);
        List<Entity> marks = new ArrayList<>();
        for (int index = 0; index < code.length; index++) {
            // Le nom flotte au-dessus du support invisible (haut de 2 blocs) : pose un peu sous le
            // sol, le chiffre se lit a hauteur des yeux, pas dans le plafond.
            var mark = LuckyCreatures.marker(level, center.getX() - 1 + index + 0.5d, center.getY() - 0.3d,
                    center.getZ() - 2 + 0.7d);
            if (mark != null) {
                mark.setCustomName(Component.literal("Chiffre " + (index + 1) + " : " + code[index])
                        .withStyle(ChatFormatting.AQUA));
                level.addFreshEntity(mark);
                marks.add(mark);
            }
        }
        player.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
        StringBuilder digits = new StringBuilder();
        for (int digit : code) {
            digits.append(digit);
        }
        CodeRoom room = new CodeRoom(digits.toString(), walls, marks, center.offset(0, -1, -3), level);
        ROOMS.put(player.getUUID(), room);
        say(player, "Tu es enferme ! Trouve le code et tape-le dans le chat pour ouvrir la porte.");
        timer(player, title, 300, p -> closeRoom(p.getUUID()));
    }

    private static boolean roomFits(ServerPlayer player, ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(2, 4, 2))) {
            BlockState state = level.getBlockState(pos);
            boolean inside = pos.getY() > center.getY() - 1;
            if (state.hasBlockEntity() || !LuckyWorld.canEdit(player, level, pos.immutable())
                    || inside && !state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static void closeRoom(UUID player) {
        CodeRoom room = ROOMS.remove(player);
        if (room == null) {
            return;
        }
        for (BlockPos pos : room.walls) {
            BlockState state = room.level.getBlockState(pos);
            if (state.is(Blocks.BEDROCK) || state.is(Blocks.LIGHT)) {
                room.level.removeBlock(pos, false);
            }
        }
        room.marks.forEach(Entity::discard);
    }

    @SubscribeEvent
    public static void onChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        CodeRoom room = ROOMS.get(event.getPlayer().getUUID());
        if (room == null || !event.getRawText().replace(" ", "").equals(room.code)) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos door = room.door.above(dy);
            if (room.level.getBlockState(door).is(Blocks.BEDROCK)) {
                room.level.removeBlock(door, false);
            }
        }
        win(player, "Code bon : la porte s'ouvre !");
        room.level.playSound(null, room.door, net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private record CodeRoom(String code, List<BlockPos> walls, List<Entity> marks, BlockPos door, ServerLevel level) {
    }

    // -- Creatures ------------------------------------------------------------------------------

    /** Une creature nommee, ajoutee au monde ; {@code null} si elle ne peut pas naitre. */
    public static <T extends Mob> T mob(ServerLevel level, EntityType<T> type, Vec3 at, String name) {
        T mob = LuckyWorld.create(level, type, at.x, at.y, at.z);
        if (mob == null) {
            return null;
        }
        if (name != null) {
            mob.setCustomName(Component.literal(name));
            mob.setCustomNameVisible(true);
        }
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    /** Change une caracteristique de base (vie, degats, vitesse...). */
    public static void stat(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                            double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
        if (attribute == Attributes.MAX_HEALTH) {
            entity.setHealth((float) value);
        }
    }

    /** Ce que lachera la creature a sa mort, en plus de son butin. */
    public static void loot(Entity entity, ItemStack... stacks) {
        List<ItemStack> list = LOOT.computeIfAbsent(entity.getUUID(), uuid -> new ArrayList<>());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        List<ItemStack> stacks = LOOT.remove(event.getEntity().getUUID());
        if (stacks == null || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        for (ItemStack stack : stacks) {
            event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY() + 0.3d, dead.getZ(), stack));
        }
    }

    // -- Defis et compte a rebours --------------------------------------------------------------

    /**
     * Un defi avec son compte a rebours. {@code step} tourne a chaque tic ; s'il rend
     * {@link Outcome#WON} ou {@link Outcome#LOST} le defi s'arrete. Le temps ecoule, le defi
     * est gagne si {@code surviveToWin}, perdu sinon. Un seul defi a la fois par joueur : le
     * nouveau remplace l'ancien, qui est perdu.
     */
    public static void challenge(ServerPlayer player, String title, int seconds, boolean surviveToWin, Step step,
                                 Consumer<ServerPlayer> onWin, Consumer<ServerPlayer> onLose) {
        Challenge challenge = new Challenge(player.getUUID(), title, Math.max(1, seconds) * 20, surviveToWin, step,
                onWin, onLose);
        synchronized (ADDED) {
            ADDED.add(challenge);
        }
    }

    /** Un simple compte a rebours, sans condition : {@code done} a la fin. */
    public static void timer(ServerPlayer player, String title, int seconds, Consumer<ServerPlayer> done) {
        challenge(player, title, seconds, true, (p, age) -> Outcome.GOING, done, p -> {
        });
    }

    /** Le joueur a-t-il un defi en cours ? */
    public static boolean busy(ServerPlayer player) {
        return CHALLENGES.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        synchronized (ADDED) {
            for (Challenge added : ADDED) {
                Challenge old = CHALLENGES.put(added.player, added);
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(added.player);
                if (old != null) {
                    old.bar.removeAllPlayers();
                    if (player != null) {
                        old.onLose.accept(player);
                    }
                }
                if (player != null) {
                    added.bar.addPlayer(player);
                }
            }
            ADDED.clear();
        }
        Iterator<Challenge> iterator = CHALLENGES.values().iterator();
        List<Runnable> endings = new ArrayList<>();
        while (iterator.hasNext()) {
            Challenge challenge = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(challenge.player);
            if (player == null) {
                challenge.bar.removeAllPlayers();
                iterator.remove();
                continue;
            }
            Outcome outcome;
            if (!player.isAlive()) {
                outcome = Outcome.LOST;
            } else {
                outcome = challenge.step.tick(player, challenge.age);
                challenge.age++;
                if (outcome == Outcome.GOING && challenge.age >= challenge.total) {
                    outcome = challenge.surviveToWin ? Outcome.WON : Outcome.LOST;
                }
            }
            if (outcome == Outcome.GOING) {
                if (challenge.age % 20 == 0) {
                    challenge.refresh();
                }
                continue;
            }
            challenge.bar.removeAllPlayers();
            iterator.remove();
            Consumer<ServerPlayer> end = outcome == Outcome.WON ? challenge.onWin : challenge.onLose;
            endings.add(() -> end.accept(player));
        }
        endings.forEach(Runnable::run);
    }

    private static final class Challenge {
        final UUID player;
        final String title;
        final int total;
        final boolean surviveToWin;
        final Step step;
        final Consumer<ServerPlayer> onWin;
        final Consumer<ServerPlayer> onLose;
        final ServerBossEvent bar;
        int age;

        Challenge(UUID player, String title, int total, boolean surviveToWin, Step step, Consumer<ServerPlayer> onWin,
                  Consumer<ServerPlayer> onLose) {
            this.player = player;
            this.title = title;
            this.total = total;
            this.surviveToWin = surviveToWin;
            this.step = step;
            this.onWin = onWin;
            this.onLose = onLose;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
            refresh();
        }

        void refresh() {
            int left = Math.max(0, (total - age + 19) / 20);
            bar.setName(Component.literal(title).withStyle(ChatFormatting.GOLD).append(Component.literal(
                    "  Temps restant : " + (left >= 60 ? left / 60 + " min " + String.format(java.util.Locale.ROOT,
                            "%02d", left % 60) + " s" : left + " s")).withStyle(ChatFormatting.WHITE)));
            bar.setProgress(Math.clamp((total - age) / (float) total, 0.0f, 1.0f));
        }
    }

    // -- Dialogues ------------------------------------------------------------------------------

    /**
     * Une question a choix ({@code CustomDialog}) : le texte, puis une ligne par reponse, a
     * cliquer. {@code answer} recoit le numero de la reponse, a partir de 0. Une nouvelle
     * question remplace la precedente.
     */
    public static void ask(ServerPlayer player, String speaker, String text, List<String> options,
                           BiConsumer<ServerPlayer, Integer> answer) {
        int token = nextToken++;
        QUESTIONS.put(player.getUUID(), new Question(token, answer));
        speak(player, speaker, text);
        for (int index = 0; index < options.size(); index++) {
            String command = "/dohy_lucky_reponse " + token + " " + index;
            MutableComponent line = Component.literal("  [" + (index + 1) + "] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(options.get(index)).withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(line.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Cliquer pour repondre")))));
        }
    }

    /** Une question a une seule reponse, pour continuer ({@code Recevoir la recompense}). */
    public static void next(ServerPlayer player, String speaker, String text, String button, Consumer<ServerPlayer> then) {
        ask(player, speaker, text, List.of(button), (p, index) -> then.accept(p));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dohy_lucky_reponse")
                .then(Commands.argument("question", IntegerArgumentType.integer(0))
                        .then(Commands.argument("reponse", IntegerArgumentType.integer(0)).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Question question = QUESTIONS.get(player.getUUID());
                            int token = IntegerArgumentType.getInteger(context, "question");
                            if (question == null || question.token != token) {
                                LuckyWorld.tell(player, "Cette question n'attend plus de reponse.", ChatFormatting.GRAY);
                                return 0;
                            }
                            QUESTIONS.remove(player.getUUID());
                            question.answer.accept(player, IntegerArgumentType.getInteger(context, "reponse"));
                            return 1;
                        }))));
    }

    private record Question(int token, BiConsumer<ServerPlayer, Integer> answer) {
    }

    // -- PNJ ------------------------------------------------------------------------------------

    /**
     * Un PNJ ({@code EntityPlayerNPC}) : une creature immobile et invulnerable, qui part au
     * bout de {@code seconds}. Un clic droit appelle {@code talk}.
     */
    public static <T extends Mob> T npc(ServerPlayer owner, ServerLevel level, EntityType<T> type, Vec3 at, String name,
                                        int seconds, BiConsumer<ServerPlayer, T> talk) {
        T mob = mob(level, type, at, name);
        if (mob == null) {
            return null;
        }
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, owner.getEyePosition());
        NPCS.put(mob.getUUID(), new Npc(owner.getUUID(), (player, entity) -> talk.accept(player, type.tryCast(entity))));
        UUID id = mob.getUUID();
        LuckyTasks.later(owner, seconds * 20, () -> {
            NPCS.remove(id);
            Entity entity = level.getEntity(id);
            if (entity != null) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0d,
                        entity.getZ(), 20, 0.3d, 0.5d, 0.3d, 0.02d);
                entity.discard();
            }
        });
        return mob;
    }

    /** Le PNJ s'en va ({@code setDead}). */
    public static void dismiss(Entity npc) {
        NPCS.remove(npc.getUUID());
        if (npc.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, npc.getX(), npc.getY() + 1.0d,
                    npc.getZ(), 20, 0.3d, 0.5d, 0.3d, 0.02d);
        }
        npc.discard();
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Npc npc = NPCS.get(event.getTarget().getUUID());
        if (npc == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.getUUID().equals(npc.owner)) {
            LuckyWorld.tell(player, "Ce n'est pas a toi que je parle.", ChatFormatting.GRAY);
            return;
        }
        npc.talk.accept(player, event.getTarget());
    }

    private record Npc(UUID owner, BiConsumer<ServerPlayer, Entity> talk) {
    }

    // -- Chutes adoucies ------------------------------------------------------------------------

    private static final java.util.Set<UUID> SOFT_FALL = new java.util.HashSet<>();

    /**
     * Pendant {@code ticks}, la prochaine chute laisse au moins un demi-coeur ({@code
     * almostCancelFallDamagePlayer}) : la peur, pas la mort.
     */
    public static void softFall(ServerPlayer player, int ticks) {
        SOFT_FALL.add(player.getUUID());
        LuckyTasks.later(player, ticks, () -> SOFT_FALL.remove(player.getUUID()));
    }

    @SubscribeEvent
    public static void onFall(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SOFT_FALL.contains(player.getUUID())
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            SOFT_FALL.remove(player.getUUID());
            event.setAmount(Math.min(event.getAmount(), Math.max(0.0f, player.getHealth() - 1.0f)));
        }
    }

    // -- Nettoyage ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            NPCS.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        QUESTIONS.remove(event.getEntity().getUUID());
        closeRoom(event.getEntity().getUUID());
        Challenge challenge = CHALLENGES.remove(event.getEntity().getUUID());
        if (challenge != null) {
            challenge.bar.removeAllPlayers();
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (UUID player : List.copyOf(ROOMS.keySet())) {
            closeRoom(player);
        }
        CHALLENGES.values().forEach(challenge -> challenge.bar.removeAllPlayers());
        CHALLENGES.clear();
        synchronized (ADDED) {
            ADDED.clear();
        }
        QUESTIONS.clear();
        NPCS.clear();
        LOOT.clear();
    }
}
