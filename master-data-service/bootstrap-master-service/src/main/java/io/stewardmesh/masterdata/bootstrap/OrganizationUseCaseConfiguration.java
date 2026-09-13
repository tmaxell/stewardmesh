package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.organization.BusinessUnitReadService;
import io.stewardmesh.masterdata.application.organization.BusinessUnitReferenceService;
import io.stewardmesh.masterdata.application.organization.SiteAssignmentReadService;
import io.stewardmesh.masterdata.application.organization.SiteAssignmentService;
import io.stewardmesh.masterdata.application.port.in.AssignSupplierSite;
import io.stewardmesh.masterdata.application.port.in.GetBusinessUnit;
import io.stewardmesh.masterdata.application.port.in.ListSiteAssignments;
import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OrganizationUseCaseConfiguration {

    @Bean
    SiteAssignmentPolicy siteAssignmentPolicy() {
        return new SiteAssignmentPolicy();
    }

    @Bean
    SynchronizeBusinessUnit synchronizeBusinessUnit(
            BusinessUnitRepository businessUnits, ApplicationTransaction transaction) {
        return new BusinessUnitReferenceService(businessUnits, transaction);
    }

    @Bean
    GetBusinessUnit getBusinessUnit(BusinessUnitRepository businessUnits) {
        return new BusinessUnitReadService(businessUnits);
    }

    @Bean
    AssignSupplierSite assignSupplierSite(
            LoadGoldenRecordProjection goldenRecords,
            BusinessUnitRepository businessUnits,
            SiteAssignmentRepository assignments,
            ApplicationTransaction transaction,
            SiteAssignmentPolicy policy) {
        return new SiteAssignmentService(
                goldenRecords, businessUnits, assignments, transaction, policy);
    }

    @Bean
    ListSiteAssignments listSiteAssignments(SiteAssignmentRepository assignments) {
        return new SiteAssignmentReadService(assignments);
    }
}
