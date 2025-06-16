package selenium.plugin;

import hudson.Extension;
import hudson.model.*;
import java.util.Collections;
import java.util.List;

@Extension
public class PropertyDashboardLink extends TransientViewActionFactory {

    @Override
    public List<Action> createFor(View v) {
        return Collections.singletonList(new SeleniumGlobalProperty());
    }
}