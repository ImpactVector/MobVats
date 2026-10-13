package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.saveddata.maps.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Detached Level: never exposes the live server, chunks, entities or mutable level data.
 * Cosmetic notifications are suppressed. Non-item spawns and environmental access fail closed.
 * This is a compatibility boundary, not a sandbox for malicious Java event handlers. */
final class InteractionLevel extends Level {
    final List<ItemStack> drops = new ArrayList<>();
    private final net.minecraft.world.item.crafting.RecipeManager recipes;
    private final net.minecraft.world.item.alchemy.PotionBrewing brewing;
    private final net.minecraft.world.flag.FeatureFlagSet features;
    private final net.minecraft.world.scores.Scoreboard scoreboard = new net.minecraft.world.scores.Scoreboard();
    boolean rejected;

    InteractionLevel(ServerLevel source) {
        super(
                new Data(source),
                source.dimension(),
                source.registryAccess(),
                source.dimensionTypeRegistration(),
                () -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE,
                false,
                false,
                0,
                0);
        recipes = source.getRecipeManager();
        brewing = source.potionBrewing();
        features = source.enabledFeatures();
    }

    private UnsupportedOperationException unsupported() {
        rejected = true;
        return new UnsupportedOperationException("Interaction requires an unsupported world effect");
    }

    public boolean addFreshEntity(Entity entity) {
        if (!(entity instanceof ItemEntity item)) throw unsupported();
        drops.add(item.getItem().copy());
        return true;
    }

    public BlockState getBlockState(BlockPos pos) {
        throw unsupported();
    }

    public FluidState getFluidState(BlockPos pos) {
        throw unsupported();
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
        throw unsupported();
    }

    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
        throw unsupported();
    }

    public void setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity entity) {
        throw unsupported();
    }

    public void removeBlockEntity(BlockPos pos) {
        throw unsupported();
    }

    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
        throw unsupported();
    }

    public net.minecraft.world.ticks.LevelTickAccess<Block> getBlockTicks() {
        throw unsupported();
    }

    public net.minecraft.world.ticks.LevelTickAccess<Fluid> getFluidTicks() {
        throw unsupported();
    }

    public void sendBlockUpdated(BlockPos p, BlockState before, BlockState after, int flags) {
        throw unsupported();
    }

    public void playSeededSound(
            Player player,
            double x,
            double y,
            double z,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume,
            float pitch,
            long seed) {}

    public void playSeededSound(
            Player player,
            Entity entity,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume,
            float pitch,
            long seed) {}

    public void levelEvent(Player player, int event, BlockPos pos, int data) {}

    public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {}

    public String gatherChunkSourceStats() {
        return "detached interaction";
    }

    public Entity getEntity(int id) {
        return null;
    }

    public List<Player> players() {
        return List.of();
    }

    protected net.minecraft.world.level.entity.LevelEntityGetter<Entity> getEntities() {
        throw unsupported();
    }

    public TickRateManager tickRateManager() {
        return new TickRateManager();
    }

    public MapItemSavedData getMapData(MapId id) {
        throw unsupported();
    }

    public void setMapData(MapId id, MapItemSavedData data) {
        throw unsupported();
    }

    public MapId getFreeMapId() {
        throw unsupported();
    }

    public void destroyBlockProgress(int id, BlockPos pos, int progress) {
        throw unsupported();
    }

    public net.minecraft.world.scores.Scoreboard getScoreboard() {
        return scoreboard;
    }

    public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() {
        return recipes;
    }

    public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing() {
        return brewing;
    }

    public float getDayTimeFraction() {
        return 0;
    }

    public float getDayTimePerTick() {
        return -1;
    }

    public void setDayTimeFraction(float value) {
        throw unsupported();
    }

    public void setDayTimePerTick(float value) {
        throw unsupported();
    }

    public int getSkyDarken() {
        return 0;
    }

    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
        return features;
    }

    public Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) {
        throw unsupported();
    }

    public float getShade(Direction direction, boolean shade) {
        return 1;
    }

    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        throw unsupported();
    }

    public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new net.minecraft.world.DifficultyInstance(getDifficulty(), 0, 0, 0);
    }

    private static final class Data implements WritableLevelData {
        private final long time;
        private final GameRules rules = new GameRules();

        Data(ServerLevel source) {
            time = source.getGameTime();
        }

        public BlockPos getSpawnPos() {
            return BlockPos.ZERO;
        }

        public float getSpawnAngle() {
            return 0;
        }

        public long getGameTime() {
            return time;
        }

        public long getDayTime() {
            return time;
        }

        public boolean isThundering() {
            return false;
        }

        public boolean isRaining() {
            return false;
        }

        public void setRaining(boolean value) {
            throw new UnsupportedOperationException("Detached level data");
        }

        public boolean isHardcore() {
            return false;
        }

        public GameRules getGameRules() {
            return rules;
        }

        public net.minecraft.world.Difficulty getDifficulty() {
            return net.minecraft.world.Difficulty.NORMAL;
        }

        public boolean isDifficultyLocked() {
            return true;
        }

        public void setSpawn(BlockPos pos, float angle) {
            throw new UnsupportedOperationException("Detached level data");
        }
    }
}
