package zw.ac.uz.dpdms.auth; import jakarta.validation.constraints.*; public record LoginRequest(@NotBlank String username,@NotBlank String password){}
