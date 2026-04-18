package com.skyflux.kiln.product.domain.model;

import com.skyflux.kiln.common.money.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Product aggregate root. Immutable; all mutations return new instances.
 * No Spring imports allowed (ArchUnit enforced).
 */
public final class Product {

    private final ProductId id;
    private final UUID tenantId;
    private final String code;
    private final String name;
    private final String description;
    private final Money price;
    private final String status;

    private Product(ProductId id, UUID tenantId, String code, String name,
                    String description, Money price, String status) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.price = Objects.requireNonNull(price);
        this.status = Objects.requireNonNull(status);
        if (code.isBlank()) throw new IllegalArgumentException("code blank");
        if (name.isBlank()) throw new IllegalArgumentException("name blank");
        if (price.amount().signum() < 0) throw new IllegalArgumentException("price negative");
    }

    /** Factory — generates a new UUID v7 ProductId and sets status to ACTIVE. */
    public static Product create(UUID tenantId, String code, String name,
                                 String description, Money price) {
        return new Product(ProductId.newId(), tenantId, code.trim(), name.trim(),
                description, price, "ACTIVE");
    }

    /** Reconstitution from persistence — preserves existing ProductId and status. */
    public static Product reconstitute(ProductId id, UUID tenantId, String code, String name,
                                       String description, Money price, String status) {
        return new Product(id, tenantId, code, name, description, price, status);
    }

    public Product deactivate() {
        if ("INACTIVE".equals(status)) throw new IllegalStateException("Already inactive");
        return new Product(id, tenantId, code, name, description, price, "INACTIVE");
    }

    public Product updateDetails(String name, String description, Money price) {
        String trimmed = Objects.requireNonNull(name, "name").trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException("name blank");
        return new Product(id, tenantId, code, trimmed, description, price, status);
    }

    public ProductId id()       { return id; }
    public UUID tenantId()      { return tenantId; }
    public String code()        { return code; }
    public String name()        { return name; }
    public String description() { return description; }
    public Money price()        { return price; }
    public String status()      { return status; }
}
