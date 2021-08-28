package cc.minetale.magma.palette;

import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import cc.minetale.magma.type.MagmaMaterial;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMaps;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import lombok.Getter;
import net.minestom.server.instance.block.Block;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Getter
public class StatePalette {

    private final Short2ObjectMap<Short> palette;

    public StatePalette(int expected) {
        this.palette = new Short2ObjectOpenHashMap<>(expected);
    }

    public StatePalette() {
        this.palette = new Short2ObjectOpenHashMap<>();
    }

    //TODO Fix docs
    /**
     * Tries to find stateId in the given Magma palette and inserts the provided one if not found.
     * @param stateId stateId to find the index for
     * @return The index of an already inserted stateId or a newly created index for the provided stateId if none were found
     */
    public short findInPaletteOrAdd(short stateId) {
        synchronized(this.palette) {
            for(var ent : Short2ObjectMaps.fastIterable(this.palette)) {
                short existingState = ent.getValue();
                if(existingState == stateId) { return ent.getShortKey(); }
            }

            return addState(stateId);
        }
    }

    public short addState(short stateId) {
        synchronized(this.palette) {
            short newIndex = getSize();
            setStateAt(newIndex, stateId);
            return newIndex;
        }
    }

    public boolean addStateIfNotFound(short stateId) {
        synchronized(this.palette) {
            for(var existingState : this.palette.values()) {
                if(existingState == stateId) { return false; }
            }

            short newIndex = getSize();
            setStateAt(newIndex, stateId);

            return true;
        }
    }

    public short getStateAt(short index) {
        return this.palette.get(index);
    }

    public void setStateAt(short index, short stateId) {
        this.palette.put(index, Short.valueOf(stateId));
    }

    public short getSize() {
        return (short) this.palette.size();
    }

    public Short2ObjectMap<Short> getAll() {
        return this.palette;
    }

    public static StatePalette read(MagmaInputStream mis, MagmaMaterial material) throws IOException {
        Block block = material.getMaterial();

        var size = material.getPropertiesAmount();
        short combinations = mis.readShort();

        StatePalette palette = new StatePalette();
        for(short i = 0; i < combinations; i++) {
            try {
                Map<String, String> properties = new HashMap<>();
                for(short j = 0; j < size; j++) {
                    String key = mis.readStringShort();
                    String value = mis.readStringShort();
                    properties.put(key, value);
                }

                short stateId = block.withProperties(properties).stateId();

                palette.setStateAt(i, stateId);
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        return palette;
    }

    public void write(MagmaOutputStream mos) throws IOException {
        var size = getSize();
        mos.writeShort(size);

        for(short i = 0; i < size; i++) {
            short stateId = getStateAt(i);

            var block = Block.fromStateId(stateId);
            if(block == null) { continue; }

            Map<String, String> properties = block.properties();

            for(Map.Entry<String, String> ent : properties.entrySet()) {
                mos.writeStringShort(ent.getKey());
                mos.writeStringShort(ent.getValue());
            }
        }
    }

}
