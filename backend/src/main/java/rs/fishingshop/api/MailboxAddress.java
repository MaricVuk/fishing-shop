package rs.fishingshop.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MailboxAddressValidator.class)
public @interface MailboxAddress {

    String message() default "is not an address mail can be sent to";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
