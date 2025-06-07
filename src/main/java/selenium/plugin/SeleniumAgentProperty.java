package selenium.plugin;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.TransientComputerActionFactory;
import java.util.Collection;
import java.util.Collections;

@Extension
public class SeleniumAgentProperty extends TransientComputerActionFactory {
    @Override
    public Collection<? extends Action> createFor(Computer target) {
        return Collections.singletonList(new SeleniumAgentAction(target));
    }
}
