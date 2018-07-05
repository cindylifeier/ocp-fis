package gov.samhsa.ocp.ocpfis.util;

import gov.samhsa.ocp.ocpfis.service.dto.ActivityReferenceDto;
import gov.samhsa.ocp.ocpfis.service.dto.AddressDto;
import gov.samhsa.ocp.ocpfis.service.dto.AppointmentParticipantDto;
import gov.samhsa.ocp.ocpfis.service.dto.NameDto;
import gov.samhsa.ocp.ocpfis.service.dto.PractitionerDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import gov.samhsa.ocp.ocpfis.service.dto.TelecomDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import org.hl7.fhir.dstu3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FhirDtoUtil {


    public static String getIdFromReferenceDto(ReferenceDto dto, ResourceType resourceType) {
        return dto.getReference().replace(resourceType + "/", "");
    }

    public static String getIdFromParticipantReferenceDto(ReferenceDto dto) {
        return dto.getReference().replace(ResourceType.Practitioner + "/", "")
                .replace(ResourceType.Patient + "/", "")
                .replace(ResourceType.RelatedPerson + "/", "")
                .replace(ResourceType.Organization + "/", "");
    }


    public static ActivityReferenceDto mapActivityDefinitionToActivityReferenceDto(ActivityDefinition activityDefintion) {
        ActivityReferenceDto activityReferenceDto=new ActivityReferenceDto();
        activityReferenceDto.setReference(ResourceType.ActivityDefinition + "/" + activityDefintion.getIdElement().getIdPart());
        activityReferenceDto.setDisplay(activityDefintion.getName());
        activityReferenceDto.setTitle(Optional.ofNullable(activityDefintion.getTitle()));
        return activityReferenceDto;
    }

    public static ReferenceDto mapPatientToReferenceDto(Patient patient) {
        ReferenceDto referenceDto = new ReferenceDto();
        referenceDto.setReference(ResourceType.Patient + "/" + patient.getIdElement().getIdPart());
        List<HumanName> names = patient.getName();
        names.stream().findFirst().ifPresent(it -> it.getGiven().stream().findFirst().ifPresent(givenName -> {
            referenceDto.setDisplay(givenName.toString() + it.getGiven());
        }));
        return referenceDto;
    }

    public static ReferenceDto mapPractitionerToReferenceDto(Practitioner practitioner) {
        ReferenceDto referenceDto = new ReferenceDto();
        referenceDto.setReference(ResourceType.Practitioner + "/" + practitioner.getIdElement().getIdPart());
        List<HumanName> names = practitioner.getName();
        names.stream().findFirst().ifPresent(it -> it.getGiven().stream().findFirst().ifPresent(givenName -> {
            referenceDto.setDisplay(givenName.toString() + it.getGiven());
        }));
        return referenceDto;
    }

    public static ReferenceDto mapPractitionerDtoToReferenceDto(PractitionerDto practitionerDto) {
        ReferenceDto referenceDto = new ReferenceDto();

        referenceDto.setReference(ResourceType.Practitioner + "/" + practitionerDto.getLogicalId());
        List<NameDto> names = practitionerDto.getName();
        names.stream().findFirst().ifPresent(it -> {
            String name = it.getFirstName() + " " + it.getLastName();
            referenceDto.setDisplay(name);
        });

        return referenceDto;
    }

    public static ValueSetDto convertCodeToValueSetDto(String code, List<ValueSetDto> valueSetDtos) {
        return valueSetDtos.stream().filter(lookup -> code.equalsIgnoreCase(lookup.getCode())).map(valueSet -> {
            ValueSetDto valueSetDto = new ValueSetDto();
            valueSetDto.setCode(valueSet.getCode());
            valueSetDto.setDisplay(valueSet.getDisplay());
            valueSetDto.setSystem(valueSet.getSystem());
            return valueSetDto;
        }).findFirst().orElse(null);
    }

    public static ValueSetDto convertDisplayCodeToValueSetDto(String code, List<ValueSetDto> valueSetDtos) {
        return valueSetDtos.stream().filter(lookup -> code.equalsIgnoreCase(lookup.getDisplay().replaceAll("\\s", "").toUpperCase())).map(valueSet -> {
            ValueSetDto valueSetDto = new ValueSetDto();
            valueSetDto.setCode(valueSet.getCode());
            valueSetDto.setDisplay(valueSet.getDisplay());
            valueSetDto.setSystem(valueSet.getSystem());
            return valueSetDto;
        }).findFirst().orElse(null);
    }

    public static ReferenceDto mapOrganizationToReferenceDto(Organization organization) {
        ReferenceDto referenceDto = new ReferenceDto();

        referenceDto.setReference(ResourceType.Organization + "/" + organization.getIdElement().getIdPart());
        referenceDto.setDisplay(organization.getName());

        return referenceDto;
    }

    public static Reference mapReferenceDtoToReference(ReferenceDto referenceDto) {
        Reference reference = new Reference();
        reference.setDisplay(referenceDto.getDisplay());
        reference.setReference(referenceDto.getReference());
        return reference;
    }

    public static ReferenceDto convertReferenceToReferenceDto(Reference reference) {
        ReferenceDto referenceDto = new ReferenceDto();
        referenceDto.setDisplay(reference.getDisplay());
        referenceDto.setReference(reference.getReference());
        return referenceDto;
    }


    public static ReferenceDto mapTaskToReferenceDto(Task task) {
        ReferenceDto referenceDto = new ReferenceDto();
        referenceDto.setReference(ResourceType.Task + "/" + task.getIdElement().getIdPart());
        referenceDto.setDisplay(task.getDescription() != null ? task.getDescription() : referenceDto.getReference());
        return referenceDto;
    }

    public static ValueSetDto convertCodeableConceptToValueSetDto(CodeableConcept source) {
        ValueSetDto valueSetDto = new ValueSetDto();
        if (source != null) {
            if (source.getCodingFirstRep().getDisplay() != null)
                valueSetDto.setDisplay(source.getCodingFirstRep().getDisplay());
            if (source.getCodingFirstRep().getSystem() != null)
                valueSetDto.setSystem(source.getCodingFirstRep().getSystem());
            if (source.getCodingFirstRep().getCode() != null)
                valueSetDto.setCode(source.getCodingFirstRep().getCode());
        }
        return valueSetDto;
    }

    public static ValueSetDto convertCodeableConceptListToValuesetDto(List<CodeableConcept> source) {
        ValueSetDto valueSetDto = new ValueSetDto();

        if (!source.isEmpty()) {
            int sourceSize = source.get(0).getCoding().size();
            if (sourceSize > 0) {
                source.get(0).getCoding().stream().findAny().ifPresent(coding -> {
                    valueSetDto.setSystem(coding.getSystem());
                    valueSetDto.setDisplay(coding.getDisplay());
                    valueSetDto.setCode(coding.getCode());
                });
            }
        }
        return valueSetDto;

    }

    public static CodeableConcept convertValuesetDtoToCodeableConcept(ValueSetDto valueSetDto) {
        CodeableConcept codeableConcept = new CodeableConcept();
        if (valueSetDto != null) {
            Coding coding = FhirUtil.getCoding(valueSetDto.getCode(), valueSetDto.getDisplay(), valueSetDto.getSystem());
            codeableConcept.addCoding(coding);
        }
        return codeableConcept;
    }

    public static Optional<String> getDisplayForCode(String code, List<ValueSetDto> lookupValueSets) {
        Optional<String> lookupDisplay = Optional.empty();

        lookupDisplay = lookupValueSets.stream()
                .filter(lookupValue -> code.equalsIgnoreCase(lookupValue.getCode()))
                .map(ValueSetDto::getDisplay).findFirst();

        return lookupDisplay;
    }

    public static List<AppointmentParticipantDto> convertAppointmentParticipantListToAppointmentParticipantDtoList(List<Appointment.AppointmentParticipantComponent> source) {
        List<AppointmentParticipantDto> participants = new ArrayList<>();

        if (source != null && source.size() > 0) {
            source.forEach(member -> {
                AppointmentParticipantDto participantDto = new AppointmentParticipantDto();
                participantDto.setActorName(member.getActor().getDisplay());
                participantDto.setActorReference(member.getActor().getReference());
                if (member.getRequired() != null) {
                    participantDto.setParticipantRequiredCode(member.getRequired().toCode());
                }
                if (member.getStatus() != null) {
                    participantDto.setParticipationStatusCode(member.getStatus().toCode());
                }
                if (member.getType() != null && !member.getType().isEmpty() && !member.getType().get(0).getCoding().isEmpty()) {
                    participantDto.setParticipationTypeCode(member.getType().get(0).getCoding().get(0).getCode());
                    if(member.getType().get(0).getCoding().get(0).getDisplay() != null && !member.getType().get(0).getCoding().get(0).getDisplay().isEmpty()){
                        participantDto.setParticipationTypeDisplay(member.getType().get(0).getCoding().get(0).getDisplay());
                    }
                    if(member.getType().get(0).getCoding().get(0).getSystem() != null && !member.getType().get(0).getCoding().get(0).getSystem().isEmpty()){
                        participantDto.setParticipationTypeSystem(member.getType().get(0).getCoding().get(0).getSystem());
                    }
                }
                participants.add(participantDto);
            });
        }
        return participants;
    }

    public static List<TelecomDto> convertTelecomListToTelecomDtoList(List<ContactPoint> source) {
        List<TelecomDto> telecomDtoList = new ArrayList<>();

        if (source != null && source.size() > 0) {

            for (ContactPoint telecom : source) {
                TelecomDto telecomDto = new TelecomDto();
                telecomDto.setValue(Optional.ofNullable(telecom.getValue()));
                if (telecom.getSystem() != null)
                    telecomDto.setSystem(Optional.ofNullable(telecom.getSystem().toCode()));
                if (telecom.getUse() != null)
                    telecomDto.setUse(Optional.ofNullable(telecom.getUse().toCode()));
                telecomDtoList.add(telecomDto);
            }
        }
        return telecomDtoList;
    }

    public static List<AddressDto> convertAddressListToAddressDtoList(List<Address> source) {
        List<AddressDto> addressDtos = new ArrayList<>();

        if (source != null && source.size() > 0) {

            for (Address address : source) {

                addressDtos.add(AddressDto.builder().line1(
                        address.getLine().size() > 0 ?
                                address.getLine().get(0).toString()
                                : "")
                        .line2(address.getLine().size() > 1 ?
                                address.getLine().get(1).toString()
                                : "")
                        .city(address.getCity())
                        .stateCode(address.getState())
                        .countryCode(address.getCountry())
                        .postalCode(address.getPostalCode())
                        .build());
            }
        }
        return addressDtos;
    }

}
