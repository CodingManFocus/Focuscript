package kr.codename.focuscript.core.compiler;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Thrown when Kotlin compilation fails.
 *
 * <p>Contains the compiler messages so tooling (e.g. Web IDE) can display the actual errors
 * without scraping server logs.</p>
 */
public final class KotlinCompilationException extends IOException {

    private final List<String> messages;

    public KotlinCompilationException(String message, List<String> messages) {
        super(message);
        this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
    }

    public List<String> getMessages() {
        return messages;
    }
}
