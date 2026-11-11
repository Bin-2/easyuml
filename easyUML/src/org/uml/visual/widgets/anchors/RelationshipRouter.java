package org.uml.visual.widgets.anchors;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.swing.Timer;
import org.netbeans.api.visual.anchor.Anchor;
import org.netbeans.api.visual.router.Router;
import org.netbeans.api.visual.router.RouterFactory;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.ConnectionWidget.RoutingPolicy;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;
import org.openide.awt.StatusDisplayer;
import org.uml.visual.widgets.ClassDiagramScene;
import org.uml.visual.widgets.components.ComponentWidgetBase;
import org.uml.visual.widgets.relations.RelationBaseWidget;

/**
 * Keeps automatic paths tied to the current diagram geometry.
 * Routing runs in short event-thread batches after the layout settles.
 */
public final class RelationshipRouter implements Router, Scene.SceneListener {

    private static final int BOX_CLEARANCE = 4;
    private static final int SEARCH_PADDING = 12;
    private static final int LINE_CLEARANCE = 6;
    private static final int SEARCH_MARGIN = 64;
    private static final int MAX_SEARCH_BOXES = 32;
    private static final int MAX_SEARCH_LINES = 16;
    private static final int SEARCH_ATTEMPTS = 3;
    private static final long BATCH_TIME = TimeUnit.MILLISECONDS.toNanos(10);

    private final ClassDiagramScene scene;
    private final Router directRouter = RouterFactory.createDirectRouter();
    private final Map<ConnectionWidget, List<Point>> paths = new LinkedHashMap<>();
    private final Timer timer;
    private Snapshot snapshot;
    private int nextConnection;
    private boolean enabled;
    private boolean paused;

    public RelationshipRouter(ClassDiagramScene scene) {
        this.scene = scene;
        timer = new Timer(15, event -> routeBatch());
        timer.setInitialDelay(120);
        scene.addSceneListener(this);
    }

    public static void route(ClassDiagramScene scene) {
        scene.setRelationshipView(ClassDiagramScene.RelationshipView.ALL);
        RelationshipRouter router = scene.getRelationshipRouter();
        router.enabled = true;
        router.resume();
    }

    public void resume() {
        paused = false;
        if (enabled) {
            scene.validate();
            refresh(true);
            scene.validate();
        }
    }

    public void pause() {
        paused = true;
        timer.stop();
    }

    @Override
    public List<Point> routeConnection(ConnectionWidget connection) {
        List<Point> direct = directRouter.routeConnection(connection);
        List<Point> path = paths.get(connection);
        if (path != null && direct.size() >= 2
                && path.get(0).equals(direct.get(0))
                && path.get(path.size() - 1).equals(direct.get(direct.size() - 1))) {
            return path;
        }
        // A moved endpoint must never keep bends calculated for its old position.
        return direct;
    }

    @Override
    public void sceneValidated() {
        if (enabled && !paused) {
            refresh(false);
        }
    }

    @Override
    public void sceneValidating() {
    }

    @Override
    public void sceneRepaint() {
    }

    private void refresh(boolean force) {
        Snapshot current = capture();
        if (!force && current.sameGeometry(snapshot)) {
            return;
        }
        timer.stop();
        snapshot = current;
        nextConnection = 0;
        paths.clear();
        for (Connection connection : snapshot.connections) {
            if (connection.automatic) {
                if (connection.widget.getRouter() != this) {
                    connection.widget.setRouter(this);
                } else {
                    connection.widget.reroute();
                }
            }
        }
        if (!snapshot.connections.isEmpty()) {
            timer.restart();
        }
    }

    private Snapshot capture() {
        Snapshot current = new Snapshot();
        for (Widget widget : scene.getMainLayer().getChildren()) {
            if (widget instanceof ComponentWidgetBase && widget.isVisible()) {
                Rectangle bounds = widget.getBounds();
                if (bounds != null && !bounds.isEmpty()) {
                    current.boxes.put(widget, widget.convertLocalToScene(bounds));
                }
            }
        }
        if (snapshot == null || !current.boxes.equals(snapshot.boxes)) {
            // A stationary box also needs new ports when the opposite end moves.
            Set<Anchor> anchors = new LinkedHashSet<>();
            for (Widget widget : scene.getConnectionLayer().getChildren()) {
                if (widget instanceof RelationBaseWidget) {
                    ConnectionWidget connection = (ConnectionWidget) widget;
                    anchors.add(connection.getSourceAnchor());
                    anchors.add(connection.getTargetAnchor());
                }
            }
            anchors.remove(null);
            for (Anchor anchor : anchors) {
                anchor.revalidateDependency();
            }
        }
        for (Widget widget : scene.getConnectionLayer().getChildren()) {
            if (!(widget instanceof RelationBaseWidget)) {
                continue;
            }
            ConnectionWidget connection = (ConnectionWidget) widget;
            Anchor source = connection.getSourceAnchor();
            Anchor target = connection.getTargetAnchor();
            if (source == null || target == null
                    || !current.boxes.containsKey(source.getRelatedWidget())
                    || !current.boxes.containsKey(target.getRelatedWidget())) {
                continue;
            }
            boolean automatic = source.getRelatedWidget() != target.getRelatedWidget()
                    && connection.getRoutingPolicy() == RoutingPolicy.ALWAYS_ROUTE;
            List<Point> points = new ArrayList<>();
            if (automatic) {
                points.addAll(directRouter.routeConnection(connection));
            } else {
                for (Point point : connection.getControlPoints()) {
                    points.add(connection.convertLocalToScene(point));
                }
            }
            if (points.size() >= 2) {
                current.connections.add(new Connection(connection, source.getRelatedWidget(),
                        target.getRelatedWidget(), automatic, points));
            }
        }
        return current;
    }

    private void routeBatch() {
        if (paused || snapshot == null) {
            timer.stop();
            return;
        }
        Snapshot current = snapshot;
        scene.validate();
        if (current != snapshot) {
            return;
        }

        long started = System.nanoTime();
        while (nextConnection < current.connections.size()) {
            Connection connection = current.connections.get(nextConnection++);
            if (connection.automatic) {
                List<Point> path = findPath(connection);
                paths.put(connection.widget, path);
                connection.widget.reroute();
            }
            if (System.nanoTime() - started >= BATCH_TIME) {
                break;
            }
        }
        scene.validate();
        if (current != snapshot) {
            return;
        }
        if (nextConnection == current.connections.size()) {
            timer.stop();
            StatusDisplayer.getDefault().setStatusText("Automatic relationship routing active");
        } else {
            StatusDisplayer.getDefault().setStatusText("Routing relationships "
                    + nextConnection + " / " + current.connections.size());
        }
    }

    private List<Point> findPath(Connection connection) {
        Map<Widget, Rectangle> obstacles = new LinkedHashMap<>();
        // The orthogonal router needs both endpoint boxes to find the exit corridors.
        obstacles.put(connection.source, padded(snapshot.boxes.get(connection.source), SEARCH_PADDING));
        obstacles.put(connection.target, padded(snapshot.boxes.get(connection.target), SEARCH_PADDING));
        Point start = connection.points.get(0);
        Point end = connection.points.get(connection.points.size() - 1);
        for (Map.Entry<Widget, Rectangle> box : snapshot.boxes.entrySet()) {
            if (obstacles.size() >= MAX_SEARCH_BOXES) {
                break;
            }
            if (nearRoute(box.getValue(), start, end)) {
                obstacles.put(box.getKey(), padded(box.getValue(), SEARCH_PADDING));
            }
        }

        List<Rectangle> verticalLines = new ArrayList<>();
        List<Rectangle> horizontalLines = new ArrayList<>();
        collectLines(connection, verticalLines, horizontalLines);
        List<Point> path = search(connection, obstacles, verticalLines, horizontalLines);
        if (path == null && (!verticalLines.isEmpty() || !horizontalLines.isEmpty())) {
            // Box avoidance takes priority when there is no separate lane available.
            verticalLines.clear();
            horizontalLines.clear();
            path = search(connection, obstacles, verticalLines, horizontalLines);
        }
        return path == null ? connection.points : path;
    }

    private List<Point> search(Connection connection, Map<Widget, Rectangle> obstacles,
            List<Rectangle> verticalLines, List<Rectangle> horizontalLines) {
        Router searchRouter = RouterFactory.createOrthogonalSearchRouter((vertical, horizontal) -> {
            vertical.addAll(obstacles.values());
            horizontal.addAll(obstacles.values());
            vertical.addAll(verticalLines);
            horizontal.addAll(horizontalLines);
        });
        for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
            List<Point> candidate = searchRouter.routeConnection(connection.widget);
            if (candidate == null || candidate.size() < 2 || length(candidate) == 0
                    || !candidate.get(0).equals(connection.points.get(0))
                    || !candidate.get(candidate.size() - 1).equals(connection.points.get(connection.points.size() - 1))
                    || length(candidate) > length(connection.points) * 6 + 256) {
                return null;
            }
            boolean clear = true;
            for (Map.Entry<Widget, Rectangle> box : snapshot.boxes.entrySet()) {
                if (crossesBox(candidate, connection, box.getKey(), box.getValue())) {
                    clear = false;
                    if (obstacles.containsKey(box.getKey()) || obstacles.size() >= MAX_SEARCH_BOXES) {
                        return null;
                    }
                    obstacles.put(box.getKey(), padded(box.getValue(), SEARCH_PADDING));
                }
            }
            if (clear) {
                return candidate;
            }
        }
        return null;
    }

    private boolean crossesBox(List<Point> path, Connection connection, Widget widget, Rectangle bounds) {
        Rectangle box = padded(bounds, BOX_CLEARANCE);
        for (int i = 1; i < path.size(); i++) {
            if ((i == 1 && widget == connection.source)
                    || (i == path.size() - 1 && widget == connection.target)) {
                continue;
            }
            Point from = path.get(i - 1);
            Point to = path.get(i);
            if (box.intersectsLine(from.x, from.y, to.x, to.y)) {
                return true;
            }
        }
        return false;
    }

    private void collectLines(Connection connection, List<Rectangle> verticalLines, List<Rectangle> horizontalLines) {
        Point start = connection.points.get(0);
        Point end = connection.points.get(connection.points.size() - 1);
        for (Connection other : snapshot.connections) {
            if (other == connection) {
                continue;
            }
            List<Point> path = other.automatic ? paths.get(other.widget) : other.points;
            if (path == null) {
                continue;
            }
            for (int i = 1; i < path.size(); i++) {
                if (verticalLines.size() + horizontalLines.size() >= MAX_SEARCH_LINES) {
                    return;
                }
                Point from = path.get(i - 1);
                Point to = path.get(i);
                if (from.equals(to) || (from.x != to.x && from.y != to.y)) {
                    continue;
                }
                Rectangle segment = new Rectangle(from);
                segment.add(to);
                if (nearRoute(segment, start, end)) {
                    List<Rectangle> collisions = from.x == to.x ? verticalLines : horizontalLines;
                    collisions.add(padded(segment, LINE_CLEARANCE));
                }
            }
        }
    }

    private static Rectangle padded(Rectangle bounds, int padding) {
        Rectangle rectangle = new Rectangle(bounds);
        rectangle.grow(padding, padding);
        return rectangle;
    }

    private static boolean nearRoute(Rectangle bounds, Point start, Point end) {
        return padded(bounds, SEARCH_MARGIN).intersectsLine(start.x, start.y, end.x, end.y);
    }

    private static double length(List<Point> points) {
        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distance(points.get(i));
        }
        return length;
    }

    private static final class Snapshot {

        private final Map<Widget, Rectangle> boxes = new LinkedHashMap<>();
        private final List<Connection> connections = new ArrayList<>();

        private boolean sameGeometry(Snapshot previous) {
            if (previous == null || !boxes.equals(previous.boxes)
                    || connections.size() != previous.connections.size()) {
                return false;
            }
            for (int i = 0; i < connections.size(); i++) {
                if (!connections.get(i).sameGeometry(previous.connections.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Connection {

        private final ConnectionWidget widget;
        private final Widget source;
        private final Widget target;
        private final boolean automatic;
        private final List<Point> points;

        private Connection(ConnectionWidget widget, Widget source, Widget target,
                boolean automatic, List<Point> points) {
            this.widget = widget;
            this.source = source;
            this.target = target;
            this.automatic = automatic;
            this.points = points;
        }

        private boolean sameGeometry(Connection previous) {
            return widget == previous.widget && source == previous.source && target == previous.target
                    && automatic == previous.automatic && points.equals(previous.points);
        }
    }
}
