package gov.samhsa.ocp.ocpfis.util;

import gov.samhsa.ocp.ocpfis.domain.LookupPathUrls;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.service.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.ValueSet;

import java.util.Comparator;
import java.util.List;

@Slf4j
public class LookUpUtil {

    public static boolean isValueSetResponseValid(ValueSet response, String type) {
        boolean isValid = true;
        if (type.equalsIgnoreCase(LookupPathUrls.US_STATE.getType())
                || type.equalsIgnoreCase(LookupPathUrls.HEALTHCARE_SERVICE_SPECIALITY_2.getType())) {
            if (response == null || response.getCompose() == null ||
                    response.getCompose().getInclude() == null ||
                    response.getCompose().getInclude().isEmpty() ||
                    response.getCompose().getInclude().get(0).getConcept() == null ||
                    response.getCompose().getInclude().get(0).getConcept().isEmpty()) {
                isValid = false;
            }
        } else {
            if (response == null ||
                    response.getExpansion() == null ||
                    response.getExpansion().getContains() == null ||
                    response.getExpansion().getContains().isEmpty()) {
                isValid = false;
            }
        }
        return isValid;
    }

    public static boolean isValueSetAvailableInServer(ValueSet response, String type) {
        return isValidResponseOrThrowException(response, type, true);
    }

    public static boolean isValidResponseOrThrowException(ValueSet response, String type, boolean throwException) {
        boolean isValid = isValueSetResponseValid(response, type);
        if (!isValid && throwException) {
            log.error("Query was successful, but found no " + type + " codes in the configured FHIR server");
            throw new ResourceNotFoundException("Query was successful, but found no " + type + " codes in the configured FHIR server");
        }
        return isValid;
    }

    public static ValueSetDto convertConceptReferenceToValueSetDto(ValueSet.ConceptReferenceComponent conceptReferenceComponent, String codingSystemUrl) {
        ValueSetDto valueSetDto = new ValueSetDto();
        valueSetDto.setCode(conceptReferenceComponent.getCode());
        valueSetDto.setDisplay(conceptReferenceComponent.getDisplay());
        valueSetDto.setSystem(codingSystemUrl);
        return valueSetDto;
    }

    public static ValueSetDto convertExpansionComponentToValueSetDto(ValueSet.ValueSetExpansionContainsComponent expansionComponent) {
        ValueSetDto valueSetDto = new ValueSetDto();
        valueSetDto.setSystem(expansionComponent.getSystem());
        valueSetDto.setCode(expansionComponent.getCode());
        valueSetDto.setDisplay(expansionComponent.getDisplay());
        return valueSetDto;
    }

    public static void sortValueSets(List<ValueSetDto> valueSetList){
        if(valueSetList != null && !valueSetList.isEmpty()){
            valueSetList.sort(Comparator.comparing(v -> v.getDisplay() != null? v.getDisplay() : v.getCode()));
        }
    }

}
