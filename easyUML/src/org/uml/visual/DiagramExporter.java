package org.uml.visual;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.Widget;
import org.uml.visual.widgets.ClassDiagramScene;
import org.uml.visual.widgets.ClassDiagramScene.RelationshipView;
import org.uml.visual.widgets.relations.RelationBaseWidget;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.freehep.graphicsio.svg.SVGGraphics2D;

/**
 * Full-diagram Java2D export. Scene access is confined to the Swing event
 * thread.
 */
public final class DiagramExporter {

    public enum Format {
        PNG, SVG, PDF
    }
    private static final int MARGIN = 16;
    private static final long MAX_PIXELS = 32_000_000L;

    private DiagramExporter() {
    }

    /**
     * Return content bounds in scene units, independent of viewport and editor
     * zoom.
     */
    public static Rectangle bounds(ClassDiagramScene scene) throws IOException {
        return onEdt(() -> {
            try (CleanScene ignored = new CleanScene(scene)) {
                return contentBounds(scene);
            }
        });
    }

    public static Dimension pngSize(Rectangle bounds, int scale) throws IOException {
        if (scale < 1 || scale > 4) {
            throw new IOException("PNG scale must be between 1x and 4x.");
        }
        long width = (long) bounds.width * scale;
        long height = (long) bounds.height * scale;
        if (width < 1 || height < 1 || width > 32768 || height > 32768
                || width * height > MAX_PIXELS) {
            throw new IOException("PNG would be " + width + " × " + height
                    + " pixels. Use a lower scale or SVG/PDF (PNG limit: 32 million pixels, 32768 per side).");
        }
        return new Dimension((int) width, (int) height);
    }

    public static Path withExtension(Path selected, Format format) {
        String name = selected.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        for (Format candidate : Format.values()) {
            String suffix = "." + candidate.name().toLowerCase(Locale.ROOT);
            if (lower.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }
        return selected.resolveSibling(name + "." + format.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Render on EDT; encode and atomically replace on caller's thread (the
     * dialog uses a worker).
     */
    public static void export(ClassDiagramScene scene, Path destination, Format format, int scale) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path temporary = Files.createTempFile(target.getParent(), ".easyuml-export-", ".tmp");
        try (Output output = onEdt(() -> prepare(scene, format, scale))) {
            output.write(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Output prepare(ClassDiagramScene scene, Format format, int scale) throws Exception {
        try (CleanScene ignored = new CleanScene(scene)) {
            Rectangle area = contentBounds(scene);
            switch (format) {
                case PNG: {
                    Dimension size = pngSize(area, scale);
                    long rasterBytes = (long) size.width * size.height * 4;
                    Runtime runtime = Runtime.getRuntime();
                    long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
                    // Leave room for encoding, the active IDE and transient image/graphics buffers.
                    if (rasterBytes * 3 > available / 2) {
                        throw new IOException("Insufficient memory for " + size.width + " × " + size.height
                                + " pixels (raster alone: " + (rasterBytes / 1048576) + " MiB). Use SVG/PDF or a lower scale.");
                    }
                    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        paint(scene, graphics, area, scale);
                    } catch (RuntimeException | Error ex) {
                        image.flush();
                        throw ex;
                    } finally {
                        graphics.dispose();
                    }
                    return new Output() {
                        public void write(Path path) throws IOException {
                            if (!ImageIO.write(image, "png", path.toFile())) {
                                throw new IOException("No PNG writer available.");
                            }
                        }

                        public void close() {
                            image.flush();
                        }
                    };
                }
                case SVG: {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    StrictSvgGraphics graphics = new StrictSvgGraphics(bytes, new Dimension(area.width, area.height));
                    java.util.Properties properties = new java.util.Properties();
                    properties.setProperty(SVGGraphics2D.TEXT_AS_SHAPES, "false");
                    graphics.setProperties(properties);
                    graphics.setRenderingHint(org.uml.explorer.SvgIconPainter.VECTOR_EXPORT, Boolean.TRUE);
                    try {
                        graphics.startExport();
                        paint(scene, graphics, area, 1);
                        graphics.endExport();
                    } finally {
                        graphics.dispose();
                    }
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes.toByteArray()));
                    Element root = document.getDocumentElement();
                    root.setAttribute("viewBox", "0 0 " + area.width + " " + area.height);
                    normalizeSvgFonts(root);
                    return new Output() {
                        public void write(Path path) throws IOException {
                            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                                Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
                                transformer.transform(new javax.xml.transform.dom.DOMSource(document),
                                        new javax.xml.transform.stream.StreamResult(writer));
                            } catch (javax.xml.transform.TransformerException ex) {
                                throw new IOException("Cannot write SVG", ex);
                            }
                        }

                        public void close() {
                        }
                    };
                }
                case PDF: {
                    PDDocument document = new PDDocument();
                    try {
                        // 96 scene units/inch -> 72 PDF points/inch. UserUnit permits oversized single pages.
                        double unit = Math.max(1, Math.max(area.width, area.height) * 0.75 / 14400);
                        float width = (float) (area.width * 0.75 / unit);
                        float height = (float) (area.height * 0.75 / unit);
                        PDPage page = new PDPage(new PDRectangle(width, height));
                        if (unit > 1) {
                            page.setUserUnit((float) unit);
                        }
                        document.addPage(page);
                        PdfBoxGraphics2D graphics = new PdfBoxGraphics2D(document, width, height);
                        graphics.setRenderingHint(org.uml.explorer.SvgIconPainter.VECTOR_EXPORT, Boolean.TRUE);
                        try {
                            paint(scene, graphics, area, 0.75 / unit);
                        } finally {
                            graphics.dispose();
                        }
                        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                            stream.drawForm(graphics.getXFormObject());
                        }
                        return new Output() {
                            public void write(Path path) throws IOException {
                                document.save(path.toFile());
                            }

                            public void close() throws IOException {
                                document.close();
                            }
                        };
                    } catch (Exception | Error ex) {
                        document.close();
                        throw ex;
                    }
                }
                default:
                    throw new IOException("Unsupported export format.");
            }
        }
    }

    /**
     * FreeHEP normally logs writer errors; exports must fail instead of
     * replacing a file with partial output.
     */
    private static final class StrictSvgGraphics extends SVGGraphics2D {

        StrictSvgGraphics(java.io.OutputStream output, Dimension size) {
            super(output, size);
        }

        private StrictSvgGraphics(StrictSvgGraphics original) {
            super(original, true);
        }

        @Override
        protected void handleException(Exception ex) {
            throw new IllegalStateException("SVG rendering failed", ex);
        }

        @Override
        public java.awt.Graphics create() {
            try {
                writeGraphicsSave();
            } catch (IOException ex) {
                handleException(ex);
            }
            return new StrictSvgGraphics(this);
        }

        @Override
        public java.awt.Graphics create(double x, double y, double width, double height) {
            StrictSvgGraphics copy = (StrictSvgGraphics) create();
            copy.translate(x, y);
            copy.clip(new java.awt.geom.Rectangle2D.Double(0, 0, width, height));
            return copy;
        }
    }

    private static void paint(ClassDiagramScene scene, Graphics2D graphics, Rectangle area, double scale) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.scale(scale, scale);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, area.width, area.height);
        graphics.setPaint(scene.getBackground());
        graphics.fillRect(0, 0, area.width, area.height);
        graphics.clipRect(0, 0, area.width, area.height);
        graphics.translate(-area.x, -area.y);
        // Scene.paint does not apply the view zoom. It draws the original widgets, routes and labels.
        scene.paint(graphics);
    }

    private static void normalizeSvgFonts(Element element) {
        // Java logical fonts are not installed font names in browsers. Retain actual family names.
        String family = element.getAttribute("font-family").replace("'", "").replace("\"", "");
        if (family.equals("Dialog") || family.equals("SansSerif") || family.equals("Default")) {
            element.setAttribute("font-family", "sans-serif");
        } else if (family.equals("DialogInput") || family.equals("Monospaced")) {
            element.setAttribute("font-family", "monospace");
        } else if (family.equals("Serif")) {
            element.setAttribute("font-family", "serif");
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                normalizeSvgFonts((Element) child);
            }
        }
    }

    private static Rectangle contentBounds(ClassDiagramScene scene) throws IOException {
        Rectangle area = null;
        for (Widget layer : Arrays.asList(scene.getMainLayer(), scene.getConnectionLayer())) {
            area = includeBounds(layer, area);
        }
        if (area == null) {
            area = new Rectangle(0, 0, 1, 1);
        }
        if (area.width > Integer.MAX_VALUE - 2 * MARGIN || area.height > Integer.MAX_VALUE - 2 * MARGIN) {
            throw new IOException("Diagram bounds exceed Java2D coordinate limits.");
        }
        area.grow(MARGIN, MARGIN);
        return area;
    }

    private static Rectangle includeBounds(Widget widget, Rectangle area) {
        if (!widget.isVisible()) {
            return area;
        }
        // Layer bounds can include the scene origin/viewport; only include actual content descendants.
        if (!(widget instanceof org.netbeans.api.visual.widget.LayerWidget) && widget.getBounds() != null) {
            Rectangle bounds = widget.convertLocalToScene(widget.getBounds());
            if (!bounds.isEmpty()) {
                area = area == null ? new Rectangle(bounds) : area.union(bounds);
            }
        }
        for (Widget child : widget.getChildren()) {
            area = includeBounds(child, area);
        }
        return area;
    }

    /**
     * Changes only widget presentation, never ObjectScene selection or the
     * diagram model.
     */
    private static final class CleanScene implements AutoCloseable {

        private final ClassDiagramScene scene;
        private final List<Widget> widgets = new ArrayList<>();
        private final List<ObjectState> states = new ArrayList<>();
        private final List<ConnectionWidget> connections = new ArrayList<>();
        private final List<Boolean> controls = new ArrayList<>();
        private final List<ConnectionWidget> revealedRelationships = new ArrayList<>();
        private final boolean interactionVisible;

        CleanScene(ClassDiagramScene scene) {
            this.scene = scene;
            interactionVisible = scene.getInterractionLayer().isVisible();
            boolean filteredRelationships = scene.getRelationshipView() != RelationshipView.ALL;
            collect(scene);
            Collections.reverse(widgets); // Members can change parent hover state; restore parents last.
            for (Widget widget : widgets) {
                states.add(widget.getState());
            }
            try {
                for (Widget widget : widgets) {
                    widget.setState(ObjectState.createNormal());
                    if (widget instanceof ConnectionWidget) {
                        ConnectionWidget connection = (ConnectionWidget) widget;
                        connections.add(connection);
                        controls.add(connection.isPaintControlPoints());
                        connection.setPaintControlPoints(false);
                        if (filteredRelationships && connection instanceof RelationBaseWidget
                                && !connection.isVisible()) {
                            revealedRelationships.add(connection);
                            connection.setVisible(true);
                        }
                    }
                }
                scene.getInterractionLayer().setVisible(false);
                scene.validate();
            } catch (RuntimeException | Error ex) {
                close();
                throw ex;
            }
        }

        private void collect(Widget widget) {
            widgets.add(widget);
            for (Widget child : widget.getChildren()) {
                collect(child);
            }
        }

        public void close() {
            for (int i = 0; i < widgets.size(); i++) {
                widgets.get(i).setState(states.get(i));
            }
            for (int i = 0; i < connections.size(); i++) {
                connections.get(i).setPaintControlPoints(controls.get(i));
            }
            for (ConnectionWidget connection : revealedRelationships) {
                connection.setVisible(false);
            }
            scene.getInterractionLayer().setVisible(interactionVisible);
            scene.validate();
        }
    }

    private interface Output extends AutoCloseable {

        void write(Path path) throws IOException;

        @Override
        void close() throws IOException;
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws IOException {
        FutureTask<T> task = new FutureTask<>(action);
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
        try {
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Export interrupted.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof OutOfMemoryError) {
                throw new IOException("Not enough memory to export. Use a lower PNG scale or a smaller diagram.", cause);
            }
            throw new IOException("Could not render diagram: " + cause.getMessage(), cause);
        }
    }
}
