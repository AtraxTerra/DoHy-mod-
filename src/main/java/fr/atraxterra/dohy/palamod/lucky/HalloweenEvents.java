package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Le lucky block d'Halloween ({@code luckyevents/halloween}) : fantomes, citrouilles et bonbons.
 * Dix-sept evenements, tous bons.
 *
 * <p>Ecarts : les creatures de PalaMod deviennent des creatures du jeu (le fantome est un vex, le
 * golem citrouille un golem de neige) ; « Spider-Steve » devient « Tisse ta toile », avec des gants
 * redessines ; le disque d'Halloween est un disque du jeu ; la pierre d'Halloween touche aussi les
 * monstres (elle ne visait que les joueurs).
 */
public final class HalloweenEvents {

    private HalloweenEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.HALLOWEEN, "bouh", "Bouh", "bouh", false, 50, HalloweenEvents::ghosts);
        c.season(LuckyType.HALLOWEEN, "hellyeah", "Hell Yeah!", "hell_yeah", false, 50, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(SeasonEffects.HELL_YEAH, 2400, 0));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2500, 0));
            SeasonKit.win(player, "Hell yeah ! Le sol s'embrase derriere toi.");
        });
        c.season(LuckyType.HALLOWEEN, "itsnotyou", "Zombie de sortie", "tu_n_es_plus_toi_meme", false, 50,
                HalloweenEvents::zombieExit);
        c.season(LuckyType.HALLOWEEN, "sixtrouilles", "6 Trouilles", "6_trouilles", false, 50,
                HalloweenEvents::sixPumpkins);
        c.season(LuckyType.HALLOWEEN, "soupedecitrouille", "Soupe de citrouille", "soupe_de_citrouille", false, 50,
                HalloweenEvents::pumpkinGolem);
        c.season(LuckyType.HALLOWEEN, "spooky_amulet", "Spooky amulet", "demonic", false, 50,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.DAEMON_AMULET.get())));
        c.season(LuckyType.HALLOWEEN, "spookyspook", "Spooky Spook", "spooky_spook", false, 50, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(SeasonEffects.SPOOKY, 2400, 0));
            SeasonKit.say(player, "Spooky spook... Ouvre ton inventaire, si tu l'oses.");
        });
        c.season(LuckyType.HALLOWEEN, "trickortreat", "Trick or treat", "trick_or_treat", false, 50,
                HalloweenEvents::trickOrTreat);
        c.season(LuckyType.HALLOWEEN, "citrouille", "Citrouille", "citrouille", false, 200,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.PUMPKIN_SHEAR.get())));
        c.season(LuckyType.HALLOWEEN, "happyhalloween", "Joyeux Halloween", "joyeux_halloween", false, 300,
                (player, level, pos) -> SeasonKit.give(player, SeasonKit.named(SeasonKit.stack("minecraft:music_disc_13",
                        1), "Joyeux Halloween", "Un disque pour frissonner")));
        c.season(LuckyType.HALLOWEEN, "spidersteve", "Tisse ta toile", "tisse_ta_toile", false, 400,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.SPIDER_GLOVES.get())));
        c.season(LuckyType.HALLOWEEN, "casedepart", "Case depart", "case_depart", false, 500,
                (player, level, pos) -> SeasonKit.give(player, LuckyType.PALADIUM.stack(8)));
        c.season(LuckyType.HALLOWEEN, "big_candies", "Tarte infinie", "big_candies", false, 1000, (player, level, pos) -> {
            SeasonKit.give(player, new ItemStack(SeasonItems.INFINITY_PIE.get()));
            SeasonKit.win(player, "Tu as recu une recompense : pose-la et fais un clic droit dessus !");
        });
        c.season(LuckyType.HALLOWEEN, "capeoupascape", "Cape ou pas cape", "cape_ou_pas_cape", false, 1500,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.GHOST_CAPE.get())));
        c.season(LuckyType.HALLOWEEN, "sorcerer", "Apprenti sorcier", "apprenti_sorcier", false, 1500,
                (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.WITCH_BROOM.get())));
        c.season(LuckyType.HALLOWEEN, "fulllegendary", "Legendaire jusqu'au bout", "legendaire_jusqu_au_bout", false,
                2000, (player, level, pos) -> SeasonKit.give(player, new ItemStack(SeasonItems.HALLOWEEN_STONE.get())));
        c.season(LuckyType.HALLOWEEN, "small_candies", "Jack-o-lantern", "small_candies", false, 5000,
                HalloweenEvents::jack);
    }

    /** Bouh ({@code EntityGhostHalloween}) : dix fantomes volants, vingt points de vie. */
    private static void ghosts(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 10; index++) {
            Vec3 at = player.position().add(level.getRandom().nextDouble() * 4.0d - 2.0d, 1.5d,
                    level.getRandom().nextDouble() * 4.0d - 2.0d);
            Mob ghost = SeasonKit.mob(level, EntityTypes.VEX, at, "Fantome");
            if (ghost != null) {
                ghost.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                SeasonKit.stat(ghost, Attributes.MAX_HEALTH, 20.0d);
                SeasonKit.stat(ghost, Attributes.ATTACK_DAMAGE, 5.0d);
                ghost.setTarget(player);
            }
        }
        SeasonKit.fail(player, "Bouh !");
    }

    /** Zombie de sortie : de deux a six zombies, a trois blocs au plus. */
    private static void zombieExit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int count = 2 + level.getRandom().nextInt(5);
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            if (SeasonKit.mob(level, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 1, 3)),
                    "Zombie de sortie") != null) {
                spawned++;
            }
        }
        SeasonKit.say(player, spawned + " zombies ont ete invoques autour de toi !");
    }

    /** 6 Trouilles ({@code EntityZombieHalloween}) : six zombies a tete de citrouille, 64 citrouilles chacun. */
    private static void sixPumpkins(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<net.neoforged.neoforge.registries.DeferredBlock<SeasonItems.PumpkinFaceBlock>> faces =
                SeasonItems.pumpkinFaces();
        for (int index = 0; index < 6; index++) {
            Mob zombie = SeasonKit.mob(level, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 3,
                    6)), "Trouille");
            if (zombie == null) {
                continue;
            }
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(faces.get(level.getRandom().nextInt(faces.size())).get()));
            zombie.setDropChance(EquipmentSlot.HEAD, 0.0f);
            SeasonKit.loot(zombie, new ItemStack(Items.PUMPKIN, 64));
            zombie.setTarget(player);
        }
    }

    /** Soupe de citrouille ({@code EntityPumpkinGolem}) : un golem qui tire sur les monstres. */
    private static void pumpkinGolem(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob golem = SeasonKit.mob(level, EntityTypes.SNOW_GOLEM, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 1,
                2)), "Golem citrouille");
        if (golem != null) {
            SeasonKit.loot(golem, new ItemStack(Items.PUMPKIN_PIE, 8));
            SeasonKit.win(player, "Un golem citrouille veille sur toi.");
        }
    }

    /**
     * Trick or treat ({@code TileEntityHopperHalloween}) : un entonnoir reclame des lingots de
     * paladium. Cinq minutes pour le remplir, sinon un gage : un mauvais evenement au hasard.
     */
    private static void trickOrTreat(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()
                || !LuckyWorld.place(player, level, pos, Blocks.HOPPER.defaultBlockState())) {
            SeasonKit.fail(player, "Pas de place pour l'entonnoir d'Halloween ici.");
            return;
        }
        int needed = 1 + level.getRandom().nextInt(34);
        int[] given = {0};
        SeasonKit.say(player, "Un bonbon ou un sort ! Remplis l'entonnoir de " + needed
                + " lingots de paladium en 5 minutes, ou gare au gage.");
        SeasonKit.challenge(player, "Remplis l'entonnoir : " + needed + " lingots de paladium", 300, false, (p, age) -> {
            if (age % 10 != 0) {
                return SeasonKit.Outcome.GOING;
            }
            if (!(level.getBlockEntity(pos) instanceof HopperBlockEntity hopper)) {
                return SeasonKit.Outcome.LOST;
            }
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                ItemStack stack = hopper.getItem(slot);
                if (stack.is(PalaItems.PALADIUM_INGOT.get())) {
                    given[0] += stack.getCount();
                    hopper.setItem(slot, ItemStack.EMPTY);
                }
            }
            if (age % 40 == 0) {
                LuckyWorld.bar(p, "Entonnoir : " + Math.min(given[0], needed) + "/" + needed, ChatFormatting.GOLD);
            }
            return given[0] >= needed ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
        }, p -> {
            removeHopper(level, pos);
            SeasonKit.win(p, "L'entonnoir est rassasie : pas de gage pour cette fois !");
        }, p -> {
            removeHopper(level, pos);
            SeasonKit.fail(p, "Tu n'as pas rempli l'entonnoir assez vite. Tu vas avoir un gage !");
            trick(p, level);
        });
    }

    private static void removeHopper(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.HOPPER)) {
            if (level.getBlockEntity(pos) instanceof HopperBlockEntity hopper) {
                hopper.clearContent();
            }
            level.removeBlock(pos, false);
        }
    }

    /** Le gage ({@code LuckyEvents.getRandomBadEvent}) : un mauvais evenement du lucky block en paladium. */
    private static void trick(ServerPlayer player, ServerLevel level) {
        List<LuckyEvent> bad = new ArrayList<>();
        double total = 0.0d;
        for (LuckyEvent event : LuckyEvents.pool(LuckyType.PALADIUM)) {
            if (event.bad()) {
                bad.add(event);
                total += event.weight();
            }
        }
        double roll = level.getRandom().nextDouble() * total;
        for (LuckyEvent event : bad) {
            roll -= event.weight();
            if (roll < 0.0d) {
                LuckyWorld.tell(player, "Gage : " + event.name(), ChatFormatting.RED);
                event.action().perform(player, level, player.blockPosition());
                return;
            }
        }
    }

    /**
     * Jack-o-lantern ({@code EntityJackLantern}) : il demande une torche pour eclairer sa route et
     * offre en echange un fragment d'endium.
     */
    private static void jack(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob jack = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player, level,
                2, 3)), "Jack-o-lantern", 300, (p, npc) -> {
            ItemStack held = p.getMainHandItem();
            if (!held.is(Items.TORCH)) {
                SeasonKit.speak(p, "Jack-o-lantern", "Offre-moi une torche pour eclairer mon trajet, s'il te plait.");
                return;
            }
            held.shrink(1);
            SeasonKit.speak(p, "Jack-o-lantern", "Merci bien ! Je t'offre ceci, ca ne me servira a rien la ou je vais.");
            SeasonKit.give(p, SeasonKit.stack("palamod:endium_fragment", 1));
            SeasonKit.dismiss(npc);
        });
        if (jack != null) {
            jack.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.JACK_O_LANTERN));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, jack.getX(), jack.getY() + 2.0d, jack.getZ(), 15, 0.3d, 0.3d,
                    0.3d, 0.01d);
            LuckyWorld.sound(level, jack.getX(), jack.getY(), jack.getZ(), "ambient.cave", SoundSource.NEUTRAL, 0.8f, 1.2f);
            SeasonKit.speak(player, "Jack-o-lantern", "Offre-moi une torche pour eclairer mon trajet, s'il te plait.");
        }
    }
}
