package dk.digitalidentity.rc.controller.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.dao.model.UserHistory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.MessageSource;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
public class UserHistoryDTOWrapper {
	private static final DateTimeFormatter timeStampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private List<UserHistoryDTO> data;

	@JsonIgnore
	private Locale locale;

	@JsonIgnore
	private MessageSource messageSource;

	public UserHistoryDTOWrapper(Locale locale, MessageSource messageSource) {
		this.locale = locale;
		this.messageSource = messageSource;
	}

	public void setData(List<UserHistory> userHistory) {
		data = new ArrayList<>();

		for (UserHistory history : userHistory) {
			UserHistoryDTO historyDTO = new UserHistoryDTO();
			historyDTO.setRoleName(history.getRoleName());
			historyDTO.setSystemName(history.getSystemName());
			historyDTO.setUsername(history.getUsername());
			historyDTO.setTimestamp(timeStampFormat.format(history.getTimestamp().toInstant()));
			historyDTO.setEventType(messageSource.getMessage(history.getEventType().getMessage(), null, locale));

			data.add(historyDTO);
		}
	}
}
