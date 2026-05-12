package com.ptt.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ptt.gateway.util.StringSequenceIdGenerator;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipper_contact_list")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ContactList extends Auditable {

    @Id
    @GeneratedValue(generator = "contact_list_seq")
    @org.hibernate.annotations.GenericGenerator(name = "contact_list_seq", strategy = "com.ptt.gateway.util.StringSequenceIdGenerator", parameters = {
            @org.hibernate.annotations.Parameter(name = StringSequenceIdGenerator.SEQUENCE_PREFIX, value = "C")
    })
    @Column(name = "contact_id")
    @EqualsAndHashCode.Include
    private String contactID;

    @ManyToOne
    @JoinColumn(name = "shipper_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ShipperManagement shipperManagement;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "surname")
    private String surName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone_num")
    private String phoneNum;

    @Column(name = "status")
    private String status;
}
