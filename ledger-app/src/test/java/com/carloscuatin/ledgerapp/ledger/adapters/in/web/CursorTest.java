package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.carloscuatin.ledgerapp.ledger.domain.TransferId;

/** Pure text: no database, no server and no tenant have any say in what a cursor is. */
class CursorTest {

    private static final String ID = "019a7f3c-5e21-7abc-8def-0123456789ab";
    private static final int LONGEST_CURSOR_THE_CONTRACT_ACCEPTS = 64;

    private static String base64Url(String content) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {ID, "00000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff"})
    void aPositionSurvivesTheRoundTrip(String id) {
        var position = new TransferId(UUID.fromString(id));

        assertThat(Cursor.decode(Cursor.encode(position))).isEqualTo(position);
    }

    @Test
    void aCursorHoldsTheVersionAndThePositionAndNothingElse() {
        var cursor = Cursor.encode(new TransferId(UUID.fromString(ID)));

        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)).isEqualTo("v1:" + ID);
    }

    @Test
    void aCursorTravelsInAQueryWithoutEscapingAndFitsTheContract() {
        var cursor = Cursor.encode(new TransferId(UUID.fromString(ID)));

        assertThat(cursor).matches("[A-Za-z0-9_-]+").hasSizeLessThanOrEqualTo(LONGEST_CURSOR_THE_CONTRACT_ACCEPTS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        " ",
        "not base64!",
        "====",
        "a",
        "abc*def"
    })
    void textThatIsNotBase64IsNotACursor(String cursor) {
        assertThatThrownBy(() -> Cursor.decode(cursor)).isExactlyInstanceOf(InvalidCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "SYNTHETICMARK",
        "v1:",
        "v1",
        ":" + ID,
        "v2:" + ID,
        "V1:" + ID,
        ID,
        " v1:" + ID,
        "v1:not-a-uuid",
        "v1:" + ID + " ",
        "v1:" + ID + "0",
        "v1:v1:" + ID,
        "v1:1-1-1-1-1",
        "v1:019A7F3C-5E21-7ABC-8DEF-0123456789AB"
    })
    void contentThatIsNotExactlyAPositionIsNotACursor(String content) {
        var cursor = base64Url(content);

        assertThatThrownBy(() -> Cursor.decode(cursor)).isExactlyInstanceOf(InvalidCursorException.class);
    }

    @Test
    void aPositionWithPaddingAddedIsNotACursor() {
        var cursor = Cursor.encode(new TransferId(UUID.fromString(ID))) + "=";

        assertThatThrownBy(() -> Cursor.decode(cursor)).isExactlyInstanceOf(InvalidCursorException.class);
    }

    @Test
    void bytesThatAreNotTextAreNotACursor() {
        var cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[] {(byte) 0xFF, (byte) 0xFE, 0, 1});

        assertThatThrownBy(() -> Cursor.decode(cursor)).isExactlyInstanceOf(InvalidCursorException.class);
    }

    /** What the JDK says about a bad UUID repeats it, and a cause travels wherever the exception is logged. */
    @Test
    void theRefusalCarriesNothingOfTheInput() {
        var cursor = base64Url("v1:SYNTHETICMARK");

        assertThatThrownBy(() -> Cursor.decode(cursor))
                .hasMessage("The cursor does not decode to a position")
                .hasNoCause();
    }
}
