package com.skyflux.kiln.tenant.config;

import com.skyflux.kiln.tenant.api.TenantContext;
import org.jooq.ExecuteContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DefaultExecuteListener;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Sets the PostgreSQL session variable {@code app.tenant_id} before each jOOQ
 * statement so the RLS policy {@code tenant_isolation} on the {@code users} table
 * can filter rows by tenant.
 *
 * <p>Implementation note: we use {@code ctx.connection().prepareStatement(sql)}
 * (inline UUID, no bind parameters) because PostgreSQL's {@code SET LOCAL}
 * command does not support prepared-statement parameters ({@code $1}).
 * {@code SET LOCAL} applies for the duration of the current transaction.
 * When there is no active transaction (autocommit mode), each statement is its
 * own implicit transaction, so the setting only lasts for that statement — which
 * is the desired behaviour (no cross-statement leakage).
 *
 * <p>To avoid interfering with jOOQ's own prepared-statement lifecycle (which
 * can cause {@code "This statement has been closed"} errors when using the same
 * {@link Connection} proxy that jOOQ holds), we obtain the <em>unwrapped</em>
 * raw JDBC connection before preparing the ancillary SET statement.
 */
class TenantRlsListener extends DefaultExecuteListener {

    @Override
    public void executeStart(ExecuteContext ctx) {
        if (!TenantContext.CURRENT.isBound()) return;
        UUID tenantId = TenantContext.CURRENT.get();
        // Inline UUID — fixed-format value, safe against injection.
        String sql = "SET LOCAL app.tenant_id = '" + tenantId + "'";
        try {
            // Unwrap to the raw PgConnection to avoid HikariCP's ProxyConnection
            // statement-tracking, which can close jOOQ's prepared statement when
            // our ancillary SET statement is closed within the try-with-resources.
            Connection raw = ctx.connection().unwrap(Connection.class);
            try (var stmt = raw.prepareStatement(sql)) {
                stmt.execute();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set tenant_id", e);
        }
    }
}
