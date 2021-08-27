package cc.minetale.magma.type;

import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.world.biomes.Biome;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.BitSet;

@Getter @AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MagmaChunk {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaChunk.class);

    private Short2ObjectMap<MagmaBiome> biomes;

    private BitSet populatedSections;
    private Byte2ObjectMap<@Nullable MagmaSection> sections;

    public static MagmaChunk fromChunk(MaterialPalette materialPalette, BiomePalette biomePalette, Chunk chunk) {
        Short2ObjectMap<MagmaBiome> magmaBiomes = new Short2ObjectOpenHashMap<>();

        Biome[] biomes = chunk.getBiomes();
        for(short i = 0; i < biomes.length; i++) {
            var biome = biomes[i];

            MagmaBiome magmaBiome = biomePalette.findInPaletteOrAdd(biome);
            magmaBiomes.put(i, magmaBiome);
        }

        BitSet populatedSections = new BitSet(16);

        Byte2ObjectMap<MagmaSection> magmaSections = new Byte2ObjectOpenHashMap<>(16);

        var sections = chunk.getSections();
        for(Int2ObjectMap.Entry<Section> ent : Int2ObjectMaps.fastIterable((Int2ObjectMap<Section>) sections)) {
            var key = (byte) ent.getIntKey();
            var section = ent.getValue();

            var magmaSection = MagmaSection.fromSection(materialPalette, section);
            magmaSections.put(key, magmaSection);

            populatedSections.set(key);
        }

        return new MagmaChunk(magmaBiomes, populatedSections, magmaSections);
    }

    public static MagmaChunk read(MaterialPalette materialPalette, BiomePalette biomePalette,
                                  MagmaInputStream mis) throws IOException {

        Short2ObjectMap<MagmaBiome> biomes = new Short2ObjectOpenHashMap<>();

        int biomesSize = mis.readShort();
        for(short i = 0; i < biomesSize; i++) {
            var index = mis.readShort();
            biomes.put(i, biomePalette.getBiomeAt(index));
        }

        Byte2ObjectMap<MagmaSection> sections = new Byte2ObjectOpenHashMap<>(16);

        BitSet populatedSections = mis.readBitSet(mis.readByte());
        for(byte i = 0; i < populatedSections.length(); i++) {
            if(!populatedSections.get(i)) {
                sections.put(i, null);
                continue;
            }

            sections.put(i, MagmaSection.read(materialPalette, mis));
        }

        return new MagmaChunk(biomes, populatedSections, sections);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeShort(this.biomes.size());
        for(short i = 0; i < this.biomes.size(); i++) {
            var biome = this.biomes.get(i);
            mos.writeShort(biome.getIndex());
        }

        mos.writeBitSetByte(this.populatedSections);

        for(byte i = 0; i < this.populatedSections.length(); i++) {
            MagmaSection section = this.sections.get(i);
            if(section == null || !this.populatedSections.get(i)) { continue; }

            section.write(mos);
        }
    }

}
