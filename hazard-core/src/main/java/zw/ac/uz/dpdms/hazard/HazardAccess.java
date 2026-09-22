package zw.ac.uz.dpdms.hazard;
import org.springframework.security.access.AccessDeniedException; import org.springframework.security.oauth2.jwt.Jwt; import org.springframework.stereotype.Component;
@Component public class HazardAccess {
 private final HazardProperties p; public HazardAccess(HazardProperties p){this.p=p;}
 public void read(Jwt j){if(!"NATIONAL".equals(role(j)) && !p.code().equals(j.getClaimAsString("hazard"))) deny();}
 public void recorder(Jwt j,String ward){if(!"RECORDER".equals(role(j))||!p.code().equals(j.getClaimAsString("hazard"))||!ward.equals(j.getClaimAsString("ward"))) deny();}
 public void supervisor(Jwt j){if(!"SUPERVISOR".equals(role(j))||!p.code().equals(j.getClaimAsString("hazard"))) deny();}
 public boolean national(Jwt j){return "NATIONAL".equals(role(j));} private String role(Jwt j){return j.getClaimAsString("role");} private void deny(){throw new AccessDeniedException("403: role is not authorised for this hazard");}
}
