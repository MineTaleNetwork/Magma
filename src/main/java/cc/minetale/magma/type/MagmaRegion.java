package cc.minetale.magma.type;

import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import com.github.luben.zstd.Zstd;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

@Getter
public class MagmaRegion {

    /**
     * X-axis length, in chunks
     */
    private final int xSize;

    /**
     * Z-axis length, in chunks
     */
    private final int zSize;

    private final BitSet populatedChunks;

    private MaterialPalette materialPalette;
    private BiomePalette biomePalette;

    private Int2ObjectMap<MagmaChunk> chunks;

    public MagmaRegion(int xSize, int zSize,
                       @NotNull BitSet populatedChunks,
                       int materialPaletteSize, byte[] materialPaletteData,
                       short biomePaletteSize,  byte[] biomePaletteData,
                       byte[] chunksData) {

        this.xSize = xSize;
        this.zSize = zSize;

        this.populatedChunks = populatedChunks;

        this.materialPalette = new MaterialPalette(materialPaletteSize);
        readMaterialPalette(materialPaletteData, materialPaletteSize);

        this.biomePalette = new BiomePalette(biomePaletteSize);
        readBiomePalette(biomePaletteData, biomePaletteSize);

        this.chunks = new Int2ObjectOpenHashMap<>(xSize * zSize);
        readMagmaChunks(chunksData);
    }

    public MagmaRegion(int xSize, int zSize,
                       @NotNull BitSet populatedChunks,
                       MaterialPalette materialPalette, BiomePalette biomePalette,
                       Int2ObjectMap<MagmaChunk> chunks) {

        this.xSize = xSize;
        this.zSize = zSize;

        this.populatedChunks = populatedChunks;

        this.materialPalette = materialPalette;
        this.biomePalette = biomePalette;

        this.chunks = chunks;
    }

    /**
     * Gets the Magma chunk at the specified block coordinates.
     *
     * @param x the x-coordinate
     * @param z the z-coordinate
     * @return the Magma chunk, or {@code null} if not populated
     */
    public MagmaChunk getMagmaChunkAt(int x, int z) {
        return this.chunks.get(ChunkUtils.getChunkIndex(x, z));
    }

    private void readMaterialPalette(byte[] paletteData, int size) {
        MagmaInputStream stream = new MagmaInputStream(
                new ByteArrayInputStream(paletteData));

        this.materialPalette = MaterialPalette.read(stream, size);
    }

    private void readBiomePalette(byte[] paletteData, short size) {
        MagmaInputStream stream = new MagmaInputStream(
                new ByteArrayInputStream(paletteData));

        this.biomePalette = BiomePalette.read(stream, size);
    }

    private void readMagmaChunks(byte[] chunksData) {
        MagmaInputStream mis = new MagmaInputStream(
                new ByteArrayInputStream(chunksData));

        int current = 0;
        for(var index = 0; index < this.populatedChunks.length(); index++) {
            if(!this.populatedChunks.get(index)) {
                // Non-populated chunk
                continue;
            }

            try {
                MagmaChunk chunk = MagmaChunk.read(this.materialPalette, this.biomePalette, mis);
                this.chunks.put(current, chunk);
            } catch(IOException e) {
                e.printStackTrace();
            }

            current++;
        }
    }

    public void write(MagmaOutputStream mos) throws IOException {
        System.out.println("Saving Region Information...");
        mos.writeShort(this.xSize);
        mos.writeShort(this.zSize);

        var bitmask = this.populatedChunks.toByteArray();
        mos.writeInt(bitmask.length);
        mos.write(bitmask);

        //Material palette
        System.out.println("Saving Material Palette...");
        this.materialPalette.write(mos);

        //Biome palette
        System.out.println("Saving Biome Palette...");
        this.biomePalette.write(mos);

        //Chunks
        System.out.println("Saving Chunks...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        MagmaOutputStream chunksMos = new MagmaOutputStream(baos);
        for(int i = 0; i < this.populatedChunks.length(); i++) {
            if(!this.populatedChunks.get(i)) { continue; }
            MagmaChunk chunk = this.chunks.get(i);
            chunk.write(chunksMos);
        }
        chunksMos.close();

        byte[] chunksData = baos.toByteArray();
        byte[] compressedChunksData = Zstd.compress(chunksData);

        mos.writeInt(compressedChunksData.length);
        mos.writeInt(chunksData.length);

        mos.write(compressedChunksData);

        System.out.println("Finished!");
    }

}