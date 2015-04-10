/*******************************************************************************
 * Copyright (c) 2012, 2015 THALES GLOBAL SERVICES and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.business.internal.session.danalysis;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.sirius.viewpoint.DAnalysis;
import org.eclipse.sirius.viewpoint.SiriusPlugin;
import org.eclipse.sirius.viewpoint.ViewpointPackage;

/**
 * Helper to collect top-level semantic resources.
 * 
 * @author <a href="mailto:esteban.dugueperoux@obeo.fr">Esteban Dugueperoux</a>
 */
public final class SemanticResourceGetter {
    private SemanticResourceGetter() {
        // Prevent instanciation.
    }

    /**
     * Finds all the resources referenced as semantic models by any of the
     * analysis in the session which are not known to be controlled semantic
     * resources.
     * <p>
     * Uses {@code DAnalysisSessionEObjectImpl.getControlledResources()} to
     * determine if a resources is known to be controlled.
     * <p>
     * Performs a full walk on all referenced DAnalysis to identify their
     * models. Will load the analysis if needed, but not the semantic models.
     * 
     * @param session
     *            the session.
     * @return all the registered top-level semantic resources in the session.
     */
    public static Collection<Resource> collectTopLevelSemanticResources(DAnalysisSessionImpl session) {
        Collection<Resource> semanticResources = new LinkedHashSet<Resource>();
        for (DAnalysis analysis : session.allAnalyses()) {
            @SuppressWarnings("unchecked")
            InternalEList<EObject> semanticRoots = (InternalEList<EObject>) analysis.eGet(ViewpointPackage.Literals.DANALYSIS__MODELS, false);
            for (EObject semanticRoot : semanticRoots.basicList()) {
                if (semanticRoot != null) {
                    try {
                        Resource resource = semanticRoot.eResource();
                        if (resource != null) {
                            semanticResources.add(resource);
                        }
                    } catch (IllegalStateException e) {
                        if (SiriusPlugin.getDefault().isDebugging()) {
                            SiriusPlugin.getDefault().getLog().log(new Status(IStatus.WARNING, SiriusPlugin.ID, e.getMessage(), e));
                        }
                    }
                }
            }
        }
        semanticResources.removeAll(session.getControlledResources());
        return semanticResources;
    }
}
