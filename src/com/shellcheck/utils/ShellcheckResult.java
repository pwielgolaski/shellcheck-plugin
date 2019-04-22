package com.shellcheck.utils;

import java.util.Collections;
import java.util.List;

public class ShellcheckResult {
    private final List<Issue> issues;
    private final String errorOutput;

    public ShellcheckResult(List<Issue> issues, String errorOutput) {
        this.issues = issues == null ? Collections.emptyList() : issues;
        this.errorOutput = errorOutput;
    }

    public ShellcheckResult(String errorOutput) {
        this(null, errorOutput);
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public String getErrorOutput() {
        return errorOutput;
    }

    public static class Issue {
        public int line;
        public int endLine;
        public int column;
        public int endColumn;
        public String level;
        public String code;
        public String message;

        public String getFormattedMessage() {
            return message.trim() + " [" + (code == null ? "none" : "SC" + code) + "]";
        }
    }
}
