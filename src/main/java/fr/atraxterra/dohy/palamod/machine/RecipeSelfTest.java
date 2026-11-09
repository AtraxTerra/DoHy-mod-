package fr.atraxterra.dohy.palamod.machine;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.palamod.pvp.PalamachineRecipes;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

@EventBusSubscriber
public final class RecipeSelfTest {
    private static final Logger LOG = LogUtils.getLogger();

    private static void log(String line) {
        LOG.info("palamod-test: " + line);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("recipetest").executes(context -> {
            MinecraftServer server = context.getSource().getServer();
            Thread thread = new Thread(() -> {
                try {
                    log("debut");
                    run(server);
                    log("fin");
                } catch (Throwable t) {
                    LOG.error("palamod-test: RECIPES FAILED", t);
                }
            }, "recipe-test");
            thread.setDaemon(true);
            thread.start();
            context.getSource().sendSuccess(() -> Component.literal("started"), false);
            return 1;
        }));
    }

    private static ItemStack first(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return first(ingredient.get());
    }

    private static ItemStack first(Ingredient ingredient) {
        return ingredient.items().findFirst().map(Holder::value).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        log("etape : recettes");
        TreeMap<String, Integer> counts = new TreeMap<>();
        int ok = 0;
        List<String> problems = new ArrayList<>();
        long start = System.currentTimeMillis();
        int seen = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (System.currentTimeMillis() - start > 20_000) {
                log("ARRET : plus de 20 s, " + seen + " recettes vues");
                break;
            }
            String id = holder.id().identifier().toString();
            String namespace = holder.id().identifier().getNamespace();
            if (!namespace.equals("palamod") && !namespace.equals("dohy")) {
                continue;
            }
            Recipe<?> recipe = holder.value();
            seen++;
            counts.merge(namespace + " " + recipe.getClass().getSimpleName(), 1, Integer::sum);
            try {
                if (recipe instanceof ShapedRecipe shaped) {
                    List<ItemStack> grid = new ArrayList<>();
                    for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
                        ItemStack stack = first(ingredient);
                        if (ingredient.isPresent() && stack.isEmpty()) {
                            problems.add(id + " : ingredient sans objet");
                        }
                        grid.add(stack);
                    }
                    CraftingInput input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
                    ok += check(server, level, id, (Recipe) shaped, input, RecipeType.CRAFTING, problems);
                } else if (recipe instanceof ShapelessRecipe shapeless) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Ingredient ingredient : shapeless.placementInfo().ingredients()) {
                        items.add(first(ingredient));
                    }
                    while (items.size() < 9) {
                        items.add(ItemStack.EMPTY);
                    }
                    CraftingInput input = CraftingInput.of(3, 3, items);
                    ok += check(server, level, id, (Recipe) shapeless, input, RecipeType.CRAFTING, problems);
                } else if (recipe instanceof AbstractCookingRecipe cooking) {
                    SingleRecipeInput input = new SingleRecipeInput(first(cooking.placementInfo().ingredients().get(0)));
                    ok += check(server, level, id, (Recipe) cooking, input, (RecipeType) cooking.getType(), problems);
                } else if (recipe instanceof CustomRecipe) {
                    log("custom (non teste ici) " + id + " " + recipe.getClass().getSimpleName());
                } else {
                    log("type inconnu " + id + " " + recipe.getClass().getName());
                }
            } catch (Throwable t) {
                problems.add(id + " : exception " + t);
            }
        }
        log("recettes chargees " + counts + " ; fonctionnelles " + ok);
        for (String problem : problems) {
            log("PROBLEME " + problem);
        }

        log("etape : speciales");
        ItemStack regen = new ItemStack(Items.POTION);
        regen.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.REGENERATION));
        ItemStack block = new ItemStack(fr.atraxterra.dohy.palamod.PalaBlocks.PALADIUM_BLOCK.get());
        ItemStack stone = new ItemStack(fr.atraxterra.dohy.palamod.golem.PalaGolem.GUARDIAN_STONE.get());
        stone.set(fr.atraxterra.dohy.palamod.golem.PalaGolem.GOLEM_DATA.get(), fr.atraxterra.dohy.palamod.golem.GolemData.EMPTY);
        ItemStack core = new ItemStack(fr.atraxterra.dohy.palamod.PalaItems.PALADIUM_CORE.get());
        ItemStack ingot = new ItemStack(fr.atraxterra.dohy.palamod.PalaItems.ENDIUM_INGOT.get());
        ItemStack none = ItemStack.EMPTY;
        special(server, level, "palamod:angelic_water_bucket",
                CraftingInput.of(1, 2, List.of(regen, new ItemStack(Items.WATER_BUCKET))));
        special(server, level, "palamod:mini_golem",
                CraftingInput.of(3, 2, List.of(block, stone, block, none, block, none)));
        special(server, level, "palamod:mini_golem_repair", CraftingInput.of(1, 3, List.of(
                new ItemStack(fr.atraxterra.dohy.palamod.golem.PalaGolem.WRENCH.get()),
                new ItemStack(fr.atraxterra.dohy.palamod.golem.PalaGolem.MINI_GOLEM.get()), block)));
        special(server, level, "palamod:endium_gauntlet", CraftingInput.of(3, 3, List.of(none, core, none,
                ingot, new ItemStack(fr.atraxterra.dohy.palamod.legendary.PalaLegendary.FORTUNE.get()), ingot,
                none, core, none)));

        log("etape : palamachine");
        int machine = 0;
        for (PalamachineRecipes.Recipe recipe : PalamachineRecipes.all()) {
            List<ItemStack> stacks = recipe.inputs().stream().map(ItemStack::new).toList();
            PalamachineRecipes.Recipe found = PalamachineRecipes.find(stacks);
            boolean air = recipe.result() == Items.AIR || recipe.inputs().contains(Items.AIR);
            if (found != recipe || air) {
                log("PROBLEME palamachine " + recipe + " trouve=" + found);
            } else {
                machine++;
            }
        }
        log("palamachine " + machine + "/" + PalamachineRecipes.all().size());
        log("etape : grinder");
        int grinder = 0;
        for (GrinderRecipes recipe : GrinderRecipes.all()) {
            if (recipe.first() == Items.AIR || recipe.second() == Items.AIR || recipe.result() == Items.AIR) {
                log("PROBLEME grinder " + recipe);
            } else {
                grinder++;
            }
        }
        log("grinder " + grinder + "/" + GrinderRecipes.all().size());
        log("etape : chaudron");
        Class<?> cauldron = Class.forName("fr.atraxterra.dohy.palamod.alchemist.CauldronRecipe");
        Field field = cauldron.getDeclaredField("RECIPES");
        field.setAccessible(true);
        int cauldronOk = 0;
        List<?> recipes = (List<?>) field.get(null);
        for (Object recipe : recipes) {
            var result = (ItemStack) cauldron.getMethod("result").invoke(recipe);
            if (result.isEmpty()) {
                log("PROBLEME chaudron " + recipe);
            } else {
                cauldronOk++;
            }
        }
        log("chaudron " + cauldronOk + "/" + recipes.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void special(MinecraftServer server, ServerLevel level, String id, CraftingInput input) {
        List<String> problems = new ArrayList<>();
        try {
            RecipeHolder<?> holder = null;
            for (RecipeHolder<?> candidate : server.getRecipeManager().getRecipes()) {
                if (candidate.id().identifier().toString().equals(id)) {
                    holder = candidate;
                }
            }
            if (holder == null) {
                log("PROBLEME speciale " + id + " : absente");
                return;
            }
            int ok = check(server, level, id, (Recipe) holder.value(), input, RecipeType.CRAFTING, problems);
            log((ok == 1 ? "speciale ok " : "PROBLEME speciale ") + id + " -> "
                    + ((Recipe) holder.value()).assemble(input) + " " + problems);
        } catch (Throwable t) {
            log("PROBLEME speciale " + id + " : exception " + t);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int check(MinecraftServer server, ServerLevel level, String id, Recipe recipe,
                             net.minecraft.world.item.crafting.RecipeInput input, RecipeType type, List<String> problems) {
        if (!recipe.matches(input, level)) {
            problems.add(id + " : ne correspond pas a ses propres ingredients");
            return 0;
        }
        ItemStack result = recipe.assemble(input);
        if (result.isEmpty()) {
            problems.add(id + " : resultat vide");
            return 0;
        }
        Optional<RecipeHolder<?>> chosen = server.getRecipeManager().getRecipeFor(type, input, level);
        if (chosen.isEmpty()) {
            problems.add(id + " : introuvable par le gestionnaire");
            return 0;
        }
        String chosenId = chosen.get().id().identifier().toString();
        if (!chosenId.equals(id)) {
            problems.add(id + " : masquee par " + chosenId + " (" + ((Recipe) chosen.get().value()).assemble(input) + ")");
            return 0;
        }
        return 1;
    }
}
