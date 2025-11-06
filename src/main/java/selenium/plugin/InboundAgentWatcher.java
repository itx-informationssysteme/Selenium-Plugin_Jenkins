package selenium.plugin;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class InboundAgentWatcher extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentWatcher.class.getName());
    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void onOnline(Computer c, TaskListener listener) {
        LOGGER.log(Level.INFO, "Agent online: {0}", c.getName());
        maybeRunAfterAllAgentsOnline();
    }

    @Override
    public void onConfigurationChange() {
        LOGGER.info("Node configuration changed, re-checking agent state");
        maybeRunAfterAllAgentsOnline();
    }

    private void maybeRunAfterAllAgentsOnline() {
        if (started.get()) {
            return;
        }

        Jenkins j = Jenkins.get();
        List<Computer> inboundComputers = j.getNodes().stream()
                .map(node -> node.toComputer())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (inboundComputers.isEmpty()) {
            return;
        }

        boolean allOnline = inboundComputers.stream().allMatch(Computer::isOnline);

        if (allOnline && started.compareAndSet(false, true)) {
            LOGGER.info(">>> All Inbound Agents are online – start Post-Agent-Startup-Logic");
            runPostAgentStartupLogic();
        }
    }

    private void runPostAgentStartupLogic() {
        for (Computer computer : Jenkins.get().getComputers()) {
            String display = computer.getDisplayName();
            if ("Jenkins".equals(display) || "(built-in)".equals(display)) {
                continue;
            }
            SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
            if (action != null) {
                try {
                    action.addNodeRestartLog("Post-Agent-Startup Trigger (InboundAgentWatcher)");
                    action.checkAndRestartNodeIfNeeded();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error while Post-Agent-Startup-Logic for " + computer.getName(), e);
                }
            }
        }
    }
}
