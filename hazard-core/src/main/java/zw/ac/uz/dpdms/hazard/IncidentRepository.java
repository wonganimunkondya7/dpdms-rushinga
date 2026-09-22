package zw.ac.uz.dpdms.hazard;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface IncidentRepository extends JpaRepository<Incident,Long> { List<Incident> findByStatus(IncidentStatus status); List<Incident> findByReporterAndWard(String reporter,String ward); }
