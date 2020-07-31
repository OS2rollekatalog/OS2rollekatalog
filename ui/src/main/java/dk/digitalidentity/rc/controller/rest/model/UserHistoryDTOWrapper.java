package dk.digitalidentity.rc.controller.rest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.context.MessageSource;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.dao.model.UserHistory;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserHistoryDTOWrapper {
	private static final DateTimeFormatter timeStampFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");

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
			historyDTO.setTimestamp(timeStampFormat.print(history.getTimestamp().getTime()));
			historyDTO.setEventType(messageSource.getMessage(history.getEventType().getMessage(), null, locale));

			data.add(historyDTO);
		}
	}
}
