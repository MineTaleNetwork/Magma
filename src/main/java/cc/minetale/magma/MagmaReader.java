package cc.minetale.magma;

import cc.minetale.magma.stream.MagmaInputStream;
import cc.minetale.magma.type.MagmaRegion;
import net.minestom.server.MinecraftServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MagmaReader {

    public static CompletableFuture<MagmaRegion> read(String name) {
        var path = MagmaUtils.DIRECTORY.resolve(name + "." + MagmaUtils.FORMAT_NAME);
        var file = path.toFile();

        if(!file.exists())
            throw new IllegalArgumentException("Magma region file doesn't exist");

        CompletableFuture<MagmaRegion> future = new CompletableFuture<>();

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            try (MagmaInputStream mis = new MagmaInputStream(
                    new FileInputStream(file))) {

                var region = MagmaRegion.read(mis);
                future.complete(region);
            } catch(IOException e) {
                e.printStackTrace();
                future.complete(null);
            }
        }).schedule();

        return future;
    }

}
