package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Le lucky block de novembre ({@code monthly/modules/november}) : quinze evenements, dont onze
 * repris des autres mois (avec leur image d'origine) et quatre nouveaux : l'inventaire explosif,
 * le sauteur professionnel, la tempete de sable et le kit du petit alchimiste.
 */
public final class NovemberEvents {

    /** « Inventaire explosif » : deux secondes au moins entre deux explosions ({@code THROWS_INTERVAL_MILLIS}). */
    private static final java.util.Map<java.util.UUID, Long> LAST_BLAST = new java.util.HashMap<>();

    private NovemberEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.NOVEMBER, "november_enflamme_dancefloor", "Enflamme le dance-floor",
                "june/enflamme_dancefloor", true, 100, c.action("enflamme_dancefloor"));
        c.season(LuckyType.NOVEMBER, "november_black_hole", "Trou noir", "march/black_hole", true, 110,
                c.action("black_hole"));
        c.season(LuckyType.NOVEMBER, "november_cadeau_pas_piege", "Cadeau pas piege", "easter/cadeau_pas_piege", false,
                150, c.action("cadeau_pas_piege"));
        c.season(LuckyType.NOVEMBER, "november_va_te_laver", "Va te laver !", "may/va_te_laver", true, 170,
                c.action("va_te_laver"));
        c.season(LuckyType.NOVEMBER, "november_explosive_inventory", "Inventaire explosif",
                "november/explosive_inventory", true, 200, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.EXPLOSIVE, 2400, 0));
                    SeasonKit.fail(player, "Je te deconseille fortement d'ouvrir ton inventaire pendant quelque temps.");
                });
        c.season(LuckyType.NOVEMBER, "november_professional_jumper", "Sauteur professionnel",
                "sauteur_pro", true, 200, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 1200, 40));
                    SeasonKit.fail(player, "Attention a ta tete quand tu sautes.");
                });
        c.season(LuckyType.NOVEMBER, "november_reveil_toi", "Reveille-toi", "june/reveille_toi", false, 240,
                c.action("reveille_toi"));
        c.season(LuckyType.NOVEMBER, "november_job_exloration", "Explorons les offres d'emploi",
                "september/explore_job_openings", false, 250, c.action("explore_jobs_opening"));
        c.season(LuckyType.NOVEMBER, "november_talk_like_pirate",
                "Parler comme un pirate, ce n'est pas fait pour tout le monde !", "july/talk_like_pirate", true, 300,
                c.action("talk_like_pirate"));
        c.season(LuckyType.NOVEMBER, "november_well_calibrated", "Bien calibre", "march/well_calibrated", false, 300,
                c.action("well_calibrated"));
        c.season(LuckyType.NOVEMBER, "november_h4ck3r", "Hackerman", "easter/hackerman", true, 350,
                c.action("hackerman"));
        c.season(LuckyType.NOVEMBER, "november_sandstorm", "Tempete de sable", "november/sand_storm", true, 500,
                NovemberEvents::sandstorm);
        c.season(LuckyType.NOVEMBER, "november_work_party", "Fete du travail", "may/fete_travail", false, 750,
                c.action("fete_travail"));
        c.season(LuckyType.NOVEMBER, "november_real_final_boss", "Le vrai boss final", "september/real_final_boss",
                false, 1500, c.action("real_final_boss"));
        c.season(LuckyType.NOVEMBER, "november_alchemist_kit", "Kit du petit alchimiste", "november/little_alchemist",
                false, 2500, NovemberEvents::alchemistKit);
    }

    /**
     * Le joueur a ouvert son inventaire ({@code throwDynamite}) : sous « Inventaire explosif », une
     * dynamite tombe a ses pieds.
     */
    static void inventoryOpened(ServerPlayer player) {
        if (!player.hasEffect(SeasonEffects.EXPLOSIVE) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_BLAST.get(player.getUUID());
        if (last != null && now - last < 40L) {
            return;
        }
        LAST_BLAST.put(player.getUUID(), now);
        LuckyWorld.tnt(level, player.getX(), player.getY(), player.getZ(), 40);
        SeasonKit.fail(player, "Attention, ton inventaire est explosif !");
    }

    /** La tempete de sable ({@code SandStormData}) : trois blocs de sable par seconde au-dessus de la tete, vingt secondes. */
    private static void sandstorm(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.fail(player, "Attention a la tete !");
        LuckyTasks.repeat(player, 0, 20, 20, step -> {
            for (int index = 0; index < 3; index++) {
                int dx = index == 0 ? 0 : level.getRandom().nextInt(5) - 2;
                int dz = index == 0 ? 0 : level.getRandom().nextInt(5) - 2;
                BlockPos at = player.blockPosition().offset(dx, 9 + level.getRandom().nextInt(3), dz);
                if (level.getBlockState(at).isAir() && LuckyWorld.canEdit(player, level, at)) {
                    FallingBlockEntity sand = FallingBlockEntity.fall(level, at, Blocks.SAND.defaultBlockState());
                    sand.dropItem = false;
                }
            }
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 6.0d, player.getZ(), 6, 2.0d, 1.0d,
                    2.0d, 0.01d);
        });
    }

    /** Le kit du petit alchimiste : 64 blocs de chaudron, son coeur et neuf seaux d'eau angelique. */
    private static void alchemistKit(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.win(player, "Voici le kit du parfait petit alchimiste !");
        SeasonKit.give(player, SeasonKit.stack("palamod:cauldron", 64), SeasonKit.stack("palamod:cauldron_core", 1));
        for (int bucket = 0; bucket < 9; bucket++) {
            SeasonKit.give(player, SeasonKit.stack("palamod:angelic_water_bucket", 1));
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5d, player.getZ(), 30, 0.5d, 0.5d,
                0.5d, 0.1d);
    }
}
