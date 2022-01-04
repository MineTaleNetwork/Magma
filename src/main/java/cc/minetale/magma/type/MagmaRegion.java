package cc.minetale.magma.type;

import cc.minetale.magma.MagmaUtils;
import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import com.github.luben.zstd.Zstd;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.BitSet;

@Getter
public class MagmaRegion {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaRegion.class);

    private final int xSize;
    private final int zSize;

    private final BitSet populatedChunks;

    private MaterialPalette materialPalette;
    private BiomePalette biomePalette;

    private Long2ObjectMap<MagmaChunk> chunks;

    public MagmaRegion(int xSize, int zSize, BitSet populatedChunks, MaterialPalette materialPalette, BiomePalette biomePalette, Long2ObjectMap<MagmaChunk> chunks) {
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
        return this.chunks.get(MagmaUtils.getMagmaChunkIndex(x, z, this.xSize));
    }

    public static MagmaRegion read(MagmaInputStream mis) throws IOException {
        LOGGER.debug("Reading region...");

        int xSize = mis.readShort();
        int zSize = mis.readShort();

        int bitmaskLength = mis.readInt();
        BitSet populatedChunks = mis.readBitSet(bitmaskLength);

        byte[] data = mis.readCompressed();
        MagmaInputStream dataMis = new MagmaInputStream(data);

        //Palettes
        MaterialPalette materialPalette = MaterialPalette.read(dataMis);
        BiomePalette biomePalette = BiomePalette.read(dataMis);

        //Chunks
        byte[] chunksData = dataMis.readByteArray();

        Long2ObjectMap<MagmaChunk> chunks = new Long2ObjectOpenHashMap<>(xSize * zSize);
        try(MagmaInputStream chunksMis = new MagmaInputStream(chunksData)) {
            int current = 0;
            for(var index = 0; index < populatedChunks.length(); index++) {
                if(!populatedChunks.get(index)) {
                    // Non-populated chunk
                    continue;
                }

                try {
                    MagmaChunk chunk = MagmaChunk.read(materialPalette, biomePalette, chunksMis);
                    chunks.put(current, chunk);
                } catch(IOException e) {
                    e.printStackTrace();
                }

                current++;
            }
        }

        LOGGER.debug("Finished reading region!");

        return new MagmaRegion(
                xSize, zSize,
                populatedChunks,
                materialPalette, biomePalette,
                chunks);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        LOGGER.debug("Writing region...");

        mos.writeShort(this.xSize);
        mos.writeShort(this.zSize);

        mos.writeBitSetInt(this.populatedChunks);

        MagmaOutputStream dataMos = new MagmaOutputStream();

        //Materials
        this.materialPalette.write(dataMos);
        this.biomePalette.write(dataMos);

        //Chunks
        MagmaOutputStream chunksMos = new MagmaOutputStream(4096);
        for(int i = 0; i < this.populatedChunks.length(); i++) {
            if(!this.populatedChunks.get(i)) { continue; }
            MagmaChunk chunk = this.chunks.get(i);
            chunk.write(chunksMos);
        }
        chunksMos.close();

        dataMos.writeMagma(chunksMos);

        //Compression
        dataMos.close();

        byte[] data = dataMos.toByteArray();
        byte[] compressedData = Zstd.compress(data);

        mos.writeCompressed(data.length, compressedData);
        LOGGER.debug("Finished writing region!");
    }

}