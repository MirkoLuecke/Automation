# JaCoCo Aggregated Coverage Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an aggregated JaCoCo HTML coverage report that is generated automatically during `mvn clean install`, covering only production code in `com.example.automation`, excluding test classes.

**Architecture:** A new `com.example.automation.coverage` module (packaging=pom) is added as the last module in the parent POM. The JaCoCo agent is configured in the parent POM via `prepare-agent`, and the resulting agent JVM argument is passed to Tycho Surefire. After tests run, the coverage module reads the `.exec` file and the production class files to produce an HTML report.

**Tech Stack:** JaCoCo 0.8.12, Tycho 3.0.5 (existing), Maven 3.x (existing). JaCoCo is a standard Maven plugin from Maven Central — first run requires internet access; subsequent runs use the local Maven cache (`~/.m2/repository`).

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `com.example.automation.coverage/pom.xml` | Create | Runs `jacoco:report` with explicit paths to exec file, classes, and sources |
| `pom.xml` | Modify | Add `jacoco.agent.argLine` default property; add `jacoco-maven-plugin:prepare-agent`; register coverage module |
| `com.example.automation.tests/pom.xml` | Modify | Pass `${jacoco.agent.argLine}` to `tycho-surefire-plugin` so the agent instruments the OSGi test JVM |

---

### Task 1: Create the coverage module

**Files:**
- Create: `com.example.automation.coverage/pom.xml`

This module has `pom` packaging (no Java sources, no OSGi bundle). It only runs `jacoco:report` during the `verify` phase. Paths are relative to `${project.basedir}` which is `com.example.automation.coverage/`.

- [ ] **Step 1: Create the directory**

```
mkdir com.example.automation.coverage
```

- [ ] **Step 2: Create `com.example.automation.coverage/pom.xml`**

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
              <dataFile>${project.basedir}/../com.example.automation.tests/target/jacoco.exec</dataFile>
              <classesDirectory>${project.basedir}/../com.example.automation/target/classes</classesDirectory>
              <sourceDirectory>${project.basedir}/../com.example.automation/src/main/java</sourceDirectory>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Commit**

```
git add com.example.automation.coverage/pom.xml
git commit -m "feat: add coverage module skeleton for JaCoCo report"
```

---

### Task 2: Configure JaCoCo agent in the parent POM

**Files:**
- Modify: `pom.xml`

The `prepare-agent` goal runs during `initialize` (its default phase) in every module. It resolves the JaCoCo agent JAR and writes its path into `${jacoco.agent.argLine}`. The empty default value in `<properties>` prevents a build error if the property is referenced before the goal runs.

- [ ] **Step 1: Add the default property**

In `pom.xml`, find the `<properties>` block:
```xml
  <properties>
    <tycho.version>3.0.5</tycho.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
```

Replace it with:
```xml
  <properties>
    <tycho.version>3.0.5</tycho.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <jacoco.agent.argLine></jacoco.agent.argLine>
  </properties>
```

- [ ] **Step 2: Add the `jacoco-maven-plugin` to the `<plugins>` section**

In `pom.xml`, find the closing `</plugins>` tag inside `<build>` (after the `target-platform-configuration` plugin block) and add the JaCoCo plugin before it:

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

- [ ] **Step 3: Register the coverage module — add it as the last `<module>` entry**

Find the `<modules>` block:
```xml
  <modules>
    <module>com.example.automation</module>
    <module>com.example.automation.feature</module>
    <module>com.example.automation.site</module>
    <module>com.example.automation.tests</module>
  </modules>
```

Replace with:
```xml
  <modules>
    <module>com.example.automation</module>
    <module>com.example.automation.feature</module>
    <module>com.example.automation.site</module>
    <module>com.example.automation.tests</module>
    <module>com.example.automation.coverage</module>
  </modules>
```

- [ ] **Step 4: Verify the parent POM parses correctly**

```
mvn help:effective-pom -N
```

Expected: no errors, effective POM printed to stdout.

- [ ] **Step 5: Commit**

```
git add pom.xml
git commit -m "feat: add JaCoCo prepare-agent to parent POM"
```

---

### Task 3: Pass the JaCoCo agent to Tycho Surefire

**Files:**
- Modify: `com.example.automation.tests/pom.xml`

Tycho Surefire forks a separate JVM to run OSGi tests. The JaCoCo agent must be passed as a JVM argument to that forked process — otherwise the agent never instruments the production code and the `.exec` file stays empty.

- [ ] **Step 1: Add `<argLine>` to the Surefire configuration**

In `com.example.automation.tests/pom.xml`, find:
```xml
        <configuration>
          <useUIHarness>true</useUIHarness>
          <useUIThread>false</useUIThread>
        </configuration>
```

Replace with:
```xml
        <configuration>
          <useUIHarness>true</useUIHarness>
          <useUIThread>false</useUIThread>
          <argLine>${jacoco.agent.argLine}</argLine>
        </configuration>
```

- [ ] **Step 2: Commit**

```
git add com.example.automation.tests/pom.xml
git commit -m "feat: pass JaCoCo agent arg to Tycho Surefire"
```

---

### Task 4: Verify the full build produces the report

This task has no source code changes. It verifies the three previous tasks work together end-to-end.

- [ ] **Step 1: Run the full build**

```
mvn clean install
```

Expected: BUILD SUCCESS. Watch for these lines in the output:
```
[INFO] --- jacoco-maven-plugin:0.8.12:prepare-agent (prepare-agent) @ com.example.automation ---
[INFO] argLine set to -javaagent:...jacocoagent.jar=...
```
and later:
```
[INFO] --- jacoco-maven-plugin:0.8.12:report (report) @ com.example.automation.coverage ---
[INFO] Loading execution data file ...\com.example.automation.tests\target\jacoco.exec
[INFO] Analyzed bundle 'com.example.automation' with ... classes
```

- [ ] **Step 2: Confirm the report file exists**

```
dir com.example.automation.coverage\target\site\jacoco\index.html
```

Expected: file listed with a non-zero size.

- [ ] **Step 3: Open the report and spot-check**

Open `com.example.automation.coverage/target/site/jacoco/index.html` in a browser. Verify:
- The bundle name shown is `com.example.automation` (not the tests bundle)
- At least one class from `com/example/automation/` is listed
- No classes from `com/example/automation/tests/` appear

- [ ] **Step 4: Commit**

```
git add .
git commit -m "feat: JaCoCo aggregated coverage report via coverage module"
git push
```

---

## Self-Review Checklist (for implementer)

- [ ] `jacoco.exec` is present in `com.example.automation.tests/target/` after build
- [ ] Report shows production classes only (no `*Test*` classes)
- [ ] `mvn clean install` succeeds without `-DskipTests`
