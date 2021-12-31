package cc.minetale.magma.type;

import cc.minetale.magma.MagmaUtils;
import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Getter @AllArgsConstructor
public class MagmaSection {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaSection.class);

    private byte[] skyLight;
    private byte[] blockLight;

    private Short2ObjectMap<MagmaBlock> blocks;
    private Byte2ObjectMap<MagmaBiome> biomes;

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

    /**
     * @return A populated section or null if section blockPalette's size is 0 (there aren't any blocks other than air)
     */
    public static MagmaSection fromSection(MaterialPalette materialPalette, BiomePalette biomePalette, Section section) {
        byte[] skyLight = section.getSkyLight();
        byte[] blockLight = section.getBlockLight();

        Palette secBlockPalette = section.blockPalette();
        Palette secBiomePalette = section.biomePalette();

        Short2ObjectMap<MagmaBlock> blocks = new Short2ObjectOpenHashMap<>();
        Byte2ObjectMap<MagmaBiome> biomes = new Byte2ObjectOpenHashMap<>();

        if(secBlockPalette.size() == 0)
            return null;

        //Block Palette
        for(int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y++) {
                for(int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    short stateId = (short) secBlockPalette.get(x, y, z);
                    if(stateId < 0) { continue; }

                    Block block = Block.fromStateId(stateId);

                    short sectionIndex = (short) MagmaUtils.getSectionIndex(secBlockPalette.dimension(), x, y, z);
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

        //Biome Palette
        final var dimension = secBiomePalette.dimension();
        for(int x = 0; x < Chunk.CHUNK_SIZE_X; x += dimension) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += dimension) {
                for(int z = 0; z <  Chunk.CHUNK_SIZE_Z; z += dimension) {
                    int id = secBiomePalette.get(x, y, z);
                    var biome = MinecraftServer.getBiomeManager().getById(id);
                    MagmaBiome magmaBiome = biomePalette.findInPaletteOrAdd(biome);
                    biomes.put((byte) MagmaUtils.getSectionIndex(secBiomePalette.dimension(), x, y, z), magmaBiome);
                }
            }
        }

        return new MagmaSection(skyLight, blockLight, blocks, biomes);
    }

    public static MagmaSection read(MaterialPalette materialPalette, BiomePalette biomePalette, MagmaInputStream mis) throws IOException {
        byte[] skyLight = mis.readByteArray();
        byte[] blockLight = mis.readByteArray();

        //Blocks
        var dimension = 16; //Dimension used by Palette#blocks()
        var count = dimension * dimension * dimension;

        var step = Chunk.CHUNK_SECTION_SIZE / dimension;

        Short2ObjectMap<MagmaBlock> blocks = new Short2ObjectOpenHashMap<>(count);
        for(int x = 0; x < Chunk.CHUNK_SIZE_X; x += step) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += step) {
                for(int z = 0; z < Chunk.CHUNK_SIZE_Z; z += step) {
                    MagmaBlock block = MagmaBlock.read(materialPalette, mis);
                    blocks.put(block.getSectionIndex(), block);
                }
            }
        }

        //Biomes
        dimension = 4; //Dimension used by Palette#biomes()
        count = dimension * dimension * dimension;

        step = Chunk.CHUNK_SECTION_SIZE / dimension;

        Byte2ObjectMap<MagmaBiome> biomes = new Byte2ObjectOpenHashMap<>(count);
        for(int x = 0; x < Chunk.CHUNK_SIZE_X; x += step) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += step) {
                for(int z = 0; z < Chunk.CHUNK_SIZE_Z; z += step) {
                    var biomeIndex = mis.readShort();
                    MagmaBiome biome = biomePalette.getBiomeAt(biomeIndex);
                    biomes.put((byte) MagmaUtils.getSectionIndex(dimension, x, y, z), biome);
                }
            }
        }

        return new MagmaSection(skyLight, blockLight, blocks, biomes);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeByteArray(this.skyLight);
        mos.writeByteArray(this.blockLight);

        //Blocks
        var dimension = 16; //Dimension used by Palette#blocks()
        var step = Chunk.CHUNK_SECTION_SIZE / dimension;

        for(int x = 0; x < Chunk.CHUNK_SIZE_X; x += step) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += step) {
                for(int z = 0; z < Chunk.CHUNK_SIZE_Z; z += step) { //Multiple of 1
                    var sectionIndex = (short) MagmaUtils.getSectionIndex(dimension, x, y, z);
                    var block = this.blocks.get(sectionIndex);
                    block.write(mos);
                }
            }
        }

        //Biomes
        dimension = 4; //Dimension used by Palette#biomes()
        step = Chunk.CHUNK_SECTION_SIZE / dimension;

        for(int x = 0; x < Chunk.CHUNK_SIZE_X; x += step) {
            for(int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += step) {
                for(int z = 0; z < Chunk.CHUNK_SIZE_Z; z += step) { //Multiple of 4
                    var sectionIndex = (byte) MagmaUtils.getSectionIndex(dimension, x, y, z);
                    var biome = this.biomes.containsKey(sectionIndex) ? this.biomes.get(sectionIndex) : null;
                    var biomeIndex = biome != null ? biome.getIndex() : 0;
                    mos.writeShort(biomeIndex);
                }
            }
        }
    }

}
