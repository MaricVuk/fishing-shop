package rs.fishingshop.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.bootstrap")
public record OwnerBootstrapProperties(String ownerEmail, String ownerPassword, String ownerName) {
}
