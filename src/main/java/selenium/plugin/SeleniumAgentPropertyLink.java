package selenium.plugin;

import hudson.Extension;
import hudson.model.*;
import java.util.Collection;
import java.util.Collections;

@Extension
public class SeleniumAgentPropertyLink extends TransientComputerActionFactory {
    @Override
    public Collection<? extends Action> createFor(Computer target) {
        Node node = target.getNode();

        if (node != null && "Jenkins".equals(node.getSearchName())) {
            return Collections.singletonList(ManagementLink.all().get(SeleniumGlobalProperty.class));
        }

        return Collections.singletonList(new SeleniumAgentAction(target));
    }
}
