package org.uml.explorer;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.net.URL;
import javax.swing.Icon;
import org.openide.util.CachedHiDPIIcon;
import org.openide.util.Lookup;
import org.openide.util.spi.SVGLoader;

/** Optional service, loaded only by hosts that have the public SVGLoader SPI. */
public final class SvgIconLoader implements SVGLoader {

    @Override
    public Icon loadIcon(URL source) throws IOException {
        // Keep the IDE's own renderer when it is available, regardless of
        // lookup ordering between providers with the default priority.
        for (SVGLoader loader : Lookup.getDefault().lookupAll(SVGLoader.class)) {
            if (!(loader instanceof SvgIconLoader)) {
                return loader.loadIcon(source);
            }
        }
        SVGDocument document = SvgIconPainter.load(source);
        double width = document.size().getWidth();
        double height = document.size().getHeight();
        if (!Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IOException("Invalid SVG icon dimensions: " + source);
        }
        return new SvgIcon(document, (int) Math.ceil(width), (int) Math.ceil(height));
    }

    private static final class SvgIcon extends CachedHiDPIIcon {
        private final SVGDocument document;

        SvgIcon(SVGDocument document, int width, int height) {
            super(width, height);
            this.document = document;
        }

        @Override
        protected Image createAndPaintImage(Component component, ColorModel colorModel,
                int deviceWidth, int deviceHeight, double scale) {
            BufferedImage image = createBufferedImage(colorModel, deviceWidth, deviceHeight);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.scale(scale, scale);
                document.render(component, graphics, new ViewBox(0, 0, getIconWidth(), getIconHeight()));
            } finally {
                graphics.dispose();
            }
            return image;
        }
    }
}
