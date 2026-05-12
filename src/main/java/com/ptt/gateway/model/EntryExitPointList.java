package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipper_entry_exit_point_list")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class EntryExitPointList extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid2")
    @org.hibernate.annotations.GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", columnDefinition = "VARCHAR(36)")
    @EqualsAndHashCode.Include
    private String id;

    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ContractList contractList;

    @Column(name = "point_name")
    private String pointName;

    @Column(name = "type")
    private String type;
}
