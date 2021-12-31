package cc.minetale.magma;

import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.palette.StatePalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import cc.minetale.magma.type.*;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class MagmaTests {

    @Test
    void material_CorrectWriteAndRead() throws Exception {
        //Setup
        var beforeMaterial = generateMaterial();

        //Writing
        MagmaOutputStream mos = new MagmaOutputStream();
        beforeMaterial.write(mos);
        mos.flush();

        //Intermediary
        MagmaInputStream mis = flipStream(mos);

        //Reading
        var afterMaterial = MagmaMaterial.read(beforeMaterial.getIndex(), mis);
        assertNotNull(afterMaterial);

        //Comparison
        compareMaterials(beforeMaterial, afterMaterial);
    }

    MagmaMaterial generateMaterial() {
        var materialIndex = 1234;
        var isCustom = false;

        var block = Block.GRASS_BLOCK;

        return new MagmaMaterial(materialIndex, isCustom, block);
    }

    static void compareMaterials(MagmaMaterial beforeMaterial, MagmaMaterial afterMaterial) {
        assertEquals(beforeMaterial.getIndex(), afterMaterial.getIndex());
        assertEquals(beforeMaterial.isCustom(), afterMaterial.isCustom());
        assertEquals(beforeMaterial.getId(), afterMaterial.getId());
        assertEquals(beforeMaterial.getPropertiesAmount(), afterMaterial.getPropertiesAmount());
        //TODO compareStatePalettes()
        var oldStatePalette = beforeMaterial.getStatePalette();
        var newStatePalette = afterMaterial.getStatePalette();
        assertEquals(oldStatePalette.getSize(), newStatePalette.getSize());
        for(short i = 0; i < oldStatePalette.getSize(); i++) {
            assertEquals(oldStatePalette.getStateAt(i), newStatePalette.getStateAt(i));
        }
    }

    @Test
    void block_CorrectWriteAndRead() throws Exception {
        //Setup
        var beforeBlock = generateBlock((short) 1234, Block.GRASS_BLOCK, null);

        //Writing
        MagmaOutputStream mos = new MagmaOutputStream();
        beforeBlock.write(mos);
        mos.flush();

        //Intermediary
        MagmaInputStream mis = flipStream(mos);

        var materialPalette = new MaterialPalette();
        var material = beforeBlock.getMaterial();

        materialPalette.setMaterialAt(material.getIndex(), material);

        //Reading
        var afterBlock = MagmaBlock.read(materialPalette, mis);
        assertNotNull(afterBlock);

        //Comparison
        compareBlocks(beforeBlock, afterBlock);
    }

    MagmaBlock generateBlock(short sectionIndex, Block block, @Nullable MaterialPalette palette) {
        var materialIndex = 321;
        var isCustom = false;

        var material = palette == null ? new MagmaMaterial(materialIndex, isCustom, block) : palette.findInPaletteOrAdd(block);

        return new MagmaBlock(sectionIndex, material, block);
    }

    void compareBlocks(MagmaBlock beforeBlock, MagmaBlock afterBlock) {
        assertEquals(beforeBlock.getSectionIndex(), afterBlock.getSectionIndex());
        compareMaterials(beforeBlock.getMaterial(), afterBlock.getMaterial());
        assertEquals(beforeBlock.getStateId(), afterBlock.getStateId());
        assertEquals(beforeBlock.getSnbt(), afterBlock.getSnbt());
    }

    @Test
    void biome_CorrectWriteAndRead() throws Exception {
        //Setup
        var beforeBiome = generateBiome(null);

        //Writing
        MagmaOutputStream mos = new MagmaOutputStream();
        beforeBiome.write(mos);
        mos.flush();

        //Intermediary
        MagmaInputStream mis = flipStream(mos);

        //Reading
        var afterBiome = MagmaBiome.read(beforeBiome.getIndex(), mis);
        assertNotNull(afterBiome);

        //Comparison
        compareBiomes(beforeBiome, afterBiome);
    }

    MagmaBiome generateBiome(@Nullable BiomePalette palette) {
        var biomeIndex = (short) 1234;
        var key = NamespaceID.from("abc", "def");

        var biome = palette == null ? new MagmaBiome(biomeIndex, key) : palette.findInPaletteOrAdd(NamespaceID.from("abc", "def"));
        return biome;
    }

    static void compareBiomes(MagmaBiome beforeBiome, MagmaBiome afterBiome) {
        assertEquals(beforeBiome.getIndex(), afterBiome.getIndex());
        assertEquals(beforeBiome.getName(), afterBiome.getName());
    }

    @Nested
    class PaletteTests {

        @Test
        void statePalette_CorrectWriteAndRead() throws Exception {
            //Setup
            var beforeStatePalette = generateStatePalette();

            //Writing
            MagmaOutputStream mos = new MagmaOutputStream();
            beforeStatePalette.write(mos);
            mos.flush();

            //Intermediary
            MagmaInputStream mis = flipStream(mos);

            var block = Block.fromStateId(beforeStatePalette.getStateAt((short) 0));
            assertNotNull(block);

            //Reading
            var material = new MagmaMaterial(123, false, block);

            var afterStatePalette = StatePalette.read(mis, material);
            assertNotNull(afterStatePalette);

            //Comparison
            compareStatePalettes(beforeStatePalette, afterStatePalette);
        }

        @NotNull private StatePalette generateStatePalette() {
            var palette = new StatePalette();
            palette.findInPaletteOrAdd((short) 123);
            return palette;
        }

        void compareStatePalettes(StatePalette beforeStatePalette, StatePalette afterStatePalette) {
            var originalSize = beforeStatePalette.getSize();
            assertEquals(originalSize, afterStatePalette.getSize());

            for(short i = 0; i < originalSize; i++) {
                var beforeState = beforeStatePalette.getStateAt(i);
                var afterState = afterStatePalette.getStateAt(i);
                assertEquals(beforeState, afterState);
            }
        }

        @Test
        void materialPalette_CorrectWriteAndRead() throws Exception {
            //Setup
            MaterialPalette beforeMaterialPalette = generateMaterialPalette();

            //Writing
            MagmaOutputStream mos = new MagmaOutputStream();
            beforeMaterialPalette.write(mos);
            mos.flush();

            //Intermediary
            MagmaInputStream mis = flipStream(mos);

            //Reading
            var afterMaterialPalette = MaterialPalette.read(mis);
            assertNotNull(afterMaterialPalette);

            //Comparison
            compareMaterialPalettes(beforeMaterialPalette, afterMaterialPalette);
        }

        @NotNull private MaterialPalette generateMaterialPalette() {
            var materialPalette = new MaterialPalette();
            materialPalette.findInPaletteOrAdd(Block.GRASS_BLOCK);
            return materialPalette;
        }

        static void compareMaterialPalettes(MaterialPalette beforeMaterialPalette, MaterialPalette afterMaterialPalette) {
            var originalSize = beforeMaterialPalette.getSize();
            assertEquals(originalSize, afterMaterialPalette.getSize());

            for(int i = 0; i < originalSize; i++) {
                var beforeMaterial = beforeMaterialPalette.getMaterialAt(i);
                assertNotNull(beforeMaterial);

                var afterMaterial = afterMaterialPalette.getMaterialAt(i);
                assertNotNull(afterMaterial);

                compareMaterials(beforeMaterial, afterMaterial);
            }
        }

        @Test
        void biomePalette_CorrectWriteAndRead() throws Exception {
            //Setup
            BiomePalette beforeBiomePalette = generateBiomePalette();

            //Writing
            MagmaOutputStream mos = new MagmaOutputStream();
            beforeBiomePalette.write(mos);
            mos.flush();

            //Intermediary
            MagmaInputStream mis = flipStream(mos);

            //Reading
            var afterBiomePalette = BiomePalette.read(mis);
            assertNotNull(afterBiomePalette);

            //Comparison
            compareBiomePalettes(beforeBiomePalette, afterBiomePalette);
        }

        @NotNull private BiomePalette generateBiomePalette() {
            var biomePalette = new BiomePalette();
            biomePalette.findInPaletteOrAdd(NamespaceID.from("abc", "def"));
            return biomePalette;
        }

        static void compareBiomePalettes(BiomePalette beforeBiomePalette, BiomePalette afterBiomePalette) {
            var originalSize = beforeBiomePalette.getSize();
            assertEquals(originalSize, afterBiomePalette.getSize());

            for(short i = 0; i < originalSize; i++) {
                var beforeBiome = beforeBiomePalette.getBiomeAt(i);
                assertNotNull(beforeBiome);

                var afterBiome = afterBiomePalette.getBiomeAt(i);
                assertNotNull(afterBiome);

                compareBiomes(beforeBiome, afterBiome);
            }
        }

    }

    @Nested
    class ComplexTests {

        @Test
        void section_CorrectWriteAndRead() throws Exception {
            //Setup
            var materialPalette = new MaterialPalette();
            var biomePalette = new BiomePalette();

            var beforeSection = generateSection(materialPalette, biomePalette);

            //Writing
            MagmaOutputStream mos = new MagmaOutputStream();
            beforeSection.write(mos);
            mos.flush();

            //Intermediary
            MagmaInputStream mis = flipStream(mos);

            //Reading
            var afterSection = MagmaSection.read(materialPalette, biomePalette, mis);
            assertNotNull(afterSection);

            //Comparison
            compareSections(beforeSection, afterSection);
        }

        @NotNull private MagmaSection generateSection(MaterialPalette materialPalette, BiomePalette biomePalette) {
            var skyLight = new byte[]{0, 1, 2};
            var blockLight = new byte[]{0, 1, 2};

            Short2ObjectMap<MagmaBlock> blocks = new Short2ObjectOpenHashMap<>();
            for(byte x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for(byte y = 0; y < Chunk.CHUNK_SECTION_SIZE; y++) {
                    for(byte z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                        var sectionIndex = (short) MagmaUtils.getSectionIndex(16, x, y, z);
                        var block = generateBlock(sectionIndex, sectionIndex % 2 == 0 ? Block.GRASS_BLOCK : Block.AIR, materialPalette);
                        blocks.put(sectionIndex, block);
                    }
                }
            }

            Byte2ObjectMap<MagmaBiome> biomes = new Byte2ObjectOpenHashMap<>();
            for(byte x = 0; x < Chunk.CHUNK_SIZE_X; x += 4) {
                for(byte y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += 4) {
                    for(byte z = 0; z < Chunk.CHUNK_SIZE_Z; z += 4) {
                        var sectionIndex = (byte) MagmaUtils.getSectionIndex(4, x, y, z);
                        var biome = generateBiome(biomePalette);
                        biomes.put(sectionIndex, biome);
                    }
                }
            }

            return new MagmaSection(skyLight, blockLight, blocks, biomes);
        }

        void compareSections(MagmaSection beforeSection, MagmaSection afterSection) {
            assertArrayEquals(beforeSection.getSkyLight(), afterSection.getSkyLight());
            assertArrayEquals(beforeSection.getBlockLight(), afterSection.getBlockLight());

            Short2ObjectMap<MagmaBlock> beforeBlocks = beforeSection.getBlocks();
            Short2ObjectMap<MagmaBlock> afterBlocks = afterSection.getBlocks();

            var blocksSize = beforeBlocks.size();
            assertEquals(blocksSize, afterBlocks.size());

            for(byte x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for(byte y = 0; y < Chunk.CHUNK_SECTION_SIZE; y++) {
                    for(byte z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                        var sectionIndex = (short) MagmaUtils.getSectionIndex(16, x, y, z);
                        var beforeBlock = beforeBlocks.get(sectionIndex);
                        var afterBlock = afterBlocks.get(sectionIndex);
                        compareBlocks(beforeBlock, afterBlock);
                    }
                }
            }

            Byte2ObjectMap<MagmaBiome> beforeBiomes = beforeSection.getBiomes();
            Byte2ObjectMap<MagmaBiome> afterBiomes = afterSection.getBiomes();

            var biomesSize = beforeBiomes.size();
            assertEquals(biomesSize, afterBiomes.size());

            for(byte x = 0; x < Chunk.CHUNK_SIZE_X; x += 4) {
                for(byte y = 0; y < Chunk.CHUNK_SECTION_SIZE; y += 4) {
                    for(byte z = 0; z < Chunk.CHUNK_SIZE_Z; z += 4) {
                        var sectionIndex = (byte) MagmaUtils.getSectionIndex(4, x, y, z);
                        var beforeBiome = beforeBiomes.get(sectionIndex);
                        var afterBiome = afterBiomes.get(sectionIndex);
                        compareBiomes(beforeBiome, afterBiome);
                    }
                }
            }
        }

        @Test
        void chunk_CorrectWriteAndRead() throws Exception {
            //Setup
            var materialPalette = new MaterialPalette();
            var biomePalette = new BiomePalette();

            var beforeChunk = generateChunk(materialPalette, biomePalette);

            //Writing
            MagmaOutputStream mos = new MagmaOutputStream();
            beforeChunk.write(mos);
            mos.flush();

            //Intermediary
            MagmaInputStream mis = flipStream(mos);

            //Reading
            var afterChunk = MagmaChunk.read(materialPalette, biomePalette, mis);
            assertNotNull(afterChunk);

            //Comparison
            compareChunks(beforeChunk, afterChunk);
        }

        @NotNull private MagmaChunk generateChunk(MaterialPalette materialPalette, BiomePalette biomePalette) {
            final var dimension = DimensionType.OVERWORLD;

            final var minY = dimension.getMinY();
            final var maxY = dimension.getHeight();

            final var sectionSize = Chunk.CHUNK_SECTION_SIZE;

            final var minSection = minY / sectionSize;
            final var maxSection = (minY + maxY) / sectionSize;

            final var sectionCount = maxSection - minSection;

            var populatedSections = new BitSet(sectionCount);
            Byte2ObjectMap<@Nullable MagmaSection> sections = new Byte2ObjectOpenHashMap<>();

            for(byte i = 0; i < sectionCount; i++) {
                populatedSections.set(i);
                var section = generateSection(materialPalette, biomePalette);
                sections.put(i, section);
            }

            return new MagmaChunk(populatedSections, sections);
        }

        void compareChunks(MagmaChunk beforeChunk, MagmaChunk afterChunk) {
            assertEquals(beforeChunk.getPopulatedSections(), afterChunk.getPopulatedSections());

            final var dimension = DimensionType.OVERWORLD;

            final var minY = dimension.getMinY();
            final var maxY = dimension.getHeight();

            final var sectionSize = Chunk.CHUNK_SECTION_SIZE;

            final var minSection = minY / sectionSize;
            final var maxSection = (minY + maxY) / sectionSize;

            final var sectionCount = maxSection - minSection;

            for(byte i = 0; i < sectionCount; i++) {
                var beforeSection = beforeChunk.getSections().get(i);
                assertNotNull(beforeSection);

                var afterSection = afterChunk.getSections().get(i);
                assertNotNull(afterSection);

                compareSections(beforeSection, afterSection);
            }
        }

        @Test
        void region_CorrectWriteAndRead() throws Exception {
            //Setup
            var materialPalette = new MaterialPalette();
            var biomePalette = new BiomePalette();

            var beforeRegion = generateRegion(materialPalette, biomePalette);

            //Writing
            MagmaOutputStream mos = new MagmaOutputStream();
            beforeRegion.write(mos);
            mos.flush();

            //Intermediary
            MagmaInputStream mis = flipStream(mos);

            //Reading
            var afterRegion = MagmaRegion.read(mis);
            assertNotNull(afterRegion);

            //Comparison
            compareRegion(beforeRegion, afterRegion);
        }

        @NotNull private MagmaRegion generateRegion(MaterialPalette materialPalette, BiomePalette biomePalette) {
            final var xSize = 2;
            final var zSize = 2;

            final var size = xSize * zSize;

            var populatedChunks = new BitSet(size);
            Long2ObjectMap<MagmaChunk> chunks = new Long2ObjectOpenHashMap<>();

            int i = 0;
            for(int x = 0; x < xSize; x++) {
                for(int z = 0; z < zSize; z++) {
                    populatedChunks.set(i);
                    chunks.put(i++, generateChunk(materialPalette, biomePalette));
                }
            }

            return new MagmaRegion(xSize, zSize, populatedChunks, materialPalette, biomePalette, chunks);
        }

        void compareRegion(MagmaRegion beforeRegion, MagmaRegion afterRegion) {
            var xSize = beforeRegion.getXSize();
            assertEquals(xSize, afterRegion.getXSize());

            var zSize = beforeRegion.getZSize();
            assertEquals(zSize, afterRegion.getZSize());

            assertEquals(beforeRegion.getPopulatedChunks(), afterRegion.getPopulatedChunks());

            PaletteTests.compareBiomePalettes(beforeRegion.getBiomePalette(), afterRegion.getBiomePalette());
            PaletteTests.compareMaterialPalettes(beforeRegion.getMaterialPalette(), afterRegion.getMaterialPalette());

            for(int x = 0; x < xSize; x++) {
                for(int z = 0; z < zSize; z++) {
                    var beforeChunk = beforeRegion.getMagmaChunkAt(x, z);
                    var afterChunk = afterRegion.getMagmaChunkAt(x, z);
                    compareChunks(beforeChunk, afterChunk);
                }
            }
        }

    }

    private MagmaInputStream flipStream(MagmaOutputStream mos) {
        var intermediateArr = mos.toByteArray();
        assertNotNull(intermediateArr);
        assertTrue(intermediateArr.length > 0);
        return new MagmaInputStream(intermediateArr);
    }

}
