package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.validation.FhirValidator;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.service.dto.AbstractCareTeamDto;
import gov.samhsa.ocp.ocpfis.service.dto.AddressDto;
import gov.samhsa.ocp.ocpfis.service.dto.ConsentDto;
import gov.samhsa.ocp.ocpfis.service.dto.GeneralConsentRelatedFieldDto;
import gov.samhsa.ocp.ocpfis.service.dto.IdentifierDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.PatientDto;
import gov.samhsa.ocp.ocpfis.service.dto.PdfDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.service.exception.ConsentPdfGenerationException;
import gov.samhsa.ocp.ocpfis.service.exception.DuplicateResourceFoundException;
import gov.samhsa.ocp.ocpfis.service.exception.NoDataFoundException;
import gov.samhsa.ocp.ocpfis.service.exception.PreconditionFailedException;
import gov.samhsa.ocp.ocpfis.service.exception.ResourceNotFoundException;
import gov.samhsa.ocp.ocpfis.service.pdf.ConsentPdfGenerator;
import gov.samhsa.ocp.ocpfis.util.FhirDtoUtil;
import gov.samhsa.ocp.ocpfis.util.FhirUtil;
import gov.samhsa.ocp.ocpfis.util.PaginationUtil;
import gov.samhsa.ocp.ocpfis.util.RichStringClientParam;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CareTeam;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Consent;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.RelatedPerson;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.codesystems.ContactPointSystem;
import org.hl7.fhir.dstu3.model.codesystems.V3ActReason;
import org.hl7.fhir.exceptions.FHIRException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ConsentServiceImpl implements ConsentService {
    public static final String INFORMANT_CODE = "INF";
    public static final String INFORMANT_RECIPIENT_CODE = "IRCP";
    public static final String PSEUDO_ORGANIZATION_NAME = "Omnibus Care Plan (SAMHSA)";
    public static final String PSEUDO_ORGANIZATION_TAX_ID = "530196960";
    public static final String CONTENTTYPE = "application/pdf";
    public static final Boolean operatedByPatient = true;


    private final IGenericClient fhirClient;
    private final LookUpService lookUpService;
    private final FisProperties fisProperties;
    private final ModelMapper modelMapper;
    private final ConsentPdfGenerator consentPdfGenerator;

    @Autowired
    private PatientService patientService;

    @Autowired
    private FhirValidator fhirValidator;


    @Autowired
    public ConsentServiceImpl(ModelMapper modelMapper,
                              IGenericClient fhirClient,
                              LookUpService lookUpService,
                              FisProperties fisProperties,
                              ConsentPdfGenerator consentPdfGenerator) {
        this.modelMapper = modelMapper;
        this.fhirClient = fhirClient;
        this.lookUpService = lookUpService;
        this.fisProperties = fisProperties;
        this.consentPdfGenerator = consentPdfGenerator;
    }

    @Override
    public PageDto<ConsentDto> getConsents(Optional<String> patient, Optional<String> practitioner, Optional<String> status, Optional<Boolean> generalDesignation, Optional<Integer> pageNumber, Optional<Integer> pageSize) {

        int numberOfConsentsPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.Consent.name());
        Bundle firstPageConsentBundle;
        Bundle otherPageConsentBundle;

        // Generate the Query Based on Input Variables
        IQuery iQuery = getConsentIQuery(patient, practitioner, status, generalDesignation);

        // Disable caching to get latest data
        iQuery = FhirUtil.setNoCacheControlDirective(iQuery);

        //Apply Filters Based on Input Variables

        firstPageConsentBundle = PaginationUtil.getSearchBundleFirstPage(iQuery, numberOfConsentsPerPage, Optional.empty());

        if (firstPageConsentBundle == null || firstPageConsentBundle.getEntry().isEmpty()) {
            return new PageDto<>(new ArrayList<>(), numberOfConsentsPerPage, 0, 0, 0, 0);
        }

        log.info("FHIR Consent(s) bundle retrieved " + firstPageConsentBundle.getTotal() + " Consent(s) from FHIR server successfully");
        otherPageConsentBundle = firstPageConsentBundle;


        if (pageNumber.isPresent() && pageNumber.get() > 1 && otherPageConsentBundle.getLink(Bundle.LINK_NEXT) != null) {
            otherPageConsentBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, firstPageConsentBundle, pageNumber.get(), numberOfConsentsPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedConsents = otherPageConsentBundle.getEntry();

        // Map to DTO
        List<ConsentDto> consentDtosList = retrievedConsents.stream().map(this::convertConsentBundleEntryToConsentDto).collect(Collectors.toList());

        return (PageDto<ConsentDto>) PaginationUtil.applyPaginationForSearchBundle(consentDtosList, otherPageConsentBundle.getTotal(), numberOfConsentsPerPage, pageNumber);

    }

    @Override
    public ConsentDto getConsentsById(String consentId) {
        log.info("Searching for consentId: " + consentId);
        IQuery consentQuery = fhirClient.search().forResource(Consent.class)
                .where(new TokenClientParam("_id").exactly().code(consentId.trim()));

        consentQuery = FhirUtil.setNoCacheControlDirective(consentQuery);

        Bundle consentBundle = (Bundle) consentQuery.returnBundle(Bundle.class)
                .execute();

        if (consentBundle == null || consentBundle.getEntry().isEmpty()) {
            log.info("No consent was found for the given consentId:" + consentId);
            throw new ResourceNotFoundException("No consent was found for the given consent ID:" + consentId);
        }

        log.info("FHIR consent bundle retrieved from FHIR server successfully for consent ID:" + consentId);

        Bundle.BundleEntryComponent retrievedConsent = consentBundle.getEntry().get(0);
        return convertConsentBundleEntryToConsentDto(retrievedConsent);
    }

    @Override
    public GeneralConsentRelatedFieldDto getGeneralConsentRelatedFields(String patient) {
        GeneralConsentRelatedFieldDto generalConsentRelatedFieldDto = new GeneralConsentRelatedFieldDto();

        //Adding To careTeams
        Bundle careTeamBundle = fhirClient.search().forResource(CareTeam.class)
                .where(new ReferenceClientParam("subject").hasId(patient))
                .returnBundle(Bundle.class).execute();

        if (!careTeamBundle.getEntry().isEmpty()) {
            List<ReferenceDto> toActors = careTeamBundle.getEntry().stream().map(careTeamEntry -> {
                CareTeam careTeam = (CareTeam) careTeamEntry.getResource();
                return convertCareTeamToReferenceDto(careTeam);
            }).collect(Collectors.toList());

            generalConsentRelatedFieldDto.setToActors(toActors);

            //Adding from careTeams
            Bundle organizationBundle = getPseudoOrganization();

            organizationBundle.getEntry().stream().findAny().ifPresent(entry -> {
                Organization organization = (Organization) entry.getResource();
                ReferenceDto referenceDto = new ReferenceDto();
                referenceDto.setReference("Organization/" + organization.getIdElement().getIdPart());
                referenceDto.setDisplay(PSEUDO_ORGANIZATION_NAME);
                generalConsentRelatedFieldDto.setFromActors(Arrays.asList(referenceDto));
            });

            generalConsentRelatedFieldDto.setPurposeOfUse(FhirDtoUtil.convertCodeToValueSetDto(V3ActReason.TREAT.toCode(), lookUpService.getPurposeOfUse()));

        } else {
            throw new ResourceNotFoundException("No care teams are present.");
        }
        return generalConsentRelatedFieldDto;
    }


    @Override
    public void createConsent(ConsentDto consentDto) {
        //Create Consent
        Bundle associatedCareTeam = fhirClient.search().forResource(CareTeam.class).where(new ReferenceClientParam("patient").hasId(consentDto.getPatient().getReference()))
                .returnBundle(Bundle.class).execute();
        if (consentDto.isGeneralDesignation()) {
            if (!associatedCareTeam.getEntry().isEmpty()) {
                if (!isDuplicate(consentDto, Optional.empty())) {
                    Consent consent = consentDtoToConsent(Optional.empty(), consentDto);
                    //Validate
                    FhirUtil.validateFhirResource(fhirValidator, consent, Optional.empty(), ResourceType.Consent.name(), "Create Consent");

                    fhirClient.create().resource(consent).execute();
                } else {
                    throw new DuplicateResourceFoundException("This patient already has a general designation consent.");
                }
            } else {
                throw new PreconditionFailedException("No care team members for this patient.");
            }
        } else {
            Consent consent = consentDtoToConsent(Optional.empty(), consentDto);
            //Validate
            FhirUtil.validateFhirResource(fhirValidator, consent, Optional.empty(), ResourceType.Consent.name(), "Create Consent");

            fhirClient.create().resource(consent).execute();
        }
    }

    @Override
    public void updateConsent(String consentId, ConsentDto consentDto) {
        //Update Consent
        if (!isDuplicate(consentDto, Optional.of(consentId))) {
            Consent consent = consentDtoToConsent(Optional.of(consentId), consentDto);
            consent.setId(consentId);

            //Validate
            FhirUtil.validateFhirResource(fhirValidator, consent, Optional.of(consentId), ResourceType.Consent.name(), "Update Consent");

            fhirClient.update().resource(consent).execute();
        } else {
            throw new DuplicateResourceFoundException("This patient already has a general designation consent.");
        }
    }

    @Override
    public PageDto<AbstractCareTeamDto> getActors(Optional<String> patientId, Optional<String> name, Optional<String> actorType, Optional<List<String>> actorsAlreadyAssigned, Optional<Integer> pageNumber, Optional<Integer> pageSize) {
        int numberOfActorsPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.Consent.name());

        //Get Actors
        Bundle organizationBundle = fhirClient.search().forResource(Organization.class)
                .where(new TokenClientParam("_id").exactly().codes(getCareTeamParticipantIdsFromPatient(patientId)))
                .where(new RichStringClientParam("name").matches().value(name.orElse("")))
                .returnBundle(Bundle.class)
                .elementsSubset("id", "resourceType", "name", "identifier", "telecom", "address")
                .execute();

        Bundle practitionerBundle = fhirClient.search().forResource(Practitioner.class)
                .where(new TokenClientParam("_id").exactly().codes(getCareTeamParticipantIdsFromPatient(patientId)))
                .where(new RichStringClientParam("name").matches().value(name.orElse("")))
                .returnBundle(Bundle.class)
                .elementsSubset("id", "resourceType", "name", "identifier", "telecom", "address")
                .execute();

        Bundle relatedBundle = fhirClient.search().forResource(RelatedPerson.class)
                .where(new TokenClientParam("_id").exactly().codes(getCareTeamParticipantIdsFromPatient(patientId)))
                .where(new RichStringClientParam("name").matches().value(name.orElse("")))
                .returnBundle(Bundle.class)
                .elementsSubset("id", "resourceType", "name", "identifier", "telecom", "address")
                .execute();


        List<Bundle.BundleEntryComponent> organizationBundleEntryList = FhirUtil.getAllBundlesComponentIntoSingleList(organizationBundle, Optional.empty(), fhirClient, fisProperties);

        List<Bundle.BundleEntryComponent> practitionerBundleEntryList = FhirUtil.getAllBundlesComponentIntoSingleList(practitionerBundle, Optional.empty(), fhirClient, fisProperties);

        List<Bundle.BundleEntryComponent> relatedPersonBundleEntryList = FhirUtil.getAllBundlesComponentIntoSingleList(relatedBundle, Optional.empty(), fhirClient, fisProperties);

        //Adding practitoner
        List<AbstractCareTeamDto> abstractCareTeamDtoList = practitionerBundleEntryList.stream().map(pr -> {
            AbstractCareTeamDto abstractCareTeamDto = new AbstractCareTeamDto();
            Practitioner practitioner = (Practitioner) pr.getResource();
            abstractCareTeamDto.setId(practitioner.getIdElement().getIdPart());
            practitioner.getName().stream().findAny().ifPresent(humanName -> {
                abstractCareTeamDto.setDisplay(humanName.getGiven().stream().findAny().get() + " " + humanName.getFamily());
            });

            abstractCareTeamDto.setCareTeamType(AbstractCareTeamDto.CareTeamType.PRACTITIONER);
            List<IdentifierDto> identifierDtos = practitioner.getIdentifier().stream()
                    .map(identifier -> covertIdentifierToIdentifierDto(identifier))
                    .collect(Collectors.toList());
            abstractCareTeamDto.setIdentifiers(identifierDtos);

            practitioner.getAddress().stream().findAny().ifPresent(address -> {
                        AddressDto addressDto = convertAddressToAddressDto(address);
                        abstractCareTeamDto.setAddress(addressDto);
                    }
            );
            practitioner.getTelecom().stream()
                    .filter(telecom -> telecom.getSystem().getDisplay().equalsIgnoreCase(ContactPointSystem.PHONE.toString()))
                    .findAny().ifPresent(phone -> abstractCareTeamDto.setPhoneNumber(Optional.ofNullable(phone.getValue())));

            practitioner.getTelecom().stream().filter(telecom -> telecom.getSystem().getDisplay().equalsIgnoreCase(ContactPointSystem.EMAIL.toString()))
                    .findAny().ifPresent(email -> abstractCareTeamDto.setEmail(Optional.ofNullable(email.getValue())));
            return abstractCareTeamDto;
        }).distinct().collect(Collectors.toList());

        //Adding organization
        abstractCareTeamDtoList.addAll(organizationBundleEntryList.stream().map(org -> {
            AbstractCareTeamDto abstractCareTeamDto = new AbstractCareTeamDto();
            Organization organization = (Organization) org.getResource();
            abstractCareTeamDto.setId(organization.getIdElement().getIdPart());
            abstractCareTeamDto.setDisplay(organization.getName());
            abstractCareTeamDto.setCareTeamType(AbstractCareTeamDto.CareTeamType.ORGANIZATION);
            List<IdentifierDto> identifierDtos = organization.getIdentifier().stream()
                    .map(identifier -> covertIdentifierToIdentifierDto(identifier))
                    .collect(Collectors.toList());
            abstractCareTeamDto.setIdentifiers(identifierDtos);

            organization.getAddress().stream().findAny().ifPresent(address -> {
                AddressDto addressDto = convertAddressToAddressDto(address);
                abstractCareTeamDto.setAddress(addressDto);
            });

            organization.getTelecom().stream()
                    .filter(telecom -> telecom.getSystem().getDisplay().equalsIgnoreCase(ContactPointSystem.PHONE.toString()))
                    .findAny().ifPresent(phone -> abstractCareTeamDto.setPhoneNumber(Optional.ofNullable(phone.getValue())));

            organization.getTelecom().stream().filter(telecom -> telecom.getSystem().getDisplay().equalsIgnoreCase(ContactPointSystem.EMAIL.toString()))
                    .findAny().ifPresent(email -> abstractCareTeamDto.setEmail(Optional.ofNullable(email.getValue())));

            return abstractCareTeamDto;
        }).distinct().collect(Collectors.toList()));

        //Adding relatedPerson
        abstractCareTeamDtoList.addAll(relatedPersonBundleEntryList.stream().map(rp -> {
            AbstractCareTeamDto abstractCareTeamDto = new AbstractCareTeamDto();
            RelatedPerson relatedPerson = (RelatedPerson) rp.getResource();
            abstractCareTeamDto.setId(relatedPerson.getIdElement().getIdPart());

            abstractCareTeamDto.setCareTeamType(AbstractCareTeamDto.CareTeamType.RELATEDPERSON);
            relatedPerson.getName().stream().findAny().ifPresent(humanName -> {
                abstractCareTeamDto.setDisplay(humanName.getGiven().stream().findAny().get() + " " + humanName.getFamily());
            });

            List<IdentifierDto> identifierDtos = relatedPerson.getIdentifier().stream()
                    .map(identifier -> covertIdentifierToIdentifierDto(identifier))
                    .collect(Collectors.toList());
            abstractCareTeamDto.setIdentifiers(identifierDtos);

            relatedPerson.getAddress().stream().findAny().ifPresent(address -> {
                        AddressDto addressDto = convertAddressToAddressDto(address);
                        abstractCareTeamDto.setAddress(addressDto);
                    }
            );
            relatedPerson.getTelecom().stream()
                    .filter(telecom -> telecom.getSystem().getDisplay().equalsIgnoreCase(ContactPointSystem.PHONE.toString()))
                    .findAny().ifPresent(phone -> abstractCareTeamDto.setPhoneNumber(Optional.ofNullable(phone.getValue())));

            relatedPerson.getTelecom().stream().filter(telecom -> telecom.getSystem().getDisplay().equalsIgnoreCase(ContactPointSystem.EMAIL.toString()))
                    .findAny().ifPresent(email -> abstractCareTeamDto.setEmail(Optional.ofNullable(email.getValue())));
            return abstractCareTeamDto;
        }).distinct().collect(Collectors.toList()));

        actorType.ifPresent(type -> abstractCareTeamDtoList.removeIf(actors -> !actors.getCareTeamType().toString().equalsIgnoreCase(type)));
        actorsAlreadyAssigned.ifPresent(actorsAlreadyPresent -> abstractCareTeamDtoList.removeIf(abstractCareTeamDto -> actorsAlreadyPresent.contains(abstractCareTeamDto.getId())));

        return (PageDto<AbstractCareTeamDto>) PaginationUtil.applyPaginationForCustomArrayList(abstractCareTeamDtoList, numberOfActorsPerPage, pageNumber, false);
    }


    private ConsentDto convertConsentBundleEntryToConsentDto(Bundle.BundleEntryComponent fhirConsentDtoModel) {
        ConsentDto consentDto = modelMapper.map(fhirConsentDtoModel.getResource(), ConsentDto.class);

        consentDto.getFromActor().forEach(member -> {
            if (member.getDisplay().equalsIgnoreCase("Omnibus Care Plan (SAMHSA)"))
                consentDto.setGeneralDesignation(true);
        });

        Consent consent = (Consent) fhirConsentDtoModel.getResource();

        try {
            if (consent.hasSourceAttachment() && !consentDto.getStatus().equalsIgnoreCase("draft"))
                consentDto.setSourceAttachment(consent.getSourceAttachment().getData());
            else if (consentDto.getStatus().equalsIgnoreCase("draft")) {
                String patientID = consentDto.getPatient().getReference().replace("Patient/", "");
                PatientDto patientDto = patientService.getPatientById(patientID);
                log.info("Generating consent PDF");
                byte[] pdfBytes = consentPdfGenerator.generateConsentPdf(consentDto, patientDto, operatedByPatient);
                consentDto.setSourceAttachment(pdfBytes);
            }

        } catch (FHIRException | IOException e) {
            log.error("No Consent document found");
            throw new NoDataFoundException("No Consent document found");
        }

        return consentDto;
    }

    private IQuery getConsentIQuery(Optional<String> patient, Optional<String> practitioner, Optional<String> status, Optional<Boolean> generalDesignation) {
        IQuery iQuery = fhirClient.search().forResource(Consent.class);

        //Query the status.
        if (status.isPresent()) {
            iQuery.where(new TokenClientParam("status").exactly().code("active"));
        } else {
            //query with practitioner.
            practitioner.ifPresent(pr -> {
                if (!getCareTeamIdsFromPractitioner(pr).isEmpty()) {
                    iQuery.where(new ReferenceClientParam("actor").hasAnyOfIds(getCareTeamIdsFromPractitioner(pr)));
                } else {
                    throw new ResourceNotFoundException("Care Team Member cannot be found for the practitioner");
                }
            });

            //query with patient.
            patient.ifPresent(pt -> iQuery.where(new ReferenceClientParam("patient").hasId(pt)));

            //Query with general designation.
            generalDesignation.ifPresent(gd -> {
                if (gd.booleanValue()) {
                    String pseudoOrgId = getPseudoOrganization().getEntry().stream().findFirst().map(pseudoOrgEntry -> {
                        Organization organization = (Organization) pseudoOrgEntry.getResource();
                        String id = organization.getIdElement().getIdPart();
                        return id;
                    }).get();
                    iQuery.where(new ReferenceClientParam("actor").hasId(pseudoOrgId));
                }
            });

            if (!practitioner.isPresent() && !patient.isPresent()) {
                throw new ResourceNotFoundException("Practitioner or Patient is required to find Consents");
            }
        }
        return iQuery;
    }

    @Override
    public void attestConsent(String consentId) {

        Consent consent = fhirClient.read().resource(Consent.class).withId(consentId.trim()).execute();
        consent.setStatus(Consent.ConsentState.ACTIVE);

        ConsentDto consentDto = getConsentsById(consentId);
        consentDto.setStatus("Active");

        String patientID = consentDto.getPatient().getReference().replace("Patient/", "");
        PatientDto patientDto = patientService.getPatientById(patientID);

        try {
            log.info("Updating consent: Generating the attested PDF");
            byte[] pdfBytes = consentPdfGenerator.generateConsentPdf(consentDto, patientDto, operatedByPatient);
            consent.setSource(addAttachment(pdfBytes));

        } catch (IOException e) {
            throw new ConsentPdfGenerationException(e);
        }
        //consent.getSourceAttachment().getData();
        log.info("Updating consent: Saving the consent into the FHIR server.");
        fhirClient.update().resource(consent).execute();
    }

    private Attachment addAttachment(byte[] pdfBytes) {
        Attachment attachment = new Attachment();
        attachment.setContentType(CONTENTTYPE);
        attachment.setData(pdfBytes);
        return attachment;
    }


    @Override
    public PdfDto createConsentPdf(String consentId) {
        ConsentDto consentDto = getConsentsById(consentId);
        String patientID = consentDto.getPatient().getReference().replace("Patient/", "");
        PatientDto patientDto = patientService.getPatientById(patientID);

        try {
            log.info("Generating consent PDF");
            byte[] pdfBytes = consentPdfGenerator.generateConsentPdf(consentDto, patientDto, operatedByPatient);
            return new PdfDto(pdfBytes);

        } catch (IOException e) {
            throw new ConsentPdfGenerationException(e);
        }
    }


    private Consent consentDtoToConsent(Optional<String> consentId, ConsentDto consentDto) {
        Consent consent = new Consent();
        if (consentDto.getPeriod() != null) {
            Period period = new Period();
            period.setStart((consentDto.getPeriod().getStart() != null) ? java.sql.Date.valueOf(consentDto.getPeriod().getStart()) : null);
            period.setEnd((consentDto.getPeriod().getEnd() != null) ? java.sql.Date.valueOf(consentDto.getPeriod().getEnd()) : null);
            consent.setPeriod(period);
        }

        consent.setPatient(FhirDtoUtil.mapReferenceDtoToReference(consentDto.getPatient()));

        if (!consentDto.getCategory().isEmpty() && consentDto.getCategory() != null) {
            List<CodeableConcept> categories = consentDto.getCategory().stream()
                    .map(category -> FhirDtoUtil.convertValuesetDtoToCodeableConcept(category))
                    .collect(Collectors.toList());
            consent.setCategory(categories);
        }

        if (consentDto.getDateTime() != null) {
            consent.setDateTime(java.sql.Date.valueOf(consentDto.getDateTime()));
        } else {
            consent.setDateTime(java.sql.Date.valueOf(LocalDate.now()));
        }

        if (!consentDto.getPurpose().isEmpty() && consentDto.getPurpose() != null) {
            List<Coding> purposes = consentDto.getPurpose().stream().map(purpose -> {
                Coding coding = new Coding();
                coding.setDisplay((purpose.getDisplay() != null && !purpose.getDisplay().isEmpty()) ? purpose.getDisplay() : null)
                        .setCode((purpose.getCode() != null && !purpose.getCode().isEmpty()) ? purpose.getCode() : null)
                        .setSystem((purpose.getSystem() != null && !purpose.getSystem().isEmpty()) ? purpose.getSystem() : null);
                return coding;
            }).collect(Collectors.toList());

            consent.setPurpose(purposes);
        }

        if (consentDto.getStatus() != null) {
            if (consentDto.getStatus() != null) {
                try {
                    consent.setStatus(Consent.ConsentState.fromCode(consentDto.getStatus()));
                } catch (FHIRException e) {
                    throw new ResourceNotFoundException("Invalid consent status found.");
                }
            }
        }

        //Setting identifier
        if (!consentId.isPresent()) {
            Identifier identifier = new Identifier();
            identifier.setValue(UUID.randomUUID().toString());
            identifier.setSystem(fisProperties.getConsent().getIdentifierSystem());
            consent.setIdentifier(identifier);
        } else if (consentDto.getIdentifier() != null) {
            Identifier identifier = new Identifier();
            identifier.setValue(consentDto.getIdentifier().getValue());
            identifier.setSystem(consentDto.getIdentifier().getSystem());
            consent.setIdentifier(identifier);
        }


        List<Consent.ConsentActorComponent> actors = new ArrayList<>();

        //Getting psuedo organization
        Bundle organizationBundle = getPseudoOrganization();

        organizationBundle.getEntry().stream().findAny().ifPresent(entry -> {
            Organization organization = (Organization) entry.getResource();
            ReferenceDto referenceDto = new ReferenceDto();
            referenceDto.setReference("Organization/" + organization.getIdElement().getIdPart());
            referenceDto.setDisplay(PSEUDO_ORGANIZATION_NAME);
            consent.setOrganization(Arrays.asList(FhirDtoUtil.mapReferenceDtoToReference(referenceDto)));

            if (consentDto.isGeneralDesignation()) {
                Consent.ConsentActorComponent fromActor = new Consent.ConsentActorComponent();
                fromActor.setReference(FhirDtoUtil.mapReferenceDtoToReference(referenceDto))
                        .setRole(FhirDtoUtil.convertValuesetDtoToCodeableConcept(FhirDtoUtil.convertCodeToValueSetDto(INFORMANT_CODE, lookUpService.getSecurityRole())));
                actors.add(fromActor);
            }
        });

        if (consentDto.isGeneralDesignation()) {
            //Adding To careTeams
            Bundle careTeamBundle = fhirClient.search().forResource(CareTeam.class)
                    .where(new ReferenceClientParam("subject").hasId(consentDto.getPatient().getReference()))
                    .returnBundle(Bundle.class).execute();

            careTeamBundle.getEntry().forEach(careTeamEntry -> {
                CareTeam careTeam = (CareTeam) careTeamEntry.getResource();
                Consent.ConsentActorComponent toActor = convertCareTeamToActor(careTeam, FhirDtoUtil.convertCodeToValueSetDto(INFORMANT_RECIPIENT_CODE, lookUpService.getSecurityRole()));
                actors.add(toActor);
            });
            consent.setActor(actors);
        } else {
            List<Consent.ConsentActorComponent> fromActors = consentDto.getFromActor().stream().map(fromActor -> {
                Consent.ConsentActorComponent from = new Consent.ConsentActorComponent();
                from.setReference(FhirDtoUtil.mapReferenceDtoToReference(fromActor)).setRole(FhirDtoUtil.convertValuesetDtoToCodeableConcept(FhirDtoUtil.convertCodeToValueSetDto(INFORMANT_CODE, lookUpService.getSecurityRole())));
                return from;
            }).collect(Collectors.toList());

            List<Consent.ConsentActorComponent> toActors = consentDto.getToActor().stream().map(toActor -> {
                Consent.ConsentActorComponent to = new Consent.ConsentActorComponent();
                to.setReference(FhirDtoUtil.mapReferenceDtoToReference(toActor)).setRole(FhirDtoUtil.convertValuesetDtoToCodeableConcept(FhirDtoUtil.convertCodeToValueSetDto(INFORMANT_RECIPIENT_CODE, lookUpService.getSecurityRole())));
                return to;
            }).collect(Collectors.toList());

            //Adding toActors to the fromActors.
            fromActors.addAll(toActors);

            consent.setActor(fromActors);
        }

        return consent;
    }

    private Consent.ConsentActorComponent convertCareTeamToActor(CareTeam careTeam, ValueSetDto securityRoleValueSet) {
        Consent.ConsentActorComponent actor = new Consent.ConsentActorComponent();
        ReferenceDto referenceDto = new ReferenceDto();
        referenceDto.setReference("CareTeam/" + careTeam.getIdElement().getIdPart());
        referenceDto.setDisplay(careTeam.getName());
        actor.setReference(FhirDtoUtil.mapReferenceDtoToReference(referenceDto));
        actor.setRole(FhirDtoUtil.convertValuesetDtoToCodeableConcept(securityRoleValueSet));
        return actor;
    }

    private ReferenceDto convertCareTeamToReferenceDto(CareTeam careTeam) {
        ReferenceDto referenceDto = new ReferenceDto();
        referenceDto.setReference(careTeam.getIdElement().getIdPart());
        referenceDto.setDisplay(careTeam.getName());
        return referenceDto;
    }

    private boolean isDuplicate(ConsentDto consentDto, Optional<String> consentId) {
        //Duplicate Check For General Designation
        if (consentDto.isGeneralDesignation()) {
            Bundle consentBundle = fhirClient.search().forResource(Consent.class).where(new ReferenceClientParam("patient").hasId(consentDto.getPatient().getReference()))
                    .returnBundle(Bundle.class).execute();
            boolean checkFromBundle = consentBundle.getEntry().stream().anyMatch(consentBundleEntry -> {
                Consent consent = (Consent) consentBundleEntry.getResource();
                List<String> fromActor = getReferenceOfCareTeam(consent, INFORMANT_CODE);

                String pseudoOrgRef = getPseudoOrganization().getEntry().stream().findFirst().map(pseudoOrg -> {
                    Organization organization = (Organization) pseudoOrg.getResource();
                    return organization.getIdElement().getIdPart();
                }).get();
                if ((fromActor.size() == 1)) {
                    if (fromActor.stream().findFirst().get().equalsIgnoreCase("Organization/" + pseudoOrgRef)) {
                        if (consentId.isPresent()) {
                            return !(consentId.get().equalsIgnoreCase(consent.getIdElement().getIdPart()));
                        } else {
                            return true;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            });

            return checkFromBundle;
        }
        return false;
    }

    private List<String> getReferenceOfCareTeam(Consent consent, String code) {
        return consent.getActor().stream().filter(actor -> actor.getRole().getCoding().stream()
                .anyMatch(role -> role.getCode().equalsIgnoreCase(code)))
                .map(actor -> actor.getReference().getReference())
                .collect(Collectors.toList());
    }

    private Bundle getPseudoOrganization() {
        return fhirClient.search().forResource(Organization.class)
                .where(new TokenClientParam("identifier").exactly().code(PSEUDO_ORGANIZATION_TAX_ID))
                .where(new StringClientParam("name").matches().value(PSEUDO_ORGANIZATION_NAME))
                .returnBundle(Bundle.class)
                .execute();

    }

    private List<String> getCareTeamIdsFromPractitioner(String practitioner) {
        IQuery careTeamQuery = fhirClient.search().forResource(CareTeam.class)
                .where(new ReferenceClientParam("participant").hasId(practitioner));

        Bundle careTeamBundle = (Bundle) careTeamQuery.returnBundle(Bundle.class).execute();

        List<String> careTeamIds = careTeamBundle.getEntry().stream().map(careTeamBundleEntry -> {
            CareTeam careTeam = (CareTeam) careTeamBundleEntry.getResource();
            return careTeam.getIdElement().getIdPart();
        }).collect(Collectors.toList());

        return careTeamIds;
    }

    private List<String> getCareTeamParticipantIdsFromPatient(Optional<String> patientId) {
        List<String> participantIds = new ArrayList<>();
        if (patientId.isPresent()) {
            Bundle careTeamBundle = fhirClient.search().forResource(CareTeam.class).where(new ReferenceClientParam("patient").hasId(patientId.get()))
                    .returnBundle(Bundle.class)
                    .elementsSubset("participant")
                    .execute();
            participantIds = careTeamBundle.getEntry().stream().flatMap(careTeam -> {
                CareTeam ct = (CareTeam) careTeam.getResource();
                return ct.getParticipant().stream().map(par -> {
                    String references = par.getMember().getReference().split("/")[1];
                    return references;
                });
            }).collect(Collectors.toList());
        }
        return participantIds;
    }

    private AddressDto convertAddressToAddressDto(Address address) {
        AddressDto addressDto = new AddressDto();
        if (address.hasLine()) {
            if (address.hasLine()) {
                address.getLine().stream().findAny().ifPresent(line -> addressDto.setLine1(line.getValue()));
            }
        }

        if (address.hasCity())
            addressDto.setCity(address.getCity());
        if (address.hasCountry())
            addressDto.setCountryCode(address.getCountry());
        if (address.hasPostalCode())
            addressDto.setPostalCode(address.getPostalCode());
        if (address.hasState())
            addressDto.setStateCode(address.getState());
        return addressDto;
    }

    private IdentifierDto covertIdentifierToIdentifierDto(Identifier identifier) {
        IdentifierDto identifierDto = new IdentifierDto();
        identifierDto.setSystem(identifier.hasSystem() ? identifier.getSystem() : null);
        identifierDto.setValue(identifier.hasValue() ? identifier.getValue() : null);
        return identifierDto;
    }

}
