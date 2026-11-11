package org.uml.model.relations;

import java.util.Objects;
import org.uml.model.components.ClassComponent;
import org.uml.model.components.ComponentBase;
import org.uml.model.components.InterfaceComponent;

/**
 * Is relation in UML class diagrams. Describes relation used when an object
 * <i>extends</i> another one.
 *
 * @author NUGS
 */
public class IsRelation extends RelationBase {

    /**
     * Returns the name of the relation.
     *
     * @return "Is"
     */
    @Override
    public String toString() {
        return "Is";
    }

    @Override
    public boolean canConnect(ComponentBase source, ComponentBase target) {
        Class<?> sc = source.getClass();
        Class<?> tc = target.getClass();
        if (source == target) {
            return false;
        }
        if (sc == ClassComponent.class && tc == ClassComponent.class) {
            return true;
        }
        if (sc == InterfaceComponent.class && tc == InterfaceComponent.class) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.source);
        if (source instanceof InterfaceComponent) {
            hash = 67 * hash + Objects.hashCode(this.target);
        }
        return hash;
    }

    // A class has one superclass, while an interface can have several parents.
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RelationBase other = (RelationBase) obj;
        if (!Objects.equals(this.source, other.source)) {
            return false;
        }
        return !(source instanceof InterfaceComponent) || Objects.equals(this.target, other.target);
    }
}
