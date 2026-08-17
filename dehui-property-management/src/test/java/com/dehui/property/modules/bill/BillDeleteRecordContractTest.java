package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.bill.application.BillCommandService;
import com.dehui.property.modules.bill.controller.BillController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;

class BillDeleteRecordContractTest {

    @Test
    void controllerExposesAdditiveRecordDeletionRoute() {
        boolean exposed = Arrays.stream(BillController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(DeleteMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> java.util.stream.Stream.concat(
                        Arrays.stream(mapping.value()),
                        Arrays.stream(mapping.path())
                ))
                .anyMatch("/bills/{id}/record"::equals);

        assertTrue(exposed, "DELETE /bills/{id}/record must be exposed without repurposing the legacy delete route");
    }

    @Test
    void recordDeletionDefinesOneTransactionBoundary() throws Exception {
        Method method = BillCommandService.class.getMethod(
                "deleteBillRecord",
                Long.class,
                Long.class,
                String.class,
                String.class,
                Long.class,
                boolean.class
        );

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }
}
