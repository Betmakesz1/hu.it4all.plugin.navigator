package hu.it4all.plugin.navigator.handlers.ui;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PartInitException;

import hu.it4all.plugin.navigator.handlers.search.InterfaceSearchEngine.MethodLocation;

/**
 * Dialógus amely megjeleníti a talált listener implementációkat
 * és lehetővé teszi a felhasználónak a választást.
 */
public class ListenerSelectionDialog extends Dialog {

    private final List<MethodLocation> listeners;
    private final String interfaceName;
    private ListViewer listViewer;
    private MethodLocation selectedListener;

    public ListenerSelectionDialog(Shell parentShell, List<MethodLocation> listeners, String interfaceName) {
        super(parentShell);
        this.listeners = listeners;
        this.interfaceName = interfaceName;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Select MDM Listener Implementation");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 10;
        layout.marginWidth = 10;
        container.setLayout(layout);

        // Cím label
        Label titleLabel = new Label(container, SWT.NONE);
        titleLabel.setText("Found " + listeners.size() + " implementation(s) of " + getSimpleName(interfaceName) + ":");
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Lista viewer
        listViewer = new ListViewer(container, SWT.BORDER | SWT.V_SCROLL);
        GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        gridData.heightHint = 200;
        gridData.widthHint = 400;
        listViewer.getControl().setLayoutData(gridData);

        // Content és label provider
        listViewer.setContentProvider(ArrayContentProvider.getInstance());
        listViewer.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                if (element instanceof MethodLocation) {
                    MethodLocation location = (MethodLocation) element;
                    return location.getDisplayName();
                }
                return super.getText(element);
            }
        });

        // Adatok beállítása
        listViewer.setInput(listeners);

        // Kiválasztás kezelése
        listViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                selectedListener = (MethodLocation) selection.getFirstElement();
            }
        });

        // Dupla kattintás kezelése
        listViewer.getControl().addListener(SWT.MouseDoubleClick, event -> {
            if (selectedListener != null) {
                okPressed();
            }
        });

        // Első elem kiválasztása alapértelmezetten
        if (!listeners.isEmpty()) {
            listViewer.setSelection(new org.eclipse.jface.viewers.StructuredSelection(listeners.get(0)));
        }

        return container;
    }

    @Override
    protected void okPressed() {
        if (selectedListener != null) {
            openInEditor(selectedListener.getCompilationUnit());
        }
        super.okPressed();
    }

    /**
     * Megnyitja a kiválasztott compilation unit-ot az editorban.
     */
    private void openInEditor(ICompilationUnit cu) {
        try {
            JavaUI.openInEditor(cu);
        } catch (PartInitException | org.eclipse.jdt.core.JavaModelException e) {
            e.printStackTrace();
        }
    }

    /**
     * Egyszerű név kinyerése a teljes névből.
     */
    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }

    /**
     * Megnyitja a dialógust és megjeleníti a listener listát.
     */
    public static void open(Shell shell, List<MethodLocation> listeners, String interfaceName) {
        ListenerSelectionDialog dialog = new ListenerSelectionDialog(shell, listeners, interfaceName);
        dialog.open();
    }
}
