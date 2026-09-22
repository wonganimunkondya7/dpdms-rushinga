package zw.ac.uz.dpdms.hazard;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AuditRepository extends JpaRepository<AuditEntry,Long> { List<AuditEntry> findByIncidentIdOrderByActedAtAsc(Long incidentId); }
