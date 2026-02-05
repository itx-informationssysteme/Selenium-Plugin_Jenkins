# Contributing to Selenium Plugin for Jenkins

Thank you for your interest in contributing to the Selenium Plugin for Jenkins!

## Development & Local Testing

To build and run the plugin locally:

```bash
# Compile, test, and validate code style
mvn clean verify

# Auto-format (if needed)
mvn spotless:apply

# Run a local Jenkins instance with the plugin
mvn -Dhost=0.0.0.0 hpi:run
```

## Docker Testing

It's also possible to run the Plugin in a Docker Jenkins Container. The following ports are required to be exposed:
- 4444 - Selenium Hub
- 4442-4443 - Connect Nodes to Selenium Hub

## Code Style

This project uses [Spotless](https://github.com/diffplug/spotless) for code formatting. Please run `mvn spotless:apply` before submitting a pull request.

## Submitting Changes

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `mvn clean verify` to ensure all tests pass
5. Submit a pull request

## Reporting Issues

Please report issues via [GitHub Issues](https://github.com/jenkinsci/selenium-hub-plugin/issues).

