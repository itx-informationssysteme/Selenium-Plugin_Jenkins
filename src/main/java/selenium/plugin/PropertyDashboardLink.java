package selenium.plugin;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.util.Collections;
import java.util.List;

@Extension
public class PropertyDashboardLink extends TransientViewActionFactory {

    private static final SeleniumGlobalProperty INSTANCE = new SeleniumGlobalProperty();

    @Override
    public List<Action> createFor(View v) {
        if (v instanceof AllView && v.getOwner() instanceof Jenkins) {
            return Collections.singletonList(INSTANCE);
        }
        return Collections.emptyList();
    }
}
