package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.port.out.ParseSupplierWorkbook;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.ingestion.xlsx.XlsxSupplierWorkbookParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SupplierWorkbookConfiguration {

    @Bean
    ParseSupplierWorkbook parseSupplierWorkbook(ImportPolicy importPolicy) {
        return new XlsxSupplierWorkbookParser(importPolicy);
    }
}
