package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.api.dto.AssignTaskRequest;
import eu.poc.taskmanagement.api.dto.CancelTaskRequest;
import eu.poc.taskmanagement.api.dto.CreateTaskRequest;
import eu.poc.taskmanagement.api.dto.ReassignTaskRequest;
import eu.poc.taskmanagement.api.dto.RejectTaskRequest;
import eu.poc.taskmanagement.generated.model.AssigneeType;
import eu.poc.taskmanagement.generated.model.TaskStatus;
import eu.poc.taskmanagement.generated.model.TaskType;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
class TaskApiMapper {

    CreateTaskRequest toInternal(eu.poc.taskmanagement.generated.model.CreateTaskRequest source) {
        return new CreateTaskRequest(
                source.getCorrelationId(),
                source.getTitle(),
                source.getDescription(),
                source.getGroupName(),
                toInstant(source.getDeadline()),
                toInternal(source.getTaskType()),
                source.getExpectedExternalUsers()
        );
    }

    AssignTaskRequest toInternal(eu.poc.taskmanagement.generated.model.AssignTaskRequest source) {
        return new AssignTaskRequest(
                source.getAssigneeName(),
                toInternal(source.getAssigneeType())
        );
    }

    ReassignTaskRequest toInternal(eu.poc.taskmanagement.generated.model.ReassignTaskRequest source) {
        return new ReassignTaskRequest(
                source.getNewAssigneeName(),
                toInternal(source.getNewAssigneeType())
        );
    }

    CancelTaskRequest toInternal(eu.poc.taskmanagement.generated.model.CancelTaskRequest source) {
        if (source == null) {
            return null;
        }
        return new CancelTaskRequest(source.getReason());
    }

    RejectTaskRequest toInternal(eu.poc.taskmanagement.generated.model.RejectTaskRequest source) {
        if (source == null) {
            return null;
        }
        return new RejectTaskRequest(source.getReason());
    }

    eu.poc.taskmanagement.model.command.AssigneeType toInternal(AssigneeType source) {
        if (source == null) {
            return null;
        }
        return eu.poc.taskmanagement.model.command.AssigneeType.valueOf(source.toString());
    }

    eu.poc.taskmanagement.model.TaskStatus toInternal(TaskStatus source) {
        if (source == null) {
            return null;
        }
        return eu.poc.taskmanagement.model.TaskStatus.valueOf(source.toString());
    }

    TaskStatus toGenerated(eu.poc.taskmanagement.model.TaskStatus source) {
        if (source == null) {
            return null;
        }
        return TaskStatus.fromValue(source.name());
    }

    eu.poc.taskmanagement.model.TaskType toInternal(TaskType source) {
        if (source == null) {
            return null;
        }
        return eu.poc.taskmanagement.model.TaskType.valueOf(source.toString());
    }

    TaskType toGenerated(eu.poc.taskmanagement.model.TaskType source) {
        if (source == null) {
            return null;
        }
        return TaskType.fromValue(source.name());
    }

    eu.poc.taskmanagement.generated.model.TaskView toGenerated(eu.poc.taskmanagement.projection.tasks.TaskView source) {
        return new eu.poc.taskmanagement.generated.model.TaskView()
                .taskId(source.taskId)
                .title(source.title)
                .description(source.description)
                .assignedGroup(source.assignedGroup)
                .assignedUser(source.assignedUser)
                .status(toGenerated(source.status))
                .taskType(toGenerated(source.taskType))
                .expectedExternalUsers(source.getExpectedExternalUsers())
                .deadline(toOffsetDateTime(source.deadline))
                .createdAt(toOffsetDateTime(source.createdAt))
                .updatedAt(toOffsetDateTime(source.updatedAt));
    }

    List<eu.poc.taskmanagement.generated.model.TaskView> toGeneratedTaskViews(
            List<eu.poc.taskmanagement.projection.tasks.TaskView> source) {
        return source.stream().map(this::toGenerated).toList();
    }

    eu.poc.taskmanagement.generated.model.AuditTrailEntry toGenerated(
            eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry source) {
        return new eu.poc.taskmanagement.generated.model.AuditTrailEntry()
                .id(source.id)
                .taskId(source.taskId)
                .eventTimestamp(toOffsetDateTime(source.eventTimestamp))
                .eventType(source.eventType)
                .payload(source.payload)
                .recordedAt(toOffsetDateTime(source.recordedAt));
    }

    List<eu.poc.taskmanagement.generated.model.AuditTrailEntry> toGeneratedAuditEntries(
            List<eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry> source) {
        return source.stream().map(this::toGenerated).toList();
    }

    Instant toInstant(OffsetDateTime source) {
        if (source == null) {
            return null;
        }
        return source.toInstant();
    }

    OffsetDateTime toOffsetDateTime(Instant source) {
        if (source == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(source, ZoneOffset.UTC);
    }
}
