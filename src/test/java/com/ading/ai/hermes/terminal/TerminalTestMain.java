package com.ading.ai.hermes.terminal;

public final class TerminalTestMain {

    private TerminalTestMain() {
    }

    public static void main(String[] args) throws Exception {
        if ("output".equals(args[0])) {
            System.out.print("HEAD-" + "x".repeat(Integer.parseInt(args[1])) + "-TAIL");
            return;
        }
        if ("sleep".equals(args[0])) {
            Thread.sleep(Long.parseLong(args[1]));
            System.out.print("finished");
        }
    }
}
