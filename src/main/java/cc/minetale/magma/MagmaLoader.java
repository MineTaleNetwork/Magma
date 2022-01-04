package cc.minetale.magma;

import cc.minetale.magma.type.MagmaRegion;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMaps;
import lombok.Getter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.utils.async.AsyncUtils;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.time.TimeUnit;
import net.minestom.server.world.biomes.Biome;
import net.minestom.server.world.biomes.BiomeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Getter
public class MagmaLoader implements IChunkLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaLoader.class);

    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();
    private static final BiomeManager BIOME_MANAGER = MinecraftServer.getBiomeManager();
    private static final ExceptionManager EXCEPTION_MANAGER = MinecraftServer.getExceptionManager();
    private static final Biome BIOME = Biome.PLAINS;

    private final Path path;
    private final MagmaRegion region;

    private MagmaLoader(@NotNull Path path, MagmaRegion region) {
        this.path = path;
        this.region = region;
    }

    /**
     * Returns the loader after the region has fully loaded.
     * @param path Path to the region to load
     * @return Ready to use {@linkplain MagmaLoader}
     */
    public static CompletableFuture<MagmaLoader> create(@NotNull Path path) {
        return new CompletableFuture<MagmaLoader>()
                .completeAsync(() -> {
                    try {
                        var region = MagmaReader.read(path).get();
                        return new MagmaLoader(path, region);
                    } catch(InterruptedException | ExecutionException e) {
                        Thread.currentThread().interrupt();
                        MinecraftServer.getExceptionManager().handleException(e);
                    }
                    return null;
                });
    }

    //TODO Improve?
    @Override
    public void loadInstance(@NotNull Instance instance) {
        try {
            for(int x = 0; x < this.region.getXSize(); x++) {
                for(int z = 0; z < this.region.getZSize(); z++) {
                    if(!ChunkUtils.isLoaded(instance, x, z))
                        instance.unloadChunk(x, z);
                }
            }

            MinecraftServer.getSchedulerManager().buildTask(() -> {
                for(int x = 0; x < this.region.getXSize(); x++) {
                    for(int z = 0; z < this.region.getZSize(); z++) {
                        instance.loadChunk(x, z);
                    }
                }
            }).delay(8, TimeUnit.SECOND).schedule();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        if (this.region == null) {
            LOGGER.debug("Tried loading a chunk when the region hasn't loaded yet.");
            return CompletableFuture.completedFuture(null);
        }

        var xSize = this.region.getXSize();
        var zSize = this.region.getZSize();

        if((chunkX < 0 || chunkX > xSize - 1) || (chunkZ < 0 || chunkZ > zSize - 1))
            return CompletableFuture.completedFuture(null);

        var chunkIndex = MagmaUtils.getMagmaChunkIndex(chunkX, chunkZ, xSize);

        var populatedChunks = this.region.getPopulatedChunks();
        if(!populatedChunks.get(chunkIndex)) {
            LOGGER.debug("Skipping {} {} because it's not populated", chunkX, chunkZ);
            return CompletableFuture.completedFuture(null);
        }

        var chunks = this.region.getChunks();
        var magmaChunk = chunks.get(chunkIndex);

        LOGGER.debug("Attempt loading at {} {}", chunkX, chunkZ);

        if(magmaChunk == null) {
            LOGGER.debug("Failed to retrieve chunk {}, {} at index {}", chunkX, chunkZ, chunkIndex);
            return CompletableFuture.completedFuture(null);
        }

        final var minY = instance.getDimensionType().getMinY();
        final var sectionSize = Chunk.CHUNK_SECTION_SIZE;
        final var minSection = minY / sectionSize;

        var chunk = new DynamicChunk(instance, chunkX, chunkZ);

        for (var sectionEntry : Byte2ObjectMaps.fastIterable(magmaChunk.getSections())) {
            var magmaSection = sectionEntry.getValue();
            if(magmaSection == null) { continue; }

            var sectionIndex = sectionEntry.getByteKey() + minSection; //Section index within a chunk

            var section = chunk.getSection(sectionIndex);
            section.setSkyLight(magmaSection.getSkyLight());
            section.setBlockLight(magmaSection.getBlockLight());

            for(var magmaBlock : magmaSection.getBlocks().values()) {
                var blockIndex = magmaBlock.getSectionIndex(); //Block index within a section

                var coords = MagmaUtils.getCoordsFromSectionIndex(16, blockIndex);

                var block = magmaBlock.getBlock();
                int x = (chunkX * Chunk.CHUNK_SIZE_X)             + coords[0];
                int y = (sectionIndex * Chunk.CHUNK_SECTION_SIZE) + coords[1];
                int z = (chunkZ * Chunk.CHUNK_SIZE_Z)             + coords[2];
                chunk.setBlock(x, y, z, block);
            }

            for(var ent : Byte2ObjectMaps.fastIterable(magmaSection.getBiomes())) {
                var biomeIndex = ent.getByteKey(); //Biome index within a section
                var magmaBiome = ent.getValue();

                int[] coords = MagmaUtils.getCoordsFromSectionIndex(4, biomeIndex);

                var biome = magmaBiome.getBiome();
                int x = coords[0] * 16;
                int y = coords[1] * 16;
                int z = coords[2] * 16;
                chunk.setBiome(x, y, z, biome);
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveInstance(@NotNull Instance instance) {
        MagmaWriter.write(region, path);
        return AsyncUtils.VOID_FUTURE;
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        throw new UnsupportedOperationException("Saving individual chunks isn't supported. Please use MagmaLoader#saveInstance instead.");
    }

    @Override
    public boolean supportsParallelLoading() {
        return true;
    }

    @Override
    public boolean supportsParallelSaving() {
        return true;
    }

}