package net.pautet.softs.demospring.identity.internal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import net.pautet.softs.demospring.identity.User;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "netatmo_connections", uniqueConstraints = {
        @UniqueConstraint(name = "uk_netatmo_user_purpose", columnNames = {"user_id", "purpose"})
})
public class NetatmoConnection {

    public enum Purpose { USER_DASHBOARD, SYSTEM_DATA_CLOUD }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Purpose purpose;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", length = 4096)
    private String accessToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token", length = 4096)
    private String refreshToken;

    private Long expiresAt;
    private Long refreshedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
    public Long getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(Long refreshedAt) { this.refreshedAt = refreshedAt; }
}
