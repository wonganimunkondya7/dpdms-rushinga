package zw.ac.uz.dpdms.hazard;
import org.springframework.security.access.AccessDeniedException; import org.springframework.security.oauth2.jwt.Jwt; import org.springframework.stereotype.Component;
@Component public class HazardAccess {
 private final HazardProperties p; public HazardAccess(HazardProperties p){this.p=p;}
 public void read(Jwt j){String role=role(j);if("NATIONAL".equals(role))return;if((!"RECORDER".equals(role)&&!"SUPERVISOR".equals(role)&&!"PROVINCIAL_ADMIN".equals(role))||!hazardAllowed(j,role))deny();if("PROVINCIAL_ADMIN".equals(role)&&province(j).isBlank())deny();}
 public void recorder(Jwt j,String ward){if(!"RECORDER".equals(role(j))||!hazardAllowed(j,"RECORDER")||!ward.equals(j.getClaimAsString("ward"))) deny();}
 public void supervisor(Jwt j){if(!"SUPERVISOR".equals(role(j))||!hazardAllowed(j,"SUPERVISOR")) deny();}
 public boolean national(Jwt j){return "NATIONAL".equals(role(j));} public boolean provincialAdmin(Jwt j){return "PROVINCIAL_ADMIN".equals(role(j));} public String province(Jwt j){String province=j.getClaimAsString("province");return province==null?"":province.trim();} private boolean hazardAllowed(Jwt j,String role){String hazard=j.getClaimAsString("hazard");return p.code().equals(hazard)||(("RECORDER".equals(role)||"SUPERVISOR".equals(role)||"PROVINCIAL_ADMIN".equals(role))&&"ALL".equals(hazard));} private String role(Jwt j){return j.getClaimAsString("role");} private void deny(){throw new AccessDeniedException("403: role is not authorised for this hazard or geographic scope");}
}
