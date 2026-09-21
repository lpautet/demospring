package net.pautet.softs.demospring.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "app_users")
public class User implements Serializable {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String username;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Transient
    private String accessToken;

    @Transient
    private String refreshToken;

    @Transient
    private Long expiresAt;

    @Transient
    private Long refreshedAt;
}
