package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.PNumberDao;
import dk.digitalidentity.rc.dao.model.PNumber;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


@SpringBootTest
@ContextConfiguration(classes = {PNumberService.class})
public class PNumberServiceTest {

    @MockBean
    private PNumberDao pNumberDao;
    @MockBean
    private NemLoginService nemLoginService;
    @Autowired
    private PNumberService pNumberService;

    @Test
    public void canUpdate() {
        // Given
        doReturn(createPNumbers(100)).when(nemLoginService).getAllPNR();
        doReturn(createPNumbers(50)).when(pNumberDao).findAll();

        // When
        pNumberService.updatePNR();

        // Then
        verify(pNumberDao, times(50)).save(any());
    }

    @Test
    public void noChanges() {
        // Given
        doReturn(createPNumbers(10)).when(nemLoginService).getAllPNR();
        doReturn(createPNumbers(10)).when(pNumberDao).findAll();

        // When
        pNumberService.updatePNR();

        // Then
        verify(pNumberDao, times(0)).save(any());
        verify(pNumberDao, times(1)).findAll();
        verifyNoMoreInteractions(pNumberDao);
    }

    @Test
    public void updatesName() {
        // Given
        final var oldPNumbers = createPNumbers(10);
        doReturn(oldPNumbers).when(pNumberDao).findAll();
        final var newPNumbers = createPNumbers(10);
        newPNumbers.forEach(p -> p.setName("new-name"));
        doReturn(newPNumbers).when(nemLoginService).getAllPNR();

        // When
        pNumberService.updatePNR();

        // Then
        assertThat(oldPNumbers).allMatch(p -> p.getName().equals("new-name"));
        verify(pNumberDao, times(1)).findAll();
        verifyNoMoreInteractions(pNumberDao);
    }

    private List<PNumber> createPNumbers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> createPNumber("" + i))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private PNumber createPNumber(final String code) {
        final PNumber pn = new PNumber();
        pn.setName("Name:"+code);
        pn.setCode(code);
        return pn;
    }

}
