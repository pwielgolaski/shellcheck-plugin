package com.shellcheck;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.shellcheck.utils.ShellcheckResult;
import com.shellcheck.utils.ShellcheckRunner;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ShellcheckExternalAnnotator extends ExternalAnnotator<ShellcheckAnnotationInput, ShellcheckAnnotationResult> {

    private static final Logger LOG = Logger.getInstance(ShellcheckExternalAnnotator.class);

    @Nullable
    @Override
    public ShellcheckAnnotationInput collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        return collectInformation(file);
    }

    @Nullable
    @Override
    public ShellcheckAnnotationInput collectInformation(@NotNull PsiFile file) {
        if (file.getContext() != null) {
            return null;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return null;
        }
        if (file.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
            return null;
        }
        ShellcheckProjectComponent component = file.getProject().getComponent(ShellcheckProjectComponent.class);
        if (!component.isSettingsValid() || !component.isEnabled() || !isShellcheckFile(file)) {
            return null;
        }
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        boolean fileModified = fileDocumentManager.isFileModified(virtualFile);
        return new ShellcheckAnnotationInput(component, file, fileModified ? file.getText() : null);
    }

    @Nullable
    @Override
    public ShellcheckAnnotationResult doAnnotate(ShellcheckAnnotationInput input) {
        ShellcheckProjectComponent component = input.getComponent();
        try {
            ShellcheckResult result = ShellcheckRunner.runCheck(component.getSettings().shellcheckExecutable, input.getCwd(), input.getFilePath(), input.getFileContent());

            if (StringUtils.isNotEmpty(result.getErrorOutput())) {
                component.showInfoNotification(result.getErrorOutput(), NotificationType.WARNING);
                return null;
            }
            return new ShellcheckAnnotationResult(input, result);
        } catch (Exception e) {
            LOG.error("Error running Shellcheck inspection: ", e);
            component.showInfoNotification("Error running Shellcheck inspection: " + e.getMessage(), NotificationType.ERROR);
        }
        return null;
    }

    @Override
    public void apply(@NotNull PsiFile file, ShellcheckAnnotationResult annotationResult, @NotNull AnnotationHolder holder) {
        if (annotationResult == null) {
            return;
        }
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return;
        }

        ShellcheckProjectComponent component = annotationResult.getInput().getComponent();
        for (ShellcheckResult.Issue issue : annotationResult.getIssues()) {
            HighlightSeverity severity = getHighlightSeverity(issue, component.getSettings().treatAllIssuesAsWarnings);
            createAnnotation(holder, document, issue, severity, component);
        }
    }

    private static HighlightSeverity getHighlightSeverity(ShellcheckResult.Issue issue, boolean treatAsWarnings) {
        switch (issue.level) {
            case "error":
                return treatAsWarnings ? HighlightSeverity.WARNING : HighlightSeverity.ERROR;
            case "warning":
                return HighlightSeverity.WARNING;
            case "info":
                return HighlightSeverity.INFORMATION;
            default:
                return HighlightSeverity.INFORMATION;
        }
    }

    @Nullable
    private Annotation createAnnotation(@NotNull AnnotationHolder holder, @NotNull Document document, @NotNull ShellcheckResult.Issue issue,
                                        @NotNull HighlightSeverity severity,
                                        ShellcheckProjectComponent component) {
        int errorLine = issue.line - 1;
        boolean showErrorOnWholeLine = component.getSettings().highlightWholeLine;

        if (errorLine < 0 || errorLine >= document.getLineCount()) {
            return null;
        }

        int lineStartOffset = document.getLineStartOffset(errorLine);
        int lineEndOffset = document.getLineEndOffset(errorLine);

        int errorLineStartOffset = appendNormalizeColumn(document, lineStartOffset, lineEndOffset, issue.column - 1);
        if (errorLineStartOffset == -1) {
            return null;
        }

        TextRange range;
        if (showErrorOnWholeLine) {
            int start = DocumentUtil.getFirstNonSpaceCharOffset(document, lineStartOffset, lineEndOffset);
            range = new TextRange(start, lineEndOffset);
        } else {
            range = new TextRange(errorLineStartOffset, errorLineStartOffset + 1);
        }

        Annotation annotation = holder.createAnnotation(severity, range, "Shellcheck: " + issue.getFormattedMessage());
        if (annotation != null) {
            annotation.setAfterEndOfLine(errorLineStartOffset == lineEndOffset);
        }
        return annotation;
    }

    private int appendNormalizeColumn(@NotNull Document document, int startOffset, int endOffset, int column) {
        CharSequence text = document.getImmutableCharSequence();
        int col = 0;
        for (int i = startOffset; i < endOffset; i++) {
            char c = text.charAt(i);
            col += (c == '\t' ? 8 : 1);
            if (col > column) {
                return i;
            }
        }
        return startOffset;
    }

    private static boolean isShellcheckFile(PsiFile file) {
        // TODO move to settings?
        List<String> acceptedExtensions = Arrays.asList("sh", "bash");
        boolean isBash = file.getFileType().getName().equals("Bash");
        String fileExtension = Optional.ofNullable(file.getVirtualFile()).map(VirtualFile::getExtension).orElse("");
        return isBash || acceptedExtensions.contains(fileExtension);
    }
}


