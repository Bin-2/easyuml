/*
 * Copyright (c) 2014, Ilija
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.uml.jung;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.netbeans.api.visual.widget.Widget;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.uml.visual.widgets.ClassDiagramScene;
import org.uml.visual.widgets.components.ComponentWidgetBase;

/**
 * Arranges the rendered class boxes in rows on the Visual Library scene.
 */
public class JUNGEngine {

    private static final int MARGIN = 40;
    private static final int GAP = 40;
    private static final double TARGET_ASPECT_RATIO = 1.5;

    private ClassDiagramScene scene;

    public JUNGEngine(ClassDiagramScene scene) {
        this.scene = scene;
    }

    public ClassDiagramScene getClassDiagramScene() {
        return scene;
    }

    public void setScene(ClassDiagramScene scene) {
        this.scene = scene;
    }

    public void applyJUNGLayout() {
        if (scene == null) {
            return;
        }
        scene.validate();

        List<MeasuredBox> boxes = new ArrayList<>();
        double paddedArea = 0;
        int widest = 0;
        for (Widget widget : scene.getMainLayer().getChildren()) {
            if (!(widget instanceof ComponentWidgetBase) || !widget.isVisible()) {
                continue;
            }
            Rectangle bounds = widget.getBounds();
            if (bounds == null || bounds.isEmpty()) {
                return;
            }
            boxes.add(new MeasuredBox(widget, bounds));
            widest = Math.max(widest, bounds.width);
            paddedArea += (bounds.width + (double) GAP) * (bounds.height + (double) GAP);
        }

        // Group boxes of similar height to reduce empty space within each row.
        boxes.sort(Comparator.comparingInt((MeasuredBox box) -> box.bounds.height).reversed());
        long canvasLimit = Integer.MAX_VALUE - MARGIN;
        long targetWidth = (long) Math.ceil(Math.sqrt(paddedArea * TARGET_ASPECT_RATIO));
        long rowWidth = Math.min(canvasLimit - MARGIN, Math.max(widest, targetWidth));
        long x = MARGIN;
        long y = MARGIN;
        int rowHeight = 0;

        // Compute every position before moving any widgets.
        for (MeasuredBox box : boxes) {
            if (rowHeight > 0 && x + box.bounds.width > MARGIN + rowWidth) {
                x = MARGIN;
                y += rowHeight + (long) GAP;
                rowHeight = 0;
            }

            // The preferred location is the widget origin, including when a resize
            // has left the visible bounds offset from that origin.
            long originX = x - box.bounds.x;
            long originY = y - box.bounds.y;
            if (x + box.bounds.width > canvasLimit || y + box.bounds.height > canvasLimit
                    || originX > Integer.MAX_VALUE || originY > Integer.MAX_VALUE) {
                DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                        "The class boxes are too large to arrange within the diagram canvas.",
                        NotifyDescriptor.WARNING_MESSAGE));
                return;
            }
            box.location = new Point((int) originX, (int) originY);
            x += box.bounds.width + (long) GAP;
            rowHeight = Math.max(rowHeight, box.bounds.height);
        }

        boolean moved = false;
        for (MeasuredBox box : boxes) {
            if (!box.location.equals(box.widget.getPreferredLocation())) {
                box.widget.setPreferredLocation(box.location);
                moved = true;
            }
        }
        if (moved) {
            scene.validate();
            scene.repaint();
            scene.getUmlTopComponent().notifyModified();
        }
    }

    private static final class MeasuredBox {
        private final Widget widget;
        private final Rectangle bounds;
        private Point location;

        private MeasuredBox(Widget widget, Rectangle bounds) {
            this.widget = widget;
            this.bounds = bounds;
        }
    }
}
