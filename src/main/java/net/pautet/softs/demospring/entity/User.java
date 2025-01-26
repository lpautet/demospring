package net.pautet.softs.demospring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {

    private @Id @GeneratedValue Long id;
    private @Column(unique = true) String username;
    private String accessToken;
    private String refreshToken;
    private Long expiresAt;

}
