/*
 * Copyright 2025 it.x informationssysteme gmbh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package selenium.plugin;

import hudson.Extension;
import hudson.model.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class SeleniumAgentPropertyLink extends TransientComputerActionFactory {

    private static final Logger LOGGER = Logger.getLogger(SeleniumAgentPropertyLink.class.getName());

    // Cache for SeleniumAgentAction instances to avoid creating new ones each time
    private static final Map<String, SeleniumAgentAction> actionCache = new ConcurrentHashMap<>();

    @Override
    public Collection<? extends Action> createFor(Computer target) {
        Node node = target.getNode();

        if (node != null && "Jenkins".equals(node.getSearchName())) {
            return Collections.singletonList(ManagementLink.all().get(SeleniumGlobalProperty.class));
        }

        String computerName = target.getName();

        // Use cached action if available, otherwise create new one and load config
        SeleniumAgentAction action = actionCache.computeIfAbsent(computerName, name -> {
            LOGGER.log(Level.INFO, "Creating new SeleniumAgentAction for: {0}", name);
            SeleniumAgentAction newAction = new SeleniumAgentAction(target);
            newAction.load(); // Load saved configuration
            LOGGER.log(Level.INFO, "Loaded config for {0}: nodeActive={1}",
                new Object[]{name, newAction.isNodeActiveConfigured()});
            return newAction;
        });

        return Collections.singletonList(action);
    }

    /**
     * Clear the cache for a specific computer (e.g., when it's removed)
     */
    public static void clearCache(String computerName) {
        actionCache.remove(computerName);
    }

    /**
     * Get a cached action for a computer
     */
    public static SeleniumAgentAction getCachedAction(String computerName) {
        return actionCache.get(computerName);
    }
}
