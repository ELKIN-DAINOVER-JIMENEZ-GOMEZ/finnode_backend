package com.finnode.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated// esto es para
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

	@NotBlank
	private String secret;

	@Min(1)
	private long accessTokenExpiry;

	@Min(1)
	private long refreshTokenExpiry;

	@NotBlank
	private String issuer;

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public long getAccessTokenExpiry() {
		return accessTokenExpiry;
	}

	public void setAccessTokenExpiry(long accessTokenExpiry) {
		this.accessTokenExpiry = accessTokenExpiry;
	}

	public long getRefreshTokenExpiry() {
		return refreshTokenExpiry;
	}

	public void setRefreshTokenExpiry(long refreshTokenExpiry) {
		this.refreshTokenExpiry = refreshTokenExpiry;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}
}
