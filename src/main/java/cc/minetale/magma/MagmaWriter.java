package cc.minetale.magma;

import cc.minetale.magma.stream.MagmaOutputStream;
import cc.minetale.magma.type.MagmaRegion;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public class MagmaWriter {

    public static boolean write(MagmaRegion region, String name) {
        try {
            var path = MagmaUtils.DIRECTORY.resolve(name + "." + MagmaUtils.FORMAT_NAME);
            var file = path.toFile();

            Files.deleteIfExists(path);
            file.getParentFile().mkdirs();

            MagmaOutputStream mos = new MagmaOutputStream(new FileOutputStream(file));

            region.write(mos);

            mos.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }

}
