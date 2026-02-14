package com.sarim.digitalbanking.payees;

import com.sarim.digitalbanking.auth.UserEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "payees")
public class PayeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // payees.owner_user_id -> users.id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserEntity ownerUser;

    // payees.payee_user_id -> users.id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_user_id", nullable = false)
    private UserEntity payeeUser;

    @Column(name = "payee_email", nullable = false)
    private String payeeEmail;

    @Column(name = "label")
    private String label;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    public PayeeEntity() {}

    public Long getId() { return id; }

    public UserEntity getOwnerUser() { return ownerUser; }
    public void setOwnerUser(UserEntity ownerUser) { this.ownerUser = ownerUser; }

    public UserEntity getPayeeUser() { return payeeUser; }
    public void setPayeeUser(UserEntity payeeUser) { this.payeeUser = payeeUser; }

    public String getPayeeEmail() { return payeeEmail; }
    public void setPayeeEmail(String payeeEmail) { this.payeeEmail = payeeEmail; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
