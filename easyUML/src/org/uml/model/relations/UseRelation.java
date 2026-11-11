package org.uml.model.relations;

import java.util.Objects;
import org.uml.model.components.ClassComponent;
import org.uml.model.components.ComponentBase;
import org.uml.model.components.InterfaceComponent;

/**
 * Use(s) relation in UML class diagrams. Describes relation used usually when
 * an object depends of another one - most often the "using" end in a
 * relationship will be aware of the other end it's using, but it's not always
 * the case the "used" end is aware who is using it. Implemented usually when
 * there is an parameter or a return type in a method or a local variable which
 * has another type.
 *
 *
 * @author NUGS
 */
public class UseRelation extends RelationBase {

    /**
     * Cardinality of the source component in the relation.
     */
    private CardinalityEnum cardinalitySource;

    /**
     * Cardinality of the target component in the relation.
     */
    private CardinalityEnum cardinalityTarget;

    /**
     * Returns the name of the relation
     *
     * @return "Use"
     */
    @Override
    public String toString() {
        return "Use";
    }

    /**
     * Returns a CardinalityEnum element that describes the cardinality at
     * source side of the relation.
     *
     * @return cardinality at source
     * @see CardinalityEnum
     */
    public CardinalityEnum getCardinalitySource() {
        return cardinalitySource;
    }

    /**
     * Sets the Cardinality at source side of the relation
     *
     * @param cardinalitySource cardinality at source of a relation
     * @see CardinalityEnum
     */
    public void setCardinalitySource(CardinalityEnum cardinalitySource) {
        this.cardinalitySource = cardinalitySource;
    }

    /**
     * Returns a CardinalityEnum element that describes the cardinality at
     * target side of the relation
     *
     * @return cardinality at target
     * @see CardinalityEnum
     */
    public CardinalityEnum getCardinalityTarget() {
        return cardinalityTarget;
    }

    /**
     * Sets the Cardinality at target side of the relation
     *
     * @param cardinalityTarget cardinality at target of a relation
     * @see CardinalityEnum
     */
    public void setCardinalityTarget(CardinalityEnum cardinalityTarget) {
        this.cardinalityTarget = cardinalityTarget;
    }

    @Override
    public boolean canConnect(ComponentBase source, ComponentBase target) {
        Class<?> sc = source.getClass();
        if (sc == ClassComponent.class || sc == InterfaceComponent.class) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.source);
        hash = 97 * hash + Objects.hashCode(this.target);
        return hash;
    }

    // TODO If needed, redo this equals in terms that it might be possible to have
    // multiple use relations of the same name.
    @Override
    public boolean equals(Object obj) {
        return obj instanceof RelationBase && matchesWithName((RelationBase) obj, name);
    }

    @Override
    public boolean matchesWithName(RelationBase other, String proposedName) {
        return other != null && getClass() == other.getClass()
                && Objects.equals(source, other.source)
                && Objects.equals(target, other.target)
                && Objects.equals(proposedName, other.name);
    }
}
