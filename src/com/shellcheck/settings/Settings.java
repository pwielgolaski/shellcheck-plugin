package com.shellcheck.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@State(name = "ShellcheckProjectComponent", storages = {@Storage("shellcheckPlugin.xml") })
public class Settings implements PersistentStateComponent<Settings> {
    public String shellcheckExecutable = "";
    public boolean treatAllIssuesAsWarnings;
    public boolean highlightWholeLine;
    public boolean pluginEnabled;

    @Nullable
    @Override
    public Settings getState() {
        return this;
    }

    @Override
    public void loadState(Settings state) {
        XmlSerializerUtil.copyBean(state, this);
    }


    public boolean isValid(Project project) {
        return ShellcheckFinder.validatePath(project, shellcheckExecutable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settings settings = (Settings) o;
        return treatAllIssuesAsWarnings == settings.treatAllIssuesAsWarnings &&
                highlightWholeLine == settings.highlightWholeLine &&
                pluginEnabled == settings.pluginEnabled &&
                Objects.equals(shellcheckExecutable, settings.shellcheckExecutable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shellcheckExecutable, treatAllIssuesAsWarnings, highlightWholeLine, pluginEnabled);
    }
}
