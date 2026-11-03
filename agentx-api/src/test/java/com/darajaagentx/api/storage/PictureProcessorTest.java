package com.darajaagentx.api.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PictureProcessorTest {

    private static byte[] encode(int w, int h, int type, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, type);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, type == BufferedImage.TYPE_INT_ARGB ? 0x80336699 : 0x336699);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void aBigJpegIsShrunkToFiveHundredAndTwelve() throws Exception {
        PictureProcessor.Result r = PictureProcessor.process(encode(4000, 3000, BufferedImage.TYPE_INT_RGB, "jpg"));
        assertThat(r.type()).isEqualTo("image/jpeg");
        assertThat(r.extension()).isEqualTo("jpg");
        assertThat(r.width()).isEqualTo(512);
        assertThat(r.height()).isEqualTo(384);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(r.data()));
        assertThat(back.getWidth()).isEqualTo(512);
    }

    @Test
    void aSmallPictureIsNotEnlargedAndAnAlphaPngStaysPng() throws Exception {
        PictureProcessor.Result r = PictureProcessor.process(encode(100, 80, BufferedImage.TYPE_INT_ARGB, "png"));
        assertThat(r.type()).isEqualTo("image/png");
        assertThat(r.width()).isEqualTo(100);
        assertThat(r.height()).isEqualTo(80);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(r.data()));
        assertThat(back.getColorModel().hasAlpha()).isTrue();
        PictureProcessor.Result flat = PictureProcessor.process(encode(100, 80, BufferedImage.TYPE_INT_RGB, "png"));
        assertThat(flat.type()).isEqualTo("image/jpeg");
    }

    @Test
    void whatIsNotAPictureIsRefusedWhateverItClaimsToBe() throws Exception {
        assertThatThrownBy(() -> PictureProcessor.process("<svg onload=alert(1)></svg>".getBytes())).hasMessageContaining("PNG or JPEG");
        assertThatThrownBy(() -> PictureProcessor.process("GIF89a....".getBytes())).hasMessageContaining("PNG or JPEG");
        assertThatThrownBy(() -> PictureProcessor.process(new byte[0])).hasMessageContaining("empty");
        assertThatThrownBy(() -> PictureProcessor.process(null)).hasMessageContaining("empty");
        byte[] png = encode(50, 50, BufferedImage.TYPE_INT_RGB, "png");
        byte[] cut = java.util.Arrays.copyOf(png, 30);
        assertThatThrownBy(() -> PictureProcessor.process(cut)).hasMessageContaining("picture");
        // a script appended after a real picture does not survive: the picture is drawn again
        byte[] tail = "<script>alert(1)</script>".getBytes();
        byte[] withTail = new byte[png.length + tail.length];
        System.arraycopy(png, 0, withTail, 0, png.length);
        System.arraycopy(tail, 0, withTail, png.length, tail.length);
        String out = new String(PictureProcessor.process(withTail).data(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(out).doesNotContain("<script>");
    }

    @Test
    void anHugePixelCountIsRefusedBeforeItIsDecoded() throws Exception {
        // 9000 x 9000 is 81 megapixels
        BufferedImage img = new BufferedImage(9000, 9000, BufferedImage.TYPE_BYTE_GRAY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        assertThatThrownBy(() -> PictureProcessor.process(out.toByteArray())).hasMessageContaining("too large");
    }
}
