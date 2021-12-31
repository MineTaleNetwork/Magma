package cc.minetale.magma.type;

import cc.minetale.magma.palette.BiomePalette;
import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.instance.Chunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.BitSet;

@Getter @AllArgsConstructor
public class MagmaChunk {

    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaChunk.class);

    private BitSet populatedSections;
    private Byte2ObjectMap<@Nullable MagmaSection> sections;

    public static MagmaChunk fromChunk(MaterialPalette materialPalette, BiomePalette biomePalette, Chunk chunk) {
        BitSet populatedSections = new BitSet(16);

        Byte2ObjectMap<MagmaSection> magmaSections = new Byte2ObjectOpenHashMap<>(16);

        final var instance = chunk.getInstance();

        var sections = chunk.getSections();
        for(byte i = 0; i < sections.size(); i++) {
            var section = sections.get(i);

            var magmaSection = MagmaSection.fromSection(materialPalette, biomePalette, section);
            if(magmaSection != null) {
                magmaSections.put(i, magmaSection);
                populatedSections.set(i);
            }
        }

        return new MagmaChunk(populatedSections, magmaSections);
    }

    public static MagmaChunk read(MaterialPalette materialPalette, BiomePalette biomePalette,
                                  MagmaInputStream mis) throws IOException {

        Byte2ObjectMap<MagmaSection> sections = new Byte2ObjectOpenHashMap<>(16);

        BitSet populatedSections = mis.readBitSet(mis.readByte());
        for(byte i = 0; i < populatedSections.length(); i++) {
            if(!populatedSections.get(i)) {
                sections.put(i, null);
                continue;
            }

            sections.put(i, MagmaSection.read(materialPalette, biomePalette, mis));
        }

        return new MagmaChunk(populatedSections, sections);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeBitSetByte(this.populatedSections);

        for(byte i = 0; i < this.populatedSections.length(); i++) {
            MagmaSection section = this.sections.get(i);
            if(section == null || !this.populatedSections.get(i)) { continue; }

            section.write(mos);
        }
    }

}
