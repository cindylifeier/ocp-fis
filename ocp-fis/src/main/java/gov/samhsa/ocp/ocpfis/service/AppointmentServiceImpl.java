package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.validation.FhirValidator;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.service.dto.AppointmentDto;
import gov.samhsa.ocp.ocpfis.service.dto.AppointmentParticipantDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.service.exception.ResourceNotFoundException;
import gov.samhsa.ocp.ocpfis.service.mapping.dtotofhirmodel.AppointmentDtoToAppointmentConverter;
import gov.samhsa.ocp.ocpfis.util.DateUtil;
import gov.samhsa.ocp.ocpfis.util.FhirDtoUtil;
import gov.samhsa.ocp.ocpfis.util.FhirUtil;
import gov.samhsa.ocp.ocpfis.util.PaginationUtil;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final IGenericClient fhirClient;

    private final FhirValidator fhirValidator;

    private final FisProperties fisProperties;

    @Autowired
    public AppointmentServiceImpl(IGenericClient fhirClient, FhirValidator fhirValidator, FisProperties fisProperties) {
        this.fhirClient = fhirClient;
        this.fhirValidator = fhirValidator;
        this.fisProperties = fisProperties;
    }

    @Override
    public void createAppointment(AppointmentDto appointmentDto) {
        String creatorName = appointmentDto.getCreatorName() != null? appointmentDto.getCreatorName().trim() : "";
        log.info("Creating an appointment initiated by " + creatorName);
        //Map
        final Appointment appointment = AppointmentDtoToAppointmentConverter.map(appointmentDto, true);
        //Set created Date
        appointment.setCreated(new Date());
        //Validate
        FhirUtil.validateFhirResource(fhirValidator, appointment, Optional.empty(), ResourceType.Appointment.name(), "Create Appointment");
        //Create
        FhirUtil.createFhirResource(fhirClient, appointment, ResourceType.Appointment.name());

    }

    @Override
    public PageDto<AppointmentDto> getAppointments (Optional<List<String>> statusList, String searchKey, String searchValue, Optional<Integer> pageNumber, Optional<Integer> pageSize) {
        int numberOfAppointmentsPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.Appointment.name());
        IQuery iQuery = fhirClient.search().forResource(Appointment.class);

        //Check for Patient
        if (searchKey.equalsIgnoreCase("patientId"))
            iQuery.where(new ReferenceClientParam("patient").hasId(searchValue));

        //Check for Appointment
        if (searchKey.equalsIgnoreCase("appointmentId"))
            iQuery.where(new TokenClientParam("_id").exactly().code(searchValue));

        Bundle firstPageAppointmentBundle;
        Bundle otherPageAppointmentBundle;
        boolean firstPage = true;

        firstPageAppointmentBundle = (Bundle) iQuery
                .count(numberOfAppointmentsPerPage)
                .returnBundle(Bundle.class)
                .execute();

        if (firstPageAppointmentBundle == null || firstPageAppointmentBundle.getEntry().isEmpty()) {
            throw new ResourceNotFoundException("No Appointments were found in the FHIR server.");
        }

        otherPageAppointmentBundle = firstPageAppointmentBundle;

        if (pageNumber.isPresent() && pageNumber.get() > 1 && otherPageAppointmentBundle.getLink(Bundle.LINK_NEXT) != null) {
            firstPage = false;
            otherPageAppointmentBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, firstPageAppointmentBundle, pageNumber.get(), numberOfAppointmentsPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedAppointments = otherPageAppointmentBundle.getEntry();


        List<AppointmentDto> appointmentDtos = retrievedAppointments.stream().filter(retrivedBundle -> retrivedBundle.getResource().getResourceType().equals(ResourceType.Appointment)).map(retrievedAppointment -> {

            Appointment appointment = (Appointment) retrievedAppointment.getResource();

            AppointmentDto appointmentDto = new AppointmentDto();


            appointmentDto.setLogicalId(appointment.getIdElement().getIdPart());

            if (appointment.hasStatus()) {
                appointmentDto.setStatusCode(appointment.getStatus().toCode());
            }

            if (appointment.hasType()) {
                ValueSetDto category = FhirDtoUtil.convertCodeableConceptToValueSetDto(appointment.getAppointmentType());
                appointmentDto.setTypeCode(category.getCode());
            }

            if (appointment.hasDescription()) {
                appointmentDto.setDescription(appointment.getDescription());
            }

            if (appointment.hasParticipant()) {
                List<AppointmentParticipantDto> participantDtos = FhirDtoUtil.convertAppointmentParticipantListToAppointmentParticipantDtoList(appointment.getParticipant());
                appointmentDto.setParticipant(participantDtos);
            }

            if (appointment.hasStart()) {
                appointmentDto.setStart(DateUtil.convertDateToLocalDateTime(appointment.getStart()));
            }

            if (appointment.hasEnd()) {
                appointmentDto.setEnd(DateUtil.convertDateToLocalDateTime(appointment.getEnd()));
            }
            return appointmentDto;

        }).collect(toList());

        double totalPages = Math.ceil((double) otherPageAppointmentBundle.getTotal() / numberOfAppointmentsPerPage);
        int currentPage = firstPage ? 1 : pageNumber.get();

        return new PageDto<>(appointmentDtos, numberOfAppointmentsPerPage, totalPages, currentPage, appointmentDtos.size(), otherPageAppointmentBundle.getTotal());

    }
}

