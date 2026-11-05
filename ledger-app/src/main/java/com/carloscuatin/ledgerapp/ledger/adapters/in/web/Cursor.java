package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;

/**
 * The form a position in a list takes for a client: the identifier of the last row it received, with a version so the
 * format can change while cursors are in use, in URL safe base64 so it travels in a query without escaping. The encoding
 * is there so that clients treat it as opaque and do not build on its content; it hides nothing, because it holds nothing
 * the client did not already receive. It is not signed because forging one gains nothing: a position only says where to
 * start among the rows row level security lets the tenant see.
 */
final class Cursor {

    private static final String VERSION_PREFIX = "v1:";

    private Cursor() {
    }

    static String encode(TransferId position) {
        var content = VERSION_PREFIX + position.value();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Only the exact text {@link #encode} produces is a cursor. The JDK parser of a UUID also takes {@code 1-1-1-1-1} and
     * capital letters, and a format with several spellings of one position could not change later without breaking
     * whichever of them some client happened to depend on.
     */
    static TransferId decode(String cursor) {
        TransferId position;
        try {
            var content = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!content.startsWith(VERSION_PREFIX)) {
                throw new InvalidCursorException();
            }
            position = new TransferId(UUID.fromString(content.substring(VERSION_PREFIX.length())));
        } catch (IllegalArgumentException _) {
            throw new InvalidCursorException();
        }
        if (!encode(position).equals(cursor)) {
            throw new InvalidCursorException();
        }
        return position;
    }
}
