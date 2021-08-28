package cc.minetale.magma.type;

import cc.minetale.magma.MagmaWriter;
import cc.minetale.magma.palette.StatePalette;
import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.NamespaceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.plaf.nimbus.State;
import java.io.IOException;

@Getter @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MagmaMaterial {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagmaWriter.class);

    private final int index;

    private final boolean isCustom;

    private final NamespaceID id;
    private final short propertiesAmount; //Amount of properties to compare to the expected amount for a block in case it's different between versions

    @Setter private StatePalette statePalette;

    public MagmaMaterial(int index, boolean isCustom, Block block) {
        this.index = index;
        this.isCustom = isCustom;
        this.id = block.namespace();
        this.propertiesAmount = (short) block.properties().size();

        var statePalette = new StatePalette();
        statePalette.addState(block.stateId());

        this.statePalette = statePalette;
    }

    public MagmaMaterial(int index, boolean isCustom, NamespaceID id) {
        this.index = index;
        this.isCustom = isCustom;
        this.id = id;
        this.propertiesAmount = (short) Block.fromNamespaceId(id).properties().size();
    }

    /**
     * @return Base block without any properties/with a default state. See also {@linkplain MagmaBlock#getBlock()}.
     */
    public Block getMaterial() {
        return Block.fromNamespaceId(this.id);
    }

    public static MagmaMaterial read(int index, MagmaInputStream mis) throws IOException {
        boolean isCustom = mis.readBoolean();

        NamespaceID id;
        if(!isCustom) {
            id = NamespaceID.from(mis.readStringShort());
        } else {
            //TODO Figure out custom blocks
            id = Block.DIRT.namespace();
            mis.skipBytes(mis.readShort()); //Skip the ID until implemented
        }

        MagmaMaterial material = new MagmaMaterial(index, isCustom, id);

        StatePalette statePalette = StatePalette.read(mis, material);
        material.setStatePalette(statePalette);

        return material;
    }

    public void write(MagmaOutputStream mos) throws IOException {
        mos.writeBoolean(this.isCustom);
        mos.writeStringShort(this.id.asString());

        this.statePalette.write(mos);
    }
}
