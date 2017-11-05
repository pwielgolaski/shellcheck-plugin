package com.shellcheck.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShellcheckRunner {
    private ShellcheckRunner() {
    }

    private static final Logger LOG = Logger.getInstance(ShellcheckRunner.class);
    private static final int TIME_OUT = (int) TimeUnit.SECONDS.toMillis(120L);

    public static ShellcheckResult runCheck(@NotNull String shellcheckExe, @NotNull String cwd, @NotNull String file, String content) {
        ShellcheckResult result;
        try {
            GeneralCommandLine commandLine = createCommandLine(shellcheckExe, cwd)
                    .withInput(content)
                    .withParameters("--format=json", content == null ? file : "-");
            ProcessOutput out = execute(commandLine);
            try {
                result = new ShellcheckResult(parse(out.getStdout()), out.getStderr());
            } catch (Exception e) {
                result = new ShellcheckResult(out.getStdout());
            }
        } catch (Exception e) {
            LOG.error("Problem with running shellcheck", e);
            result = new ShellcheckResult(e.toString());
        }
        return result;
    }

    private static List<ShellcheckResult.Issue> parse(String json) {
        Gson g = new GsonBuilder().create();
        Type listType = new TypeToken<List<ShellcheckResult.Issue>>() {
        }.getType();
        return g.fromJson(json, listType);
    }

    @NotNull
    public static String runVersion(@NotNull String shellcheckExe, @NotNull String cwd) throws ExecutionException {
        if (!new File(shellcheckExe).exists()) {
            LOG.warn("Calling version with invalid shellcheckExe exe " + shellcheckExe);
            return "";
        }

        ProcessOutput out = execute(createCommandLine(shellcheckExe, cwd).withParameters("--version"));
        if (out.getExitCode() == 0) {
            String output = out.getStdout().trim();
            Matcher matcher = Pattern.compile("^version:(.+)$", Pattern.MULTILINE).matcher(output);
            return matcher.find() ? matcher.group(1).trim() : output;
        }

        return "";
    }

    @NotNull
    private static CommandLineWithInput createCommandLine(@NotNull String shellcheckExe, @NotNull String cwd) {
        CommandLineWithInput commandLine = new CommandLineWithInput();
        commandLine.setExePath(shellcheckExe);
        commandLine.setWorkDirectory(cwd);
        return commandLine;
    }

    @NotNull
    public static ProcessOutput execute(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
        LOG.info("Running command: " + commandLine.getCommandLineString());
        Process process = commandLine.createProcess();
        OSProcessHandler processHandler = new ColoredProcessHandler(process, commandLine.getCommandLineString(), StandardCharsets.UTF_8);
        final ProcessOutput output = new ProcessOutput();
        processHandler.addProcessListener(new ProcessAdapter() {
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType.equals(ProcessOutputTypes.STDERR)) {
                    output.appendStderr(event.getText());
                } else if (!outputType.equals(ProcessOutputTypes.SYSTEM)) {
                    output.appendStdout(event.getText());
                }
            }
        });
        processHandler.startNotify();
        if (processHandler.waitFor(TIME_OUT)) {
            output.setExitCode(process.exitValue());
        } else {
            processHandler.destroyProcess();
            output.setTimeout();
        }
        if (output.isTimeout()) {
            throw new ExecutionException("Command '" + commandLine.getCommandLineString() + "' is timed out.");
        }
        return output;
    }
}