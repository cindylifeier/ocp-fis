package gov.samhsa.ocp.ocpfis.service;

import gov.samhsa.ocp.ocpfis.service.dto.PractitionerDto;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PractitionerService {
    List<PractitionerDto> getAllPractitioners(Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size);
    Set<PractitionerDto> searchPractitioners(String searchValue, Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size);
}
