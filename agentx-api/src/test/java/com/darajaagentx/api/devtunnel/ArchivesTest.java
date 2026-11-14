package com.darajaagentx.api.devtunnel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchivesTest {

    static byte[] tarGz(String... namesAndContents) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            byte[] content = namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8);
            byte[] header = new byte[512];
            byte[] name = namesAndContents[i].getBytes(StandardCharsets.UTF_8);
            System.arraycopy(name, 0, header, 0, name.length);
            byte[] size = String.format("%011o", content.length).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(size, 0, header, 124, size.length);
            header[156] = '0';
            tar.write(header);
            tar.write(content);
            tar.write(new byte[(512 - content.length % 512) % 512]);
        }
        tar.write(new byte[1024]);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(tar.toByteArray());
        }
        return gz.toByteArray();
    }

    static byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void takesTheProgramOutOfATarGzEvenInsideAFolder(@TempDir Path folder) throws IOException {
        byte[] data = tarGz("README.md", "hello", "zrok2_2.0.0_linux_amd64/zrok2", "the program");
        Path written = Archives.extract(data, "zrok2", folder.resolve("bin"));
        assertThat(written).isEqualTo(folder.resolve("bin").resolve("zrok2"));
        assertThat(Files.readString(written)).isEqualTo("the program");
    }

    @Test
    void takesTheProgramOutOfAZip(@TempDir Path folder) throws IOException {
        Path written = Archives.extract(zip("ngrok.exe", "windows program"), "ngrok.exe", folder);
        assertThat(Files.readString(written)).isEqualTo("windows program");
    }

    @Test
    void complainsWhenTheProgramIsNotThere(@TempDir Path folder) throws IOException {
        assertThatThrownBy(() -> Archives.extract(tarGz("other", "x"), "zrok2", folder))
                .isInstanceOf(IOException.class).hasMessageContaining("was not inside");
    }

    @Test
    void complainsAboutSomethingThatIsNotAnArchive(@TempDir Path folder) {
        assertThatThrownBy(() -> Archives.extract("<html>not found</html>".getBytes(StandardCharsets.UTF_8), "zrok2", folder))
                .isInstanceOf(IOException.class).hasMessageContaining("not a .zip or .tar.gz");
    }
}
