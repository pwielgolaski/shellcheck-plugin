package com.shellcheck;

import com.shellcheck.utils.ShellcheckResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

class ShellcheckAnnotationResult {

    private final ShellcheckAnnotationInput input;
    private final ShellcheckResult result;

    ShellcheckAnnotationResult(ShellcheckAnnotationInput input, ShellcheckResult result) {
        this.input = input;
        this.result = result;
    }

    public List<ShellcheckResult.Issue> getIssues() {
        return Optional.ofNullable(result).map(ShellcheckResult::getIssues).orElse(Collections.emptyList());
    }

    public ShellcheckAnnotationInput getInput() {
        return input;
    }

}
