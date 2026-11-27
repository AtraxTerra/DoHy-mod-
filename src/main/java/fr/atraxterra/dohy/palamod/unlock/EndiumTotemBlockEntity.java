package fr.atraxterra.dohy.palamod.unlock;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le rituel d'endium, au totem ({@code TileEntityEndiumTotem}).
 *
 * <p>Releve dans le code : toutes les secondes, le totem verifie la structure
 * ({@link EndiumRitual}) et les sacrifies des quatre autels ; tant qu'elle
 * tient, la poudre etincelle et les sacrifies sont inscrits. Les zombies
 * tues dans la zone (+-10 blocs) par un inscrit font monter le compteur,
 * affiche aux joueurs ({@code PacketRituel}). A 25, l'animation : les
 * sacrifies meurent. Un clic sur le totem fait alors monter des flammes, le
 * totem disparait et laisse un coffre en bois avec un <b>nugget d'endium</b>
 * au milieu (case 13).
 *
 * <p><b>Pour le solo</b> : les zombies, en armure complete en paladium, sont
 * appeles par le totem lui-meme (quatre a la fois au plus, un toutes les trois
 * secondes) — de jour comme de nuit ; ils ne lachent pas leur armure. Un
 * autel garde son offrande tout le rituel (le joueur reste dessus, la pierre
 * reste posee) ; une offrande qui manque, une poudre ou un autel casse, ou
 * plus aucun joueur dans la zone, et le rituel est rompu : les zombies
 * repartent, le compteur revient a zero, les offrandes restent.
 */
public class EndiumTotemBlockEntity extends BlockEntity {

    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int SACRIFICED = 2;
    private static final int REVEALING = 3;

    /** Avant le premier zombie : cinq secondes de poudre qui etincelle. */
    private static final int FIRST_ZOMBIE = 100;
    /** Un zombie toutes les trois secondes, quatre debout a la fois au plus. */
    private static final int NEXT_ZOMBIE = 60;
    private static final int MAX_ALIVE = 4;
    /** Les flammes de la revelation ({@code BlockEndiumTotem$1}) : trois secondes. */
    private static final int REVEAL_TICKS = 60;
    /** Sorti de la zone (un zombie poursuivi, un pas de trop) : dix secondes pour y revenir. */
    private static final int ALONE_GRACE = 10;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(0xB00000, 1.6f);

    private int phase = IDLE;
    private int kills;
    private int timer;
    /** Les secondes passees sans aucun inscrit dans la zone. */
    private int alone;
    private final List<UUID> participants = new ArrayList<>();
    private ServerBossEvent bar;

    public EndiumTotemBlockEntity(BlockPos pos, BlockState state) {
        super(PalaUnlocks.ENDIUM_TOTEM_TYPE.get(), pos, state);
    }

    /** Un rituel en cours ou fini : les pierres des autels ne se reprennent plus. */
    boolean busy() {
        return phase != IDLE;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        switch (phase) {
            case IDLE -> {
                if (server.getGameTime() % 20L == 0L) {
                    tryStart(server);
                }
            }
            case RUNNING -> running(server);
            case SACRIFICED -> {
                if (server.getGameTime() % 10L == 0L) {
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, worldPosition.getX() + 0.5d,
                            worldPosition.getY() + 1.2d, worldPosition.getZ() + 0.5d, 3, 0.25d, 0.2d, 0.25d, 0.01d);
                }
            }
            case REVEALING -> revealing(server);
            default -> phase = IDLE;
        }
    }

    // -- Depart -------------------------------------------------------------------

    private void tryStart(ServerLevel server) {
        if (EndiumRitual.missingPowders(server, worldPosition) > 0
                || EndiumRitual.missingAltars(server, worldPosition) > 0) {
            return;
        }
        for (BlockPos offset : EndiumRitual.ALTARS) {
            if (!EndiumRitual.offered(server, worldPosition.offset(offset))) {
                return;
            }
        }
        List<ServerPlayer> present = server.getEntitiesOfClass(ServerPlayer.class,
                EndiumRitual.area(worldPosition), player -> player.isAlive() && !player.isSpectator());
        if (present.isEmpty()) {
            return;
        }
        phase = RUNNING;
        kills = 0;
        alone = 0;
        timer = FIRST_ZOMBIE;
        participants.clear();
        present.forEach(player -> participants.add(player.getUUID()));
        setPowders(server, true);
        bar().setVisible(true);
        present.forEach(bar()::addPlayer);
        updateBar();
        server.playSound(null, worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.8f, 0.6f);
        say(server, Component.translatable("block.palamod.endium_totem.start", EndiumRitual.KILLS)
                .withStyle(ChatFormatting.DARK_PURPLE));
        setChanged();
    }

    // -- Pendant ------------------------------------------------------------------

    private void running(ServerLevel server) {
        if (server.getGameTime() % 20L == 0L) {
            String broken = brokenReason(server);
            if (broken == null && players(server).isEmpty()) {
                // personne dans la zone : un delai de grace, annonce une fois
                alone++;
                if (alone >= ALONE_GRACE) {
                    broken = "block.palamod.endium_totem.broken_alone";
                } else if (alone == 1) {
                    say(server, Component.translatable("block.palamod.endium_totem.leaving", ALONE_GRACE)
                            .withStyle(ChatFormatting.GOLD));
                }
            } else if (broken == null) {
                alone = 0;
            }
            if (broken != null) {
                fail(server, broken);
                return;
            }
            for (ServerPlayer player : players(server)) {
                bar().addPlayer(player);
            }
        }
        if (kills >= EndiumRitual.KILLS) {
            sacrifice(server);
            return;
        }
        if (--timer <= 0) {
            timer = NEXT_ZOMBIE;
            List<Zombie> alive = summoned(server);
            if (alive.size() < MAX_ALIVE && kills + alive.size() < EndiumRitual.KILLS) {
                summon(server);
            }
        }
    }

    /** Ce qui rompt le rituel, ou {@code null}. */
    private String brokenReason(ServerLevel server) {
        if (EndiumRitual.missingPowders(server, worldPosition) > 0) {
            return "block.palamod.endium_totem.broken_powder";
        }
        if (EndiumRitual.missingAltars(server, worldPosition) > 0) {
            return "block.palamod.endium_totem.broken_altar";
        }
        for (BlockPos offset : EndiumRitual.ALTARS) {
            if (!EndiumRitual.offered(server, worldPosition.offset(offset))) {
                return "block.palamod.endium_totem.broken_offering";
            }
        }
        return null;
    }

    /** Les inscrits encore vivants dans la zone (un spectateur ne tient pas le rituel). */
    private List<ServerPlayer> players(ServerLevel server) {
        return server.getEntitiesOfClass(ServerPlayer.class, EndiumRitual.area(worldPosition),
                player -> player.isAlive() && !player.isSpectator() && participants.contains(player.getUUID()));
    }

    void onKill(ServerPlayer player) {
        if (phase != RUNNING || !participants.contains(player.getUUID())) {
            return;
        }
        kills++;
        updateBar();
        setChanged();
    }

    /** Un zombie en armure de paladium, dans un bras de la croix. */
    private void summon(ServerLevel server) {
        RandomSource random = server.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            int along = random.nextInt(9) - 4;
            int across = random.nextInt(3) - 1;
            boolean xArm = random.nextBoolean();
            BlockPos spot = worldPosition.offset(xArm ? along : across, 0, xArm ? across : along);
            if (spot.equals(worldPosition) || EndiumRitual.ALTARS.contains(spot.subtract(worldPosition))
                    || !server.getBlockState(spot).getCollisionShape(server, spot).isEmpty()
                    || !server.getBlockState(spot.above()).getCollisionShape(server, spot.above()).isEmpty()
                    || !server.getBlockState(spot.below()).isFaceSturdy(server, spot.below(), Direction.UP)) {
                continue;
            }
            Zombie zombie = EntityTypes.ZOMBIE.create(server, EntitySpawnReason.EVENT);
            if (zombie == null) {
                return;
            }
            zombie.snapTo(spot.getX() + 0.5d, spot.getY(), spot.getZ() + 0.5d, random.nextFloat() * 360.0f, 0.0f);
            equip(zombie, EquipmentSlot.HEAD, "paladium_helmet");
            equip(zombie, EquipmentSlot.CHEST, "paladium_chestplate");
            equip(zombie, EquipmentSlot.LEGS, "paladium_leggings");
            equip(zombie, EquipmentSlot.FEET, "paladium_boots");
            zombie.setCanPickUpLoot(false);
            zombie.setPersistenceRequired();
            zombie.addTag(EndiumRitual.TAG);
            zombie.getPersistentData().putLong(EndiumRitual.TOTEM, worldPosition.asLong());
            server.addFreshEntity(zombie);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, spot.getX() + 0.5d, spot.getY() + 0.5d, spot.getZ() + 0.5d,
                    12, 0.3d, 0.5d, 0.3d, 0.02d);
            return;
        }
    }

    private static void equip(Zombie zombie, EquipmentSlot slot, String name) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name));
        if (item != Items.AIR) {
            zombie.setItemSlot(slot, new ItemStack(item));
            zombie.setDropChance(slot, 0.0f);
        }
    }

    /** Les zombies de ce totem encore debout. */
    private List<Zombie> summoned(ServerLevel server) {
        long key = worldPosition.asLong();
        return server.getEntitiesOfClass(Zombie.class, EndiumRitual.area(worldPosition).inflate(16.0d),
                zombie -> zombie.isAlive() && zombie.entityTags().contains(EndiumRitual.TAG)
                        && zombie.getPersistentData().getLongOr(EndiumRitual.TOTEM, Long.MIN_VALUE) == key);
    }

    // -- La fin -------------------------------------------------------------------

    /** Vingt-cinq zombies : les sacrifies meurent ({@code TileEntityEndiumTotem$1}). */
    private void sacrifice(ServerLevel server) {
        for (BlockPos offset : EndiumRitual.ALTARS) {
            BlockPos altar = worldPosition.offset(offset);
            server.sendParticles(BLOOD, altar.getX() + 0.5d, altar.getY() + 1.4d, altar.getZ() + 0.5d,
                    40, 0.3d, 0.6d, 0.3d, 0.0d);
            Player standing = EndiumRitual.standingOn(server, altar);
            if (standing != null && !standing.getAbilities().invulnerable) {
                standing.hurtServer(server, server.damageSources().magic(), Float.MAX_VALUE);
            }
            if (server.getBlockEntity(altar) instanceof SacrificeAltarBlockEntity entity) {
                entity.consume();
            }
        }
        summoned(server).forEach(Zombie::discard);
        setPowders(server, false);
        closeBar();
        server.playSound(null, worldPosition, SoundEvents.WITHER_DEATH, SoundSource.BLOCKS, 0.7f, 1.2f);
        say(server, Component.translatable("block.palamod.endium_totem.done").withStyle(ChatFormatting.LIGHT_PURPLE));
        phase = SACRIFICED;
        setChanged();
    }

    void click(Player player) {
        if (phase == SACRIFICED && level instanceof ServerLevel server) {
            phase = REVEALING;
            timer = 0;
            server.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0f, 0.6f);
            setChanged();
            return;
        }
        if (phase == IDLE) {
            SacrificeAltarBlock.tell(player, status());
        }
    }

    /** Ce qui manque encore, dit au joueur qui clique le totem. */
    private Component status() {
        int powders = EndiumRitual.missingPowders(level, worldPosition);
        int altars = EndiumRitual.missingAltars(level, worldPosition);
        if (powders > 0 || altars > 0) {
            return Component.translatable("block.palamod.endium_totem.missing", altars, powders);
        }
        return Component.translatable("block.palamod.endium_totem.offerings");
    }

    /** Les flammes, puis le coffre ({@code BlockEndiumTotem$1}). */
    private void revealing(ServerLevel server) {
        timer++;
        double radius = 0.4d + timer / (double) REVEAL_TICKS;
        for (int index = 0; index < 4; index++) {
            double angle = (timer * 12 + index * 90) * Math.PI / 180.0d;
            server.sendParticles(ParticleTypes.FLAME, worldPosition.getX() + 0.5d + Math.cos(angle) * radius,
                    worldPosition.getY() + 0.2d + timer / 30.0d, worldPosition.getZ() + 0.5d + Math.sin(angle) * radius,
                    1, 0.0d, 0.0d, 0.0d, 0.01d);
        }
        if (timer < REVEAL_TICKS) {
            return;
        }
        BlockPos pos = worldPosition;
        server.removeBlock(pos, false);
        server.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        if (server.getBlockEntity(pos) instanceof Container chest) {
            Item nugget = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "endium_nugget"));
            chest.setItem(13, new ItemStack(nugget));
        }
        server.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    // -- Echec --------------------------------------------------------------------

    private void fail(ServerLevel server, String reason) {
        say(server, Component.translatable("block.palamod.endium_totem.failed",
                Component.translatable(reason)).withStyle(ChatFormatting.RED));
        summoned(server).forEach(Zombie::discard);
        setPowders(server, false);
        closeBar();
        phase = IDLE;
        kills = 0;
        participants.clear();
        setChanged();
    }

    /** Dechargee avec son chunk : la barre part, le reste attend le retour du joueur. */
    @Override
    public void setRemoved() {
        closeBar();
        super.setRemoved();
    }

    /** Casse pendant le rituel : les zombies repartent, la poudre s'eteint. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (phase == RUNNING && level instanceof ServerLevel server) {
            summoned(server).forEach(Zombie::discard);
            setPowders(server, false);
        }
        closeBar();
    }

    // -- Affichage ----------------------------------------------------------------

    private ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
        return bar;
    }

    private void updateBar() {
        bar().setName(Component.translatable("block.palamod.endium_totem.bar", kills, EndiumRitual.KILLS));
        bar().setProgress(Math.min(1.0f, kills / (float) EndiumRitual.KILLS));
    }

    private void closeBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
        }
    }

    private void say(ServerLevel server, Component message) {
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class,
                EndiumRitual.area(worldPosition).inflate(6.0d), ServerPlayer::isAlive)) {
            player.sendSystemMessage(message);
        }
    }

    /** Allume ou eteint la poudre du contour ({@code setPerlinpinpinActive}). */
    private void setPowders(ServerLevel server, boolean active) {
        for (BlockPos offset : EndiumRitual.POWDERS) {
            BlockPos pos = worldPosition.offset(offset);
            BlockState state = server.getBlockState(pos);
            if (state.getBlock() instanceof PerlinpinpinPowderBlock
                    && state.getValue(PerlinpinpinPowderBlock.ACTIVE) != active) {
                server.setBlock(pos, state.setValue(PerlinpinpinPowderBlock.ACTIVE, active), 2);
            }
        }
    }

    // -- Sauvegarde ---------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        phase = input.getIntOr("Phase", IDLE);
        kills = input.getIntOr("Kills", 0);
        timer = input.getIntOr("Timer", 0);
        participants.clear();
        input.read("Participants", UUIDUtil.STRING_CODEC.listOf()).ifPresent(participants::addAll);
        if (phase == REVEALING) {
            // Recharge pendant les flammes : on les reprend du debut.
            timer = 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Phase", phase);
        output.putInt("Kills", kills);
        output.putInt("Timer", timer);
        output.store("Participants", UUIDUtil.STRING_CODEC.listOf(), List.copyOf(participants));
    }
}
