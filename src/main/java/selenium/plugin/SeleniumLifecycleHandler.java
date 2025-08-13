package selenium.plugin;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.RestartListener;
import java.io.IOException;
import jenkins.model.Jenkins;

@Extension
public class SeleniumLifecycleHandler extends RestartListener {

    @Override
    public boolean isReadyToRestart() throws IOException, InterruptedException {
        return true;
    }

    @Override
    public void onRestart() {
        stopAllNodes();
    }

    private static void stopAllNodes() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) return;

        for (Computer computer : jenkins.getComputers()) {
            SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
            try {
                if (action != null && action.getNodeActive()) {
                    action.doStopNode();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
