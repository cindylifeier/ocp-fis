package gov.samhsa.ocp.ocpfis.service;

import gov.samhsa.ocp.ocpfis.service.dto.ActivityDefinitionDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;

import java.util.List;
import java.util.Optional;

public interface ActivityDefinitionService {

    PageDto<ActivityDefinitionDto> getAllActivityDefinitionsByOrganization(String organizationResourceId, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> page, Optional<Integer> size);

    void createActivityDefinition(ActivityDefinitionDto activityDefinitionDto,String organizationId);

    List<ReferenceDto> getActivityDefinitionsByPractitioner(String practitioner);

    ActivityDefinitionDto getActivityDefinitionById(String id);

    void deleteResource(String resource,String value);
}
