package hu.it4all.plugin.navigator.handlers.nature;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

import hu.it4all.plugin.navigator.handlers.builder.MDMEventBuilder;

/**
 * Project Nature az MDM Event navigációhoz.
 * Ha egy projekthez hozzá van adva ez a nature, akkor a builder
 * automatikusan fut a projekten.
 */
public class MDMEventNature implements IProjectNature {

    public static final String NATURE_ID = "hu.it4all.plugin.navigator.mdmEventNature";

    private IProject project;

    @Override
    public void configure() throws CoreException {
        IProjectDescription desc = project.getDescription();
        ICommand[] commands = desc.getBuildSpec();

        for (ICommand command : commands) {
            if (command.getBuilderName().equals(MDMEventBuilder.BUILDER_ID)) {
                return; 
            }
        }

        
        ICommand[] newCommands = new ICommand[commands.length + 1];
        System.arraycopy(commands, 0, newCommands, 0, commands.length);
        ICommand command = desc.newCommand();
        command.setBuilderName(MDMEventBuilder.BUILDER_ID);
        newCommands[newCommands.length - 1] = command;
        desc.setBuildSpec(newCommands);
        project.setDescription(desc, null);
    }

    @Override
    public void deconfigure() throws CoreException {
        IProjectDescription desc = project.getDescription();
        ICommand[] commands = desc.getBuildSpec();

        for (int i = 0; i < commands.length; ++i) {
            if (commands[i].getBuilderName().equals(MDMEventBuilder.BUILDER_ID)) {
                ICommand[] newCommands = new ICommand[commands.length - 1];
                System.arraycopy(commands, 0, newCommands, 0, i);
                System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);
                desc.setBuildSpec(newCommands);
                project.setDescription(desc, null);
                return;
            }
        }
    }

    @Override
    public IProject getProject() {
        return project;
    }

    @Override
    public void setProject(IProject project) {
        this.project = project;
    }

    /**
     * Ellenőrzi, hogy a projekthez hozzá van-e adva az MDM Event Nature.
     */
    public static boolean hasNature(IProject project) {
        try {
            return project.hasNature(NATURE_ID);
        } catch (CoreException e) {
            return false;
        }
    }

    /**
     * Hozzáadja a nature-t a projekthez.
     */
    public static void addNature(IProject project) throws CoreException {
        if (!hasNature(project)) {
            IProjectDescription description = project.getDescription();
            String[] natures = description.getNatureIds();
            String[] newNatures = new String[natures.length + 1];
            System.arraycopy(natures, 0, newNatures, 0, natures.length);
            newNatures[natures.length] = NATURE_ID;
            description.setNatureIds(newNatures);
            project.setDescription(description, null);
        }
    }

    /**
     * Eltávolítja a nature-t a projektből.
     */
    public static void removeNature(IProject project) throws CoreException {
        if (hasNature(project)) {
            IProjectDescription description = project.getDescription();
            String[] natures = description.getNatureIds();

            for (int i = 0; i < natures.length; ++i) {
                if (NATURE_ID.equals(natures[i])) {
                    String[] newNatures = new String[natures.length - 1];
                    System.arraycopy(natures, 0, newNatures, 0, i);
                    System.arraycopy(natures, i + 1, newNatures, i, natures.length - i - 1);
                    description.setNatureIds(newNatures);
                    project.setDescription(description, null);
                    return;
                }
            }
        }
    }
}
