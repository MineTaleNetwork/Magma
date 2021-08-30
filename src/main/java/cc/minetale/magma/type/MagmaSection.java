package cc.minetale.magma.type;

import cc.minetale.magma.MagmaWriter;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Getter @AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MagmaSection {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaSection.class);

    private byte[] skyLight;
    private byte[] blockLight;

    private int bitsPerEntry;
    private int bitsIncrement;

    private Int2ObjectMap<MagmaBlock> blocks;

    public void addMaterialsToPalette(Int2ObjectMap<MagmaMaterial> materialPalette) {
        for(MagmaBlock block : this.blocks.values()) {
            var material = block.getMaterial();
            var namespace = material.getMaterial().namespace();

            var isUnique = materialPalette.values().stream().noneMatch(otherMaterial -> {
                var otherBlock = otherMaterial.getMaterial();
                var otherNamespace = otherBlock.namespace();
                return otherNamespace.equals(namespace);
            });

            if(isUnique)
                materialPalette.put(materialPalette.size(), material);
        }
    }

    public static MagmaSection fromSection(MaterialPalette materialPalette, Section section) {
        byte[] skyLight = section.getSkyLight();
        byte[] blockLight = section.getBlockLight();

        //Palette information
        Palette palette = section.getPalette();

        int bitsPerEntry = palette.getBitsPerEntry();
        int bitsIncrement = 2; //As there is no way to retrieve this from the section, I decided to use a value that is always used either way.

        Int2ObjectMap<MagmaBlock> blocks = new Int2ObjectOpenHashMap<>();

        if(palette.getBlockCount() == 0)
            return new MagmaSection(skyLight, blockLight, bitsPerEntry, bitsIncrement, blocks);

        for(int x = 0; x < Chunk.CHUNK_SECTION_SIZE; x++) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y++) {
                for(int z = 0; z < Chunk.CHUNK_SECTION_SIZE; z++) {
                    short stateId = palette.getBlockAt(x, y, z);
                    if(stateId <= 0) { continue; }

                    Block block = Block.fromStateId(stateId);

                    int sectionIndex = Palette.getSectionIndex(x, y, z);
                    if(blocks.containsKey(sectionIndex)) { continue; }

                    if(block == null) {
                        block = Block.BARRIER;
                        //Notify about the broken block
                        LOGGER.error("Unable to find a block from the given StateId. Expected: {}", stateId);
                    }

                    MagmaMaterial material = materialPalette.findInPaletteOrAdd(block);

                    MagmaBlock magmaBlock = new MagmaBlock(sectionIndex, material, block);
                    blocks.put(sectionIndex, magmaBlock);
                }
            }
        }

        return new MagmaSection(skyLight, blockLight, bitsPerEntry, bitsIncrement, blocks);
    }

    public static MagmaSection read(MaterialPalette materialPalette, MagmaInputStream mis) throws IOException {
        byte[] skyLight = mis.readByteArray();
        byte[] blockLight = mis.readByteArray();

        int bitsPerEntry = mis.readInt();
        int bitsIncrement = mis.readInt();

        int blockCount = mis.readShort();

        Int2ObjectMap<MagmaBlock> blocks = new Int2ObjectOpenHashMap<>(blockCount);

        for(var i = 0; i < blockCount; i++) {
            MagmaBlock block = MagmaBlock.read(materialPalette, mis);
            blocks.put(block.getSectionIndex(), block);
        }

        return new MagmaSection(skyLight, blockLight, bitsPerEntry, bitsIncrement, blocks);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeByteArray(this.skyLight);
        mos.writeByteArray(this.blockLight);

        mos.writeInt(this.bitsPerEntry);
        mos.writeInt(this.bitsIncrement);

        mos.writeShort(this.blocks.size());

        for(MagmaBlock block : this.blocks.values()) {
            block.write(mos);
        }
    }

}
