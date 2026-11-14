package com.darajaagentx.api.devtunnel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Takes one program out of a .zip or a .tar.gz download. It reads only what it needs and never writes anywhere but the folder it is given. */
final class Archives {

    private Archives() {
    }

    /** Writes the file called wantedName (in any folder inside the archive) to targetFolder and returns its path. */
    static Path extract(byte[] data, String wantedName, Path targetFolder) throws IOException {
        Files.createDirectories(targetFolder);
        Path target = targetFolder.resolve(wantedName);
        boolean zip = data.length > 3 && data[0] == 'P' && data[1] == 'K';
        boolean gzip = data.length > 2 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b;
        boolean found;
        if (zip) {
            found = fromZip(data, wantedName, target);
        } else if (gzip) {
            found = fromTarGz(data, wantedName, target);
        } else {
            throw new IOException("the download is not a .zip or .tar.gz file");
        }
        if (!found) {
            throw new IOException(wantedName + " was not inside the download");
        }
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows has no such permissions and does not need them
        }
        return target;
    }

    private static boolean fromZip(byte[] data, String wanted, Path target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && baseName(entry.getName()).equals(wanted)) {
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fromTarGz(byte[] data, String wanted, Path target) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] header = new byte[512];
            while (readFully(in, header)) {
                if (isZero(header)) {
                    return false;
                }
                String name = text(header, 0, 100);
                String prefix = text(header, 345, 155);
                long size = octal(header, 124, 12);
                char type = (char) header[156];
                long padded = (size + 511) / 512 * 512;
                boolean regular = type == '0' || type == 0;
                if (regular && baseName(prefix.isEmpty() ? name : prefix + "/" + name).equals(wanted)) {
                    byte[] content = in.readNBytes((int) size);
                    if (content.length < size) {
                        throw new IOException("the download is cut short");
                    }
                    Files.write(target, content);
                    return true;
                }
                skip(in, padded);
            }
        }
        return false;
    }

    private static boolean readFully(InputStream in, byte[] into) throws IOException {
        int read = in.readNBytes(into, 0, into.length);
        return read == into.length;
    }

    private static void skip(InputStream in, long count) throws IOException {
        long left = count;
        byte[] buffer = new byte[8192];
        while (left > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read < 0) {
                throw new IOException("the download is cut short");
            }
            left -= read;
        }
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String text(byte[] block, int from, int length) {
        int end = from;
        while (end < from + length && block[end] != 0) {
            end++;
        }
        return new String(block, from, end - from, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] block, int from, int length) {
        String s = text(block, from, length).strip();
        return s.isEmpty() ? 0 : Long.parseLong(s, 8);
    }

    private static String baseName(String path) {
        String p = path.replace('\\', '/');
        return p.substring(p.lastIndexOf('/') + 1);
    }
}
