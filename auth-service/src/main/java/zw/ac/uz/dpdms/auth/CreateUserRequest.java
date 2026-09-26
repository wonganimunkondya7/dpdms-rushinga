package zw.ac.uz.dpdms.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 150) String username,
    @NotBlank @Size(min = 12, max = 200) String password,
    @NotBlank String role,
    String hazard,
    String ward) {}
