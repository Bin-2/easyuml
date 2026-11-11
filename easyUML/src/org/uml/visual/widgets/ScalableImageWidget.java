package org.uml.visual.widgets;

import javax.swing.Icon;
import org.netbeans.api.visual.widget.ImageWidget;
import org.netbeans.api.visual.widget.Scene;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;

/**
 * ImageWidget layout with Icon painting, preserving NetBeans' SVG/HiDPI
 * delegate.
 */
public final class ScalableImageWidget extends ImageWidget {

    private java.net.URL source;

    public ScalableImageWidget(Scene scene) {
        super(scene);
    }

    public void setImageResource(String resource) {
        // Old hosts use the PNG for layout; newer ImageUtilities selects its
        // SVG sibling. Canvas painting below uses SVG geometry on both.
        String fallback = resource.endsWith(".svg")
                ? resource.substring(0, resource.length() - 4) + ".png" : resource;
        setImage(ImageUtilities.loadImage(fallback));
        ClassLoader loader = Lookup.getDefault().lookup(ClassLoader.class);
        if (loader == null) {
            loader = ScalableImageWidget.class.getClassLoader();
        }
        source = loader.getResource(resource);
    }

    @Override
    public void setImage(java.awt.Image image) {
        source = null;
        super.setImage(image);
    }

    @Override
    protected void paintWidget() {
        if (getImage() == null) {
            return;
        }
        Icon icon = ImageUtilities.image2Icon(getImage());
        if (isPaintAsDisabled()) {
            icon = ImageUtilities.createDisabledIcon(icon);
        }
        java.awt.Graphics2D graphics = getGraphics();
        if ((!isPaintAsDisabled()
                || Boolean.TRUE.equals(graphics.getRenderingHint(org.uml.explorer.SvgIconPainter.VECTOR_EXPORT)))
                && source != null && source.getPath().endsWith(".svg")) {
            org.uml.explorer.SvgIconPainter.paint(source, graphics, icon.getIconWidth(), icon.getIconHeight());
            return;
        }
        if (graphics.getDeviceConfiguration() != null) {
            icon.paintIcon(getScene().getView(), graphics, 0, 0);
        } else {
            // NB24 CachedHiDPIIcon requires a graphics configuration. Vector writers have none.
            // Rasterize just this small icon at 4x; the surrounding diagram stays vector content.
            int width = icon.getIconWidth(), height = icon.getIconHeight();
            java.awt.image.BufferedImage raster = new java.awt.image.BufferedImage(
                    width * 4, height * 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D target = raster.createGraphics();
            try {
                target.scale(4, 4);
                icon.paintIcon(getScene().getView(), target, 0, 0);
            } finally {
                target.dispose();
            }
            try {
                graphics.drawImage(raster, 0, 0, width, height, null);
            } finally {
                raster.flush();
            }
        }
    }
}
