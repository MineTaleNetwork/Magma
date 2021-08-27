package cc.minetale.magma.stream;

import com.github.luben.zstd.Zstd;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;

public class MagmaInputStream extends DataInputStream {

    public MagmaInputStream(InputStream in) {
        super(in);
    }

    public int[] readIntArray(final int count) throws IOException {
        var arr = new int[count];

        for (int i = 0; i < count; i++) {
            arr[i] = readInt();
        }

        return arr;
    }

    public int[] readIntArray() throws IOException {
        return readIntArray(readInt());
    }

    public byte[] readByteArray(final int length) throws IOException {
        var arr = new byte[length];

        int readByteCount = read(arr);

        if (readByteCount == -1) {
            throw new EOFException();
        }

        return arr;
    }

    public byte[] readByteArray() throws IOException {
        var length = readInt();
        if(length == 0) { return new byte[0]; }

        return readByteArray(length);
    }

    public char[] readCharArray(final int length) throws IOException {
        var arr = new char[length];

        for (int i = 0; i < length; i++) {
            arr[i] = readChar();
        }

        return arr;
    }

    public BitSet readBitSet(final int byteCount) throws IOException {
        byte[] raw = readByteArray(byteCount);

        return BitSet.valueOf(raw);
    }

    public String parseString(int chars) throws IOException {
        char[] raw = readCharArray(chars);
        return new String(raw);
    }

    public String readStringByte() throws IOException {
        return parseString(readByte());
    }

    public String readStringShort() throws IOException {
        return parseString(readShort());
    }

    public String readStringInt() throws IOException {
        return parseString(readInt());
    }

    /**
     * Reads a block of zstd-compressed data. This method
     * expects the following ints to be the compressed size,
     * and uncompressed size respectively.
     *
     * @return the uncompressed data
     * @throws IOException if the bytes cannot be read
     * @throws IllegalArgumentException if the uncompressed length doesn't match
     */
    public byte[] readCompressed() throws IOException {
        var compressedLength = readInt();
        var uncompressedLength = readInt();

        byte[] compressed = readByteArray(compressedLength);
        byte[] data = Zstd.decompress(compressed, uncompressedLength);

        if (data.length != uncompressedLength) {
            throw new IllegalArgumentException("Uncompressed length doesn't match");
        }

        return data;
    }

    /**
     * Skips a block of zstd-compressed data.
     *
     * @return the number of bytes skipped
     * @throws IOException if the bytes cannot be skipped
     * @see #readCompressed() for requirements
     */
    public long skipCompressed() throws IOException {
        var compressedLength = readInt();

        // Skip uncompressed length + compressed data
        return skip(4 + compressedLength);
    }
}
