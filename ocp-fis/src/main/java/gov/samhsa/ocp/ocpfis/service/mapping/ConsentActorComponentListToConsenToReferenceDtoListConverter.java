package gov.samhsa.ocp.ocpfis.service.mapping;


import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import org.hl7.fhir.dstu3.model.Consent;
import org.modelmapper.AbstractConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component

public class ConsentActorComponentListToConsenToReferenceDtoListConverter extends AbstractConverter<List<Consent.ConsentActorComponent>, List<ReferenceDto>> {
    @Override
    protected List<ReferenceDto> convert(List<Consent.ConsentActorComponent> source) {
        final String code = "IRCP";
        List<ReferenceDto> referenceDtos = new ArrayList<>();

        if (source != null && source.size() > 0) {
            ReferenceDto referenceDto = new ReferenceDto();

            for (Consent.ConsentActorComponent consentActor : source) {
                if (consentActor.hasRole() && consentActor.getRole().hasCoding())
                    if (consentActor.getRole().getCoding().get(0).getCode().equalsIgnoreCase(code)) {
                        referenceDtos.add(
                                referenceDto.builder()
                                        .reference(consentActor.getReference().getReference())
                                        .display(consentActor.getReference().getDisplay())
                                        .build()
                        );
                    }
            }
        }
        return referenceDtos;
    }
}
