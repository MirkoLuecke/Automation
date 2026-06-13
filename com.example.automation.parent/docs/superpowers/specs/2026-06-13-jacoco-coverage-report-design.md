# JaCoCo Aggregated Coverage Report — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an aggregated JaCoCo HTML coverage report that runs as part of `mvn clean install`, covering only production code in `com.example.automation`, excluding test classes.

**Architecture:** A new `com.example.automation.coverage` module (packaging=pom) is listed last in the parent POM so it executes after all other modules. The JaCoCo agent is injected via `prepare-agent` in the parent POM and passed to Tycho Surefire as a JVM argument. The coverage module reads the resulting `.exec` file and the compiled production classes to produce an HTML report.

**Tech Stack:** JaCoCo 0.8.12, Tycho 3.0.5 (existing), Maven 3.x (existing)

---

## Data Flow

```
mvn clean install
  |
  +-- initialize (all modules)
  |     jacoco:prepare-agent  ->  sets ${jacoco.agent.argLine}
  |
  +-- com.example.automation
  |     compiles ->  target/classes/
  |
  +-- com.example.automation.tests
  |     tycho-surefire (argLine=${jacoco.agent.argLine})
  |     ->  target/jacoco.exec   (coverage data)
  |
  +-- com.example.automation.coverage  [new, runs last]
        jacoco:report (phase=verify)
          dataFile:         ../com.example.automation.tests/target/jacoco.exec
          classesDirectory: ../com.example.automation/target/classes
          sourceDirectory:  ../com.example.automation/src/main/java
          ->  target/site/jacoco/index.html
```

Test classes are excluded because only `com.example.automation/target/classes` is used as the classes directory — the test bundle's classes are never referenced.

---

## Files Changed or Created

| File | Change |
|------|--------|
| `pom.xml` | Add `<jacoco.agent.argLine>` default property; add `jacoco-maven-plugin` with `prepare-agent` execution; add `com.example.automation.coverage` as last module |
| `com.example.automation.tests/pom.xml` | Add `<argLine>${jacoco.agent.argLine}</argLine>` to `tycho-surefire-plugin` configuration |
| `com.example.automation.coverage/pom.xml` | New file — `jacoco:report` execution with explicit paths |

---

## Configuration Details

### Parent `pom.xml` — new property

```xml
<jacoco.agent.argLine></jacoco.agent.argLine>
```

Empty default ensures the build does not fail when JaCoCo is skipped or not yet resolved.

### Parent `pom.xml` — new plugin in `<plugins>`

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <id>prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
      <configuration>
        <propertyName>jacoco.agent.argLine</propertyName>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### `com.example.automation.tests/pom.xml` — Surefire argLine

```xml
<configuration>
  <useUIHarness>true</useUIHarness>
  <useUIThread>false</useUIThread>
  <argLine>${jacoco.agent.argLine}</argLine>
</configuration>
```

### `com.example.automation.coverage/pom.xml` — full file

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example.automation</groupId>
    <artifactId>com.example.automation.parent</artifactId>
    <version>1.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>com.example.automation.coverage</artifactId>
  <packaging>pom</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.12</version>
        <executions>
          <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
            <configuration>
              <dataFile>
                ${project.basedir}/../com.example.automation.tests/target/jacoco.exec
              </dataFile>
              <classesDirectory>
                ${project.basedir}/../com.example.automation/target/classes
              </classesDirectory>
              <sourceDirectory>
                ${project.basedir}/../com.example.automation/src/main/java
              </sourceDirectory>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## Output

After `mvn clean install`, open:

```
com.example.automation.coverage/target/site/jacoco/index.html
```

The report shows line, branch, and method coverage for all production classes in `com.example.automation`.
