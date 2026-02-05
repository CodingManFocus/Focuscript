package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;

public final class PaperTextRenderer {
    private PaperTextRenderer() {}

    public static Component toComponent(FsText text) {
        Component comp = Component.text(text.plain());

        TextColor color = parseColor(text.color());
        if (color != null) {
            comp = comp.color(color);
        }

        comp = comp.decoration(TextDecoration.BOLD, text.isBold())
                .decoration(TextDecoration.ITALIC, text.isItalic())
                .decoration(TextDecoration.UNDERLINED, text.isUnderlined())
                .decoration(TextDecoration.STRIKETHROUGH, text.isStrikethrough())
                .decoration(TextDecoration.OBFUSCATED, text.isObfuscated());

        return comp;
    }

    public static FsText fromComponent(Component component) {
        if (component == null) return null;
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return FsText.of(plain);
    }

    private static TextColor parseColor(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(normalized);
            if (hex != null) return hex;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(normalized.toLowerCase(Locale.ROOT));
        return named;
    }
}
