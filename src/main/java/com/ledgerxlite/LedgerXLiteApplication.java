package com.ledgerxlite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for LedgerX Lite.
 * A financial ledger system inspired by LedgerX architecture.
 *
 * @EnableTransactionManagement ensures @Transactional annotations work properly.
 * While @SpringBootApplication auto-configures this, we declare it explicitly
 * for clarity and to ensure transaction support is never accidentally disabled.
 *
 * @author LedgerX Lite Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableTransactionManagement
public class LedgerXLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerXLiteApplication.class, args);
    }

}
