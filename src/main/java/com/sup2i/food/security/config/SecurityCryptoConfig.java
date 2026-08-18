package com.sup2i.food.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityCryptoConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(SecurityProperties properties) {
        byte[] decoded = Base64.getDecoder()
            .decode(properties.jwt().secretBase64());

        if (decoded.length < 32) {
            throw new IllegalStateException(
                "SUP2I JWT secret must contain at least 256 bits."
            );
        }

        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder
            .withSecretKey(jwtSecretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
        SecretKey jwtSecretKey,
        SecurityProperties properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(
                properties.jwt().issuer()
            )
        );

        return decoder;
    }
}