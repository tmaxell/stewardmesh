package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.RouteSupplierImportMatchesCommand;
import io.stewardmesh.masterdata.application.identity.RouteSupplierImportMatchesResult;

public interface RouteSupplierImportMatches extends UseCase<
        RouteSupplierImportMatchesCommand, RouteSupplierImportMatchesResult> {}
