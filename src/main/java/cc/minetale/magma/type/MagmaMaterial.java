package cc.minetale.magma.type;

import cc.minetale.magma.MagmaWriter;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.NamespaceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Getter @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MagmaMaterial {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaWriter.class);

    private final int index;

    private final boolean isCustom;

    private final NamespaceID id;
    private final short propertiesAmount; //Amount of properties to compare to the expected amount for a block in case it's different between versions

    public MagmaMaterial(int index, boolean isCustom, Block block) {
        this.index = index;
        this.isCustom = isCustom;
        this.id = block.namespace();
        this.propertiesAmount = (short) block.properties().size();
    }

    /**
     * @return Base block without any properties/with a default state. See also {@linkplain MagmaBlock#getBlock()}.
     */
    public Block getMaterial() {
        return Block.fromNamespaceId(this.id);
    }

    public static MagmaMaterial read(int index, MagmaInputStream in) throws IOException {
        boolean isCustom = in.readBoolean();

        NamespaceID id;
        if(!isCustom) {
            id = NamespaceID.from(in.readStringShort());
        } else {
            //TODO Figure out custom blocks
            id = Block.DIRT.namespace();
            in.skipBytes(in.readShort()); //Skip the ID until implemented
        }

        short propertiesAmount = in.readShort();

        MagmaMaterial material = new MagmaMaterial(index, isCustom, id, propertiesAmount);

        var expectedPropsAmount = material.getMaterial().properties().size();
        if(expectedPropsAmount != material.propertiesAmount) {
            LOGGER.warn("Amount of properties in the material doesn't match the one expected by the game. Maybe changed between versions?\n" +
                            "Before Expected: {}. Now Expects: {}.",
                    propertiesAmount, expectedPropsAmount);
        }

        return material;
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeBoolean(this.isCustom);
        mos.writeStringShort(this.id.asString());
        mos.writeShort(this.propertiesAmount);
    }
}
