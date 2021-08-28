package cc.minetale.magma;

import cc.minetale.magma.type.MagmaRegion;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMaps;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Getter
public class MagmaLoader implements IChunkLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaLoader.class);

    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();
    private static final BiomeManager BIOME_MANAGER = MinecraftServer.getBiomeManager();
    private static final ExceptionManager EXCEPTION_MANAGER = MinecraftServer.getExceptionManager();
    private static final Biome BIOME = Biome.PLAINS;

    private final String name;
    private final MagmaRegion region;

    private MagmaLoader(@NotNull String name, MagmaRegion region) {
        this.name = name;
        this.region = region;
    }

    /**
     * Returns the loader after the region has fully loaded.
     * @param name Name of the region to load
     * @return Ready to use {@linkplain MagmaLoader}
     */
    public static CompletableFuture<MagmaLoader> create(@NotNull String name) {
        return new CompletableFuture<MagmaLoader>()
                .completeAsync(() -> {
                    try {
                        var region = MagmaReader.read(name).get();
                        return new MagmaLoader(name, region);
                    } catch(InterruptedException | ExecutionException e) {
                        Thread.currentThread().interrupt();
                        MinecraftServer.getExceptionManager().handleException(e);
                    }
                    return null;
                });
    }

    //TODO Improve
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
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        if (this.region == null) {
            LOGGER.error("Tried loading a chunk when the region hasn't loaded yet.");
            return CompletableFuture.completedFuture(null);
        }

        if((chunkX < 0 || chunkX >= this.region.getXSize()) || (chunkZ < 0 || chunkZ >= this.region.getZSize()))
            return CompletableFuture.completedFuture(null);

        var chunkIndex = MagmaUtils.getMagmaChunkIndex(chunkX, chunkZ, this.region.getXSize());

        var chunks = this.region.getChunks();
        var magmaChunk = chunks.get(chunkIndex);

        LOGGER.info("Attempt loading at {} {}", chunkX, chunkZ);

        if(magmaChunk == null) {
            LOGGER.error("Failed to retrieve chunk {}, {} at index {}", chunkX, chunkZ, chunkIndex);
            return CompletableFuture.completedFuture(null);
        }

        Biome[] biomes = new Biome[1024];
        for(var ent : Short2ObjectMaps.fastIterable(magmaChunk.getBiomes())) {
            var index = ent.getShortKey();
            var magmaBiome = ent.getValue();

            biomes[index] = magmaBiome.getBiome();
        }

        Chunk chunk = new DynamicChunk(instance, biomes, chunkX, chunkZ);

        for (var sectionEntry : Byte2ObjectMaps.fastIterable(magmaChunk.getSections())) {
            var magmaSection = sectionEntry.getValue();
            if(magmaSection == null) { continue; }

            var sectionIndex = sectionEntry.getByteKey(); //Section index within a chunk

            var section = chunk.getSection(sectionIndex);
            section.setSkyLight(magmaSection.getSkyLight());
            section.setBlockLight(magmaSection.getBlockLight());

            //TODO Set bitsPerEntry and bitsIncrement, but seems to be always the same... for now

            for(var blockEntry : Int2ObjectMaps.fastIterable(magmaSection.getBlocks())) {
                var blockIndex = blockEntry.getIntKey(); //Block index within a section
                var magmaBlock = blockEntry.getValue();

                var coords = MagmaUtils.getCoordsFromSectionIndex(blockIndex);

                var block = magmaBlock.getBlock();
                int x = (chunkX * Chunk.CHUNK_SIZE_X) + coords[0];
                int y = (sectionIndex * Chunk.CHUNK_SECTION_SIZE) + coords[1];
                int z = (chunkZ * Chunk.CHUNK_SIZE_Z) + coords[2];
                chunk.setBlock(x, y, z, block);
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveInstance(@NotNull Instance instance) {
        MagmaWriter.write(region, name);
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