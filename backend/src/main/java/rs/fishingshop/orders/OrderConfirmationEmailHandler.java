package rs.fishingshop.orders;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.jobs.PermanentJobFailure;

@Component
class OrderConfirmationEmailHandler implements JobHandler {

    private final CustomerOrderRepository orders;
    private final JavaMailSender mail;
    private final InternetAddress from;

    OrderConfirmationEmailHandler(CustomerOrderRepository orders, JavaMailSender mail,
            @Value("${shop.mail.from}") String from) throws AddressException {
        this.orders = orders;
        this.mail = mail;
        this.from = new InternetAddress(from, true);
    }

    @Override
    public JobType type() {
        return JobType.ORDER_CONFIRMATION_EMAIL;
    }

    @Override
    public void handle(ClaimedJob job) {
        CustomerOrder order = orders.findWithItems(job.orderId())
                .orElseThrow(() -> new IllegalStateException("Order " + job.orderId() + " does not exist"));
        InternetAddress to;
        try {
            to = new InternetAddress(order.getCustomerEmail(), true);
        } catch (AddressException invalid) {
            throw new PermanentJobFailure("Order " + order.getId() + " has an address mail cannot use", invalid);
        }
        MimeMessage message = mail.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Potvrda porudžbine #" + order.getId());
            helper.setText(body(order));
            message.setHeader("Message-ID", "<order-confirmation-" + order.getId() + "@" + domainOf(from) + ">");
        } catch (MessagingException invalid) {
            throw new MailPreparationException("Cannot build the confirmation for order " + order.getId(), invalid);
        }
        mail.send(message);
    }

    private String body(CustomerOrder order) {
        StringBuilder text = new StringBuilder()
                .append("Zdravo ").append(order.getCustomerName()).append(",\n\n")
                .append("hvala na porudžbini. Primili smo je i javićemo se kada bude poslata.\n\n")
                .append("Porudžbina #").append(order.getId()).append("\n\n");
        for (OrderItem item : order.getItems()) {
            text.append(item.getProductName()).append(" (").append(item.getSku()).append(")\n")
                    .append("  ").append(item.getQuantity()).append(" x ").append(money(item.getUnitPrice()))
                    .append(" = ").append(money(item.lineTotal())).append("\n");
        }
        text.append("\nUkupno: ").append(money(order.getTotal())).append("\n\n")
                .append("Adresa za dostavu:\n").append(order.getShippingAddress()).append("\n\n")
                .append(from.getPersonal() == null ? "" : from.getPersonal()).append("\n");
        return text.toString();
    }

    private static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0.00", symbols).format(amount) + " RSD";
    }

    private static String domainOf(InternetAddress address) {
        String email = address.getAddress();
        return email.substring(email.indexOf('@') + 1);
    }
}
