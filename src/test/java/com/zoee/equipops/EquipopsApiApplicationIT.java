package com.zoee.equipops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "equipops.order.timeout-scan.enabled=false")
class EquipopsApiApplicationIT {

    @Test
    void contextLoads() {
    }
}
