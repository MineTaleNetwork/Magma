package cc.minetale.magma;

import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.type.MagmaChunk;
import cc.minetale.magma.type.MagmaRegion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.timer.TaskBuilder;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MagmaUtils {

    public static final Path DEFAULT_DIRECTORY = Path.of(".", "regions");
    public static final String FORMAT_NAME = "magma";

    //Batch size in chunks
    public static final int BATCH_SIZE = 16;

    public static Path getDefaultLocation(String fileName) {
        return MagmaUtils.DEFAULT_DIRECTORY.resolve(fileName + "." + MagmaUtils.FORMAT_NAME);
    }

    public static CompletableFuture<MagmaRegion> load(Instance instance, Pos from, Pos to) {
        Pos fromChunk = from.sub(from.x() % 16, from.y(), from.z() % 16).div(16);
        Pos toChunk = to.sub(to.x() % 16, to.y(), to.z() % 16).div(16);

        int xSize = Math.abs(toChunk.blockX() - fromChunk.blockX());
        int zSize = Math.abs(toChunk.blockZ() - fromChunk.blockZ());
        var totalChunks = xSize * zSize;

        BitSet populatedChunks = new BitSet(totalChunks);

        int lowestX = Math.min(fromChunk.blockX(), toChunk.blockX());
        int lowestZ = Math.min(fromChunk.blockZ(), toChunk.blockZ());

        CompletableFuture<MagmaRegion> future = new CompletableFuture<>();

        MaterialPalette materialPalette = new MaterialPalette();
        BiomePalette biomePalette = new BiomePalette();

        Int2ObjectMap<MagmaChunk> loadedChunks = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        AtomicInteger totalFinished = new AtomicInteger(0);

        List<TaskBuilder> tasks = Collections.synchronizedList(new ArrayList<>(totalChunks / BATCH_SIZE));

        final var totalBatches = (int) Math.ceil((double) totalChunks / BATCH_SIZE);
        for(int i = 0; i < totalBatches; i++) { //Batch
            final var batchIndex = i;
            TaskBuilder task = MinecraftServer.getSchedulerManager().buildTask(() -> {
                final var chunksInBatch = Math.min(BATCH_SIZE, totalChunks - (batchIndex * BATCH_SIZE));
                AtomicInteger finishedInBatch = new AtomicInteger(0);

                for(int j = 0; j < chunksInBatch; j++) { //Chunk
                    final var chunkIndex = (batchIndex * BATCH_SIZE) + j;
                    final var chunkPos = getPositionFromMagmaIndex(chunkIndex, xSize).add(lowestX, 0, lowestZ).mul(16);

                    final var unload = !ChunkUtils.isLoaded(instance, chunkPos); //Don't unload chunks that were already loaded

                    instance.loadOptionalChunk(chunkPos).thenAccept(chunk -> {
                        int finished = finishedInBatch.incrementAndGet();

                        if(chunk == null || !chunk.isLoaded()) { return; }

                        Collection<Section> sections = chunk.getSections().values();
                        var isChunkEmpty = sections.stream()
                                .allMatch(section -> section.getPalette().getBlockCount() == 0);

                        if(isChunkEmpty) { return; }

                        populatedChunks.set(chunkIndex);
                        loadedChunks.put(chunkIndex, MagmaChunk.fromChunk(materialPalette, biomePalette, chunk));

                        if(totalFinished.incrementAndGet() >= totalBatches)
                            future.complete(new MagmaRegion(xSize, zSize, populatedChunks, materialPalette, biomePalette, loadedChunks));

                        if(unload)
                            instance.unloadChunk(chunk);

                        if(!tasks.isEmpty() && finished >= chunksInBatch) {
                            var index = tasks.size() - 1;
                            tasks.remove(index);
                            tasks.get(index - 1).schedule();
                        }
                    });
                }
            }).delay(500, ChronoUnit.MILLIS);

            tasks.add(task);
        }

        tasks.get(totalBatches - 1).schedule();

        return future;
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

    //Reversed Palette#getSectionIndex
    public static int[] getCoordsFromSectionIndex(int index) {
        int[] coords = new int[3];
        coords[1] = index >> 8;       //Y
        coords[2] = index >> 4 & 0xF; //Z
        coords[0] = index      & 0xF; //X
        return coords;
    }

}
