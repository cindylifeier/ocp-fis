package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.service.dto.LocationDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.exception.LocationNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Location;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LocationServiceImpl implements LocationService {

    private final ModelMapper modelMapper;

    private final IGenericClient fhirClient;

    private final FisProperties fisProperties;

    @Autowired
    public LocationServiceImpl(ModelMapper modelMapper, IGenericClient fhirClient, FisProperties fisProperties) {
        this.modelMapper = modelMapper;
        this.fhirClient = fhirClient;
        this.fisProperties = fisProperties;
    }

    @Override
    public PageDto<LocationDto> getAllLocations(Optional<List<String>> status, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> page, Optional<Integer> size) {

        int numberOfLocationsPerPage = size.filter(s -> s > 0 &&
                s <= fisProperties.getLocation().getPagination().getMaxSize()).orElse(fisProperties.getLocation().getPagination().getDefaultSize());

        Bundle firstPageLocationSearchBundle;
        Bundle otherPageLocationSearchBundle;
        boolean firstPage = true;

        IQuery locationsSearchQuery = fhirClient.search().forResource(Location.class);

        //Check for location status
        if (status.isPresent() && status.get().size() > 0) {
            log.info("Searching for ALL locations with the following specific status(es).");
            status.get().forEach(log::info);
            locationsSearchQuery.where(new TokenClientParam("status").exactly().codes(status.get()));
        } else {
            log.info("Searching for locations with ALL statuses");
        }

        // Check if there are any additional search criteria
        if (searchKey.isPresent() && searchValue.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.LocationSearchKey.NAME.name())) {
            log.info("Searching for " + SearchKeyEnum.LocationSearchKey.NAME.name() + " = " + searchValue.get().trim());
            locationsSearchQuery.where(new StringClientParam("name").matches().value(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchValue.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.LocationSearchKey.LOGICALID.name())) {
            log.info("Searching for " + SearchKeyEnum.LocationSearchKey.LOGICALID.name() + " = " + searchValue.get().trim());
            locationsSearchQuery.where(new TokenClientParam("_id").exactly().code(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchValue.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.LocationSearchKey.IDENTIFIERVALUE.name())) {
            log.info("Searching for " + SearchKeyEnum.LocationSearchKey.IDENTIFIERVALUE.name() + " = " + searchValue.get().trim());
            locationsSearchQuery.where(new TokenClientParam("identifier").exactly().code(searchValue.get().trim()));
        } else {
            log.info("No additional search criteria entered.");
        }

        //The following bundle only contains Page 1 of the resultSet
        firstPageLocationSearchBundle = (Bundle) locationsSearchQuery.count(numberOfLocationsPerPage)
                .returnBundle(Bundle.class)
                .encodedJson()
                .execute();

        if (firstPageLocationSearchBundle == null || firstPageLocationSearchBundle.getEntry().size() < 1) {
            throw new LocationNotFoundException("No locations were found in the FHIR server");
        }

        log.info("FHIR Location(s) bundle retrieved " + firstPageLocationSearchBundle.getTotal() + " location(s) from FHIR server successfully");
        otherPageLocationSearchBundle = firstPageLocationSearchBundle;
        if (page.isPresent() && page.get() > 1) {
            // Load the required page
            firstPage = false;
            otherPageLocationSearchBundle = getLocationSearchBundleAfterFirstPage(firstPageLocationSearchBundle, page.get(), numberOfLocationsPerPage);
        }
        List<Bundle.BundleEntryComponent> retrievedLocations = otherPageLocationSearchBundle.getEntry();

        //Arrange Page related info
        List<LocationDto> locationsList = retrievedLocations.stream().map(this::convertLocationBundleEntryToLocationDto).collect(Collectors.toList());
        double totalPages = Math.ceil((double) otherPageLocationSearchBundle.getTotal() / numberOfLocationsPerPage);
        int currentPage = firstPage ? 1 : page.get();

        return new PageDto<>(locationsList, numberOfLocationsPerPage, totalPages, currentPage, locationsList.size(), otherPageLocationSearchBundle.getTotal());
    }

    @Override
    public PageDto<LocationDto> getLocationsByOrganization(String organizationResourceId, Optional<List<String>> status, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> page, Optional<Integer> size) {
        int numberOfLocationsPerPage = size.filter(s -> s > 0 &&
                s <= fisProperties.getLocation().getPagination().getMaxSize()).orElse(fisProperties.getLocation().getPagination().getDefaultSize());

        Bundle firstPageLocationSearchBundle;
        Bundle otherPageLocationSearchBundle;
        boolean firstPage = true;

        IQuery locationsSearchQuery = fhirClient.search().forResource(Location.class).where(new ReferenceClientParam("organization").hasId(organizationResourceId));

        //Check for location status
        if (status.isPresent() && status.get().size() > 0) {
            log.info("Searching for location with the following specific status(es) for the given OrganizationID:" + organizationResourceId);
            status.get().forEach(log::info);
            locationsSearchQuery.where(new TokenClientParam("status").exactly().codes(status.get()));
        } else {
            log.info("Searching for locations with ALL statuses for the given OrganizationID:" + organizationResourceId);
        }

        // Check if there are any additional search criteria
        if (searchKey.isPresent() && searchValue.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.LocationSearchKey.NAME.name())) {
            log.info("Searching for " + SearchKeyEnum.LocationSearchKey.NAME.name() + " = " + searchValue.get().trim());
            locationsSearchQuery.where(new StringClientParam("name").matches().value(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchValue.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.LocationSearchKey.LOGICALID.name())) {
            log.info("Searching for " + SearchKeyEnum.LocationSearchKey.LOGICALID.name() + " = " + searchValue.get().trim());
            locationsSearchQuery.where(new TokenClientParam("_id").exactly().code(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchValue.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.LocationSearchKey.IDENTIFIERVALUE.name())) {
            log.info("Searching for " + SearchKeyEnum.LocationSearchKey.IDENTIFIERVALUE.name() + " = " + searchValue.get().trim());
            locationsSearchQuery.where(new TokenClientParam("identifier").exactly().code(searchValue.get().trim()));
        } else {
            log.info("No additional search criteria entered.");
        }

        //The following bundle only contains Page 1 of the resultSet
        firstPageLocationSearchBundle = (Bundle) locationsSearchQuery.count(numberOfLocationsPerPage)
                .returnBundle(Bundle.class)
                .encodedJson()
                .execute();

        if (firstPageLocationSearchBundle == null || firstPageLocationSearchBundle.getEntry().size() < 1) {
            log.info("No location found for the given OrganizationID:" + organizationResourceId);
            throw new LocationNotFoundException("No location found for the given OrganizationID:" + organizationResourceId);
        }

        log.info("FHIR Location(s) bundle retrieved " + firstPageLocationSearchBundle.getTotal() + " location(s) from FHIR server successfully");

        otherPageLocationSearchBundle = firstPageLocationSearchBundle;
        if (page.isPresent() && page.get() > 1) {
            // Load the required page
            firstPage = false;
            otherPageLocationSearchBundle = getLocationSearchBundleAfterFirstPage(otherPageLocationSearchBundle, page.get(), numberOfLocationsPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedLocations = otherPageLocationSearchBundle.getEntry();

        //Arrange Page related info
        List<LocationDto> locationsList = retrievedLocations.stream().map(this::convertLocationBundleEntryToLocationDto).collect(Collectors.toList());
        double totalPages = Math.ceil((double) otherPageLocationSearchBundle.getTotal() / numberOfLocationsPerPage);
        int currentPage = firstPage ? 1 : page.get();

        return new PageDto<>(locationsList, numberOfLocationsPerPage, totalPages, currentPage, locationsList.size(), otherPageLocationSearchBundle.getTotal());
    }

    @Override
    public LocationDto getLocation(String locationId) {
        Bundle locationBundle = fhirClient.search().forResource(Location.class)
                .where(new TokenClientParam("_id").exactly().code(locationId))
                .returnBundle(Bundle.class)
                .execute();

        if (locationBundle == null || locationBundle.getEntry().size() < 1) {
            log.info("No location was found for the given LocationID:" + locationId);
            throw new LocationNotFoundException("No location was found for the given LocationID:" + locationId);
        }

        log.info("FHIR Location bundle retrieved from FHIR server successfully");

        Bundle.BundleEntryComponent retrievedLocation = locationBundle.getEntry().get(0);

        return convertLocationBundleEntryToLocationDto(retrievedLocation);
    }

    @Override
    public LocationDto getChildLocation(String locationId) {
        Bundle childLocationBundle = fhirClient.search().forResource(Location.class)
                .where(new ReferenceClientParam("partof").hasId(locationId))
                .returnBundle(Bundle.class)
                .execute();

        if (childLocationBundle == null || childLocationBundle.getEntry().size() < 1) {
            log.info("No child location found for the given LocationID:" + locationId);
            throw new LocationNotFoundException("No child location found for the given LocationID:" + locationId);
        }

        log.info("FHIR Location bundle retrieved from FHIR server successfully");

        Bundle.BundleEntryComponent retrievedLocation = childLocationBundle.getEntry().get(0);

        return convertLocationBundleEntryToLocationDto(retrievedLocation);
    }

    private Bundle getLocationSearchBundleAfterFirstPage(Bundle locationSearchBundle, int page, int size) {
        if (locationSearchBundle.getLink(Bundle.LINK_NEXT) != null) {
            //Assuming page number starts with 1
            int offset = ((page >= 1 ? page : 1) - 1) * size;

            if (offset >= locationSearchBundle.getTotal()) {
                throw new LocationNotFoundException("No locations were found in the FHIR server for the page number: " + page);
            }

            String pageUrl = fisProperties.getFhir().getPublish().getServerUrl().getResource()
                    + "?_getpages=" + locationSearchBundle.getId()
                    + "&_getpagesoffset=" + offset
                    + "&_count=" + size
                    + "&_bundletype=searchset";

            // Load the required page
            return fhirClient.search().byUrl(pageUrl)
                    .returnBundle(Bundle.class)
                    .execute();
        } else {
            throw new LocationNotFoundException("No locations were found in the FHIR server for the page number: " + page);
        }
    }

    private LocationDto convertLocationBundleEntryToLocationDto(Bundle.BundleEntryComponent fhirLocationModel) {
        LocationDto tempLocationDto = modelMapper.map(fhirLocationModel.getResource(), LocationDto.class);
        tempLocationDto.setLogicalId(fhirLocationModel.getResource().getIdElement().getIdPart());
        return tempLocationDto;
    }
}
