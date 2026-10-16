package net.impactvector.mobvats.gametest;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.impactvector.mobvats.gamecontent.product.ProductContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mobvats")
@PrefixGameTestTemplate(false)
public final class ProductGameTests {
    private static final BlockPos P = new BlockPos(4, 2, 4);

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void registrationsAndCreativeOrder(GameTestHelper h) {
        var names = List.of("raw_meat", "cooked_meat", "fertilizer", "meat_block", "waste_block");
        var items = ProductContent.items();
        for (int i = 0; i < names.size(); i++) {
            h.assertTrue(BuiltInRegistries.ITEM.get(id(names.get(i))) == items.get(i), "Wrong item identity");
            h.assertTrue(new ItemStack(items.get(i)).getMaxStackSize() == 64, "Wrong stack limit");
        }
        h.assertTrue(ProductContent.FERTILIZER.get() != Items.BONE_MEAL, "Fertilizer aliases bone meal");
        for (var block : List.of(ProductContent.MEAT_BLOCK.get(), ProductContent.WASTE_BLOCK.get())) {
            h.assertTrue(
                    BuiltInRegistries.BLOCK.get(BuiltInRegistries.ITEM.getKey(block.asItem())) == block,
                    "Block/item registration mismatch");
            h.assertTrue(!block.defaultBlockState().hasBlockEntity(), "Unexpected block entity");
            h.assertTrue(block.asItem().getDefaultInstance().get(DataComponents.FOOD) == null, "Edible block");
        }
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id("mobvats"));
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        var display = new ArrayList<>(tab.getDisplayItems());
        int first = -1;
        for (int i = 0; i < display.size(); i++) if (display.get(i).is(items.getFirst())) first = i;
        h.assertTrue(first >= 0 && first + 5 <= display.size(), "Missing products in creative tab");
        for (int i = 0; i < 5; i++) h.assertTrue(display.get(first + i).is(items.get(i)), "Product order changed");
        h.succeed();
    }

    private static void food(GameTestHelper h, Item item, Item vanilla, int hunger, float saturation) {
        var stack = new ItemStack(item, 2);
        var properties = stack.get(DataComponents.FOOD);
        h.assertTrue(
                properties != null
                        && properties.nutrition() == hunger
                        && Math.abs(properties.saturation() - saturation) < 0.00001F
                        && properties.equals(vanilla.getDefaultInstance().get(DataComponents.FOOD)),
                "Food values differ");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0);
        h.assertTrue(
                item.use(h.getLevel(), playerWithStack(player, stack), InteractionHand.MAIN_HAND)
                        .getResult()
                        .consumesAction(),
                "Cannot start eating");
        var remaining = item.finishUsingItem(stack, h.getLevel(), player);
        h.assertTrue(
                remaining.getCount() == 1 && player.getFoodData().getFoodLevel() == hunger,
                "Eating failed to feed player/consume portion");
        h.succeed();
    }

    private static net.minecraft.world.entity.player.Player playerWithStack(
            net.minecraft.world.entity.player.Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void rawMeatFood(GameTestHelper h) {
        food(h, ProductContent.RAW_MEAT.get(), Items.BEEF, 3, 1.8F);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void cookedMeatFood(GameTestHelper h) {
        food(h, ProductContent.COOKED_MEAT.get(), Items.COOKED_BEEF, 8, 12.8F);
    }

    private static <T extends AbstractCookingRecipe> void cooking(GameTestHelper h, RecipeType<T> type, int ticks) {
        var input = new SingleRecipeInput(new ItemStack(ProductContent.RAW_MEAT.get()));
        var recipe = h.getLevel()
                .getRecipeManager()
                .getRecipeFor(type, input, h.getLevel())
                .orElseThrow()
                .value();
        var output = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(
                output.is(ProductContent.COOKED_MEAT.get())
                        && output.getCount() == 1
                        && recipe.getCookingTime() == ticks
                        && recipe.getExperience() == 0.35F,
                "Cooking mismatch");
        h.assertTrue(
                !recipe.matches(new SingleRecipeInput(new ItemStack(Items.BEEF)), h.getLevel()),
                "Vanilla beef matches product recipe");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void furnaceRecipe(GameTestHelper h) {
        cooking(h, RecipeType.SMELTING, 200);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void smokerRecipe(GameTestHelper h) {
        cooking(h, RecipeType.SMOKING, 100);
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void campfireRecipe(GameTestHelper h) {
        cooking(h, RecipeType.CAMPFIRE_COOKING, 600);
    }

    private static InteractionResult use(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(h.absolutePos(pos.offset(0, 0, 3))));
        playerWithStack(player, stack);
        var absolute = h.absolutePos(pos);
        return stack.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fertilizerCropsAndFailureConsumption(GameTestHelper h) {
        for (var crop : List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS)) {
            var block = (CropBlock) crop;
            h.setBlock(P.below(), Blocks.FARMLAND);
            h.setBlock(P, crop);
            var stack = new ItemStack(ProductContent.FERTILIZER.get(), 32);
            h.getLevel().random.setSeed(0);
            // Beetroot divides the random age increment by three, which can round to zero.
            for (int i = 0; i < 16 && block.getAge(h.getBlockState(P)) == 0; i++) {
                int before = stack.getCount();
                h.assertTrue(
                        use(h, P, stack).consumesAction() && stack.getCount() == before - 1,
                        "Valid crop did not consume exactly one: " + crop);
            }
            h.assertTrue(block.getAge(h.getBlockState(P)) > 0, "Crop never grew: " + crop);
            int remaining = stack.getCount();
            h.setBlock(P, block.getStateForAge(block.getMaxAge()));
            h.assertTrue(
                    use(h, P, stack) == InteractionResult.PASS && stack.getCount() == remaining,
                    "Mature crop consumed fertilizer");
        }
        h.setBlock(P, Blocks.STONE);
        var stack = new ItemStack(ProductContent.FERTILIZER.get(), 3);
        h.assertTrue(
                use(h, P, stack) == InteractionResult.PASS && stack.getCount() == 3,
                "Unsupported block consumed fertilizer");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void fertilizerSaplingsGrassAndFlowers(GameTestHelper h) {
        h.setBlock(P.below(), Blocks.GRASS_BLOCK);
        h.setBlock(P, Blocks.OAK_SAPLING);
        // Same seed gives the same vanilla probabilistic failure; a valid target still consumes one.
        for (var item : List.of(ProductContent.FERTILIZER.get(), Items.BONE_MEAL)) {
            h.setBlock(P, Blocks.OAK_SAPLING);
            h.getLevel().random.setSeed(0);
            var stack = new ItemStack(item, 32);
            h.assertTrue(
                    use(h, P, stack).consumesAction()
                            && stack.getCount() == 31
                            && h.getBlockState(P).getValue(SaplingBlock.STAGE) == 0,
                    "Valid probabilistic failure differs from bone meal");
            for (int i = 0; i < 16 && h.getBlockState(P).getValue(SaplingBlock.STAGE) == 0; i++) use(h, P, stack);
            h.assertTrue(h.getBlockState(P).getValue(SaplingBlock.STAGE) == 1, "Sapling never advanced");
        }
        h.setBlock(P, Blocks.SHORT_GRASS);
        var grass = new ItemStack(ProductContent.FERTILIZER.get(), 2);
        h.assertTrue(
                use(h, P, grass).consumesAction()
                        && grass.getCount() == 1
                        && h.getBlockState(P).is(Blocks.TALL_GRASS),
                "Grass did not grow");
        h.setBlock(P.above(), Blocks.AIR);
        h.setBlock(P, Blocks.SUNFLOWER);
        h.setBlock(
                P.above(),
                Blocks.SUNFLOWER
                        .defaultBlockState()
                        .setValue(
                                net.minecraft.world.level.block.DoublePlantBlock.HALF,
                                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var flower = new ItemStack(ProductContent.FERTILIZER.get(), 2);
        h.assertTrue(use(h, P, flower).consumesAction() && flower.getCount() == 1, "Flower use failed");
        h.assertTrue(
                h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(1)).stream()
                        .anyMatch(e -> e.getItem().is(Items.SUNFLOWER)),
                "Flower failed to produce duplicate");
        h.setBlock(P.above(), Blocks.AIR);
        h.setBlock(P, Blocks.AIR);
        var ground = new ItemStack(ProductContent.FERTILIZER.get(), 2);
        h.assertTrue(
                use(h, P.below(), ground).consumesAction() && ground.getCount() == 1,
                "Grass block rejected fertilizer");
        h.succeed();
    }

    private static void buildingBlock(GameTestHelper h, Block block) {
        h.setBlock(P.below(), Blocks.STONE);
        var stack = new ItemStack(block, 2);
        h.assertTrue(use(h, P, stack).consumesAction() && stack.getCount() == 1, "Placement failed");
        var absolute = h.absolutePos(P);
        BlockState state = h.getBlockState(P);
        h.assertTrue(
                state.is(block)
                        && state.isCollisionShapeFullBlock(h.getLevel(), absolute)
                        && state.getDestroySpeed(h.getLevel(), absolute) > 0
                        && !state.requiresCorrectToolForDrops(),
                "Block is not an ordinary hand-mineable cube");
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(
                player.gameMode.destroyBlock(absolute) && h.getBlockState(P).isAir(), "Survival mining failed");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(1));
        h.assertTrue(
                drops.size() == 1
                        && drops.getFirst().getItem().is(block.asItem())
                        && drops.getFirst().getItem().getCount() == 1,
                "Mining did not recover one block");
        h.assertTrue(use(h, P, drops.getFirst().getItem().copy()).consumesAction(), "Cannot replace mined block");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void meatBlockSurvival(GameTestHelper h) {
        buildingBlock(h, ProductContent.MEAT_BLOCK.get());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void wasteBlockSurvival(GameTestHelper h) {
        buildingBlock(h, ProductContent.WASTE_BLOCK.get());
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void completeProductResources(GameTestHelper h) throws Exception {
        var loader = ProductGameTests.class.getClassLoader();
        var lang = JsonParser.parseReader(new InputStreamReader(
                        loader.getResourceAsStream("assets/mobvats/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (var name : List.of("raw_meat", "cooked_meat", "fertilizer", "meat_block", "waste_block")) {
            boolean block = name.endsWith("_block");
            var paths = new ArrayList<>(List.of(
                    "assets/mobvats/models/item/" + name + ".json",
                    "assets/mobvats/textures/" + (block ? "blocks/" : "items/") + name + ".png"));
            if (block)
                paths.addAll(List.of(
                        "assets/mobvats/models/block/" + name + ".json",
                        "assets/mobvats/blockstates/" + name + ".json",
                        "data/mobvats/loot_table/blocks/" + name + ".json"));
            for (var path : paths)
                try (var stream = loader.getResourceAsStream(path)) {
                    h.assertTrue(stream != null && stream.readAllBytes().length > 0, "Missing resource: " + path);
                }
            h.assertTrue(
                    lang.get((block ? "block" : "item") + ".mobvats." + name)
                            .getAsString()
                            .equals(
                                    switch (name) {
                                        case "raw_meat" -> "Raw Meat";
                                        case "cooked_meat" -> "Cooked Meat";
                                        case "fertilizer" -> "Fertilizer";
                                        case "meat_block" -> "Meat Block";
                                        default -> "Waste Block";
                                    }),
                    "Wrong translation");
        }
        h.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("mobvats", name);
    }

    private ProductGameTests() {}
}
