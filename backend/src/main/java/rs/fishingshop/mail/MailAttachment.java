package rs.fishingshop.mail;

public record MailAttachment(String fileName, byte[] content, String contentType) {
}
