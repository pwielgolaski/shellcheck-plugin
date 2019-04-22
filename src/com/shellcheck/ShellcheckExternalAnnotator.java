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
import java.util.OptionalInt;

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
        boolean showErrorOnWholeLine = component.getSettings().highlightWholeLine;
        ErrorRange errorRange = new ErrorRange(document, issue);
        if (!errorRange.isValid()) {
            return null;
        }

        TextRange range;
        if (showErrorOnWholeLine) {
            int start = DocumentUtil.getFirstNonSpaceCharOffset(document, errorRange.getLineRange().getStartOffset(), errorRange.getLineRange().getEndOffset());
            range = new TextRange(start, errorRange.getLineRange().getEndOffset());
        } else {
            range = new TextRange(errorRange.getColumnRange().getStartOffset(), errorRange.getColumnRange().getEndOffset());
        }

        Annotation annotation = holder.createAnnotation(severity, range, "Shellcheck: " + issue.getFormattedMessage());
        if (annotation != null) {
            annotation.setAfterEndOfLine(errorRange.getColumnRange().getStartOffset() == errorRange.getLineRange().getEndOffset());
        }
        return annotation;
    }

    private static boolean isShellcheckFile(PsiFile file) {
        // TODO move to settings?
        List<String> acceptedExtensions = Arrays.asList("sh", "bash");
        boolean isBash = file.getFileType().getName().equals("Bash");
        String fileExtension = Optional.ofNullable(file.getVirtualFile()).map(VirtualFile::getExtension).orElse("");
        return isBash || acceptedExtensions.contains(fileExtension);
    }

    private static class ErrorRange {
        private boolean valid;
        private TextRange lineRange;
        private TextRange columnRange;

        ErrorRange(Document document, ShellcheckResult.Issue issue) {
            calculate(document, issue);
        }

        boolean isValid() {
            return valid;
        }

        TextRange getLineRange() {
            return lineRange;
        }

        TextRange getColumnRange() {
            return columnRange;
        }

        private void calculate(Document document, ShellcheckResult.Issue issue) {
            int line = issue.line - 1;
            int endLine = issue.endLine == 0 ? line : issue.endLine - 1;

            if (endLine >= 0 && endLine < document.getLineCount()) {
                TextRange beginLineRange = TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line));
                int lineStartOffset = appendNormalizeColumn(document, beginLineRange, issue.column - 1).orElse(beginLineRange.getStartOffset());

                int endColumn = issue.endColumn == 0 ? issue.column : issue.endColumn;
                TextRange endLineRange = TextRange.create(document.getLineStartOffset(endLine), document.getLineEndOffset(endLine));
                int endLineEndOffset = appendNormalizeColumn(document, endLineRange, endColumn - 1).orElse(endLineRange.getEndOffset());

                lineRange = TextRange.create(beginLineRange.getStartOffset(), endLineRange.getEndOffset());
                columnRange = TextRange.create(lineStartOffset, endLineEndOffset);
                valid = true;
            }
        }

        private OptionalInt appendNormalizeColumn(@NotNull Document document, TextRange lineRange, int column) {
            CharSequence text = document.getImmutableCharSequence();
            int col = 0;
            for (int i = lineRange.getStartOffset(); i < lineRange.getEndOffset(); i++) {
                char c = text.charAt(i);
                col += (c == '\t' ? 8 : 1);
                if (col > column) {
                    return OptionalInt.of(i);
                }
            }
            return OptionalInt.empty();
        }
    }
}


