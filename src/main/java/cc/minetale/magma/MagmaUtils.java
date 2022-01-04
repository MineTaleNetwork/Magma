package cc.minetale.magma;

import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.type.MagmaChunk;
import cc.minetale.magma.type.MagmaRegion;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Task;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class MagmaUtils {

    private MagmaUtils() {}

    public static final Path DEFAULT_DIRECTORY = Path.of(".", "regions");
    public static final String FORMAT_NAME = "magma";

    //Batch size in chunks
    public static final int BATCH_SIZE = 16;

    public static Path getDefaultLocation(String fileName) {
        return MagmaUtils.DEFAULT_DIRECTORY.resolve(fileName + "." + MagmaUtils.FORMAT_NAME);
    }

    public static CompletableFuture<MagmaRegion> load(Instance instance, Vec from, Vec to) {
        final var minY = instance.getDimensionType().getMinY();
        final var maxY = instance.getDimensionType().getHeight();

        final var sectionSize = Chunk.CHUNK_SECTION_SIZE;

        final var minSection = minY / sectionSize;
        final var maxSection = (minY + maxY) / sectionSize;

        Vec fromChunk = from.sub(from.x() % 16, from.y(), from.z() % 16).div(16);
        Vec toChunk = to.sub(to.x() % 16, to.y(), to.z() % 16).div(16);

        int xSize = Math.abs(toChunk.blockX() - fromChunk.blockX());
        int zSize = Math.abs(toChunk.blockZ() - fromChunk.blockZ());
        var totalChunks = xSize * zSize;

        BitSet populatedChunks = new BitSet(totalChunks);

        int lowestX = Math.min(fromChunk.blockX(), toChunk.blockX());
        int lowestZ = Math.min(fromChunk.blockZ(), toChunk.blockZ());

        CompletableFuture<MagmaRegion> future = new CompletableFuture<>();

        MaterialPalette materialPalette = new MaterialPalette();
        BiomePalette biomePalette = new BiomePalette();

        Long2ObjectMap<MagmaChunk> loadedChunks = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
        AtomicInteger totalChunksProcessed = new AtomicInteger(0);

        List<Task.Builder> tasks = Collections.synchronizedList(new ArrayList<>(totalChunks / BATCH_SIZE));

        final var totalBatches = (int) Math.ceil((double) totalChunks / BATCH_SIZE);
        for(int i = 0; i < totalBatches; i++) { //Batch
            final var batchIndex = i;
            Task.Builder task = MinecraftServer.getSchedulerManager().buildTask(() -> {
                final var chunksInBatch = Math.min(BATCH_SIZE, totalChunks - (batchIndex * BATCH_SIZE));
                AtomicInteger finishedInBatch = new AtomicInteger(0);

                for(int j = 0; j < chunksInBatch; j++) { //Chunk
                    final var chunkIndex = (batchIndex * BATCH_SIZE) + j;
                    final var chunkPos = getPositionFromMagmaIndex(chunkIndex, xSize).add(lowestX, 0, lowestZ).mul(16);

                    final var unload = !ChunkUtils.isLoaded(instance, chunkPos); //Don't unload chunks that were already loaded

                    instance.loadOptionalChunk(chunkPos).thenAccept(chunk -> {
                        //How many chunks will be finished in this batch after this one gets processed
                        int finished = finishedInBatch.incrementAndGet();

                        if(chunk == null || !chunk.isLoaded()) { return; }

                        boolean isChunkEmpty = true;
                        for(int y = 0; y < maxSection; y++) {
                            var section = chunk.getSection(y);
                            if(section.blockPalette().size() > 0) {
                                isChunkEmpty = false;
                                break;
                            }
                        }

                        if(isChunkEmpty) {
                            totalChunksProcessed.getAndIncrement();
                            scheduleNextBatch(tasks, finished, chunksInBatch);
                            return;
                        }

                        populatedChunks.set(chunkIndex);
                        loadedChunks.put(chunkIndex, MagmaChunk.fromChunk(materialPalette, biomePalette, chunk));

                        if(totalChunksProcessed.incrementAndGet() >= totalChunks)
                            future.complete(new MagmaRegion(xSize, zSize, populatedChunks, materialPalette, biomePalette, loadedChunks));

                        if(unload)
                            instance.unloadChunk(chunk);

                        scheduleNextBatch(tasks, finished, chunksInBatch);
                    });
                }
            }).delay(500, ChronoUnit.MILLIS);

            tasks.add(task);
        }

        tasks.get(totalBatches - 1).schedule();

        return future;
    }

    private static void scheduleNextBatch(List<Task.Builder> tasks, int finished, int chunksInBatch) {
        if(!tasks.isEmpty() && finished >= chunksInBatch) {
            var index = tasks.size() - 1;
            tasks.remove(index);
            tasks.get(index - 1).schedule();
        }
    }

    public static int getMagmaChunkIndex(int x, int z, int xSize) {
        return z * xSize + x;
    }

    public static int getMagmaChunkIndex(Chunk chunk, int xSize) {
        return getMagmaChunkIndex(chunk.getChunkX(), chunk.getChunkZ(), xSize);
    }

    public static Vec getPositionFromMagmaIndex(int index, int xSize) {
        final int x = index % xSize;
        final int z = index / xSize;
        return new Vec(x, 0, z);
    }

    //Reversed PaletteImpl#getSectionIndex
    public static int[] getCoordsFromSectionIndex(int dimension, int index) {
        int[] coords = new int[3];
        coords[1] = index >> (dimension / 2);       //Y
        coords[2] = index >> (dimension / 4) & 0xF; //Z
        coords[0] = index                    & 0xF; //X
        return coords;
    }

    public static int getSectionIndex(int dimension, int x, int y, int z) {
        return y << (dimension / 2) | z << (dimension / 4) | x;
    }
}
