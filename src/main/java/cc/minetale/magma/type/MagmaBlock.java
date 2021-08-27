package cc.minetale.magma.type;

import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.instance.block.Block;
import net.minestom.server.tag.Tag;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Getter @AllArgsConstructor()
public class MagmaBlock {

    private final int sectionIndex; //Position index relative to the section

    private final MagmaMaterial material; //Material gotten from the Magma palette
    private final Map<String, String> properties; //Properties of the block (basically same as stateId, but represented as readable text)

    private final String snbt;

    public MagmaBlock(int sectionIndex, MagmaMaterial material, Block block) {
        this.sectionIndex = sectionIndex;

        this.material = material;

        this.properties = block.properties();
        this.snbt = block.getTag(Tag.SNBT); //TODO See if we should use Tags or actual NBT utilities for SNBT
    }

    /**
     * @return Block with properties/state and NBT if any. See also {@linkplain MagmaMaterial#getMaterial()}.
     */
    public Block getBlock() {
        Block block = this.material.getMaterial()
                .withProperties(this.properties);

        if(this.snbt != null && !this.snbt.isEmpty())
            block.withTag(Tag.SNBT, this.snbt);

        return block;
    }

    public static MagmaBlock read(MaterialPalette materialPalette, MagmaInputStream mis) throws IOException {
        int sectionIndex = mis.readInt();

        var materialIndex = mis.readInt();
        MagmaMaterial material = materialPalette.getMaterialAt(materialIndex);

        Block block = material.getMaterial();
        Map<String, String> properties = new HashMap<>(block.properties());

        int expected = material.getPropertiesAmount();
        for(int i = 0; i < expected; i++) {
            String key = mis.readStringByte();
            if(!properties.containsKey(key)) { //Skip the property. The name probably changed, but we don't know what to.
                mis.skipBytes(mis.readShort() * Character.BYTES);
                continue;
            }

            String value = mis.readStringShort();
            properties.put(key, value);
        }

        boolean hasSnbt = mis.readBoolean();
        String snbt = hasSnbt ? mis.readStringInt() : null;

        return new MagmaBlock(sectionIndex, material, properties, snbt);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeInt(this.sectionIndex);
        mos.writeInt(this.material.getIndex());

        for(Map.Entry<String, String> ent : this.properties.entrySet()) {
            String key = ent.getKey();
            mos.writeStringByte(key);

            String value = ent.getValue();
            mos.writeStringShort(value);
        }

        if(this.snbt != null && !this.snbt.isEmpty()) {
            mos.writeBoolean(true);
            mos.writeStringInt(this.snbt);
        } else {
            mos.writeBoolean(false);
        }
    }

}
