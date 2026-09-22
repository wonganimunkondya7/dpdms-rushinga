package zw.ac.uz.dpdms.hazard;

import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="incidents")
public class Incident {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false) public String ward; @Column(nullable=false) public String district; @Column(nullable=false) public String province;
 @Column(nullable=false) public Instant occurredAt; @Column(nullable=false) public String reporter;
 @Enumerated(EnumType.STRING) @Column(nullable=false) public Severity severity;
 @Enumerated(EnumType.STRING) @Column(nullable=false) public IncidentStatus status=IncidentStatus.PENDING;
 @Column(nullable=false) public double latitude; @Column(nullable=false) public double longitude;
 @Column(columnDefinition="json", nullable=false) public String indicatorsJson="{}";
 public Instant createdAt=Instant.now(); public Instant updatedAt=Instant.now();
 @PreUpdate void updated(){updatedAt=Instant.now();}
}
