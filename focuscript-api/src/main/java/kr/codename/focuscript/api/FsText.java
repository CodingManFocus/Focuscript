package kr.codename.focuscript.api;

import java.util.Objects;

/**
 * Text abstraction that does NOT expose Adventure types.
 * MVP: plain text only.
 */
public final class FsText {
    private final String plain;
    private final String color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underlined;
    private final boolean strikethrough;
    private final boolean obfuscated;

    private FsText(
            String plain,
            String color,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated
    ) {
        this.plain = Objects.requireNonNull(plain, "plain");
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    public static FsText of(String plain) {
        return new FsText(plain, null, false, false, false, false, false);
    }

    public String plain() {
        return plain;
    }

    public String color() {
        return color;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public boolean isUnderlined() {
        return underlined;
    }

    public boolean isStrikethrough() {
        return strikethrough;
    }

    public boolean isObfuscated() {
        return obfuscated;
    }

    public FsText color(String color) {
        return new FsText(plain, color, bold, italic, underlined, strikethrough, obfuscated);
    }

    public FsText bold() {
        return bold(true);
    }

    public FsText bold(boolean value) {
        return new FsText(plain, color, value, italic, underlined, strikethrough, obfuscated);
    }

    public FsText italic() {
        return italic(true);
    }

    public FsText italic(boolean value) {
        return new FsText(plain, color, bold, value, underlined, strikethrough, obfuscated);
    }

    public FsText underlined() {
        return underlined(true);
    }

    public FsText underlined(boolean value) {
        return new FsText(plain, color, bold, italic, value, strikethrough, obfuscated);
    }

    public FsText strikethrough() {
        return strikethrough(true);
    }

    public FsText strikethrough(boolean value) {
        return new FsText(plain, color, bold, italic, underlined, value, obfuscated);
    }

    public FsText obfuscated() {
        return obfuscated(true);
    }

    public FsText obfuscated(boolean value) {
        return new FsText(plain, color, bold, italic, underlined, strikethrough, value);
    }

    @Override
    public String toString() {
        return plain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FsText other)) return false;
        return bold == other.bold
                && italic == other.italic
                && underlined == other.underlined
                && strikethrough == other.strikethrough
                && obfuscated == other.obfuscated
                && plain.equals(other.plain)
                && Objects.equals(color, other.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plain, color, bold, italic, underlined, strikethrough, obfuscated);
    }
}
