package cc.minetale.magma.palette;

import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import cc.minetale.magma.type.MagmaMaterial;
import com.github.luben.zstd.Zstd;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.NamespaceID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Getter
public class MaterialPalette {

    private final Int2ObjectMap<MagmaMaterial> palette;

    public MaterialPalette(int expected) {
        this.palette = new Int2ObjectOpenHashMap<>(expected);
    }

    public MaterialPalette() {
        this.palette = new Int2ObjectOpenHashMap<>();
    }

    //TODO Fix docs
    /**
     * Tries to find {@linkplain MagmaMaterial} in the given Magma palette and adds a new one if it couldn't find one.
     * @param block Minestom {@linkplain Block} to search Magma
     * @return An existing or newly created {@linkplain MagmaMaterial}
     */
    public MagmaMaterial findInPaletteOrAdd(Block block) {
        NamespaceID id = block.namespace();

        synchronized(this.palette) {
            for(MagmaMaterial material : this.palette.values()) {
                NamespaceID existingID = material.getId();
                if(existingID.equals(id)) {
                    var statePalette = material.getStatePalette();
                    statePalette.addStateIfNotFound(block.stateId());
                    return material;
                }
            }

            var newIndex = this.palette.size();
            MagmaMaterial newMaterial = new MagmaMaterial(newIndex, false, block); //TODO Implement custom blocks

            setMaterialAt(newIndex, newMaterial);

            return newMaterial;
        }
    }

    public MagmaMaterial getMaterialAt(int index) {
        return this.palette.get(index);
    }

    public void setMaterialAt(int index, MagmaMaterial material) {
        this.palette.put(index, material);
    }

    public int getSize() {
        return this.palette.size();
    }

    public Int2ObjectMap<MagmaMaterial> getAll() {
        return this.palette;
    }

    public static MaterialPalette read(MagmaInputStream mis) throws IOException {
        int size = mis.readInt();

        MaterialPalette palette = new MaterialPalette(size);
        for(var i = 0; i < size; i++) {
            try {
                MagmaMaterial material = MagmaMaterial.read(i, mis);
                palette.setMaterialAt(i, material);
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        return palette;
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeInt(getSize());

        for(int i = 0; i < getSize(); i++) {
            MagmaMaterial material = getMaterialAt(i);
            material.write(mos);
        }
    }

}
