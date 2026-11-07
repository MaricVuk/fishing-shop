package rs.fishingshop.api;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MailboxAddressValidator implements ConstraintValidator<MailboxAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            InternetAddress parsed = new InternetAddress(value, true);
            return value.equals(parsed.getAddress());
        } catch (AddressException invalid) {
            return false;
        }
    }
}
