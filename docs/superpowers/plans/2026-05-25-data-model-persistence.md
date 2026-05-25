# Workflow Data Model and Persistence — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `Workflow`, `Step`, `StepStatus` model classes and a `WorkflowRepository` that loads/saves workflow JSON files from `${user.home}/automation/`, with full JUnit 4 test coverage.

**Architecture:** Plain POJOs serialized with embedded Gson (via `Bundle-ClassPath`). `WorkflowRepository` accepts a `File` directory in its constructor so tests can inject a `TemporaryFolder`. All tests run in `com.example.automation.tests` under Tycho's Eclipse test runner. Tests are written first; the build fails until the implementation is added.

**Tech Stack:** Java 17, OSGi/Tycho 3.0.5, Gson 2.10.1 (embedded via `maven-dependency-plugin`), JUnit 4

---

## File Map

| File | Change |
|---|---|
| `com.example.automation.parent/com.example.automation/.gitignore` | Add `/lib/` |
| `com.example.automation.parent/com.example.automation/pom.xml` | Add `maven-dependency-plugin` to download Gson |
| `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` | Add `Bundle-ClassPath` (Gson) and `Export-Package` |
| `com.example.automation.parent/com.example.automation/build.properties` | Add `lib/` to `bin.includes` |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/StepStatus.java` | New |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Step.java` | New |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Workflow.java` | New |
| `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/persistence/WorkflowRepository.java` | New |
| `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRepositoryTest.java` | New |

---

### Task 1: Configure Gson embedding

**Files:**
- Modify: `com.example.automation.parent/com.example.automation/.gitignore`
- Modify: `com.example.automation.parent/com.example.automation/pom.xml`
- Modify: `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF`
- Modify: `com.example.automation.parent/com.example.automation/build.properties`

- [ ] **Step 1: Add `/lib/` to the plugin .gitignore**

Replace `com.example.automation.parent/com.example.automation/.gitignore` with:

```
/target/
/.classpath
/.project
/.settings/
/lib/
```

- [ ] **Step 2: Add `maven-dependency-plugin` to plugin pom.xml**

Replace `com.example.automation.parent/com.example.automation/pom.xml` with:

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
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>com.example.automation</artifactId>
  <packaging>eclipse-plugin</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <executions>
          <execution>
            <id>copy-gson</id>
            <phase>generate-resources</phase>
            <goals><goal>copy</goal></goals>
            <configuration>
              <artifactItems>
                <artifactItem>
                  <groupId>com.google.code.gson</groupId>
                  <artifactId>gson</artifactId>
                  <version>2.10.1</version>
                  <outputDirectory>${project.basedir}/lib</outputDirectory>
                  <destFileName>gson-2.10.1.jar</destFileName>
                </artifactItem>
              </artifactItems>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Update MANIFEST.MF**

Replace `com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF` with:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Automation
Bundle-SymbolicName: com.example.automation;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.example.automation.Activator
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.commands
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
Bundle-ClassPath: .,
 lib/gson-2.10.1.jar
Export-Package: com.example.automation.model,
 com.example.automation.persistence

```

The blank line at the end is required by the OSGi spec — keep it.

`Export-Package` is required so the test bundle (a separate OSGi bundle) can import these packages via `Require-Bundle`.

- [ ] **Step 4: Update build.properties**

Replace `com.example.automation.parent/com.example.automation/build.properties` with:

```properties
source.. = src/main/java/
output.. = target/classes/
bin.includes = META-INF/,\
               .,\
               plugin.xml,\
               icons/,\
               lib/
```

- [ ] **Step 5: Download Gson**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn generate-resources -pl com.example.automation
```

Expected: `[INFO] BUILD SUCCESS` and file `com.example.automation\lib\gson-2.10.1.jar` exists.

- [ ] **Step 6: Verify**

```powershell
ls "C:\Users\mirko\test\com.example.automation.parent\com.example.automation\lib\"
```

Expected: `gson-2.10.1.jar` with non-zero size.

- [ ] **Step 7: Full build — confirm no regressions**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected:
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- [ ] **Step 8: Commit**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/.gitignore"
git add "com.example.automation.parent/com.example.automation/pom.xml"
git add "com.example.automation.parent/com.example.automation/META-INF/MANIFEST.MF"
git add "com.example.automation.parent/com.example.automation/build.properties"
git commit -m "feat: embed Gson 2.10.1 in plugin jar for JSON serialization"
```

---

### Task 2: Write failing tests (TDD — test first)

**Files:**
- Create: `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRepositoryTest.java`

Write the full test class now. The build will fail because the model and persistence packages don't exist yet — that's expected and confirms the tests are driving the implementation.

- [ ] **Step 1: Create WorkflowRepositoryTest.java**

Create `com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRepositoryTest.java`:

```java
package com.example.automation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.example.automation.model.Step;
import com.example.automation.model.StepStatus;
import com.example.automation.model.Workflow;
import com.example.automation.persistence.WorkflowRepository;

public class WorkflowRepositoryTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private WorkflowRepository repo() {
        return new WorkflowRepository(temp.getRoot());
    }

    @Test
    public void roundTrip() throws Exception {
        Workflow wf = new Workflow("my-wf", "My Workflow", "Does stuff");
        Step step = new Step("com.example.action.maven");
        Map<String, String> config = new HashMap<>();
        config.put("path", "/some/path");
        step.setConfig(config);
        wf.getSteps().add(step);

        WorkflowRepository repo = repo();
        repo.save(wf);
        Workflow loaded = repo.load("my-wf");

        assertEquals("my-wf", loaded.getWorkflowId());
        assertEquals("My Workflow", loaded.getDisplayName());
        assertEquals("Does stuff", loaded.getDescription());
        assertEquals(1, loaded.getSteps().size());
        assertEquals("com.example.action.maven", loaded.getSteps().get(0).getActionId());
        assertEquals("/some/path", loaded.getSteps().get(0).getConfig().get("path"));
    }

    @Test
    public void transientFieldsNotPersisted() throws Exception {
        Workflow wf = new Workflow("wf1", "WF1", "desc");
        Step step = new Step("action1");
        step.setStatus(StepStatus.RED);
        step.setProgress(75);
        wf.getSteps().add(step);

        WorkflowRepository repo = repo();
        repo.save(wf);
        Workflow loaded = repo.load("wf1");

        assertEquals(StepStatus.WHITE, loaded.getSteps().get(0).getStatus());
        assertEquals(0, loaded.getSteps().get(0).getProgress());
    }

    @Test
    public void listReturnsAllWorkflows() throws Exception {
        WorkflowRepository repo = repo();
        repo.save(new Workflow("wf-a", "A", "desc a"));
        repo.save(new Workflow("wf-b", "B", "desc b"));

        List<Workflow> list = repo.list();
        assertEquals(2, list.size());
    }

    @Test
    public void saveOverwritesExisting() throws Exception {
        WorkflowRepository repo = repo();
        repo.save(new Workflow("wf1", "Old Name", "desc"));
        repo.save(new Workflow("wf1", "New Name", "desc"));

        Workflow loaded = repo.load("wf1");
        assertEquals("New Name", loaded.getDisplayName());
    }

    @Test
    public void directoryAutoCreated() throws Exception {
        File subDir = new File(temp.getRoot(), "sub/auto");
        WorkflowRepository repo = new WorkflowRepository(subDir);
        repo.save(new Workflow("wf1", "WF1", "desc"));

        assertTrue(subDir.exists());
        assertTrue(new File(subDir, "wf1.json").exists());
    }

    @Test
    public void malformedFileSkipped() throws Exception {
        File bad = new File(temp.getRoot(), "bad.json");
        try (FileWriter w = new FileWriter(bad)) {
            w.write("NOT VALID JSON {{{{");
        }
        WorkflowRepository repo = repo();
        repo.save(new Workflow("good", "Good", "desc"));

        List<Workflow> list = repo.list();
        assertEquals(1, list.size());
        assertEquals("good", list.get(0).getWorkflowId());
    }

    @Test
    public void deleteRemovesFile() throws Exception {
        WorkflowRepository repo = repo();
        repo.save(new Workflow("to-delete", "To Delete", "desc"));
        assertTrue(new File(temp.getRoot(), "to-delete.json").exists());

        repo.delete("to-delete");

        assertFalse(new File(temp.getRoot(), "to-delete.json").exists());
    }
}
```

- [ ] **Step 2: Run build — confirm compilation failure**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: `BUILD FAILURE` with errors like:
```
package com.example.automation.model does not exist
package com.example.automation.persistence does not exist
```

This is correct — the tests are driving the implementation.

- [ ] **Step 3: Commit the failing tests**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation.tests/src/main/java/com/example/automation/tests/WorkflowRepositoryTest.java"
git commit -m "test: add WorkflowRepositoryTest (failing - model not yet implemented)"
```

---

### Task 3: Implement model classes

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/StepStatus.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Step.java`
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Workflow.java`

- [ ] **Step 1: Create StepStatus.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/StepStatus.java`:

```java
package com.example.automation.model;

public enum StepStatus {
    WHITE, GREEN, YELLOW, RED
}
```

- [ ] **Step 2: Create Step.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Step.java`:

```java
package com.example.automation.model;

import java.util.HashMap;
import java.util.Map;

public class Step {

    private String actionId;
    private Map<String, String> config = new HashMap<>();

    private transient StepStatus status = StepStatus.WHITE;
    private transient int progress = 0;

    public Step() {}

    public Step(String actionId) {
        this.actionId = actionId;
    }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public Map<String, String> getConfig() { return config; }
    public void setConfig(Map<String, String> config) { this.config = config; }

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
```

- [ ] **Step 3: Create Workflow.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/Workflow.java`:

```java
package com.example.automation.model;

import java.util.ArrayList;
import java.util.List;

public class Workflow {

    private String workflowId;
    private String displayName;
    private String description;
    private List<Step> steps = new ArrayList<>();

    public Workflow() {}

    public Workflow(String workflowId, String displayName, String description) {
        this.workflowId = workflowId;
        this.displayName = displayName;
        this.description = description;
    }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }
}
```

- [ ] **Step 4: Run build — expect failure only for WorkflowRepository**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected: `BUILD FAILURE` with:
```
cannot find symbol: class WorkflowRepository
```

Model classes now compile; only the repository is missing.

- [ ] **Step 5: Commit model classes**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/model/"
git commit -m "feat: add Workflow, Step, StepStatus model classes"
```

---

### Task 4: Implement WorkflowRepository

**Files:**
- Create: `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/persistence/WorkflowRepository.java`

- [ ] **Step 1: Create WorkflowRepository.java**

Create `com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/persistence/WorkflowRepository.java`:

```java
package com.example.automation.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

import com.example.automation.model.Workflow;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class WorkflowRepository {

    private static final ILog LOG = Platform.getLog(WorkflowRepository.class);

    private final File storageDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WorkflowRepository() {
        this(new File(System.getProperty("user.home"), "automation"));
    }

    public WorkflowRepository(File storageDir) {
        this.storageDir = storageDir;
    }

    public List<Workflow> list() {
        ensureDirectoryExists();
        List<Workflow> result = new ArrayList<>();
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return result;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Workflow wf = gson.fromJson(reader, Workflow.class);
                if (wf != null) result.add(wf);
            } catch (Exception e) {
                LOG.error("Failed to read workflow file: " + file.getName(), e);
            }
        }
        return result;
    }

    public void save(Workflow workflow) throws IOException {
        ensureDirectoryExists();
        File temp = new File(storageDir, workflow.getWorkflowId() + ".json.tmp");
        File target = new File(storageDir, workflow.getWorkflowId() + ".json");
        try (FileWriter writer = new FileWriter(temp)) {
            gson.toJson(workflow, writer);
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public Workflow load(String workflowId) throws IOException {
        File file = new File(storageDir, workflowId + ".json");
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, Workflow.class);
        }
    }

    public boolean delete(String workflowId) {
        return new File(storageDir, workflowId + ".json").delete();
    }

    private void ensureDirectoryExists() {
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }
}
```

`Files.move(..., REPLACE_EXISTING)` is used instead of `File.renameTo()` because it works reliably on Windows when the target file already exists.

- [ ] **Step 2: Run full build — all tests must pass**

```powershell
cd C:\Users\mirko\test\com.example.automation.parent
mvn clean verify
```

Expected last lines:
```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

(3 existing SWTBot tests + 7 new `WorkflowRepositoryTest` cases = 10 total)

- [ ] **Step 3: Commit WorkflowRepository**

```powershell
cd C:\Users\mirko\test
git add "com.example.automation.parent/com.example.automation/src/main/java/com/example/automation/persistence/"
git commit -m "feat: add WorkflowRepository with JSON persistence"
```
