package easyuml;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.openide.modules.ModuleInstall;
import org.openide.modules.Modules;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 * Prevents the consolidated module from loading alongside its old components.
 */
public final class SingleNbmInstall extends ModuleInstall {

    private static final List<String> LEGACY_MODULES = Arrays.asList(
            "com.github.javaparser", "org.uml.dom4j", "org.uml.explorer",
            "org.uml.filetype", "org.uml.model", "org.uml.newcode",
            "org.uml.project", "org.uml.reveng", "org.uml.visual");

    @Override
    public void validate() {
        Modules modules = Modules.getDefault();
        List<String> installed = LEGACY_MODULES.stream()
                .filter(name -> modules.findCodeNameBase(name) != null)
                .collect(Collectors.toList());
        if (!installed.isEmpty()) {
            String message = NbBundle.getMessage(SingleNbmInstall.class, "MSG_LegacyModules",
                    String.join(", ", installed));
            throw Exceptions.attachLocalizedMessage(new IllegalStateException(message), message);
        }
    }
}
