package com.example.automation.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.automation.EclipseVariables;
import com.example.automation.api.IAction;
import com.example.automation.api.IActionContext;

/**
 * {@link com.example.automation.api.IAction} that checks out a named branch in a
 * local Git repository by running {@code git checkout}.
 *
 * <p>Config keys: {@code repoDir} (required), {@code branch} (optional — defaults to {@code main}).
 */
public class GitCheckoutAction implements IAction {

    @Override public String getId()          { return "git-checkout"; }
    @Override public String getName()        { return "Git Checkout"; }
    @Override public String getDescription() { return "Checks out a branch in a local git repository."; }

    @Override
    public Map<String, String> getDefaultConfig() {
        return Map.of("repoDir", "", "branch", "main");
    }

    @Override
    public List<String> validate(Map<String, String> config) {
        List<String> errors = new ArrayList<>();
        if (config.getOrDefault("repoDir", "").isBlank())
            errors.add("repoDir must not be blank");
        return errors;
    }

    @Override
    public void execute(Map<String, String> config, IActionContext context) throws Exception {
        String repoDir = config.get("repoDir");
        String branch  = config.getOrDefault("branch", "main");

        if (repoDir == null || repoDir.isBlank())
            throw new IllegalArgumentException("repoDir must not be blank");
        if (branch.isBlank()) branch = "main";
        repoDir = EclipseVariables.resolve(repoDir);
        ProcessRunner.run(
            List.of("git", "-C", repoDir, "checkout", branch),
            null, context);
    }
}
