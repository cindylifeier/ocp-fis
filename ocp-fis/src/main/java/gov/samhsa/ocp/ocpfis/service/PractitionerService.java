package gov.samhsa.ocp.ocpfis.service;

import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.PractitionerDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import gov.samhsa.ocp.ocpfis.web.PractitionerController;

import java.util.List;
import java.util.Optional;

public interface
PractitionerService {
    PageDto<PractitionerDto> getAllPractitioners(Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size);

    PageDto<PractitionerDto> searchPractitioners(PractitionerController.SearchType searchType, String searchValue, Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size);

    void createPractitioner(PractitionerDto practitionerDto);

    void updatePractitioner(String practitionerId, PractitionerDto practitionerDto);

    PractitionerDto getPractitioner(String practitionerId);

    List<ReferenceDto> getPractitionersInOrganizationByPractitionerId(String practitioner);

    List<ReferenceDto> getPractitionersByRole(String role);
}
