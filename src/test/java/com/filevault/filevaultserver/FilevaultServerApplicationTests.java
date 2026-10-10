package com.filevault.filevaultserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // The storage config is required but never contacted at startup; dummy values are enough here.
        "app.storage.r2.account-id=test-account",
        "app.storage.r2.access-key-id=test-key",
        "app.storage.r2.secret-access-key=test-secret",
        "app.storage.r2.bucket=test-bucket"
})
class FilevaultServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
