package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipper_management")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class ShipperManagement extends Auditable {

    @Id
    @Column(name = "shipper_id", unique = true, nullable = false)
    private String shipperID;

    @Column(name = "shipper_name")
    private String shipperName;

    @Column(name = "initials")
    private String initials;

    @Column(name = "shipper_short_name")
    private String shipperShortName;

    @Column(name = "status")
    private String status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "auto_deactivate_data_link")
    private Boolean autoDeactivateDataLink;

    @OneToMany(mappedBy = "shipperManagement", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ContractList> contracts = new ArrayList<>();

    @OneToMany(mappedBy = "shipperManagement", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ContactList> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "shipperManagement", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ShipperTagsLink> tagsLinks = new ArrayList<>();

    @OneToMany(mappedBy = "shipperManagement", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ShipperCalcLink> calcLinks = new ArrayList<>();
}
