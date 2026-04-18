package com.skyflux.kiln.user.application.port.out;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Outbound port: persistence abstraction for the {@code User} aggregate. */
public interface UserRepository {
    Optional<User> findById(UserId id);

    /** List active (non-deleted) users, paginated. RLS auto-filters by tenant. */
    PageResult<User> listActive(PageQuery query);

    /**
     * Look up a user by their (normalized, lowercase) email address.
     *
     * <p>Used by the login flow. Callers are responsible for normalizing the
     * query key — the adapter does a byte-for-byte match against the DB.
     */
    Optional<User> findByEmail(String email);

    void save(User user);

    /**
     * Total number of users persisted. Phase 4.2 introduced this for the
     * {@code AdminController} demo surface; later phases may expose it via
     * dedicated read models if query load grows.
     */
    long countAll();

    /**
     * Gate 3 C1: atomically increment the lockout counter and, if the post-
     * increment value reaches {@code threshold}, set {@code locked_until =
     * now + duration} and reset the counter to 0. Returns the post-UPDATE
     * {@code User} state so the caller can publish domain events with the
     * resolved lock timestamp.
     *
     * <p>Atomic via a single SQL {@code UPDATE ... RETURNING *} — two
     * concurrent wrong-password requests on the same account each advance
     * the counter by 1, eliminating the lost-update race that makes the
     * read-modify-write form of {@code user.registerLoginFailure(...) +
     * save(...)} miss lockouts under burst.
     *
     * <p>The domain-layer {@link User#registerLoginFailure} remains the
     * authoritative expression of the rule and is exercised by
     * {@code UserTest}; this repo method is the persistence-layer shortcut
     * that preserves the invariant atomically.
     */
    User recordLoginFailure(UserId id, Instant now, int threshold, Duration duration);

    /**
     * Atomically clear the lockout counter and any active lock. Paired with
     * {@link #recordLoginFailure} so successful logins cannot race with
     * concurrent failing ones.
     */
    User recordLoginSuccess(UserId id);
}
