package br.com.fiap.citizentech.water;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProfileValidationTests {
    @Test void validatesProfilesAndPairedActivityQuantity() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertTrue(validator.validate(new ProfileRequest("argiloso",32.0,12.0,30.0,15.0,"DEMO",true)).isEmpty());
            assertFalse(validator.validate(new ProfileRequest("argiloso",12.0,32.0,30.0,15.0,"DEMO",true)).isEmpty());
            assertFalse(validator.validate(new br.com.fiap.citizentech.activity.ActivityRequest(
                    br.com.fiap.citizentech.activity.FieldActivity.Type.IRRIGATION,java.time.Instant.now().minusSeconds(10),
                    "Teste",java.math.BigDecimal.TEN,null)).isEmpty());
        }
    }
}
