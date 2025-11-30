package com.example.ai.tool.analysis.phase_three_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "clients") // The table name is 'clients', the schema will be handled dynamically
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    private Long id;

    @Column(name = "client_gid_id")
    private String clientGidId;

    private String email;

    // @Column(name = "created_at")
    // private LocalDateTime createdAt;

    // @Column(name = "updated_at")
    // private LocalDateTime updatedAt;

    // @Column(name = "account_status")
    // private String accountStatus;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "family_name")
    private String familyName;

    // private String type;
}
