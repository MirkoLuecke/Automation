package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

public class GitCloneAction implements IAction {

    @Override public String getId()          { return "git-clone"; }
    @Override public String getName()        { return "Git Clone"; }
    @Override public String getDescription() { return "Clones a git repository to a local directory."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("url", "", "targetDir", "", "branch", "");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("url", "").isBlank())
            errors.add("url must not be blank");
        if (config.getOrDefault("targetDir", "").isBlank())
            errors.add("targetDir must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String url       = config.get("url");
        String targetDir = config.get("targetDir");
        String branch    = config.getOrDefault("branch", "");

        List<String> cmd = new ArrayList<>(List.of("git", "clone"));
        if (!branch.isBlank()) { cmd.add("--branch"); cmd.add(branch); }
        cmd.add(url);
        cmd.add(targetDir);

        ProcessRunner.run(cmd, null, context);
    }
}
