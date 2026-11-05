package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Whether the list keeps its index, asked of the database with the statements the adapter runs, as the application role,
 * with its tenant, in a read only transaction. The index is invisible to any other test: without it every page is still
 * right, only slower for a small tenant, and slower the more rows the others have.
 * <p>
 * The rows live in a database of their own, because what a planner chooses depends on all the rows of a table, and the
 * ones other tests leave would change it. A table this size is below the 30 000 rows ANALYZE samples by default, so it
 * reads all of them and its statistics are the same on every run, and autovacuum repeating it changes nothing. The
 * identifiers grow with the position in the table, as they do with time ordered identifiers: that makes the primary key
 * as attractive to the planner as it can get. Below about 2 000 rows the planner does prefer it, and a page of a table
 * that small costs nothing either way.
 */
class TransferListPlanIT {

    private static final String THE_INDEX = "transfer_tenant_id_id_idx";
    private static final String THE_PRIMARY_KEY = "transfer_pkey";
    private static final int ROWS_IN_THE_TABLE = 20_000;
    private static final int ONE_ROW_OF_THE_TENANT_IN = 100;
    private static final UUID SMALL_TENANT = UUID.randomUUID();
    private static final UUID LARGE_TENANT = UUID.randomUUID();
    private static final UUID MIDDLE_OF_THE_TABLE = idAt(ROWS_IN_THE_TABLE / 2);
    private static final int ROWS_ASKED = 51;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static String url;

    /** What a statement runs with depends on how many times its connection has run it, and both have to hold. */
    private enum PlanMode {
        FIRST_RUNS("force_custom_plan"), LATER_RUNS("force_generic_plan");

        private final String setting;

        PlanMode(String setting) {
            this.setting = setting;
        }
    }

    @BeforeAll
    static void fillATableWithOneSmallTenantAmongTheRowsOfAnother() throws SQLException {
        url = LedgerDatabase.createEmptyDatabaseForTheMigrator();
        LedgerDatabase.startMigrateProfile(url).close();
        try (var connection = LedgerDatabase.asSuperuser(url);
                var seed = connection.prepareStatement(
                        """
                                INSERT INTO transfer
                                    (id, tenant_id, source_account_id, destination_account_id, amount_minor, currency, status)
                                SELECT (lpad(to_hex(1700000000000 + n), 12, '0') || '-7000-8000-' || lpad(to_hex(n), 12, '0'))::uuid,
                                    CASE WHEN n % ? = 0 THEN ?::uuid ELSE ?::uuid END,
                                    gen_random_uuid(), gen_random_uuid(), 1, 'COP', 'PENDING'
                                FROM generate_series(1, ?) AS n
                                """);
                var analyze = connection.createStatement()) {
            seed.setInt(1, ONE_ROW_OF_THE_TENANT_IN);
            seed.setObject(2, SMALL_TENANT);
            seed.setObject(3, LARGE_TENANT);
            seed.setInt(4, ROWS_IN_THE_TABLE);
            seed.executeUpdate();
            analyze.execute("ANALYZE transfer");
        }
    }

    /** The identifier the statement that fills the table gives this position. */
    private static UUID idAt(int position) {
        return new UUID((1_700_000_000_000L + position) << 16 | 0x7000L, 0x8000_0000_0000_0000L | position);
    }

    private static Stream<Arguments> everyStatementInEveryPlanMode() {
        return Stream.of(PlanMode.values()).flatMap(mode -> Stream.of(
                Arguments.of(JdbcTransferQueries.NEWEST_PAGE, List.of(ROWS_ASKED), mode),
                Arguments.of(JdbcTransferQueries.PAGE_BEFORE,
                        List.of("'" + MIDDLE_OF_THE_TABLE + "'", ROWS_ASKED), mode)));
    }

    @ParameterizedTest
    @MethodSource("everyStatementInEveryPlanMode")
    void aPageOfASmallTenantWalksItsIndexAndDiscardsNothing(String statement, List<Object> values, PlanMode mode)
            throws Exception {
        try (var connection = LedgerDatabase.asApplication(url)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            var plan = planOf(connection, statement, values, mode);

            var nodes = nodesOf(plan);
            assertThat(nodes).extracting(TransferListPlanIT::nodeType).hasSize(2).first().isEqualTo("Limit");
            assertThat(nodeType(nodes.getLast())).isIn("Index Scan", "Index Only Scan");
            assertThat(nodes.getLast().path("Index Name").asString()).isEqualTo(THE_INDEX);
            assertThat(rowsDiscardedIn(nodes)).isZero();
            assertThat(plan.path("Actual Rows").asInt()).isEqualTo(ROWS_ASKED);
            connection.rollback();
        }
    }

    /**
     * Without the index the same statement must look through the rows of the other tenant, which is what the test above
     * guards against. If this fails, the table no longer tells the two cases apart and the test above proves nothing. The
     * index comes back with the rollback.
     */
    @ParameterizedTest
    @MethodSource("everyStatementInEveryPlanMode")
    void withoutTheIndexTheSameStatementDiscardsTheRowsOfTheOtherTenant(String statement, List<Object> values,
            PlanMode mode) throws Exception {
        try (var connection = LedgerDatabase.asSuperuser(url)) {
            connection.setAutoCommit(false);
            try (var drop = connection.createStatement()) {
                drop.execute("DROP INDEX " + THE_INDEX);
                drop.execute("SET LOCAL ROLE ledger_app");
            }

            var nodes = nodesOf(planOf(connection, statement, values, mode));

            assertThat(nodes.getLast().path("Index Name").asString()).isEqualTo(THE_PRIMARY_KEY);
            assertThat(rowsDiscardedIn(nodes)).isGreaterThan(ROWS_ASKED);
            connection.rollback();
        }
    }

    /** The plans above are only worth something if they are the ones row level security shapes for the application. */
    @Test
    void thePlansAreTheOnesOfTheApplicationInAReadOnlyTransaction() throws SQLException {
        try (var connection = LedgerDatabase.asApplication(url)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            TransferRows.setTenant(connection, SMALL_TENANT);

            assertThat(TransferRows.currentUser(connection)).isEqualTo("ledger_app");
            assertThat(TransferRows.visibleIds(connection)).hasSize(ROWS_IN_THE_TABLE / ONE_ROW_OF_THE_TENANT_IN);
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SHOW transaction_read_only")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("on");
            }
            connection.rollback();
        }
    }

    /**
     * Through a prepared statement, the way a driver reuses one, so the planner mode applies; {@code EXPLAIN} of a single
     * statement with bound values never uses a generic plan. Each marker of the statement becomes its numbered form.
     */
    private static JsonNode planOf(Connection connection, String statement, List<Object> values, PlanMode mode)
            throws SQLException {
        TransferRows.setTenant(connection, SMALL_TENANT);
        var numbered = statement;
        for (var position = 1; position <= values.size(); position++) {
            numbered = numbered.replaceFirst("\\?", "\\$" + position);
        }
        var arguments = String.join(", ", values.stream().map(String::valueOf).toList());
        try (var explain = connection.createStatement()) {
            explain.execute("PREPARE page AS " + numbered);
            explain.execute("SET LOCAL plan_cache_mode = " + mode.setting);
            try (var rows = explain.executeQuery(
                    "EXPLAIN (ANALYZE, FORMAT JSON) EXECUTE page(%s)".formatted(arguments))) {
                rows.next();
                return JSON.readTree(rows.getString(1)).path(0).path("Plan");
            }
        }
    }

    private static List<JsonNode> nodesOf(JsonNode plan) {
        var nodes = new ArrayList<JsonNode>();
        nodes.add(plan);
        for (var child : plan.path("Plans")) {
            nodes.addAll(nodesOf(child));
        }
        return nodes;
    }

    private static String nodeType(JsonNode node) {
        return node.path("Node Type").asString();
    }

    private static int rowsDiscardedIn(List<JsonNode> nodes) {
        return nodes.stream().mapToInt(node -> node.path("Rows Removed by Filter").asInt(0)).sum();
    }
}
