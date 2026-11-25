package ee.taltech.ite4120.reports.photo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.helex.commons.exception.ApiClientException;
import org.springframework.stereotype.Component;

/**
 * Draws a photo anew, so nothing of the file but its pixels is kept. The size is read
 * before decoding, because a small file can claim a picture that fills the memory.
 */
@Component
public class PhotoCleaner {

    private static final int MAX_SIDE = 1600;
    private static final long MAX_PIXELS = 50_000_000;

    public byte[] clean(byte[] file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw refused();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw refused();
                }
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new ApiClientException("the photo has too many pixels");
                }
                return redraw(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw refused();
        }
    }

    private static byte[] redraw(BufferedImage photo) throws IOException {
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(photo.getWidth(), photo.getHeight()));
        int w = Math.max(1, (int) Math.round(photo.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(photo.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(photo, 0, 0, w, h, Color.WHITE, null);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bytes);
        return bytes.toByteArray();
    }

    private static ApiClientException refused() {
        return new ApiClientException("the file must be a JPEG or PNG picture");
    }
}
