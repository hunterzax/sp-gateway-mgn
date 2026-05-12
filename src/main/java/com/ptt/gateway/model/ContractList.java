package com.ptt.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipper_contract_list")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ContractList extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    @EqualsAndHashCode.Include
    private Long contractID;

    @ManyToOne
    @JoinColumn(name = "shipper_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ShipperManagement shipperManagement;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status")
    private String status;

    @OneToMany(mappedBy = "contractList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EntryExitPointList> entryExitPoints = new ArrayList<>();
}
