package com.ptt.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import com.ptt.gateway.util.StringSequenceIdGenerator;

@Entity
@Table(name = "shipper_tags_link", uniqueConstraints = @UniqueConstraint(columnNames = { "shipper_id", "tag_id" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ShipperTagsLink extends Auditable {

    @Id
    @GeneratedValue(generator = "shipper_tags_link_seq")
    @GenericGenerator(name = "shipper_tags_link_seq", strategy = "com.ptt.gateway.util.StringSequenceIdGenerator", parameters = {
            @Parameter(name = StringSequenceIdGenerator.SEQUENCE_PREFIX, value = "TL")
    })
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private String id;

    @ManyToOne
    @JoinColumn(name = "shipper_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ShipperManagement shipperManagement;

    @Column(name = "tag_id")
    private String tagID;
}
