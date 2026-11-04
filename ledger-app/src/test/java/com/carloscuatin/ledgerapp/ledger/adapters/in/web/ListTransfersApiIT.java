package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertRejectedInputs;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.TransferRows;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;

/**
 * Listing on the real server. What the application answers is checked against the table as the superuser, past row level
 * security, because a list that loses rows or shows those of another tenant raises no error anywhere.
 */
class ListTransfersApiIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";
    private static final String MARK = "SYNTHETICMARK";
    private static final UUID HIGHEST_POSITION = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final int MOST_PAGES_IN_A_WALK = 1_000;
    private static final UUID LOWEST_POSITION = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /** A page as a client reads it, with the order it arrived in. */
    private record Page(List<UUID> ids, String nextCursor) {
    }

    private Page pageOf(UUID tenant, String query) throws Exception {
        var response = send(requestAs(tenant, TRANSFERS + query));
        assertThat(response.statusCode()).isEqualTo(200);
        return pageIn(response);
    }

    private static Page pageIn(HttpResponse<String> response) {
        var page = JSON.readTree(response.body());
        var ids = new ArrayList<UUID>();
        for (var item : page.path("items")) {
            ids.add(UUID.fromString(item.path("id").asString()));
        }
        var next = page.path("nextCursor");
        return new Page(ids, next.isNull() ? null : next.asString());
    }

    private static List<UUID> seedInOrder(UUID tenant, int rows) throws Exception {
        var unique = UUID.randomUUID().getLeastSignificantBits();
        var ids = new ArrayList<UUID>();
        for (var row = 0; row < rows; row++) {
            ids.add(positionNear(row, unique));
            TransferRows.seedAt(tenant, ids.getLast());
        }
        return ids;
    }

    /** Far apart, and grow with {@code row}, so that a test can place a row right next to any of them. */
    private static UUID positionNear(int row, long unique) {
        return new UUID((row + 1L) << 32 | 0x7000L, unique);
    }

    private static List<UUID> newestFirst(List<UUID> oldestFirst) {
        return oldestFirst.reversed();
    }

    private void create(UUID tenant) throws Exception {
        assertThat(send(postingJson(requestAs(tenant, TRANSFERS), aValidBody())).statusCode()).isEqualTo(201);
    }

    private static String cursorAt(UUID position) {
        return Cursor.encode(new TransferId(position));
    }

    private static String base64Url(String content) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String inQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void theFirstPageHoldsTheNewestTransfers() throws Exception {
        var tenant = UUID.randomUUID();
        for (var row = 0; row < 5; row++) {
            create(tenant);
        }
        var inTheTable = TransferRows.idsNewestFirst(tenant);

        var response = send(requestAs(tenant, TRANSFERS + "?limit=2"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        var body = JSON.readTree(response.body());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("items", "nextCursor");
        var page = pageIn(response);
        assertThat(inTheTable).hasSize(5);
        assertThat(page.ids()).containsExactlyElementsOf(inTheTable.subList(0, 2));
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void anItemIsTheSameTransferAReadShows() throws Exception {
        var tenant = UUID.randomUUID();
        create(tenant);
        var id = TransferRows.idsNewestFirst(tenant).getFirst();

        var item = JSON.readTree(send(requestAs(tenant, TRANSFERS)).body()).path("items").path(0);

        assertThat(item).isEqualTo(JSON.readTree(send(requestAs(tenant, TRANSFERS + "/" + id)).body()));
    }

    @Test
    void theCursorHoldsNothingButTheLastIdentifierTheClientReceived() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 3);

        var page = pageOf(tenant, "?limit=2");

        var content = new String(Base64.getUrlDecoder().decode(page.nextCursor()), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("v1:" + rows.get(1));
    }

    @Test
    void followingTheCursorWalksEveryTransferOnce() throws Exception {
        var tenant = UUID.randomUUID();
        for (var row = 0; row < 5; row++) {
            create(tenant);
        }
        var walked = new ArrayList<UUID>();

        var first = pageOf(tenant, "?limit=2");
        walked.addAll(first.ids());
        var second = pageOf(tenant, "?limit=2&cursor=" + first.nextCursor());
        walked.addAll(second.ids());
        var last = pageOf(tenant, "?limit=2&cursor=" + second.nextCursor());
        walked.addAll(last.ids());

        assertThat(last.ids()).hasSize(1);
        assertThat(last.nextCursor()).isNull();
        assertThat(JSON.readTree(send(requestAs(tenant, TRANSFERS + "?limit=2&cursor=" + second.nextCursor())).body())
                .path("nextCursor").isNull()).isTrue();
        assertThat(walked).containsExactlyElementsOf(TransferRows.idsNewestFirst(tenant));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void anyPageSizeWalksTheSameRowsInTheSameOrder(int limit) throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 5);

        assertThat(walk(tenant, limit, () -> {
        })).containsExactlyElementsOf(newestFirst(rows));
    }

    private interface BetweenPages {

        void run() throws Exception;
    }

    /** Bounded because a cursor that never ends is exactly what could go wrong here, and it must fail and not hang. */
    private List<UUID> walk(UUID tenant, int limit, BetweenPages between) throws Exception {
        var walked = new ArrayList<UUID>();
        var page = pageOf(tenant, "?limit=" + limit);
        walked.addAll(page.ids());
        for (var pages = 1; page.nextCursor() != null; pages++) {
            assertThat(pages).isLessThan(MOST_PAGES_IN_A_WALK);
            between.run();
            page = pageOf(tenant, "?limit=%d&cursor=%s".formatted(limit, page.nextCursor()));
            walked.addAll(page.ids());
        }
        return walked;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5})
    void aPageThatEndsTheRowsHasNoFurtherCursor(int limit) throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, limit);

        var page = pageOf(tenant, "?limit=" + limit);

        assertThat(page.ids()).containsExactlyElementsOf(newestFirst(rows));
        assertThat(page.nextCursor()).isNull();
        assertThat(JSON.readTree(send(requestAs(tenant, TRANSFERS + "?limit=" + limit)).body()).has("nextCursor"))
                .isTrue();
    }

    /** The extra row asked of the database is what spares a client the last request here, which would come back empty. */
    @Test
    void aFullPageAtTheEndOfAWalkDoesNotLeadToAnEmptyOne() throws Exception {
        var tenant = UUID.randomUUID();
        seedInOrder(tenant, 4);

        var first = pageOf(tenant, "?limit=2");
        var second = pageOf(tenant, "?limit=2&cursor=" + first.nextCursor());

        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.ids()).hasSize(2);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void aTenantWithoutTransfersGetsAnEmptyPage() throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS));

        assertThat(response.statusCode()).isEqualTo(200);
        var body = JSON.readTree(response.body());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("items", "nextCursor");
        assertThat(body.path("items").isEmpty()).isTrue();
        assertThat(body.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void transfersCreatedBetweenPagesDoNotDisturbAWalk() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 6);

        var walked = walk(tenant, 2, () -> {
            create(tenant);
            create(tenant);
        });

        assertThat(walked).containsExactlyElementsOf(newestFirst(rows));
        assertThat(TransferRows.countInTheTableFor(tenant)).isEqualTo(10);
    }

    /**
     * Inside a millisecond the identifiers of the generator are in no particular order, so a transfer created during a
     * walk can land on either side of the cursor. Below it, it appears in the walk and takes a place in a page, and the
     * rows that were already there are all still visited: that is all the contract promises.
     */
    @Test
    void aTransferCreatedRightBelowTheCursorAppearsAndOmitsNothing() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 4);
        var cursorRow = rows.get(2);
        var rightBelow = new UUID(cursorRow.getMostSignificantBits(), cursorRow.getLeastSignificantBits() - 1);

        var first = pageOf(tenant, "?limit=2");
        TransferRows.seedAt(tenant, rightBelow);
        var second = pageOf(tenant, "?limit=2&cursor=" + first.nextCursor());
        var third = pageOf(tenant, "?limit=2&cursor=" + second.nextCursor());

        assertThat(first.ids()).containsExactly(rows.get(3), cursorRow);
        assertThat(second.ids()).containsExactly(rightBelow, rows.get(1));
        assertThat(third.ids()).containsExactly(rows.get(0));
        assertThat(third.nextCursor()).isNull();
    }

    /**
     * Above the cursor it is missed by this walk. This is also what happens to a transfer whose identifier was taken
     * before the walk passed that position and whose commit came after: identifiers are not assigned in commit order, so
     * this list cannot be used to follow new transfers.
     */
    @Test
    void aTransferCreatedRightAboveTheCursorIsMissedAndRepeatsNothing() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 4);
        var cursorRow = rows.get(2);
        var rightAbove = new UUID(cursorRow.getMostSignificantBits(), cursorRow.getLeastSignificantBits() + 1);

        var first = pageOf(tenant, "?limit=2");
        TransferRows.seedAt(tenant, rightAbove);
        var second = pageOf(tenant, "?limit=2&cursor=" + first.nextCursor());

        assertThat(second.ids()).containsExactly(rows.get(1), rows.get(0));
        assertThat(second.nextCursor()).isNull();
        assertThat(TransferRows.idsNewestFirst(tenant)).contains(rightAbove);
    }

    /** While rows really arrive at the same time: whatever those do, the ones that existed are each visited once. */
    @Test
    void aWalkDuringConcurrentCreationsVisitsEveryExistingTransferOnce() throws Exception {
        var tenant = UUID.randomUUID();
        for (var row = 0; row < 40; row++) {
            create(tenant);
        }
        var existing = TransferRows.idsNewestFirst(tenant);
        var walkEnded = new AtomicBoolean();
        var writers = IntStream.range(0, 4).mapToObj(writer -> CompletableFuture.runAsync(() -> {
            while (!walkEnded.get()) {
                try {
                    create(tenant);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }
        })).toList();

        List<UUID> walked;
        try {
            walked = walk(tenant, 3, () -> {
            });
        } finally {
            walkEnded.set(true);
        }
        CompletableFuture.allOf(writers.toArray(CompletableFuture[]::new)).join();

        assertThat(new HashSet<>(walked)).hasSameSizeAs(walked);
        assertThat(walked).containsAll(existing);
        assertThat(walked.stream().filter(existing::contains).toList()).containsExactlyElementsOf(existing);
        assertThat(walked).isSubsetOf(TransferRows.idsNewestFirst(tenant));
    }

    @Test
    void theListShowsOnlyTheTenantOfThePrincipal() throws Exception {
        var tenantA = UUID.randomUUID();
        var tenantB = UUID.randomUUID();
        create(tenantA);
        create(tenantA);
        create(tenantB);
        var ofA = TransferRows.idsNewestFirst(tenantA);
        var ofB = TransferRows.idsNewestFirst(tenantB);

        assertThat(pageOf(tenantA, "").ids()).containsExactlyElementsOf(ofA);
        assertThat(pageOf(tenantB, "").ids()).containsExactlyElementsOf(ofB);
        assertThat(ofA).hasSize(2).doesNotContainAnyElementsOf(ofB);
        assertThat(ofB).hasSize(1);
        assertThat(TransferRows.tenantOf(ofA.getFirst())).isEqualTo(tenantA);
        assertThat(TransferRows.tenantOf(ofB.getFirst())).isEqualTo(tenantB);
    }

    @Test
    void aTenantInTheRequestDoesNotOpenAnotherTenantsList() throws Exception {
        var owner = UUID.randomUUID();
        var asker = UUID.randomUUID();
        create(owner);

        var response = send(
                requestAs(asker, TRANSFERS + "?tenant_id=" + owner).header("X-Tenant-Id", owner.toString()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(pageIn(response).ids()).isEmpty();
        assertThat(TransferRows.countInTheTableFor(owner)).isEqualTo(1);
    }

    /**
     * The position of a row of another tenant and a position where no row exists, side by side and with nothing of the
     * asker between them, must answer the same: any difference would tell that the identifier exists.
     */
    @Test
    void aCursorMadeFromARowOfAnotherTenantShowsOnlyTheOwnRowsAndNoSign() throws Exception {
        var owner = UUID.randomUUID();
        var asker = UUID.randomUUID();
        var ofAsker = seedInOrder(asker, 3);
        var between = ofAsker.get(1);
        var ofOwner = new UUID(between.getMostSignificantBits() + (1L << 16), between.getLeastSignificantBits());
        var unknown = new UUID(between.getMostSignificantBits() + (1L << 17), between.getLeastSignificantBits());
        TransferRows.seedAt(owner, ofOwner);

        var forTheRow = send(requestAs(asker, TRANSFERS + "?cursor=" + cursorAt(ofOwner)));
        var forNothing = send(requestAs(asker, TRANSFERS + "?cursor=" + cursorAt(unknown)));

        assertThat(pageIn(forTheRow).ids()).containsExactly(ofAsker.get(1), ofAsker.get(0));
        assertThat(pageIn(forTheRow).nextCursor()).isNull();
        assertThat(forTheRow.statusCode()).isEqualTo(forNothing.statusCode());
        assertThat(forTheRow.body()).isEqualTo(forNothing.body()).doesNotContain(ofOwner.toString())
                .doesNotContain(owner.toString());
        assertThat(headersWithoutTheDate(forTheRow)).isEqualTo(headersWithoutTheDate(forNothing));
        assertThat(TransferRows.tenantOf(ofOwner)).isEqualTo(owner);
        for (var shown : pageIn(forTheRow).ids()) {
            assertThat(TransferRows.tenantOf(shown)).isEqualTo(asker);
        }
    }

    private static Map<String, List<String>> headersWithoutTheDate(HttpResponse<String> response) {
        var headers = new TreeMap<>(response.headers().map());
        headers.remove("date");
        return headers;
    }

    @Test
    void aCursorMadeFromARowOfAnotherTenantShowsNothingToATenantWithoutRows() throws Exception {
        var owner = UUID.randomUUID();
        create(owner);
        var ofOwner = TransferRows.idsNewestFirst(owner).getFirst();

        var page = pageOf(UUID.randomUUID(), "?cursor=" + cursorAt(ofOwner));

        assertThat(page.ids()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    /** Valid ones an attacker can write without any row: they move the position among the own rows and nothing more. */
    @Test
    void theHighestPositionShowsOnlyTheOwnRows() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 2);
        create(UUID.randomUUID());

        var page = pageOf(tenant, "?cursor=" + cursorAt(HIGHEST_POSITION));

        assertThat(page.ids()).containsExactlyElementsOf(newestFirst(rows));
        assertThat(TransferRows.countInTheTableFor(tenant)).isEqualTo(2);
        assertThat(TransferRows.countInTheTable()).isGreaterThan(2);
    }

    @Test
    void theLowestPositionHasNothingBelowIt() throws Exception {
        var tenant = UUID.randomUUID();
        seedInOrder(tenant, 2);

        var page = pageOf(tenant, "?cursor=" + cursorAt(LOWEST_POSITION));

        assertThat(page.ids()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    private static Stream<String> cursorsThatAreNotAPosition() {
        var valid = cursorAt(UUID.fromString("019a7f3c-5e21-7abc-8def-0123456789ab"));
        return Stream.of(
                "",
                "%s!*%s".formatted(MARK, MARK),
                MARK + "+/=",
                base64Url(MARK),
                base64Url("v2:019a7f3c-5e21-7abc-8def-0123456789ab"),
                base64Url("v1:" + MARK),
                base64Url("v1:1-1-1-1-1"),
                valid + "=",
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xFF, (byte) 0xFE}),
                MARK + "a".repeat(64 - MARK.length()));
    }

    @ParameterizedTest
    @MethodSource("cursorsThatAreNotAPosition")
    void aCursorThatIsNotAPositionIsRefusedWithoutRepeatingIt(String cursor) throws Exception {
        var tenant = UUID.randomUUID();
        seedInOrder(tenant, 1);

        var response = send(requestAs(tenant, TRANSFERS + "?cursor=" + inQuery(cursor)));

        var problem = assertProblem(response, ProblemType.INVALID_CURSOR, TRANSFERS);
        assertThat(problem.has("errors")).isFalse();
        assertThat(response.body()).doesNotContain(MARK);
        if (!cursor.isEmpty()) {
            assertThat(response.body()).doesNotContain(cursor);
        }
    }

    /** The framework joins repeated values with a comma, and what it joins is judged like any other cursor. */
    @Test
    void aCursorRepeatedInTheQueryIsNotAPosition() throws Exception {
        var valid = cursorAt(HIGHEST_POSITION);
        var tenant = UUID.randomUUID();

        assertProblem(send(requestAs(tenant, TRANSFERS + "?cursor=a&cursor=b")), ProblemType.INVALID_CURSOR, TRANSFERS);
        assertProblem(send(requestAs(tenant, "%s?cursor=%s&cursor=%s".formatted(TRANSFERS, valid, valid))),
                ProblemType.VALIDATION_FAILED, TRANSFERS);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aCursorThatIsNotAPositionIsNotAnErrorInTheLog(CapturedOutput output) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?cursor=" + base64Url(MARK)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(output).doesNotContain(" ERROR ").doesNotContain(" WARN ").doesNotContain("Exception")
                .doesNotContain(base64Url(MARK));
    }

    @Test
    void aCursorOverItsLengthIsAConstraintOfTheContract() throws Exception {
        var cursor = MARK + "a".repeat(65 - MARK.length());

        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?cursor=" + cursor));

        var problem = assertProblem(response, ProblemType.VALIDATION_FAILED, TRANSFERS);
        assertRejectedInputs(problem, List.of(Map.of("parameter", "cursor", "reason", "invalid")));
        assertThat(response.body()).doesNotContain(MARK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1"})
    void aPageSizeOutsideItsLimitsIsAConstraintOfTheContract(String limit) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?limit=" + limit));

        var problem = assertProblem(response, ProblemType.VALIDATION_FAILED, TRANSFERS);
        assertRejectedInputs(problem, List.of(Map.of("parameter", "limit", "reason", "invalid")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "99999999999", "1e2"})
    void aPageSizeThatIsNotANumberIsMalformed(String limit) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?limit=" + limit));

        var problem = assertProblem(response, ProblemType.MALFORMED_REQUEST, TRANSFERS);
        assertThat(problem.path("detail").asString()).doesNotContain(limit);
    }

    @Test
    void withoutASizeAPageHoldsFifty() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 60);

        var page = pageOf(tenant, "");

        assertThat(page.ids()).containsExactlyElementsOf(newestFirst(rows).subList(0, 50));
        assertThat(page.nextCursor()).isEqualTo(cursorAt(rows.get(10)));
        assertThat(pageOf(tenant, "?limit=").ids()).hasSize(50);
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "100, 100"})
    void theLimitsOfThePageSizeAreIncluded(int limit, int rowsInThePage) throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, limit + 1);

        var first = pageOf(tenant, "?limit=" + limit);
        var rest = pageOf(tenant, "?limit=%d&cursor=%s".formatted(limit, first.nextCursor()));

        assertThat(first.ids()).hasSize(rowsInThePage);
        assertThat(rest.ids()).containsExactly(rows.getFirst());
        assertThat(rest.nextCursor()).isNull();
    }

    /** Failing over one row means a page never shows amounts that are wrong, at the price of the whole page. */
    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "XAU"})
    void aPageHoldingARowWithoutAnAmountToShowIsAFailureThatRevealsNothing(String currency) throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 2);
        var row = TransferRows.seedAsSuperuser(tenant, 12345, currency, "PENDING");

        var response = send(requestAs(tenant, TRANSFERS));

        assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
        assertThat(response.body()).doesNotContain(currency).doesNotContain("12345").doesNotContain(row.toString())
                .doesNotContain(rows.getFirst().toString());
    }

    /**
     * The row asked for beyond the page is read like the others, so it fails a page that would never have shown it. A
     * page size of one is then no way past it: no page around that row ever answers, and no cursor leads beyond it.
     */
    @Test
    void theRowBeyondThePageAlsoFailsItAndAWalkCannotGetPast() throws Exception {
        var tenant = UUID.randomUUID();
        var rows = seedInOrder(tenant, 3);
        var unreadable = new UUID(rows.get(1).getMostSignificantBits() - (1L << 16),
                rows.get(1).getLeastSignificantBits());
        TransferRows.seedAsSuperuserAt(unreadable, tenant, 12345, "ZZZ", "PENDING");

        var first = pageOf(tenant, "?limit=1");
        var second = send(requestAs(tenant, "%s?limit=1&cursor=%s".formatted(TRANSFERS, first.nextCursor())));
        var fromTheUnreadableRow = pageOf(tenant, "?limit=1&cursor=" + cursorAt(unreadable));

        assertThat(first.ids()).containsExactly(rows.get(2));
        assertProblem(second, ProblemType.INTERNAL_ERROR, TRANSFERS);
        assertThat(second.body()).doesNotContain(rows.get(1).toString()).doesNotContain(unreadable.toString());
        assertThat(fromTheUnreadableRow.ids()).containsExactly(rows.get(0));
    }

    /** Only the first value counts when the framework can convert just one, and it still has to be within the limits. */
    @ParameterizedTest
    @CsvSource({"limit=1&limit=2, 200", "limit=0&limit=2, 400"})
    void aPageSizeRepeatedInTheQueryIsJudgedByItsFirstValue(String query, int status) throws Exception {
        var response = send(requestAs(UUID.randomUUID(), TRANSFERS + "?" + query));

        assertThat(response.statusCode()).isEqualTo(status);
    }
}
