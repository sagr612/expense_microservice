package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.ExpenseDto;
import com.greatest.expenseService.services.ExpenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseConsumerTest {

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private ExpenseConsumer expenseConsumer;

    @Test
    void listen_processesEvent_whenDataIsValid() {
        ExpenseDto dto = ExpenseDto.builder().userId("user-1").externalId("ext-1").build();
        when(expenseService.createExpense(dto)).thenReturn(dto);

        assertThatCode(() -> expenseConsumer.listen(dto)).doesNotThrowAnyException();

        verify(expenseService).createExpense(dto);
    }

    @Test
    void listen_propagatesException_forContainerErrorHandlerToHandle() {

        ExpenseDto dto = ExpenseDto.builder().userId("user-1").externalId("FAIL_ME").build();
        when(expenseService.createExpense(dto)).thenThrow(new RuntimeException("downstream failure"));

        assertThatThrownBy(() -> expenseConsumer.listen(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("downstream failure");
    }
}