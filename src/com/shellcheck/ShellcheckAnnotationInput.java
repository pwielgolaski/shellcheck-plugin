package com.shellcheck;

import com.intellij.psi.PsiFile;

class ShellcheckAnnotationInput {
    private final ShellcheckProjectComponent component;
    private final PsiFile psiFile;
    private final String fileContent;

    ShellcheckAnnotationInput(ShellcheckProjectComponent component, PsiFile psiFile, String fileContent) {
        this.component = component;
        this.psiFile = psiFile;
        this.fileContent = fileContent;
    }

    ShellcheckProjectComponent getComponent() {
        return component;
    }

    String getCwd() {
        return psiFile.getProject().getBasePath();
    }

    String getFilePath() {
        return psiFile.getVirtualFile().getPath();
    }

    String getFileContent() {
        return fileContent;
    }

}
