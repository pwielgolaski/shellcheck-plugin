package com.shellcheck.settings;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import com.shellcheck.ShellcheckBundle;
import com.shellcheck.utils.ShellcheckRunner;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ItemEvent;
import java.util.Optional;
import java.util.stream.Stream;

public class ShellcheckSettingsPage implements Configurable {
    private final Project project;
    private final Settings settings;

    private JCheckBox pluginEnabledCheckbox;
    private JPanel panel;
    private JPanel errorPanel;
    private JCheckBox treatAllIssuesCheckBox;
    private JCheckBox highlightWholeLineCheckBox;
    private JLabel versionLabel;
    private JLabel shellcheckExeLabel;
    private TextFieldWithHistoryWithBrowseButton shellcheckExeField;

    public ShellcheckSettingsPage(@NotNull final Project project, @NotNull Settings settings) {
        this.project = project;
        this.settings = settings;
        initShellcheckField();
    }

    private void addListeners() {
        pluginEnabledCheckbox.addItemListener(e -> setEnabledState(e.getStateChange() == ItemEvent.SELECTED));
        DocumentAdapter docAdp = new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
                updateLaterInEDT();
            }
        };
        shellcheckExeField.getChildComponent().getTextEditor().getDocument().addDocumentListener(docAdp);
    }

    private void updateLaterInEDT() {
        UIUtil.invokeLaterIfNeeded(ShellcheckSettingsPage.this::update);
    }

    private void update() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        updateVersion();
    }

    private void setEnabledState(boolean enabled) {
        Stream.of(shellcheckExeField, shellcheckExeLabel,
                treatAllIssuesCheckBox, highlightWholeLineCheckBox)
                .forEach(c -> c.setEnabled(enabled));
    }

    private void updateVersion() {
        updateVersion(shellcheckExeField.getChildComponent().getText());
    }
    private void updateVersion(String shellcheckExe) {
        String version = "n.a.";
        if (ShellcheckFinder.validatePath(project, shellcheckExe)) {
            try {
                version = ShellcheckRunner.runVersion(shellcheckExe, Optional.ofNullable(project).map(Project::getBasePath).orElse("."));
            } catch (ExecutionException e) {
                version = "error";
            }
        }
        versionLabel.setText(version);
    }

    private void initShellcheckField() {
        TextFieldWithHistory textFieldWithHistory = shellcheckExeField.getChildComponent();
        textFieldWithHistory.setHistorySize(-1);
        textFieldWithHistory.setMinimumAndPreferredWidth(0);

        SwingHelper.addHistoryOnExpansion(textFieldWithHistory, ShellcheckFinder::findAllShellcheckExe);
        SwingHelper.installFileCompletionAndBrowseDialog(project, shellcheckExeField, "Select Shellcheck Exe", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    @Nls
    @Override
    public String getDisplayName() {
        return ShellcheckBundle.message("shellcheck.name");
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        loadSettings();
        updateVersion();
        addListeners();
        return panel;
    }

    @Override
    public boolean isModified() {
        return pluginEnabledCheckbox.isSelected() != settings.pluginEnabled
                || !shellcheckExeField.getChildComponent().getText().equals(settings.shellcheckExecutable)
                || treatAllIssuesCheckBox.isSelected() != settings.treatAllIssuesAsWarnings
                || highlightWholeLineCheckBox.isSelected() != settings.highlightWholeLine;
    }

    @Override
    public void apply() throws ConfigurationException {
        saveSettings();
    }

    private void saveSettings() {
        settings.pluginEnabled = pluginEnabledCheckbox.isSelected();
        settings.shellcheckExecutable = shellcheckExeField.getChildComponent().getText();
        settings.treatAllIssuesAsWarnings = treatAllIssuesCheckBox.isSelected();
        settings.highlightWholeLine = highlightWholeLineCheckBox.isSelected();
    }

    private void loadSettings() {
        pluginEnabledCheckbox.setSelected(settings.pluginEnabled);
        shellcheckExeField.getChildComponent().setText(settings.shellcheckExecutable);
        treatAllIssuesCheckBox.setSelected(settings.treatAllIssuesAsWarnings);
        highlightWholeLineCheckBox.setSelected(settings.highlightWholeLine);
        setEnabledState(settings.pluginEnabled);
    }

    @Override
    public void reset() {
        loadSettings();
    }

    public void showSettings() {
        String dimensionKey = ShowSettingsUtilImpl.createDimensionKey(this);
        SingleConfigurableEditor singleConfigurableEditor = new SingleConfigurableEditor(project, this, dimensionKey, false);
        singleConfigurableEditor.show();
    }
}
