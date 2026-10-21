package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.SettingsDao;
import dk.digitalidentity.rc.dao.model.Setting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

	@Mock
	private SettingsDao settingsDao;

	@InjectMocks
	private SettingsService settingsService;

	private static Setting setting(String key, String value) {
		Setting setting = new Setting();
		setting.setKey(key);
		setting.setValue(value);
		return setting;
	}

	@Nested
	@DisplayName("repair job cursor")
	class RepairJobCursor {

		@Test
		@DisplayName("a cursor stored under the running version is resumed from")
		void cursorOfSameVersionIsReturned() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB"))
				.thenReturn(List.of(setting("REPAIR_JOB_CURSOR_JOB", "2:4200")));

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobCursor("JOB", 2)).isEqualTo(4200L);
		}

		@Test
		@DisplayName("a cursor from an older version is ignored, so a raised version walks the table again")
		void cursorOfOtherVersionIsIgnored() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB"))
				.thenReturn(List.of(setting("REPAIR_JOB_CURSOR_JOB", "1:4200")));

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobCursor("JOB", 2)).isZero();
		}

		@Test
		@DisplayName("an unreadable cursor starts the pass over")
		void unreadableCursorStartsOver() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB"))
				.thenReturn(List.of(setting("REPAIR_JOB_CURSOR_JOB", "not-a-cursor")));

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobCursor("JOB", 1)).isZero();
		}

		@Test
		@DisplayName("a key that only shares the prefix does not count as the job's cursor")
		void prefixMatchIsNotEnough() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB"))
				.thenReturn(List.of(setting("REPAIR_JOB_CURSOR_JOB_TWO", "1:4200")));

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobCursor("JOB", 1)).isZero();
		}

		@Test
		@DisplayName("two rows from two cluster nodes resolve to the furthest cursor")
		void duplicateRowsResolveToTheFurthest() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB")).thenReturn(List.of(
				setting("REPAIR_JOB_CURSOR_JOB", "1:400"),
				setting("REPAIR_JOB_CURSOR_JOB", "1:1800")));

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobCursor("JOB", 1)).isEqualTo(1800L);
		}

		@Test
		@DisplayName("a lower cursor from a node further behind is not written")
		void cursorIsNotMovedBackwards() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB"))
				.thenReturn(List.of(setting("REPAIR_JOB_CURSOR_JOB", "1:100000")));

			// ---- When ---- //
			settingsService.setRepairJobCursor("JOB", 1, 800L);

			// ---- Then ---- //
			verify(settingsDao, never()).save(any());
		}

		@Test
		@DisplayName("a raised version writes its cursor even though it is lower than the old one")
		void raisedVersionOverwritesLowerCursor() {
			// ---- Given ---- //
			Setting existing = setting("REPAIR_JOB_CURSOR_JOB", "1:100000");
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB")).thenReturn(List.of(existing));

			// ---- When ---- //
			settingsService.setRepairJobCursor("JOB", 2, 800L);

			// ---- Then ---- //
			verify(settingsDao).save(existing);
			assertThat(existing.getValue()).isEqualTo("2:800");
		}

		@Test
		@DisplayName("the version is stored with the cursor and reuses the existing row")
		void cursorIsStoredWithItsVersion() {
			// ---- Given ---- //
			Setting existing = setting("REPAIR_JOB_CURSOR_JOB", "1:400");
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_CURSOR_JOB")).thenReturn(List.of(existing));

			// ---- When ---- //
			settingsService.setRepairJobCursor("JOB", 1, 1800L);

			// ---- Then ---- //
			ArgumentCaptor<Setting> saved = ArgumentCaptor.forClass(Setting.class);
			verify(settingsDao).save(saved.capture());
			assertThat(saved.getValue()).isSameAs(existing);
			assertThat(saved.getValue().getValue()).isEqualTo("1:1800");
		}
	}

	@Nested
	@DisplayName("repair job version")
	class RepairJobVersion {

		@Test
		@DisplayName("a job with no row has never run")
		void missingVersionIsZero() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_VERSION_JOB")).thenReturn(List.of());

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobVersion("JOB")).isZero();
		}

		@Test
		@DisplayName("rows two nodes raced to insert are collapsed on the next write")
		void duplicateRowsAreCollapsed() {
			// ---- Given ---- //
			Setting first = setting("REPAIR_JOB_VERSION_JOB", "1");
			Setting second = setting("REPAIR_JOB_VERSION_JOB", "1");
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_VERSION_JOB"))
				.thenReturn(List.of(first, second));

			// ---- When ---- //
			settingsService.setRepairJobVersion("JOB", 2);

			// ---- Then ---- //
			verify(settingsDao).save(first);
			verify(settingsDao).deleteAll(List.of(second));
		}

		@Test
		@DisplayName("two rows from two cluster nodes resolve to the highest version")
		void duplicateRowsResolveToTheHighest() {
			// ---- Given ---- //
			when(settingsDao.findByKeyStartingWith("REPAIR_JOB_VERSION_JOB")).thenReturn(List.of(
				setting("REPAIR_JOB_VERSION_JOB", "1"),
				setting("REPAIR_JOB_VERSION_JOB", "3")));

			// ---- When / Then ---- //
			assertThat(settingsService.getRepairJobVersion("JOB")).isEqualTo(3);
		}
	}
}
