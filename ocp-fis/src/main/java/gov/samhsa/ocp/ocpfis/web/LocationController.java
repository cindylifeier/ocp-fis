package gov.samhsa.ocp.ocpfis.web;

import gov.samhsa.ocp.ocpfis.service.LocationService;
import gov.samhsa.ocp.ocpfis.service.dto.LocationDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * @param statusList
     * @param searchKey
     * @param searchValue
     * @param pageNumber
     * @param pageSize
     * @return
     */
    @GetMapping("/locations")
    public PageDto<LocationDto> getAllLocations(@RequestParam(value = "statusList") Optional<List<String>> statusList,
                                                @RequestParam(value = "searchKey") Optional<String> searchKey,
                                                @RequestParam(value = "searchValue") Optional<String> searchValue,
                                                @RequestParam(value = "pageNumber") Optional<Integer> pageNumber,
                                                @RequestParam(value = "pageSize") Optional<Integer> pageSize) {
        return locationService.getAllLocations(statusList, searchKey, searchValue, pageNumber, pageSize);
    }

    /**
     * Gets all locations(all levels) that are managed under a given Organization Id
     *
     * @param organizationId
     * @param statusList
     * @param searchKey
     * @param searchValue
     * @param pageNumber
     * @param pageSize
     * @return
     */
    @GetMapping("/organizations/{organizationId}/locations")
    public PageDto<LocationDto> getLocationsByOrganization(@PathVariable String organizationId,
                                                           @RequestParam(value = "statusList") Optional<List<String>> statusList,
                                                           @RequestParam(value = "searchKey") Optional<String> searchKey,
                                                           @RequestParam(value = "searchValue") Optional<String> searchValue,
                                                           @RequestParam(value = "pageNumber") Optional<Integer> pageNumber,
                                                           @RequestParam(value = "pageSize") Optional<Integer> pageSize) {
        return locationService.getLocationsByOrganization(organizationId, statusList, searchKey, searchValue, pageNumber, pageSize);
    }

    /**
     * @param locationId
     * @return
     */
    @GetMapping("/locations/{locationId}")
    public LocationDto getLocation(@PathVariable String locationId) {
        return locationService.getLocation(locationId);
    }

    /**
     * Gets level 1 child location for a given Location Id
     *
     * @param locationId
     * @return
     */
    @GetMapping("/locations/{locationId}/child-location")
    public LocationDto getChildLocation(@PathVariable String locationId) {
        return locationService.getChildLocation(locationId);
    }

    @PostMapping("/organization/{organizationId}/location")
    @ResponseStatus(HttpStatus.CREATED)
    public void createLocation(@PathVariable String organizationId,
                               @Valid @RequestBody LocationDto locationDto) {
        locationService.createLocation(organizationId, locationDto);

    }

    @PostMapping("/organization/{organizationId}/location/{locationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void createLocation(@PathVariable String organizationId,
                               @PathVariable String locationId,
                               @Valid @RequestBody LocationDto locationDto) {
        locationService.updateLocation(organizationId, locationId, locationDto);

    }
}
