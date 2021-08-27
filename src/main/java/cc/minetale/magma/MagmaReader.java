package cc.minetale.magma;

import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.type.MagmaRegion;
import net.minestom.server.MinecraftServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;

public class MagmaReader {

    public static CompletableFuture<MagmaRegion> read(String name) {
        var path = MagmaUtils.DIRECTORY.resolve(name + "." + MagmaUtils.FORMAT_NAME);
        var file = path.toFile();

        if(!file.exists())
            throw new IllegalArgumentException("Magma region file doesn't exist");

        CompletableFuture<MagmaRegion> future = new CompletableFuture<>();

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            try (MagmaInputStream in = new MagmaInputStream(
                    new FileInputStream(file))) {

                // X-axis and Z-axis length respectively
                int xSize = in.readShort();
                System.out.println(xSize);
                int zSize = in.readShort();
                System.out.println(zSize);

                int bitmaskLength = in.readInt();
                BitSet populatedChunks = in.readBitSet(bitmaskLength);
                System.out.println(bitmaskLength);

                int materialPaletteSize = in.readInt();
                byte[] materialPalette = in.readCompressed();
                System.out.println(materialPaletteSize);

                short biomePaletteSize = in.readShort();
                byte[] biomePalette = in.readCompressed();
                System.out.println(biomePaletteSize);

                byte[] chunksData = in.readCompressed();

                future.complete(new MagmaRegion(
                        xSize, zSize,
                        populatedChunks,
                        materialPaletteSize, materialPalette,
                        biomePaletteSize,    biomePalette,
                        chunksData
                ));
            } catch(IOException e) {
                e.printStackTrace();
                future.complete(null);
            }
        }).schedule();

        return future;
    }

}
