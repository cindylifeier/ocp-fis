package gov.samhsa.ocp.ocpfis.service.mapping;

import gov.samhsa.ocp.ocpfis.constants.AppointmentConstants;
import gov.samhsa.ocp.ocpfis.service.dto.AppointmentDto;
import gov.samhsa.ocp.ocpfis.service.dto.AppointmentParticipantDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.util.DateUtil;
import gov.samhsa.ocp.ocpfis.util.FhirDtoUtil;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.exceptions.FHIRException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class AppointmentToAppointmentDtoConverter {

    public static AppointmentDto map(Appointment appointment, Optional<String> requesterReference) {

        AppointmentDto appointmentDto = new AppointmentDto();

        appointmentDto.setLogicalId(appointment.getIdElement().getIdPart());

        if (appointment.hasStatus()) {
            appointmentDto.setStatusCode(appointment.getStatus().toCode());
        }

        if (appointment.hasAppointmentType()) {
            ValueSetDto type = FhirDtoUtil.convertCodeableConceptToValueSetDto(appointment.getAppointmentType());
            appointmentDto.setTypeCode(type.getCode());
            appointmentDto.setTypeSystem(type.getSystem());
            appointmentDto.setTypeDisplay(type.getDisplay());
        }

        if (appointment.hasDescription()) {
            appointmentDto.setDescription(appointment.getDescription());
        }

        if (appointment.hasParticipant()) {
            List<AppointmentParticipantDto> participantDtos = FhirDtoUtil.convertAppointmentParticipantListToAppointmentParticipantDtoList(appointment.getParticipant());
            appointmentDto.setParticipant(participantDtos);

            if (requesterReference.isPresent()) {
                String reference = requesterReference.get();
                participantDtos.forEach(
                        participant -> {
                            if (participant.getActorReference().trim().equalsIgnoreCase(reference.trim())) {
                                appointmentDto.setRequesterParticipationStatusCode(participant.getParticipationStatusCode());
                            }
                            if (participant.getActorReference().trim().equalsIgnoreCase(reference.trim()) &&
                                    participant.getParticipationTypeCode().equalsIgnoreCase(AppointmentConstants.AUTHOR_PARTICIPANT_TYPE_CODE)) {
                                appointmentDto.setCanEdit(true);
                                if (!appointment.getStatus().toCode().equalsIgnoreCase(AppointmentConstants.CANCELLED_APPOINTMENT_STATUS)) {
                                    appointmentDto.setCanCancel(true);
                                }
                            } else if (appointment.getStatus().toCode().equalsIgnoreCase(AppointmentConstants.PROPOSED_APPOINTMENT_STATUS) ||
                                    appointment.getStatus().toCode().equalsIgnoreCase(AppointmentConstants.PENDING_APPOINTMENT_STATUS) ||
                                    appointment.getStatus().toCode().equalsIgnoreCase(AppointmentConstants.BOOKED_APPOINTMENT_STATUS)) {
                                if (participant.getActorReference().trim().equalsIgnoreCase(reference.trim()) && participant.getParticipationStatusCode().equalsIgnoreCase(AppointmentConstants.NEEDS_ACTION_PARTICIPATION_STATUS)) {
                                    appointmentDto.setCanAccept(true);
                                    appointmentDto.setCanDecline(true);
                                    appointmentDto.setCanTentativelyAccept(true);
                                } else if (participant.getActorReference().trim().equalsIgnoreCase(reference.trim()) && participant.getParticipationStatusCode().equalsIgnoreCase(AppointmentConstants.ACCEPTED_PARTICIPATION_STATUS)) {
                                    appointmentDto.setCanDecline(true);
                                    appointmentDto.setCanTentativelyAccept(true);
                                } else if (participant.getActorReference().trim().equalsIgnoreCase(reference.trim()) && participant.getParticipationStatusCode().equalsIgnoreCase(AppointmentConstants.DECLINED_PARTICIPATION_STATUS)) {
                                    appointmentDto.setCanAccept(true);
                                    appointmentDto.setCanTentativelyAccept(true);
                                } else if (participant.getActorReference().trim().equalsIgnoreCase(reference.trim()) && participant.getParticipationStatusCode().equalsIgnoreCase(AppointmentConstants.TENTATIVE_PARTICIPATION_STATUS)) {
                                    appointmentDto.setCanAccept(true);
                                    appointmentDto.setCanDecline(true);
                                }
                            }
                        }
                );
            }
        }

        if (!appointmentDto.getParticipant().isEmpty()) {
            List<AppointmentParticipantDto> patientActors = appointmentDto.getParticipant().stream()
                    .filter(participant -> participant.getActorReference() != null && participant.getActorReference().toUpperCase().contains(AppointmentConstants.PATIENT_ACTOR_REFERENCE.toUpperCase()))
                    .collect(toList());
            if (!patientActors.isEmpty()) {
                appointmentDto.setPatientName(patientActors.get(0).getActorName());
                String resourceId = patientActors.get(0).getActorReference().trim().split("/")[1];
                appointmentDto.setPatientId(resourceId);
            }

            List<AppointmentParticipantDto> creators = appointmentDto.getParticipant().stream()
                    .filter(participant -> participant.getActorReference() != null && !participant.getActorReference().isEmpty() && participant.getActorName() != null && !participant.getActorName().isEmpty() && participant.getParticipationTypeCode().equalsIgnoreCase(AppointmentConstants.AUTHOR_PARTICIPANT_TYPE_CODE))
                    .collect(toList());

            if (creators != null && !creators.isEmpty()) {
                appointmentDto.setCreatorName(creators.get(0).getActorName().trim());
                appointmentDto.setCreatorReference(creators.get(0).getActorReference().trim());
                appointmentDto.setCreatorRequired(creators.get(0).getParticipantRequiredCode());
                try {
                    appointmentDto.setCreatorRequiredDisplay(Optional.of(Appointment.ParticipantRequired.fromCode(creators.get(0).getParticipantRequiredCode()).getDisplay()));
                } catch (FHIRException e) {
                    e.printStackTrace();
                }
            }

            List<String> participantName = appointmentDto.getParticipant().stream().map(AppointmentParticipantDto::getActorName).collect(toList());
            appointmentDto.setParticipantName(participantName);
        }

        String duration = "";

        if (appointment.hasStart()) {
            appointmentDto.setStart(DateUtil.convertUTCDateToLocalDateTime(appointment.getStart()));
            DateTimeFormatter startFormatterDate = DateTimeFormatter.ofPattern(AppointmentConstants.DATE_TIME_FORMATTER_PATTERN_DATE);
            String formattedDate = appointmentDto.getStart().format(startFormatterDate);
            appointmentDto.setAppointmentDate(formattedDate);

            duration = duration + DateUtil.convertLocalDateTimeToHumanReadableFormat(appointmentDto.getStart());
        }

        if (appointment.hasEnd()) {
            appointmentDto.setEnd(DateUtil.convertUTCDateToLocalDateTime(appointment.getEnd()));

            duration = duration + " - " + DateUtil.convertLocalDateTimeToHumanReadableFormat(appointmentDto.getEnd());
        }

        if (appointment.hasCreated()) {
            appointmentDto.setCreated(DateUtil.convertUTCDateToLocalDateTime(appointment.getCreated()));
        }
        appointmentDto.setAppointmentDuration(duration);

        return appointmentDto;
    }

}
