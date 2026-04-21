package com.finnode.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

	private String accessToken;
	private String refreshToken;
	private String tokenType;
	private long expiresIn;
}
