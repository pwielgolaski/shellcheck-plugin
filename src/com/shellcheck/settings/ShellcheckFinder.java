package com.shellcheck.settings;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public final class ShellcheckFinder {
    private ShellcheckFinder() {
    }

    @NotNull
    static List<String> findAllShellcheckExe() {
        List<File> fromPath = PathEnvironmentVariableUtil.findAllExeFilesInPath(getBinName("shellcheck"));
        return fromPath.stream().map(File::getAbsolutePath).distinct().collect(Collectors.toList());
    }

    static String getBinName(String baseBinName) {
        // TODO do we need different name for windows?
        return SystemInfo.isWindows ? baseBinName + ".cmd" : baseBinName;
    }

    static boolean validatePath(Project project, String path) {
        File filePath = new File(path);
        if (filePath.isAbsolute()) {
            if (!filePath.exists() || !filePath.isFile()) {
                return false;
            }
        } else {
            if (project == null || project.getBaseDir() == null) {
                return true;
            }
            VirtualFile child = project.getBaseDir().findFileByRelativePath(path);
            if (child == null || !child.exists() || child.isDirectory()) {
                return false;
            }
        }
        return true;
    }
}