package cc.minetale.magma.type;

import cc.minetale.magma.palette.MaterialPalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTException;
import org.jglrxavpok.hephaistos.parser.SNBTParser;

import java.io.IOException;
import java.io.StringReader;

@Getter @AllArgsConstructor()
public class MagmaBlock {

    private final short sectionIndex; //Position index relative to the section

    private final MagmaMaterial material; //Material gotten from the Magma palette
    private final short stateId;

    private final @Nullable String snbt;

    public MagmaBlock(short sectionIndex, MagmaMaterial material, Block block) {
        this.sectionIndex = sectionIndex;

        this.material = material;

        this.stateId = block.stateId();

        if(block.hasNbt()) {
            this.snbt = block.nbt().toSNBT();
        } else {
            this.snbt = null;
        }
    }

    /**
     * @return Block with properties/state and NBT if any. See also {@linkplain MagmaMaterial#getMaterial()}.
     */
    public Block getBlock() {
        Block block = Block.fromStateId(this.stateId);

        if(this.snbt != null && !this.snbt.isEmpty()) {
            try {
                block.withNbt((NBTCompound) new SNBTParser(new StringReader(this.snbt)).parse());
            } catch(NBTException e) {
                e.printStackTrace();
            }
        }

        return block;
    }

    public static MagmaBlock read(MaterialPalette materialPalette, MagmaInputStream mis) throws IOException {
        short sectionIndex = mis.readShort();

        var materialIndex = mis.readInt();
        MagmaMaterial material = materialPalette.getMaterialAt(materialIndex);

        var stateIndex = mis.readShort();

        var statePalette = material.getStatePalette();
        short stateId = statePalette.getStateAt(stateIndex);

        var hasSnbt = mis.readBoolean();
        String snbt = hasSnbt ? mis.readStringInt() : null;

        return new MagmaBlock(sectionIndex, material, stateId, snbt);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeShort(this.sectionIndex);
        mos.writeInt(this.material.getIndex());

        var statePalette = this.material.getStatePalette();
        mos.writeShort(statePalette.findInPaletteOrAdd(this.stateId));

        if(this.snbt != null && !this.snbt.isEmpty()) {
            mos.writeBoolean(true);
            mos.writeStringInt(this.snbt);
        } else {
            mos.writeBoolean(false);
        }
    }

}
