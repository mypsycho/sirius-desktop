/*******************************************************************************
 * Copyright (c) 2007, 2021 THALES GLOBAL SERVICES and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.diagram.business.internal.metamodel.spec;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreEList;
import org.eclipse.sirius.diagram.AbstractDNode;
import org.eclipse.sirius.diagram.DDiagram;
import org.eclipse.sirius.diagram.DDiagramElement;
import org.eclipse.sirius.diagram.DDiagramElementContainer;
import org.eclipse.sirius.diagram.DNode;
import org.eclipse.sirius.diagram.DiagramPackage;
import org.eclipse.sirius.diagram.business.internal.metamodel.operations.model.DDiagramElementContainerOperations;
import org.eclipse.sirius.diagram.business.internal.metamodel.operations.model.DDiagramElementSpecOperations;
import org.eclipse.sirius.diagram.description.ContainerMapping;
import org.eclipse.sirius.diagram.description.DiagramElementMapping;
import org.eclipse.sirius.diagram.description.DragAndDropTargetDescription;
import org.eclipse.sirius.diagram.description.NodeMapping;
import org.eclipse.sirius.diagram.impl.DNodeListImpl;
import org.eclipse.sirius.viewpoint.Style;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;

/**
 * Implementation of {@link org.eclipse.sirius.viewpoint.DNodeList}.
 * 
 * @author cbrun, mchauvin, ymortier
 */
public class DNodeListSpec extends DNodeListImpl {
    @Override
    public EList<DDiagramElement> getElements() {
        final EList<DDiagramElement> result = new BasicEList<DDiagramElement>();
        result.addAll(getOwnedBorderedNodes());
        result.addAll(getOwnedElements());
        return new EcoreEList.UnmodifiableEList<DDiagramElement>(eInternalContainer(), DiagramPackage.eINSTANCE.getDDiagramElementContainer_Elements(), result.size(), result.toArray());
    }

    @Override
    public DiagramElementMapping getMapping() {
        return getActualMapping();
    }

    /*
     * Behavior that should come thanks to viewpointelementcontainer.
     */

    @Override
    public DDiagram getParentDiagram() {
        return DDiagramElementSpecOperations.getParentDiagram(this);
    }

    @Override
    public EList<DNode> getNodes() {
        final Collection<AbstractDNode> result = DDiagramElementContainerOperations.getNodes(this);
        Collection<DNode> dNodeResult = new ArrayList<DNode>();
        for (AbstractDNode dNode : Collections2.filter(result, Predicates.instanceOf(DNode.class))) {
            dNodeResult.add((DNode) dNode);
        }
        return new EcoreEList.UnmodifiableEList<DNode>(eInternalContainer(), DiagramPackage.eINSTANCE.getDDiagramElementContainer_Nodes(), dNodeResult.size(), dNodeResult.toArray());
    }

    @Override
    public EList<DDiagramElementContainer> getContainers() {
        final Collection<DDiagramElementContainer> result = DDiagramElementContainerOperations.getContainers(this);
        return new EcoreEList.UnmodifiableEList<DDiagramElementContainer>(eInternalContainer(), DiagramPackage.eINSTANCE.getDDiagram_Containers(), result.size(), result.toArray());
    }

    @Override
    public EList<DDiagramElementContainer> getContainersFromMapping(final ContainerMapping mapping) {
        return DDiagramElementContainerOperations.getContainersFromMapping(this, mapping);

    }

    @Override
    public EList<DNode> getNodesFromMapping(final NodeMapping mapping) {
        return DDiagramElementContainerOperations.getNodesFromMapping(this, mapping);

    }

    @Override
    public Style getStyle() {
        return getOwnedStyle();
    }

    @Override
    public DragAndDropTargetDescription getDragAndDropDescription() {
        return DDiagramElementContainerOperations.getDragAndDropDescription(this);
    }

    @Override
    public String toString() {
        return "NodeList " + getName(); //$NON-NLS-1$
    }
}
