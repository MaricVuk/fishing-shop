package rs.fishingshop.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.PermanentJobFailure;

@Component
public class ShopMail {

    private final JavaMailSender sender;
    private final InternetAddress from;

    ShopMail(JavaMailSender sender, @Value("${shop.mail.from}") String from) throws AddressException {
        this.sender = sender;
        this.from = new InternetAddress(from, true);
    }

    public String signature() {
        return from.getPersonal() == null ? "" : from.getPersonal();
    }

    public void send(String to, String messageKey, String subject, String text, MailAttachment attachment) {
        InternetAddress recipient;
        try {
            recipient = new InternetAddress(to, true);
        } catch (AddressException invalid) {
            throw new PermanentJobFailure("The mail server cannot use the address of " + messageKey, invalid);
        }
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text);
            if (attachment != null) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            message.setHeader("Message-ID", "<" + messageKey + "@" + domainOf(from) + ">");
        } catch (MessagingException invalid) {
            throw new MailPreparationException("Cannot build the message " + messageKey, invalid);
        }
        sender.send(message);
    }

    private static String domainOf(InternetAddress address) {
        String email = address.getAddress();
        return email.substring(email.indexOf('@') + 1);
    }
}
