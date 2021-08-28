package cc.minetale.magma.palette;

import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import cc.minetale.magma.type.MagmaBiome;
import com.github.luben.zstd.Zstd;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.biomes.Biome;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BiomePalette {

    private final Short2ObjectMap<MagmaBiome> palette;

    public BiomePalette(int expected) {
        this.palette = new Short2ObjectOpenHashMap<>(expected);
    }

    public BiomePalette() {
        this.palette = new Short2ObjectOpenHashMap<>();
    }

    public @Nullable MagmaBiome findInPalette(Biome biome) {
        return findInPalette(biome.getName());
    }

    public @Nullable MagmaBiome findInPalette(NamespaceID name) {
        synchronized(this.palette) {
            for(var biome : this.palette.values()) {
                NamespaceID existingName = biome.getName();
                if(existingName.equals(name)) { return biome; }
            }

            return null;
        }
    }

    /**
     * Tries to find {@linkplain MagmaBiome} in the given Magma palette and adds a new one if it couldn't find one.
     * @param biome {@linkplain NamespaceID} Minestom biome to find the Magma version of
     * @return An existing or newly created {@linkplain MagmaBiome}
     */
    public MagmaBiome findInPaletteOrAdd(Biome biome) {
        return findInPaletteOrAdd(biome.getName());
    }

    /**
     * Tries to find {@linkplain MagmaBiome} in the given Magma palette and adds a new one if it couldn't find one.
     * @param name {@linkplain NamespaceID} of the biome to search for
     * @return An existing or newly created {@linkplain MagmaBiome}
     */
    public MagmaBiome findInPaletteOrAdd(NamespaceID name) {
        synchronized(this.palette) {
            for(MagmaBiome biome : this.palette.values()) {
                NamespaceID existingName = biome.getName();
                if(existingName.equals(name)) { return biome; }
            }

            var newIndex = getSize();
            MagmaBiome biome = new MagmaBiome(newIndex, name);

            setBiomeAt(newIndex, biome);

            return biome;
        }
    }

    public MagmaBiome getBiomeAt(short index) {
        return this.palette.get(index);
    }

    public void setBiomeAt(short index, MagmaBiome biome) {
        this.palette.put(index, biome);
    }

    public short getSize() {
        return (short) this.palette.size();
    }

    public Short2ObjectMap<MagmaBiome> getAll() {
        return this.palette;
    }

    public static BiomePalette read(MagmaInputStream mis, short size) {
        BiomePalette palette = new BiomePalette(size);
        for(short i = 0; i < size; i++) {
            try {
                MagmaBiome biome = MagmaBiome.read(i, mis);
                palette.setBiomeAt(i, biome);
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        return palette;
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeShort(getSize());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        MagmaOutputStream paletteMos = new MagmaOutputStream(baos);
        for(short i = 0; i < getSize(); i++) {
            MagmaBiome biome = getBiomeAt(i);
            biome.write(paletteMos);
        }
        paletteMos.close();

        byte[] paletteData = baos.toByteArray();
        byte[] compressedPaletteData = Zstd.compress(paletteData);

        mos.writeInt(compressedPaletteData.length);
        mos.writeInt(paletteData.length);

        mos.write(compressedPaletteData);
    }

}
