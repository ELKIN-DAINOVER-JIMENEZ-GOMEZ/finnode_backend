package com.finnode.auth.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

	private UUID userId;
	private String email;
	private String fullName;
	private Instant timestamp;
}
