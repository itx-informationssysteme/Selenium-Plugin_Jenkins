# Selenium-Plugin_Jenkins

## Introduction

TODO Describe what your plugin does here

## Plugin Development

`mvn clean verify`

on failure run:
`mvn spotless:apply`

`mvn -Dhost=0.0.0.0 hpi:run`

if the jenkins instance is running on docker the following ports need to be forwarded:

```
- "4444:4444"
- "4442-4443:4442-4443"
```

## Issues

TODO Decide where you're going to host your issues, the default is Jenkins JIRA, but you can also enable GitHub issues,
If you use GitHub issues there's no need for this section; else add the following line:

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins.io/).

## Contributing

TODO review the default [CONTRIBUTING](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md) file and make sure it is appropriate for your plugin, if not then add your own one adapted from the base file

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

