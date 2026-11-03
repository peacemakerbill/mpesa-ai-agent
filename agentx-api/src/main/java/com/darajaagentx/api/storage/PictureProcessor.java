package com.darajaagentx.api.storage;

import com.darajaagentx.api.exception.InvalidActionRequestException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Makes a profile picture safe and small. The file is decoded and drawn again, so anything that was not a picture is
 * refused, and the metadata is dropped (a phone photo carries the place it was taken). The result is at most 512 pixels
 * on its longest side.
 */
public final class PictureProcessor {

    public static final int MAX_SIDE = 512;
    private static final long MAX_PIXELS = 40_000_000L;   // refuse a decompression bomb before decoding it

    /** The file to keep. [type] is image/png or image/jpeg. */
    public record Result(byte[] data, String type, String extension, int width, int height) {
    }

    private PictureProcessor() {
    }

    public static Result process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidActionRequestException("The picture is empty.");
        }
        String kind = sniff(input);
        if (kind == null) {
            throw new InvalidActionRequestException("Use a PNG or JPEG picture.");
        }
        BufferedImage source = decode(input);
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        boolean alpha = source.getColorModel().hasAlpha() && "png".equals(kind);
        BufferedImage out = new BufferedImage(nw, nh, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!alpha) {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, nw, nh);
            }
            g.drawImage(source, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (alpha) {
                if (!ImageIO.write(out, "png", bytes)) {
                    throw new IOException("no PNG writer");
                }
                return new Result(bytes.toByteArray(), "image/png", "png", nw, nh);
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            return new Result(bytes.toByteArray(), "image/jpeg", "jpg", nw, nh);
        } catch (IOException e) {
            throw new InvalidActionRequestException("That picture could not be processed. Try another one.");
        }
    }

    private static BufferedImage decode(byte[] input) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new InvalidActionRequestException("That file is not a picture this app can read. Use a PNG or JPEG.");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new InvalidActionRequestException("That picture is too large. Use one under 40 megapixels.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidActionRequestException("That picture could not be read.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidActionRequestException invalid) {
                throw invalid;
            }
            throw new InvalidActionRequestException("That picture could not be read. Try another one.");
        }
    }

    /** "png" or "jpg" from the first bytes, or null. The type the client claims is not trusted. */
    static String sniff(byte[] b) {
        if (b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length > 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) {
            return "jpg";
        }
        return null;
    }
}
