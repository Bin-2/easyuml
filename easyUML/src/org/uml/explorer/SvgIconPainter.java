package org.uml.explorer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.ResourcePolicy;
import com.github.weisj.jsvg.attributes.ViewBox;

/**
 * SVG reader for canvas icons and exports, independent of the IDE's SVG APIs.
 */
public final class SvgIconPainter {

    private static final ConcurrentMap<String, SoftReference<SVGDocument>> DOCUMENTS = new ConcurrentHashMap<>();
    public static final RenderingHints.Key VECTOR_EXPORT = new RenderingHints.Key(0) {
        @Override
        public boolean isCompatibleValue(Object value) {
            return value instanceof Boolean;
        }
    };

    private SvgIconPainter() {
    }

    public static void paint(URL source, Graphics2D target, int width, int height) {
        try {
            SVGDocument document = load(source);
            Graphics2D graphics = (Graphics2D) target.create();
            try {
                document.render((java.awt.Component) null, graphics, new ViewBox(0, 0, width, height));
            } finally {
                graphics.dispose();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot render SVG icon: " + source, ex);
        }
    }

    static SVGDocument load(URL source) throws IOException {
        String key = source.toExternalForm();
        SoftReference<SVGDocument> reference = DOCUMENTS.get(key);
        SVGDocument cached = reference == null ? null : reference.get();
        if (cached != null) {
            return cached;
        }
        try (InputStream input = source.openStream()) {
            LoaderContext context = LoaderContext.builder().externalResourcePolicy(ResourcePolicy.DENY_ALL).build();
            SVGDocument document = new SVGLoader().load(input, null, context);
            if (document == null) {
                throw new IOException("Invalid SVG icon: " + source);
            }
            DOCUMENTS.put(key, new SoftReference<>(document));
            return document;
        }
    }
}
