package dk.digitalidentity.rc.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Kø-besked for {@link RoleMembershipChangedEventHandler}: signalerer at en UserRoles effektive
 * medlemskab kan have ændret sig, og at downstream-systemer skal gentjekke netop den rolle.
 * Bærer bevidst kun rolle-id'et — ikke selve medlemskabet — da forbrugerne genberegner medlemskab
 * ved flush. Det gør beskeden idempotent og rækkefølge-uafhængig.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleMembershipChangedMessage {
	private Long userRoleId;
	private LocalDateTime timestamp;
}
