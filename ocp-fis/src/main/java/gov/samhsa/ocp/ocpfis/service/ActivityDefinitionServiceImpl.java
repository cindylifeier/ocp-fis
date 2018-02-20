package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.validation.FhirValidator;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.domain.SearchKeyEnum;
import gov.samhsa.ocp.ocpfis.service.dto.ActionParticipantDto;
import gov.samhsa.ocp.ocpfis.service.dto.ActivityDefinitionDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.PeriodDto;
import gov.samhsa.ocp.ocpfis.service.dto.TimingDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.service.exception.BadRequestException;
import gov.samhsa.ocp.ocpfis.service.exception.DuplicateResourceFoundException;
import gov.samhsa.ocp.ocpfis.util.FhirUtils;
import gov.samhsa.ocp.ocpfis.util.PaginationUtil;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.ActivityDefinition;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Enumerations;
import org.hl7.fhir.dstu3.model.RelatedArtifact;
import org.hl7.fhir.dstu3.model.RelatedArtifact.RelatedArtifactType;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.Timing;
import org.hl7.fhir.exceptions.FHIRException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class ActivityDefinitionServiceImpl implements ActivityDefinitionService {

    private final ModelMapper modelMapper;

    private final IGenericClient fhirClient;

    private final FhirValidator fhirValidator;

    private final LookUpService lookUpService;

    private final FisProperties fisProperties;

    @Autowired
    public ActivityDefinitionServiceImpl(ModelMapper modelMapper, IGenericClient fhirClient, FhirValidator fhirValidator, LookUpService lookUpService, FisProperties fisProperties) {
        this.modelMapper = modelMapper;
        this.fhirClient = fhirClient;
        this.fhirValidator = fhirValidator;
        this.lookUpService = lookUpService;
        this.fisProperties = fisProperties;
    }

    @Override
    public PageDto<ActivityDefinitionDto> getAllActivityDefinitionsByOrganization(String organizationResourceId, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> pageNumber, Optional<Integer> pageSize) {
        int numberOfActivityDefinitionsPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.ActivityDefinition.name());

        Bundle firstPageActivityDefinitionSearchBundle;
        Bundle otherPageActivityDefinitionSearchBundle;
        boolean firstPage = true;

        IQuery activityDefinitionsSearchQuery = fhirClient.search().forResource(ActivityDefinition.class).where(new StringClientParam("publisher").matches().value("Organization/" + organizationResourceId));

        // Check if there are any additional search criteria
        activityDefinitionsSearchQuery = addAdditionalSearchConditions(activityDefinitionsSearchQuery, searchKey, searchValue);

        //The following bundle only contains Page 1 of the resultSet with location
        firstPageActivityDefinitionSearchBundle = (Bundle) activityDefinitionsSearchQuery.count(numberOfActivityDefinitionsPerPage)
                .returnBundle(Bundle.class)
                .encodedJson()
                .execute();

        if (firstPageActivityDefinitionSearchBundle == null || firstPageActivityDefinitionSearchBundle.getEntry().isEmpty()) {
            log.info("No Activity Definition found for the given OrganizationID:" + organizationResourceId);
            return new PageDto<>(new ArrayList<>(), numberOfActivityDefinitionsPerPage, 0, 0, 0, 0);
        }

        log.info("FHIR Activity Definition(s) bundle retrieved " + firstPageActivityDefinitionSearchBundle.getTotal() + " Activity Definition(s) from FHIR server successfully");

        otherPageActivityDefinitionSearchBundle = firstPageActivityDefinitionSearchBundle;
        if (pageNumber.isPresent() && pageNumber.get() > 1) {
            // Load the required page
            firstPage = false;
            otherPageActivityDefinitionSearchBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, otherPageActivityDefinitionSearchBundle, pageNumber.get(), numberOfActivityDefinitionsPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedActivityDefinitions = otherPageActivityDefinitionSearchBundle.getEntry();

        //Arrange Page related info
        List<ActivityDefinitionDto> activityDefinitionsList = retrievedActivityDefinitions.stream().map(aa -> convertActivityDefinitionBundleEntryToActivityDefinitionDto(aa)).collect(toList());

        double totalPages = Math.ceil((double) otherPageActivityDefinitionSearchBundle.getTotal() / numberOfActivityDefinitionsPerPage);
        int currentPage = firstPage ? 1 : pageNumber.get();

        return new PageDto<>(activityDefinitionsList, numberOfActivityDefinitionsPerPage, totalPages, currentPage, activityDefinitionsList.size(), otherPageActivityDefinitionSearchBundle.getTotal());
    }


    @Override
    public void createActivityDefinition(ActivityDefinitionDto activityDefinitionDto, String organizationId) {
        if (!isDuplicate(activityDefinitionDto, organizationId)) {
            ActivityDefinition activityDefinition = new ActivityDefinition();
            activityDefinition.setName(activityDefinitionDto.getName());
            activityDefinition.setTitle(activityDefinitionDto.getTitle());
            activityDefinition.setDescription(activityDefinitionDto.getDescription());

            activityDefinition.setVersion(fisProperties.getActivityDefinition().getVersion());
            activityDefinition.setStatus(Enumerations.PublicationStatus.valueOf(activityDefinitionDto.getStatus().getCode().toUpperCase()));
            try {
                activityDefinition.setDate(FhirUtils.convertToDate(activityDefinitionDto.getDate()));
            } catch (ParseException e) {
                throw new BadRequestException("Invalid date was given.");
            }
            activityDefinition.setKind(ActivityDefinition.ActivityDefinitionKind.valueOf(activityDefinitionDto.getKind().getCode().toUpperCase()));
            activityDefinition.setPublisher("Organization/" + organizationId);

            //Relative Artifact
            List<RelatedArtifact> relatedArtifacts = new ArrayList<>();
            if (activityDefinitionDto.getRelatedArtifact() != null && !activityDefinitionDto.getRelatedArtifact().isEmpty()) {
                activityDefinitionDto.getRelatedArtifact().forEach(relatedArtifactDto -> {
                    RelatedArtifact relatedArtifact = new RelatedArtifact();
                    relatedArtifact.setType(RelatedArtifactType.valueOf(relatedArtifactDto.getCode().toUpperCase()));
                    relatedArtifacts.add(relatedArtifact);
                });
                activityDefinition.setRelatedArtifact(relatedArtifacts);
            }

            //Participant
            CodeableConcept actionParticipantRole = new CodeableConcept();
            actionParticipantRole.addCoding().setCode(activityDefinitionDto.getParticipant().getActionRoleCode())
                    .setDisplay(activityDefinitionDto.getParticipant().getActionRoleDisplay())
                    .setSystem(activityDefinitionDto.getParticipant().getActionRoleSystem());

            activityDefinition.addParticipant().setRole(actionParticipantRole).setType(ActivityDefinition.ActivityParticipantType.valueOf(activityDefinitionDto.getParticipant().getActionTypeCode().toUpperCase()));

            //Topic
            CodeableConcept topic = new CodeableConcept();
            topic.addCoding().setCode(activityDefinitionDto.getTopic().getCode()).setSystem(activityDefinitionDto.getTopic().getSystem())
                    .setDisplay(activityDefinitionDto.getTopic().getDisplay());
            activityDefinition.addTopic(topic);

            //Period
            if (activityDefinitionDto.getStatus().getCode().equalsIgnoreCase("active")) {

                    if (activityDefinitionDto.getEffectivePeriod().getStart() != null) {
                        activityDefinition.getEffectivePeriod().setStart((java.sql.Date.valueOf(activityDefinitionDto.getEffectivePeriod().getStart())));
                    } else {
                        activityDefinition.getEffectivePeriod().setStart(java.sql.Date.valueOf(Calendar.getInstance().toString()));
                    }

            }

            if (activityDefinitionDto.getStatus().getCode().equalsIgnoreCase("expired")) {
                activityDefinition.getEffectivePeriod().setEnd(java.sql.Date.valueOf(LocalDate.now()));
            } else {
                activityDefinition.getEffectivePeriod().setEnd(java.sql.Date.valueOf(activityDefinitionDto.getEffectivePeriod().getEnd()));
            }

            //Timing
            Timing timing = new Timing();
            timing.getRepeat().setDurationMax(activityDefinitionDto.getTiming().getDurationMax());
            timing.getRepeat().setFrequency(activityDefinitionDto.getTiming().getFrequency());
            activityDefinition.setTiming(timing);

            fhirClient.create().resource(activityDefinition).execute();
        } else {
            throw new DuplicateResourceFoundException("Duplicate Activity Definition is already present.");
        }
    }

    private IQuery addAdditionalSearchConditions(IQuery activityDefinitionsSearchQuery, Optional<String> searchKey, Optional<String> searchValue) {
        if (searchKey.isPresent() && !SearchKeyEnum.HealthcareServiceSearchKey.contains(searchKey.get())) {
            throw new BadRequestException("Unidentified search key:" + searchKey.get());
        } else if ((searchKey.isPresent() && !searchValue.isPresent()) ||
                (searchKey.isPresent() && searchValue.get().trim().isEmpty())) {
            throw new BadRequestException("No search value found for the search key" + searchKey.get());
        }

        // Check if there are any additional search criteria
        if (searchKey.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.HealthcareServiceSearchKey.NAME.name())) {
            log.info("Searching for " + SearchKeyEnum.HealthcareServiceSearchKey.NAME.name() + " = " + searchValue.get().trim());
            activityDefinitionsSearchQuery.where(new StringClientParam("name").matches().value(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.HealthcareServiceSearchKey.LOGICALID.name())) {
            log.info("Searching for " + SearchKeyEnum.HealthcareServiceSearchKey.LOGICALID.name() + " = " + searchValue.get().trim());
            activityDefinitionsSearchQuery.where(new TokenClientParam("_id").exactly().code(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.HealthcareServiceSearchKey.IDENTIFIERVALUE.name())) {
            log.info("Searching for " + SearchKeyEnum.HealthcareServiceSearchKey.IDENTIFIERVALUE.name() + " = " + searchValue.get().trim());
            activityDefinitionsSearchQuery.where(new TokenClientParam("identifier").exactly().code(searchValue.get().trim()));
        } else {
            log.info("No additional search criteria entered.");
        }
        return activityDefinitionsSearchQuery;
    }

    private ActivityDefinitionDto convertActivityDefinitionBundleEntryToActivityDefinitionDto(Bundle.BundleEntryComponent fhirActivityDefinitionModel) {
        ActivityDefinitionDto tempActivityDefinitionDto = modelMapper.map(fhirActivityDefinitionModel.getResource(), ActivityDefinitionDto.class);
        tempActivityDefinitionDto.setLogicalId(fhirActivityDefinitionModel.getResource().getIdElement().getIdPart());
        ActivityDefinition activityDefinition= (ActivityDefinition) fhirActivityDefinitionModel.getResource();

        tempActivityDefinitionDto.getStatus().setCode(activityDefinition.getStatus().toCode());

        tempActivityDefinitionDto.getKind().setCode(activityDefinition.getKind().toCode());

        if(activityDefinition.getEffectivePeriod()!=null && !activityDefinition.getEffectivePeriod().isEmpty()) {
            PeriodDto periodDto = new PeriodDto();
            tempActivityDefinitionDto.setEffectivePeriod(periodDto);

            tempActivityDefinitionDto.getEffectivePeriod().setStart(FhirUtils.convertToLocalDate(activityDefinition.getEffectivePeriod().getStart()));
            tempActivityDefinitionDto.getEffectivePeriod().setEnd(FhirUtils.convertToLocalDate(activityDefinition.getEffectivePeriod().getEnd()));
        }

        if(activityDefinition.getParticipant()!=null && !activityDefinition.getParticipant().isEmpty()) {
            ActionParticipantDto actionParticipantDto = new ActionParticipantDto();
            tempActivityDefinitionDto.setParticipant(actionParticipantDto);

            ActivityDefinition.ActivityDefinitionParticipantComponent participantComponent = activityDefinition.getParticipant().stream().findAny().get();

            tempActivityDefinitionDto.getParticipant().setActionTypeCode(participantComponent.getType().toCode());
            tempActivityDefinitionDto.getParticipant().setActionTypeDisplay(participantComponent.getType().getDisplay());

            tempActivityDefinitionDto.getParticipant().setActionRoleCode(participantComponent.getRole().getCoding().stream().findAny().get().getCode());
            tempActivityDefinitionDto.getParticipant().setActionRoleDisplay(participantComponent.getRole().getCoding().stream().findAny().get().getDisplay());
        }


        TimingDto timingDto = new TimingDto();
        tempActivityDefinitionDto.setTiming(timingDto);
        try {
            if((activityDefinition.getTimingTiming()!=null) && !activityDefinition.getTimingTiming().isEmpty()) {
                if((activityDefinition.getTimingTiming().getRepeat() !=null ||!(activityDefinition.getTimingTiming().getRepeat().isEmpty())))
                {
                    tempActivityDefinitionDto.getTiming().setDurationMax((activityDefinition.getTimingTiming().getRepeat().getDurationMax().floatValue()));
                    tempActivityDefinitionDto.getTiming().setFrequency(activityDefinition.getTimingTiming().getRepeat().getFrequency());
                }
            }
        } catch (FHIRException e) {
        }
        return tempActivityDefinitionDto;
    }

    private boolean isDuplicate(ActivityDefinitionDto activityDefinitionDto, String organizationid) {
        if (activityDefinitionDto.getStatus().getCode().equalsIgnoreCase(Enumerations.PublicationStatus.ACTIVE.toString())) {

            if (isDuplicateWithNamePublisherKindAndStatus(activityDefinitionDto, organizationid) || isDuplicateWithTitlePublisherKindAndStatus(activityDefinitionDto, organizationid)) {
                return true;
            } else {
                return false;
            }

        }
        return false;
    }


    private boolean isDuplicateWithNamePublisherKindAndStatus(ActivityDefinitionDto activityDefinitionDto, String organizationid) {
        Bundle duplicateCheckWithNamePublisherAndStatusBundle = (Bundle) fhirClient.search().forResource(ActivityDefinition.class)
                .where(new StringClientParam("publisher").matches().value("Organization/" + organizationid))
                .where(new TokenClientParam("status").exactly().code("active"))
                .where(new StringClientParam("name").matches().value(activityDefinitionDto.getName()))
                .returnBundle(Bundle.class)
                .execute();

        return hasSameKind(duplicateCheckWithNamePublisherAndStatusBundle, activityDefinitionDto);

    }

    private boolean isDuplicateWithTitlePublisherKindAndStatus(ActivityDefinitionDto activityDefinitionDto, String organizationid) {

        Bundle duplicateCheckWithTitlePublisherAndStatusBundle = (Bundle) fhirClient.search().forResource(ActivityDefinition.class)
                .where(new StringClientParam("publisher").matches().value("Organization/" + organizationid))
                .where(new TokenClientParam("status").exactly().code("active"))
                .where(new StringClientParam("title").matches().value(activityDefinitionDto.getTitle()))
                .returnBundle(Bundle.class)
                .execute();

        return hasSameKind(duplicateCheckWithTitlePublisherAndStatusBundle, activityDefinitionDto);
    }

    private boolean hasSameKind(Bundle bundle, ActivityDefinitionDto activityDefinitionDto) {
        List<Bundle.BundleEntryComponent> duplicateCheckList = new ArrayList<>();
        if (!bundle.isEmpty()) {
            duplicateCheckList = bundle.getEntry().stream().filter(activityDefinitionResource -> {
                ActivityDefinition activityDefinition = (ActivityDefinition) activityDefinitionResource.getResource();
                return activityDefinition.getKind().toCode().equalsIgnoreCase(activityDefinitionDto.getKind().getCode());
            }).collect(toList());
        }
        if (duplicateCheckList.isEmpty()) {
            return false;
        } else {
            return true;
        }

    }


}