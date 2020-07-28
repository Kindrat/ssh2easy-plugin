package jenkins.plugins.ssh2easy.gssh;

import java.io.OutputStream;
import java.io.PrintStream;

public class LoggerDecorator {
    private static final String PREFIX = "[GSSH]";
    private final PrintStream delegate;

    public LoggerDecorator(PrintStream delegate) {
        this.delegate = delegate;
    }

    public void delimiter() {
        printInternal("##########################################################################");
    }

    public void log(Throwable error, String message, Object... args) {
        log(message, args);
        error.printStackTrace(delegate);
    }

    public void log(String message, Throwable error) {
        log(message);
        error.printStackTrace(delegate);
    }

    public void log(String template, Object... args) {
        log(String.format(template, args));
    }

    public void log(String message) {
        printInternal(String.format("%s %s", PREFIX, message));
    }

    public OutputStream outputStream() {
        return delegate;
    }

    private void printInternal(String message) {
        delegate.println(message);
    }
}
