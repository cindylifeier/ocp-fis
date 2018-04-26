package gov.samhsa.ocp.ocpfis.web;

import gov.samhsa.ocp.ocpfis.service.ConsentService;
import gov.samhsa.ocp.ocpfis.service.PatientService;
import gov.samhsa.ocp.ocpfis.service.dto.ConsentDto;
import gov.samhsa.ocp.ocpfis.service.dto.GeneralConsentRelatedFieldDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.PatientDto;
import gov.samhsa.ocp.ocpfis.service.dto.PdfDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import gov.samhsa.ocp.ocpfis.service.pdf.ConsentPdfGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@Slf4j
public class ConsentController {
    @Autowired
    private ConsentService consentService;

    @GetMapping("/consents")
    public PageDto<ConsentDto> getConsents(@RequestParam(value = "patient") Optional<String> patient,
                                           @RequestParam(value = "practitioner") Optional<String> practitioner,
                                           @RequestParam(value = "status") Optional<String> status,
                                           @RequestParam(value = "generalDesignation") Optional<Boolean> generalDesignation,
                                           @RequestParam Optional<Integer> pageNumber,
                                           @RequestParam Optional<Integer> pageSize) {
        return consentService.getConsents(patient, practitioner, status, generalDesignation, pageNumber, pageSize);
    }

    @GetMapping("/consents/{consentId}")
    public ConsentDto getConsentById(@PathVariable String consentId) {
        return consentService.getConsentsById(consentId);
    }

    @GetMapping("/consents/{consentId}/pdf")
    public PdfDto createConsentPdf(@PathVariable String consentId) throws IOException {
        return consentService.createConsentPdf(consentId);
    }

    @PutMapping("/consents/{consentId}/attestation")
    @ResponseStatus(HttpStatus.OK)
    public void attestConsent(@PathVariable String consentId) {
        consentService.attestConsent(consentId);
    }


    @PostMapping("/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public void createConsent(@Valid @RequestBody ConsentDto consentDto) {
        consentService.createConsent(consentDto);
        log.info("Consent successfully created");
    }

    @PutMapping("/consents/{consent}")
    @ResponseStatus(HttpStatus.OK)
    public void updateConsent(@PathVariable String consent, @Valid @RequestBody ConsentDto consentDto){
        consentService.updateConsent(consent, consentDto);
    }

    @GetMapping("/generalConsent/{patient}")
    public GeneralConsentRelatedFieldDto getRelatedFieldForGeneralConsent(@PathVariable String patient){
        return consentService.getGeneralConsentRelatedFields(patient);
    }

    @GetMapping("/actors")
    public PageDto<ReferenceDto> getActors(@RequestParam String name, @RequestParam Optional<List<String>> actorsAlreadyAssigned, @RequestParam Optional<Integer> pageNumber, @RequestParam Optional<Integer> pageSize){
        return consentService.getActors(name,actorsAlreadyAssigned, pageNumber, pageSize);
    }
}
