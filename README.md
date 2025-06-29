# Selenium Plugin for Jenkins

A Jenkins plugin that integrates Selenium Grid directly into your Jenkins environment. It allows you to easily configure and run Selenium Hub and Nodes on your Jenkins Agents.

## Release

You can find the latest Release [here](https://github.com/julianboehne/Selenium-Plugin_Jenkins/releases).

---

## 🛠️ Development & Local Testing

To build and run the plugin locally:

```bash
# Compile, test, and validate code style
mvn clean verify

# Auto-format (if needed)
mvn spotless:apply

# Run a local Jenkins instance with the plugin
mvn -Dhost=0.0.0.0 hpi:run
```

It's also possible to run the Plugin in a Docker Jenkins Container, but following Ports are required to be exposed:
- 4444 - Selenium Hub
- 4442-4443 - Connect Nodes to Selenium Hub
# Plugin Overlay
![Plugin Dashboard](images/jenkins-selenium-settings.png)
![Plugin Agent Dashboard](images/jenkins-selenium-node.png)
![Selenium-Hub](images/selenium-hub.png)
