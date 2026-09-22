package zw.ac.uz.dpdms.hazard;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="incident_audit") public class AuditEntry { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; public Long incidentId; @Column(nullable=false) public String actor; @Column(nullable=false) public Instant actedAt=Instant.now(); @Column(nullable=false) public String action; @Column(length=1000) public String detail; }
