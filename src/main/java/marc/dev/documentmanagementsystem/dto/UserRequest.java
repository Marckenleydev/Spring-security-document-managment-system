package marc.dev.documentmanagementsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRequest {
    @NotEmpty(message = "firstName cannot be empty")
    private String firstName;
    @NotEmpty(message = "lastName cannot be empty")
    private String lastName;
    @NotEmpty(message = "email cannot be empty")
    @Email(message = "Invalid email address")
    private String email;
    @NotEmpty(message = "password cannot be empty")
    private String password;
    private String bio;
    private String phone;

}
