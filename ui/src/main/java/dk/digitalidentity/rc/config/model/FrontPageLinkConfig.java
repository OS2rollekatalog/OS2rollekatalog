package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class FrontPageLinkConfig {
	private List<String> icons = Arrays.asList("fa-sitemap","fa-gears","fa-pencil","fa-check","fa-eye","fa-users","fa-bell","fa-edit","fa-tasks","fa-handshake-o","fa-download","fa-envelope","fa-exclamation","fa-list","fa-wrench","fa-book","fa-unlock-alt","fa-shield","fa-laptop","fa-user","fa-id-card","fa-file-excel-o","fa-thumb-tack","fa-table","fa-male","fa-female");
}
