package gov.samhsa.ocp.ocpfis.service.mapping.dtotofhirmodel;

import gov.samhsa.ocp.ocpfis.service.dto.LocationDto;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Location;
import org.modelmapper.PropertyMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LocationDtoToLocationMap extends PropertyMap<LocationDto, Location> {
    @Autowired
    private IdentifierDtoListToIdentifierListConverter identifierDtoListToIdentifierListConverter;
    @Autowired
    private AddressDtoToAddressConverter addressDtoToAddressConverter;

    @Autowired
    TelecomDtoListToTelecomListConverter telecomDtoListToTelecomListConverter;

    @Override
    protected void configure() {
        using(identifierDtoListToIdentifierListConverter).map(source.getIdentifiers()).setIdentifier(null);
        using(addressDtoToAddressConverter).map(source.getAddress()).setAddress(null);
        using(telecomDtoListToTelecomListConverter).map(source.getTelecoms()).setTelecom(null);
    }
}
