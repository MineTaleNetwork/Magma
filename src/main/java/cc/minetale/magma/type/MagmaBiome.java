package cc.minetale.magma.type;

import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.stream.MagmaOutputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.biomes.Biome;

import java.io.IOException;

@Getter @RequiredArgsConstructor
public class MagmaBiome {

    private final short index;
    private final NamespaceID name;

    public Biome getBiome() {
        return MinecraftServer.getBiomeManager().getByName(name);
    }

    public static MagmaBiome read(short index, MagmaInputStream in) throws IOException {
        NamespaceID id = NamespaceID.from(in.readStringShort());
        return new MagmaBiome(index, id);
    }

    public void write(MagmaOutputStream mos) throws IOException {
        var nameStr = this.name.asString();
        mos.writeStringShort(nameStr);
    }

}