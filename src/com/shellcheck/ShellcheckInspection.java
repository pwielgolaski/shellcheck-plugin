package com.shellcheck;

import com.intellij.codeInspection.BatchSuppressableTool;
import com.intellij.codeInspection.ExternalAnnotatorInspectionVisitor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.shellcheck.settings.ShellcheckSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public class ShellcheckInspection extends LocalInspectionTool implements BatchSuppressableTool, UnfairLocalInspectionTool {

    @NotNull
    public String getDisplayName() {
        return ShellcheckBundle.message("shellcheck.property.inspection.display.name");
    }

    @NotNull
    public String getShortName() {
        return ShellcheckBundle.message("shellcheck.property.inspection.short.name");
    }


    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
        return ExternalAnnotatorInspectionVisitor.checkFileWithExternalAnnotator(file, manager, isOnTheFly, new ShellcheckExternalAnnotator());
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
        return new ExternalAnnotatorInspectionVisitor(holder, new ShellcheckExternalAnnotator(), isOnTheFly);
    }

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element) {
        return false;
    }

    @NotNull
    @Override
    public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
        return new SuppressQuickFix[0];
    }
}