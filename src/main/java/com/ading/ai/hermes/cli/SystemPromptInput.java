package com.ading.ai.hermes.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class SystemPromptInput implements CliStartupConfiguration.PromptInput {

    private final Console console;
    private final BufferedReader reader;
    private final PrintStream out;

    private SystemPromptInput(Console console, BufferedReader reader, PrintStream out) {
        this.console = console;
        this.reader = reader;
        this.out = out;
    }

    static SystemPromptInput standard() {
        return new SystemPromptInput(
                System.console(),
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out
        );
    }

    @Override
    public String readLine(String prompt) {
        if (console != null) {
            return console.readLine(prompt);
        }
        out.print(prompt);
        out.flush();
        return readFromStandardInput();
    }

    @Override
    public String readSecret(String prompt) {
        if (console != null) {
            char[] value = console.readPassword(prompt);
            if (value == null) {
                return null;
            }
            try {
                return new String(value);
            } finally {
                Arrays.fill(value, '\0');
            }
        }
        out.println("当前控制台无法隐藏 API Key，输入内容会显示在屏幕上。");
        return readLine(prompt);
    }

    private String readFromStandardInput() {
        try {
            return reader.readLine();
        } catch (IOException error) {
            throw new IllegalStateException("读取 CLI 配置失败", error);
        }
    }
}
