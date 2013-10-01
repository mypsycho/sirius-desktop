/*******************************************************************************
 * Copyright (c) 2008, 2009 THALES GLOBAL SERVICES.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.ui.business.api.viewpoint;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.sirius.business.api.componentization.SiriusRegistry;
import org.eclipse.sirius.business.api.helper.SiriusResourceHelper;
import org.eclipse.sirius.business.api.query.IdentifiedElementQuery;
import org.eclipse.sirius.business.api.query.SiriusQuery;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.business.api.session.danalysis.DAnalysisSession;
import org.eclipse.sirius.business.api.session.danalysis.DAnalysisSessionHelper;
import org.eclipse.sirius.business.internal.movida.Movida;
import org.eclipse.sirius.common.tools.api.util.EqualityHelper;
import org.eclipse.sirius.common.tools.api.util.Option;
import org.eclipse.sirius.common.tools.api.util.StringUtil;
import org.eclipse.sirius.common.ui.tools.api.util.SWTUtil;
import org.eclipse.sirius.ui.business.internal.commands.ChangeSiriusSelectionCommand;
import org.eclipse.sirius.viewpoint.description.RepresentationExtensionDescription;
import org.eclipse.sirius.viewpoint.description.Viewpoint;
import org.eclipse.sirius.viewpoint.provider.SiriusEditPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * A class which to show swt widgets with available viewpoints.
 * 
 * @author mchauvin
 */
public final class SiriusSelection {

    private static final String[] COLUMNS = { " ", "icon", "Sirius" };

    /**
     * Avoid instantiation.
     */
    private SiriusSelection() {

    }

    /**
     * Return the lists of corresponding viewpoints.
     * 
     * @param fileExtension
     *            The extension of the semantic model
     * @return The set of corresponding viewpoints, sorted with workspace
     *         viewpoints before plug-in viewpoints, and otherwise by name.
     */
    public static Set<Viewpoint> getSiriuss(final String fileExtension) {
        final Predicate<Viewpoint> isValidSirius = new Predicate<Viewpoint>() {
            public boolean apply(final Viewpoint viewpoint) {
                return new SiriusQuery(viewpoint).handlesSemanticModelExtension(fileExtension != null ? fileExtension : StringUtil.JOKER_STRING);
            }
        };

        final Set<Viewpoint> allSiriuss = SiriusRegistry.getInstance().getSiriuss();
        final Set<Viewpoint> validSiriuss = new HashSet<Viewpoint>();
        validSiriuss.addAll(Collections2.filter(allSiriuss, isValidSirius));
        return validSiriuss;
    }

    /**
     * Return the lists of corresponding viewpoints.
     * 
     * @param fileExtensions
     *            The extensions of the semantic models
     * @return The list of corresponding viewpoints
     */
    private static Set<Viewpoint> getSiriuss(final Collection<String> fileExtensions) {
        final SortedSet<Viewpoint> validSiriuss = new TreeSet<Viewpoint>(new SiriusRegistry.SiriusComparator());
        for (final String extension : fileExtensions) {
            validSiriuss.addAll(SiriusSelection.getSiriuss(extension));
        }
        return validSiriuss;
    }

    /**
     * Returns the semantic extensions of the given session.
     * 
     * @param session
     *            the session.
     * @return the semantic extensions of the given session.
     */
    private static Collection<String> getSemanticFileExtensions(final Session session) {
        final Collection<String> fileExtensions = new HashSet<String>();
        for (final Resource resource : session.getSemanticResources()) {
            if (resource != null && resource.getURI() != null) {
                final String currentFileExtension = resource.getURI().fileExtension();
                if (currentFileExtension != null) {
                    fileExtensions.add(currentFileExtension);
                }
            }
        }
        return fileExtensions;
    }

    /**
     * Create a selection wizard page to select a viewpoint.
     * 
     * @param fileExtension
     *            the semantic file extension.
     * @param viewpointsMap
     *            an empty map, which will be filled
     * @return the wizard page
     * @since 2.0
     */
    public static WizardPage createWizardPage(final String fileExtension, final SortedMap<Viewpoint, Boolean> viewpointsMap) {
        final SortedSet<Viewpoint> viewpoints = new TreeSet<Viewpoint>(new SiriusRegistry.SiriusComparator());
        viewpoints.addAll(SiriusSelection.getSiriuss(fileExtension));

        for (final Viewpoint viewpoint : viewpoints) {
            viewpointsMap.put(viewpoint, Boolean.FALSE);
        }

        final WizardPage page = new WizardPage("viewpointsSelection", "Viewpoint selection", null) {

            public void createControl(final Composite parent) {
                setControl(SiriusSelection.createSiriussTableControl(parent, this.getContainer(), viewpointsMap));
            }

            private boolean isThereOneSelectedSirius() {
                return Maps.filterValues(viewpointsMap, new Predicate<Boolean>() {
                    public boolean apply(final Boolean input) {
                        return input.booleanValue();
                    }
                }).entrySet().iterator().hasNext();
            }

            @Override
            public boolean isPageComplete() {
                return super.isPageComplete() && isThereOneSelectedSirius();
            }

        };
        return page;
    }

    private static Control createSiriussTableControl(final Composite parent, final IWizardContainer wizardContainer, final Map<Viewpoint, Boolean> viewpoints) {
        return SiriusSelection.createSiriussTableControl(parent, viewpoints.keySet(), new WizardSiriussTableLazyCellModifier(viewpoints, wizardContainer), new SiriussTableLabelProvider(
                viewpoints));
    }

    /**
     * A cell modifier which applies to a wizard page.
     * 
     * @author mchauvin
     */
    private static class WizardSiriussTableLazyCellModifier extends SiriussTableLazyCellModifier {

        private final IWizardContainer wizardContainer;

        /**
         * Constructor.
         */
        public WizardSiriussTableLazyCellModifier(final Map<Viewpoint, Boolean> viewpoints, final IWizardContainer wizardContainer) {
            super(viewpoints);
            this.wizardContainer = wizardContainer;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void modify(final Object element, final String property, final Object value) {
            super.modify(element, property, value);

            if (property.equals(COLUMNS[0])) {
                this.wizardContainer.updateButtons();
            }
        }
    }

    /**
     * Create a selection wizard page to select a viewpoint.
     * 
     * @param semanticModel
     *            the semantic model or null.
     * @param viewpointsMap
     *            an empty map which will be filled
     * @return the wizard page
     * @since 2.0
     */
    public static WizardPage createWizardPage(final IFile semanticModel, final SortedMap<Viewpoint, Boolean> viewpointsMap) {

        String semanticExtension = null;

        if (semanticModel != null) {
            semanticExtension = semanticModel.getFileExtension();
        }

        return SiriusSelection.createWizardPage(semanticExtension, viewpointsMap);
    }

    /**
     * Create and open a new dialog to change the viewpoints selection status.
     * 
     * @param session
     *            the session
     */
    public static void openSiriussSelectionDialog(final Session session) {
        openSiriussSelectionDialog(session, true);
    }

    /**
     * Create and open a new dialog to change the viewpoints selection status.
     * 
     * @param session
     *            the session
     * @param createNewRepresentations
     *            true to create new DRepresentation for
     *            RepresentationDescription having their initialization
     *            attribute at true for selected {@link Viewpoint}.
     */
    public static void openSiriussSelectionDialog(final Session session, boolean createNewRepresentations) {
        if (Movida.isEnabled()) {
            session.getSemanticCrossReferencer();
            org.eclipse.sirius.business.internal.movida.registry.SiriusRegistry registry = (org.eclipse.sirius.business.internal.movida.registry.SiriusRegistry) SiriusRegistry
                    .getInstance();
            org.eclipse.sirius.business.internal.movida.SiriusSelection selection = DAnalysisSessionHelper.getSiriusSelection(registry, (DAnalysisSession) session);
            Set<URI> selectedBefore = selection.getSelected();
            SiriusSelectionDialog vsd = new SiriusSelectionDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(), registry, selection, getSemanticFileExtensions(session));
            if (vsd.open() == Window.OK) {
                applySiriusSelectionChange(session, selection, selectedBefore);
            }
        } else {
            session.getSemanticCrossReferencer();
            final SortedMap<Viewpoint, Boolean> viewpointsMap = SiriusSelection.getSiriussWithMonitor(session);

            final SortedMap<Viewpoint, Boolean> copyOfSiriussMap = Maps.newTreeMap(new SiriusRegistry.SiriusComparator());
            copyOfSiriussMap.putAll(viewpointsMap);

            final TitleAreaDialog dialog = new TitleAreaDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell()) {
                @Override
                protected Control createDialogArea(final Composite parent) {
                    return SiriusSelection.createSiriussTableControl(parent, copyOfSiriussMap);
                }

                @Override
                protected void okPressed() {
                    Multimap<String, String> missingDependencies = getMissingDependencies(Maps.filterValues(copyOfSiriussMap, Predicates.equalTo(Boolean.TRUE)).keySet());
                    if (missingDependencies.isEmpty()) {
                        super.okPressed();
                    } else {
                        String message = getMissingDependenciesErrorMessage(missingDependencies);
                        MessageDialog.openInformation(getShell(), "Invalid selection", message);
                    }
                }
            };
            dialog.create();
            dialog.getShell().setText("Viewpoint Selection");
            dialog.setTitle("Selected viewpoints");
            dialog.setMessage("Change viewpoints selection status (see tooltip for details about each viewpoint)");
            dialog.setBlockOnOpen(true);
            dialog.open();

            if (Window.OK == dialog.getReturnCode()) {
                SiriusSelection.applyNewSiriusSelection(viewpointsMap, copyOfSiriussMap, session, createNewRepresentations);
            }
        }
    }

    /**
     * Compute the error message for the given missing dependencies which
     * indicates the required Sirius ativation to complete the current
     * selection.
     * 
     * @param missingDependencies
     *            a multimap, for example the result
     *            {@link SiriusSelection#getMissingDependencies(Set)} which
     *            contains for each selected viewpoint which has missing
     *            dependencies, an entry with the selected viewpoint's name as
     *            key and the list of the missing viewpoints' names as value.
     * @return an error message which indicates the required Sirius ativation
     *         to complete the current selection.
     */
    public static String getMissingDependenciesErrorMessage(Multimap<String, String> missingDependencies) {
        return "The list of selected viewpoints is invalid; please fix the problems:\n" + "- "
                + Joiner.on("\n- ").withKeyValueSeparator(" requires: ").join(Maps.transformValues(missingDependencies.asMap(), new Function<Collection<String>, String>() {
                    public String apply(java.util.Collection<String> from) {
                        return Joiner.on(", ").join(from);
                    }
                }));
    }

    private static void applySiriusSelectionChange(final Session session, org.eclipse.sirius.business.internal.movida.SiriusSelection selection, Set<URI> selectedBefore) {
        Set<URI> selectedAfter = selection.getSelected();
        Set<URI> newSelected = Sets.difference(selectedAfter, selectedBefore);
        Set<URI> newDeselected = Sets.difference(selectedBefore, selectedAfter);
        final SiriusSelection.Callback callback = new SiriusSelectionCallbackWithConfimation();
        final Set<Viewpoint> newSelectedSiriuss = Sets.newHashSet(Iterables.transform(newSelected, new Function<URI, Viewpoint>() {
            public Viewpoint apply(URI from) {
                return SiriusResourceHelper.getCorrespondingSirius(session, from, true).get();
            }
        }));
        final Set<Viewpoint> newDeselectedSiriuss = Sets.newHashSet(Iterables.transform(newDeselected, new Function<URI, Viewpoint>() {
            public Viewpoint apply(URI from) {
                return SiriusResourceHelper.getCorrespondingSirius(session, from, true).get();
            }
        }));
        // Only if there is something to do
        if (!newSelected.isEmpty() || !newDeselected.isEmpty()) {

            try {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                IRunnableContext context = new ProgressMonitorDialog(shell);
                IRunnableWithProgress runnable = new IRunnableWithProgress() {
                    public void run(final IProgressMonitor monitor) {
                        try {
                            monitor.beginTask("Apply new viewpoints selection...", 1);
                            Command command = new ChangeSiriusSelectionCommand(session, callback, newSelectedSiriuss, newDeselectedSiriuss, new SubProgressMonitor(monitor, 1));
                            TransactionalEditingDomain domain = session.getTransactionalEditingDomain();
                            domain.getCommandStack().execute(command);
                        } finally {
                            monitor.done();
                        }
                    }

                };
                PlatformUI.getWorkbench().getProgressService().runInUI(context, runnable, null);
            } catch (final InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e);
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Compute the missing viewpoint dependencies (if any) for all the
     * viewpoints enabled by the user.
     * 
     * @param selected
     *            the viewpoint selection request by the user.
     * @return for each selected viewpoint which has missing dependencies, an
     *         entry with the selected viewpoint's name as key and the list of
     *         the missing viewpoints' names as key.
     */
    public static Multimap<String, String> getMissingDependencies(Set<Viewpoint> selected) {
        Set<String> selectedURIs = Sets.newHashSet(Iterables.filter(Iterables.transform(selected, new Function<Viewpoint, String>() {
            public String apply(Viewpoint from) {
                Option<URI> uri = new SiriusQuery(from).getSiriusURI();
                if (uri.some()) {
                    return uri.get().toString();
                } else {
                    return null;
                }
            }
        }), Predicates.notNull()));

        Multimap<String, String> result = HashMultimap.create();
        for (Viewpoint viewpoint : selected) {
            for (RepresentationExtensionDescription extension : new SiriusQuery(viewpoint).getAllRepresentationExtensionDescriptions()) {
                String extended = extension.getViewpointURI();
                Pattern pattern = Pattern.compile(extended);
                if (!atLeastOneUriMatchesPattern(selectedURIs, pattern)) {
                    result.put(viewpoint.getName(), extended.trim().replaceFirst("^viewpoint:/[^/]+/", ""));
                }
            }
        }
        return result;
    }

    private static boolean atLeastOneUriMatchesPattern(Set<String> selectedURIs, Pattern pattern) {
        for (String uriToMatch : selectedURIs) {
            Matcher matcher = pattern.matcher(uriToMatch);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }


    private static void applyNewSiriusSelection(final SortedMap<Viewpoint, Boolean> originalMap, final SortedMap<Viewpoint, Boolean> newMap, final Session session,
            final boolean createNewRepresentations) {

        // newMap is a copy of originalMap with modifications on values.
        // No elements should have been added.
        if (originalMap.size() != newMap.size()) {
            throw new IllegalArgumentException("Original and new lists of viewpoints should not be 'different'");
        }

        final Set<Viewpoint> newSelectedSiriuss = Sets.newHashSet();
        final Set<Viewpoint> newDeselectedSiriuss = Sets.newHashSet();

        /*
         * newMap and originalMap are sorted with the same comparator and keys
         * haven't changed. We can iterate on the 2 maps together.
         */
        final Iterator<Entry<Viewpoint, Boolean>> originalIterator = originalMap.entrySet().iterator();
        final Iterator<Entry<Viewpoint, Boolean>> newIterator = newMap.entrySet().iterator();

        while (originalIterator.hasNext() && newIterator.hasNext()) {
            final Entry<Viewpoint, Boolean> originalEntry = originalIterator.next();
            final Entry<Viewpoint, Boolean> newEntry = newIterator.next();

            /* XOR : only if original and new booleans are different */
            if (originalEntry.getValue().booleanValue() ^ newEntry.getValue().booleanValue()) {

                // originalEntry and newEntry booleans are differents
                // Just need to test one of them

                // true : has been selected
                if (newEntry.getValue().booleanValue()) {
                    // We can use here originalEntry or newEntry indifferently
                    newSelectedSiriuss.add(originalEntry.getKey());
                } else {
                    // We can use here originalEntry or newEntry indifferently
                    newDeselectedSiriuss.add(originalEntry.getKey());
                }
            }
        }

        final SiriusSelection.Callback callback = new SiriusSelectionCallbackWithConfimation();

        // Only if there is something to do
        if (!newSelectedSiriuss.isEmpty() || !newDeselectedSiriuss.isEmpty()) {

            try {
                IRunnableWithProgress runnable = new IRunnableWithProgress() {
                    public void run(final IProgressMonitor monitor) {
                        Command command = new ChangeSiriusSelectionCommand(session, callback, newSelectedSiriuss, newDeselectedSiriuss, createNewRepresentations, monitor);
                        TransactionalEditingDomain domain = session.getTransactionalEditingDomain();
                        domain.getCommandStack().execute(command);
                    }

                };
                new ProgressMonitorDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()).run(true, false, runnable);
            } catch (final InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e);
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static SortedMap<Viewpoint, Boolean> getSiriussWithMonitor(final Session session) {
        final SortedSet<Viewpoint> allSiriuss = new TreeSet<Viewpoint>(new SiriusRegistry.SiriusComparator());
        final SortedMap<Viewpoint, Boolean> viewpointsMap = Maps.newTreeMap(new SiriusRegistry.SiriusComparator());
        final IProgressService ps = PlatformUI.getWorkbench().getProgressService();
        try {
            ps.busyCursorWhile(new IRunnableWithProgress() {
                public void run(final IProgressMonitor pm) {
                    pm.beginTask("Loading viewpoints...", 4);

                    final Collection<String> semanticFileExtensions = SiriusSelection.getSemanticFileExtensions(session);
                    pm.worked(1);

                    final Set<Viewpoint> viewpoints = SiriusSelection.getSiriuss(semanticFileExtensions);
                    pm.worked(1);

                    allSiriuss.addAll(viewpoints);
                    pm.worked(1);

                    Collection<Viewpoint> selectedSiriuss = session.getSelectedSiriuss(false);

                    for (final Viewpoint viewpoint : allSiriuss) {
                        boolean selected = false;

                        for (Viewpoint selectedSirius : selectedSiriuss) {
                            if (EqualityHelper.areEquals(selectedSirius, viewpoint)) {
                                selected = true;
                                break;
                            }
                        }

                        viewpointsMap.put(viewpoint, Boolean.valueOf(selected));
                    }

                    pm.done();
                }
            });
            return viewpointsMap;
        } catch (final InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create the main table control.
     * 
     * @param parent
     *            the parent
     * @param allSiriuss
     *            the viewpoints
     * @param session
     *            the current session
     * @return the created control.
     */
    private static Control createSiriussTableControl(final Composite parent, final SortedMap<Viewpoint, Boolean> viewpointsMap) {
        return SiriusSelection.createSiriussTableControl(parent, viewpointsMap.keySet(), new SiriussTableLazyCellModifier(viewpointsMap), new SiriussTableLabelProvider(viewpointsMap));
    }

    /**
     * Create the main table control.
     * 
     * @param parent
     *            the parent
     * @param viewpoints
     *            the viewpoints
     * @param cellModifier
     *            the cell modifier
     * @param labelProvider
     *            the label provider
     * @param callback
     *            the callback
     * @return the created control.
     */
    private static Control createSiriussTableControl(final Composite parent, final Set<Viewpoint> viewpoints, final TableViewerAwareCellModifier cellModifier, final IBaseLabelProvider labelProvider) {

        final Composite control = SWTUtil.createCompositeBothFill(parent, 1, false);
        final TableViewer tableViewer = new TableViewer(control, SWT.BORDER | SWT.FULL_SELECTION);
        ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);

        final Table table = tableViewer.getTable();

        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        final TableColumn tc0 = new TableColumn(table, SWT.CENTER, 0);
        tc0.setWidth(30);

        final TableColumn tc1 = new TableColumn(table, SWT.CENTER, 1);
        tc1.setWidth(30);

        final TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.LEFT, 2);
        column.getColumn().setWidth(450);

        table.setSize(new Point(table.getSize().x, 510));

        // resize the last column
        /*
         * control.addControlListener(new ControlAdapter() { public void
         * controlResized(ControlEvent e) { Rectangle area =
         * control.getClientArea(); Point preferredSize =
         * table.computeSize(SWT.DEFAULT, SWT.DEFAULT); int width = area.width -
         * 2 table.getBorderWidth(); if (preferredSize.y > area.height +
         * table.getHeaderHeight()) { // if a vertical scroll bar is required,
         * substract its width Point vBarSize =
         * table.getVerticalBar().getSize(); width -= vBarSize.x; } Point
         * oldSize = table.getSize(); if (oldSize.x > area.width) { // make the
         * last column smaller and then the table tc2.setWidth(width -
         * tc0.getWidth() - tc1.getWidth()); table.setSize(area.width,
         * area.height); } else { // make the table bigger and then teh last
         * column table.setSize(area.width, area.height); tc2.setWidth(width -
         * tc0.getWidth() - tc1.getWidth()); } } });
         */

        // Can only changes the first column - the visible column
        final CellEditor[] editors = new CellEditor[3];
        editors[0] = new CheckboxCellEditor(table);
        for (int i = 1; i < 3; i++) {
            editors[i] = null;
        }

        tableViewer.setColumnProperties(COLUMNS);

        tableViewer.setCellEditors(editors);
        cellModifier.setViewer(tableViewer);
        tableViewer.setCellModifier(cellModifier);
        tableViewer.setContentProvider(new SiriussTableContentProvider());
        tableViewer.setLabelProvider(labelProvider);
        tableViewer.setComparator(new ViewerComparator());

        tableViewer.setInput(viewpoints);

        /* Lines and headers are not visible */
        table.setLinesVisible(false);
        table.setHeaderVisible(false);

        return control;
    }

    /**
     * A simple callback.
     * 
     * @author mchauvin
     */
    public interface Callback {

        /**
         * Select a {@link Viewpoint}.
         * 
         * @param viewpoint
         *            the {@link Viewpoint} to select
         * @param session
         *            the current session
         * @deprecated use
         *             {@link Callback#selectSirius(Viewpoint, Session, IProgressMonitor)}
         *             instead
         */
        void selectSirius(Viewpoint viewpoint, Session session);

        /**
         * Select a {@link Viewpoint}.
         * 
         * @param viewpoint
         *            the {@link Viewpoint} to select
         * @param session
         *            the current session
         * @param createNewRepresentations
         *            true to create new DRepresentation for
         *            RepresentationDescription having their initialization
         *            attribute at true for selected {@link Viewpoint}s.
         * @deprecated use
         *             {@link Callback#selectSirius(Viewpoint, Session, boolean,IProgressMonitor)}
         *             instead
         */
        void selectSirius(Viewpoint viewpoint, Session session, boolean createNewRepresentations);

        /**
         * deselect a viewpoint.
         * 
         * @param deselectedSirius
         *            the deselected viewpoint
         * @param session
         *            the current session
         * @deprecated use
         *             {@link Callback#deselectSirius(Viewpoint, Session, IProgressMonitor)}
         *             instead
         */
        void deselectSirius(Viewpoint deselectedSirius, Session session);

        /**
         * Select a {@link Viewpoint}.
         * 
         * @param viewpoint
         *            the {@link Viewpoint} to select
         * @param session
         *            the current session
         * @param monitor
         *            a {@link IProgressMonitor} to show progression
         */
        void selectSirius(Viewpoint viewpoint, Session session, IProgressMonitor monitor);

        /**
         * Select a {@link Viewpoint}.
         * 
         * @param viewpoint
         *            the {@link Viewpoint} to select
         * @param session
         *            the current session
         * @param createNewRepresentations
         *            true to create new DRepresentation for
         *            RepresentationDescription having their initialization
         *            attribute at true for selected {@link Viewpoint}s.
         * @param monitor
         *            a {@link IProgressMonitor} to show progression
         */
        void selectSirius(Viewpoint viewpoint, Session session, boolean createNewRepresentations, IProgressMonitor monitor);

        /**
         * deselect a viewpoint.
         * 
         * @param deselectedSirius
         *            the deselected viewpoint
         * @param session
         *            the current session
         * @param monitor
         *            a {@link IProgressMonitor} to show progression
         */
        void deselectSirius(Viewpoint deselectedSirius, Session session, IProgressMonitor monitor);

    }

    /**
     * The label provider
     * 
     * @author mchauvin
     */
    private static final class SiriussTableLabelProvider extends ColumnLabelProvider {

        private final Map<Viewpoint, Boolean> viewpoints;

        private int columnIndex;

        /**
         * Constructor.
         * 
         * @param viewpoints
         *            the viewpoints
         */
        public SiriussTableLabelProvider(final Map<Viewpoint, Boolean> viewpoints) {
            super();
            this.viewpoints = viewpoints;
        }

        private boolean findSirius(final Viewpoint vp) {
            for (final Map.Entry<Viewpoint, Boolean> entry : viewpoints.entrySet()) {
                if (EqualityHelper.areEquals(entry.getKey(), vp) && entry.getValue()) {
                    return true;
                }
            }
            return false;
        }

        private ImageDescriptor getOverlayedDescriptor(final Image baseImage, final String decoratorPath) {
            final ImageDescriptor decoratorDescriptor = SiriusEditPlugin.Implementation.getBundledImageDescriptor(decoratorPath);
            return new DecorationOverlayIcon(baseImage, decoratorDescriptor, IDecoration.BOTTOM_LEFT);
        }

        private Image getEnhancedImage(final Image image, final Viewpoint viewpoint) {
            if (SiriusRegistry.getInstance().isFromPlugin(viewpoint) && image != null) {
                return SiriusEditPlugin.getPlugin().getImage(getOverlayedDescriptor(image, "icons/full/others/plugin.gif"));
            }
            return image;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Image getImage(final Object element) {
            Image image = null;

            switch (columnIndex) {
            case 0:
                if (element instanceof Viewpoint) {
                    final Viewpoint vp = (Viewpoint) element;
                    image = SiriusEditPlugin.getPlugin().getBundledImage("/icons/full/others/checkbox_inactive.gif");
                    if (findSirius(vp)) {
                        image = SiriusEditPlugin.getPlugin().getBundledImage("/icons/full/others/checkbox_active.gif");
                    }
                }
                break;
            case 1:
                if (element instanceof Viewpoint) {
                    final Viewpoint vp = (Viewpoint) element;
                    if (vp.getIcon() != null && vp.getIcon().length() > 0) {
                        final ImageDescriptor desc = SiriusEditPlugin.Implementation.findImageDescriptor(vp.getIcon());
                        if (desc != null) {
                            image = SiriusEditPlugin.getPlugin().getImage(desc);
                            image = getEnhancedImage(image, vp);
                        }
                    }
                    if (image == null) {
                        image = SiriusEditPlugin.getPlugin().getImage(SiriusEditPlugin.getPlugin().getItemImageDescriptor(vp));
                        image = getEnhancedImage(image, vp);
                    }
                }
                break;
            case 2:
                break;
            default:
                break;
            }
            return image;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText(final Object element) {
            switch (columnIndex) {
            case 2:
                if (element instanceof Viewpoint) {
                    return new IdentifiedElementQuery((Viewpoint) element).getLabel();
                }
                break;
            default:
                break;
            }
            return null;
        }

        @Override
        public String getToolTipText(final Object element) {
            String toolTip = null;
            if (columnIndex == 2 && element instanceof Viewpoint) {
                Viewpoint viewpoint = (Viewpoint) element;
                final Resource resource = ((Viewpoint) element).eResource();
                if (resource != null) {
                    toolTip = resource.getURI().toString();
                }
                if (viewpoint.getEndUserDocumentation() != null && viewpoint.getEndUserDocumentation().trim().length() > 0) {
                    if (toolTip != null) {
                        toolTip += "\n\n";
                    } else {
                        toolTip = "";
                    }
                    toolTip += viewpoint.getEndUserDocumentation();
                }
            }
            return toolTip;
        }

        @Override
        public Point getToolTipShift(final Object object) {
            return new Point(5, 5);
        }

        @Override
        public int getToolTipDisplayDelayTime(final Object object) {
            return 200;
        }

        @Override
        public void update(final ViewerCell cell) {
            columnIndex = cell.getColumnIndex();
            super.update(cell);
        }

        @Override
        public int getToolTipStyle(final Object object) {
            return SWT.SHADOW_OUT;
        }

    }

    /**
     * The content provider.
     * 
     * @author mchauvin
     */
    private static final class SiriussTableContentProvider implements IStructuredContentProvider {

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        public Object[] getElements(final Object inputElement) {
            if (inputElement instanceof Set<?>) {
                final Set<Viewpoint> viewpoints = (Set<Viewpoint>) inputElement;
                return viewpoints.toArray();
            }
            return Collections.EMPTY_LIST.toArray();
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.eclipse.jface.viewers.IContentProvider#dispose()
         */
        public void dispose() {
            // nothing to do
        }

        /**
         * {@inheritDoc}
         */
        public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
            // nothing to do
        }
    }

    /**
     * An common interface which adds a set viewer method.
     * 
     * @author mchauvin
     */
    private interface TableViewerAwareCellModifier extends ICellModifier {
        /**
         * Set the table viewer to update.
         * 
         * @param viewer
         *            the viewer to update
         */
        void setViewer(final TableViewer viewer);
    }

    /**
     * An common abstract class for cell modifiers.
     * 
     * @author mchauvin
     */
    private abstract static class AbstractSiriussTableCellModifier implements TableViewerAwareCellModifier {

        protected TableViewer tableViewer;

        /** ll viewpoints and there selection state. */
        protected final Map<Viewpoint, Boolean> viewpoints;

        /**
         * Cosntructor.
         * 
         * @param viewpoints
         *            All viewpoints and there selection state.
         */
        public AbstractSiriussTableCellModifier(final Map<Viewpoint, Boolean> viewpoints) {
            this.viewpoints = viewpoints;
        }

        /**
         * {@inheritDoc}
         */
        public void setViewer(final TableViewer viewer) {
            this.tableViewer = viewer;
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.eclipse.jface.viewers.ICellModifier#canModify(java.lang.Object,
         *      java.lang.String)
         */
        public boolean canModify(final Object element, final String property) {

            if (property.equals(COLUMNS[0])) {
                /* first column */
                return true;
            }
            return false;
        }

    }

    /**
     * The cell modifier which only modifies the input map.
     * 
     * @author mchauvin
     */
    private static class SiriussTableLazyCellModifier extends AbstractSiriussTableCellModifier {

        /**
         * Constructor.
         * 
         * @param viewpoints
         *            All viewpoints and there selection state.
         */
        public SiriussTableLazyCellModifier(final Map<Viewpoint, Boolean> viewpoints) {
            super(viewpoints);
        }

        /**
         * {@inheritDoc}
         */
        public Object getValue(final Object element, final String property) {

            final Viewpoint viewpoint = (Viewpoint) element;
            Object result = null;

            if (property.equals(COLUMNS[0])) {
                /* first column */

                result = Boolean.FALSE;
                for (final Map.Entry<Viewpoint, Boolean> entry : viewpoints.entrySet()) {
                    if (entry.getValue().booleanValue() && EqualityHelper.areEquals(viewpoint, entry.getKey())) {
                        result = Boolean.TRUE;
                        break;
                    }
                }
            } else if (property.equals(COLUMNS[1])) {
                /* second column */
                // do nothing as there is only an image
            } else {
                /* third column */
                result = new IdentifiedElementQuery(viewpoint).getLabel();
            }
            return result;
        }

        /**
         * {@inheritDoc}
         */
        public void modify(final Object element, final String property, final Object value) {

            Object objElement;

            if (element instanceof Item) {
                objElement = ((Item) element).getData();
            } else {
                objElement = element;
            }

            if (property.equals(COLUMNS[0])) {
                final Viewpoint vp = (Viewpoint) objElement;

                // Convert Object to Boolean without instanceof
                final Boolean result = Boolean.valueOf(Boolean.TRUE.equals(value));

                for (final Viewpoint viewpoint : viewpoints.keySet()) {
                    if (EqualityHelper.areEquals(viewpoint, vp)) {
                        viewpoints.put(viewpoint, result);
                        break;
                    }
                }

                /* update the label provider */
                this.tableViewer.update(vp, null);
            }
        }
    }
}
