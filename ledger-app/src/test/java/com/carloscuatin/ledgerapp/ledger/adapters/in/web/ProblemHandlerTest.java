package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

/** The exceptions of the framework no request of a test can provoke on purpose. */
@ExtendWith(OutputCaptureExtension.class)
class ProblemHandlerTest {

    private static final String MARK = "SYNTHETICMARK";

    private final ProblemHandler handler = new ProblemHandler();

    /** It has the same parent as a client's value that cannot be converted, and it is this side that cannot convert it. */
    @Test
    void aConversionTheApplicationCannotDoIsAFailureOfThisSide(CapturedOutput output) throws Exception {
        var response = handle(new ConversionNotSupportedException(MARK, String.class, null));

        assertThat(problemIn(response).getType()).isEqualTo(ProblemType.INTERNAL_ERROR.type());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problemIn(response).getDetail()).doesNotContain(MARK);
        assertThat(output).contains(" ERROR ").contains(ConversionNotSupportedException.class.getName());
    }

    @Test
    void aResponseThatCannotBeWrittenIsAFailureOfThisSide(CapturedOutput output) throws Exception {
        var response = handle(new HttpMessageNotWritableException(MARK));

        assertThat(problemIn(response).getType()).isEqualTo(ProblemType.INTERNAL_ERROR.type());
        assertThat(problemIn(response).getDetail()).doesNotContain(MARK);
        assertThat(output).contains(" ERROR ").contains(MARK);
    }

    @Test
    void anyOtherClientMistakeIsABadRequest(CapturedOutput output) throws Exception {
        var response = handle(new ServletRequestBindingException(MARK));

        assertThat(problemIn(response).getType()).isEqualTo(ProblemType.BAD_REQUEST.type());
        assertThat(problemIn(response).getDetail()).doesNotContain(MARK);
        assertThat(output).doesNotContain(MARK);
    }

    @Test
    void aClientStatusWithNoTypeOfItsOwnKeepsToTheCatalog() throws Exception {
        var response = handle(new ResponseStatusException(HttpStatus.CONFLICT, MARK));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(problemIn(response).getType()).isEqualTo(ProblemType.BAD_REQUEST.type());
    }

    @Test
    void aRefusalIsAProblemWhateverTheClientAccepts() throws Exception {
        var response = handle(new ServletRequestBindingException(MARK));

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().containsHeader(HttpHeaders.ACCEPT)).isFalse();
    }

    private ResponseEntity<Object> handle(Exception failure) throws Exception {
        var request = new MockHttpServletRequest("GET", "/v1/transfers");
        var response = handler.handleException(failure, new ServletWebRequest(request));
        assertThat(response).isNotNull();
        return response;
    }

    private static ProblemDetail problemIn(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) response.getBody();
    }
}
