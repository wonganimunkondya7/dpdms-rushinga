package zw.ac.uz.dpdms.hazard;
import static org.junit.jupiter.api.Assertions.*; import java.time.*; import java.util.*; import org.junit.jupiter.api.*; import org.springframework.security.access.AccessDeniedException; import org.springframework.security.oauth2.jwt.Jwt;
class HazardAccessTest { private final HazardAccess access=new HazardAccess(new HazardProperties("FLOOD",List.of())); private Jwt jwt(String role,String hazard,String ward){return new Jwt("test",Instant.now(),Instant.now().plusSeconds(60),Map.of("alg","none"),Map.of("sub","user", "role",role,"hazard",hazard,"ward",ward));}
 @Test void floodRecorderCannotWriteDrought(){assertThrows(AccessDeniedException.class,()->access.recorder(jwt("RECORDER","FLOOD","Ward 1"),"Ward 2"));}
 @Test void nationalIsReadOnlyAcrossHazards(){assertDoesNotThrow(()->access.read(jwt("NATIONAL","", ""))); assertThrows(AccessDeniedException.class,()->access.supervisor(jwt("NATIONAL","", "")));}
 @Test void provincialAdministratorCannotCrossHazards(){assertDoesNotThrow(()->access.read(jwt("PROVINCIAL_ADMIN","FLOOD",""))); assertThrows(AccessDeniedException.class,()->access.read(jwt("PROVINCIAL_ADMIN","DROUGHT","")));}
}
