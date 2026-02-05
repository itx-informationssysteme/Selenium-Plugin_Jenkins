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

    private boolean isController(Computer target) {
        return target instanceof jenkins.model.Jenkins.MasterComputer;
    }

    @Override
    public Collection<? extends Action> createFor(Computer target) {
        if (isController(target)) {
            return Collections.singletonList(ManagementLink.all().get(SeleniumGlobalProperty.class));
        }

        String computerName = target.getName();

        SeleniumAgentAction action = actionCache.computeIfAbsent(computerName, name -> {
            LOGGER.log(Level.FINE, "Creating new SeleniumAgentAction for: {0}", name);
            SeleniumAgentAction newAction = new SeleniumAgentAction(target);
            newAction.load(); // Load saved configuration
            LOGGER.log(Level.FINE, "Loaded config for {0}: nodeActive={1}", new Object[] {
                name, newAction.isNodeActiveConfigured()
            });
            return newAction;
        });

        return Collections.singletonList(action);
    }

    public static void clearCache(String computerName) {
        actionCache.remove(computerName);
    }

    public static SeleniumAgentAction getCachedAction(String computerName) {
        return actionCache.get(computerName);
    }
}
