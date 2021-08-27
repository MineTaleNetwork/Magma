package cc.minetale.magma.stream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.BitSet;

public class MagmaOutputStream extends DataOutputStream {

    public MagmaOutputStream(OutputStream out) {
        super(out);
    }

    public void writeStringByte(String string) throws IOException {
        writeByte(string.length());
        writeChars(string);
    }

    public void writeStringShort(String string) throws IOException {
        writeShort(string.length());
        writeChars(string);
    }

    public void writeStringInt(String string) throws IOException {
        writeInt(string.length());
        writeChars(string);
    }

    public void writeBitSetByte(BitSet bitSet) throws IOException {
        byte[] bytes = bitSet.toByteArray();
        writeByte(bytes.length);
        write(bytes);
    }

    public void writeBitSetShort(BitSet bitSet) throws IOException {
        byte[] bytes = bitSet.toByteArray();
        writeShort(bytes.length);
        write(bytes);
    }

    public void writeBitSetInt(BitSet bitSet) throws IOException {
        byte[] bytes = bitSet.toByteArray();
        writeInt(bytes.length);
        write(bytes);
    }

    public void writeByteArray(byte[] bytes) throws IOException {
        writeInt(bytes.length);
        write(bytes);
    }

}
