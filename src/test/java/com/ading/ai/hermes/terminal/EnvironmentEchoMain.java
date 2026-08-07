package com.ading.ai.hermes.terminal;

public final class EnvironmentEchoMain {

    private EnvironmentEchoMain() {
    }

    public static void main(String[] args) {
        System.out.print(System.getenv(args[0]) + "|" + System.getenv(args[1]));
    }
}
