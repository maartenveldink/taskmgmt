package eu.poc.taskmanagement.api.mapping;

import eu.poc.taskmanagement.api.dto.AssignTaskRequest;
import eu.poc.taskmanagement.api.dto.CancelTaskRequest;
import eu.poc.taskmanagement.api.dto.CreateTaskRequest;
import eu.poc.taskmanagement.api.dto.ReassignTaskRequest;
import eu.poc.taskmanagement.api.dto.RejectTaskRequest;
import eu.poc.taskmanagement.generated.model.AssigneeType;
import eu.poc.taskmanagement.generated.model.TaskStatus;
import eu.poc.taskmanagement.generated.model.TaskType;
import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.CancelTaskCommand;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.ReassignTaskCommand;
import eu.poc.taskmanagement.model.command.RejectTaskCommand;
import eu.poc.taskmanagement.model.command.StartTaskCommand;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class TasksHttpMapper {

    public CreateTaskRequest toInternal(eu.poc.taskmanagement.generated.model.CreateTaskRequest source) {
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

    public AssignTaskRequest toInternal(eu.poc.taskmanagement.generated.model.AssignTaskRequest source) {
        return new AssignTaskRequest(
                source.getAssigneeName(),
                toInternal(source.getAssigneeType())
        );
    }

    public ReassignTaskRequest toInternal(eu.poc.taskmanagement.generated.model.ReassignTaskRequest source) {
        return new ReassignTaskRequest(
                source.getNewAssigneeName(),
                toInternal(source.getNewAssigneeType())
        );
    }

    public CancelTaskRequest toInternal(eu.poc.taskmanagement.generated.model.CancelTaskRequest source) {
        if (source == null) {
            return null;
        }
        return new CancelTaskRequest(source.getReason());
    }

    public RejectTaskRequest toInternal(eu.poc.taskmanagement.generated.model.RejectTaskRequest source) {
        if (source == null) {
            return null;
        }
        return new RejectTaskRequest(source.getReason());
    }

    public CreateTaskCommand toCreateTaskCommand(eu.poc.taskmanagement.generated.model.CreateTaskRequest source,
                                                  String defaultGroup) {
        String group = source.getGroupName() != null && !source.getGroupName().isBlank()
                ? source.getGroupName()
                : defaultGroup;
        eu.poc.taskmanagement.model.TaskType taskType = source.getTaskType() != null
                ? toInternal(source.getTaskType())
                : eu.poc.taskmanagement.model.TaskType.STANDARD;
        List<String> expectedUsers = source.getExpectedExternalUsers() == null
                ? List.of()
                : source.getExpectedExternalUsers().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        return new CreateTaskCommand(
                source.getCorrelationId(),
                source.getTitle(),
                source.getDescription(),
                group,
                toInstant(source.getDeadline()),
                taskType,
                expectedUsers
        );
    }

    public AssignTaskCommand toAssignTaskCommand(String taskId, eu.poc.taskmanagement.generated.model.AssignTaskRequest source) {
        return new AssignTaskCommand(
                taskId,
                source.getAssigneeName(),
                toInternal(source.getAssigneeType())
        );
    }

    public ReassignTaskCommand toReassignTaskCommand(String taskId, eu.poc.taskmanagement.generated.model.ReassignTaskRequest source) {
        return new ReassignTaskCommand(
                taskId,
                source.getNewAssigneeName(),
                toInternal(source.getNewAssigneeType())
        );
    }

    public StartTaskCommand toStartTaskCommand(String taskId) {
        return new StartTaskCommand(taskId);
    }

    public CompleteTaskCommand toCompleteTaskCommand(String taskId) {
        return new CompleteTaskCommand(taskId);
    }

    public CancelTaskCommand toCancelTaskCommand(String taskId, eu.poc.taskmanagement.generated.model.CancelTaskRequest source) {
        return new CancelTaskCommand(taskId, source != null ? source.getReason() : null);
    }

    public RejectTaskCommand toRejectTaskCommand(String taskId, eu.poc.taskmanagement.generated.model.RejectTaskRequest source) {
        return new RejectTaskCommand(taskId, source != null ? source.getReason() : null);
    }

    public eu.poc.taskmanagement.model.command.AssigneeType toInternal(AssigneeType source) {
        if (source == null) {
            return null;
        }
        return eu.poc.taskmanagement.model.command.AssigneeType.valueOf(source.toString());
    }

    public eu.poc.taskmanagement.model.TaskStatus toInternal(TaskStatus source) {
        if (source == null) {
            return null;
        }
        return eu.poc.taskmanagement.model.TaskStatus.valueOf(source.toString());
    }

    public TaskStatus toGenerated(eu.poc.taskmanagement.model.TaskStatus source) {
        if (source == null) {
            return null;
        }
        return TaskStatus.fromValue(source.name());
    }

    public eu.poc.taskmanagement.model.TaskType toInternal(TaskType source) {
        if (source == null) {
            return null;
        }
        return eu.poc.taskmanagement.model.TaskType.valueOf(source.toString());
    }

    public TaskType toGenerated(eu.poc.taskmanagement.model.TaskType source) {
        if (source == null) {
            return null;
        }
        return TaskType.fromValue(source.name());
    }

    public eu.poc.taskmanagement.generated.model.TaskView toGenerated(eu.poc.taskmanagement.projection.tasks.TaskView source) {
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

    public List<eu.poc.taskmanagement.generated.model.TaskView> toGeneratedTaskViews(
            List<eu.poc.taskmanagement.projection.tasks.TaskView> source) {
        return source.stream().map(this::toGenerated).toList();
    }

    public eu.poc.taskmanagement.generated.model.AuditTrailEntry toGenerated(
            eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry source) {
        return new eu.poc.taskmanagement.generated.model.AuditTrailEntry()
                .id(source.id)
                .taskId(source.taskId)
                .eventTimestamp(toOffsetDateTime(source.eventTimestamp))
                .eventType(source.eventType)
                .payload(source.payload)
                .recordedAt(toOffsetDateTime(source.recordedAt));
    }

    public List<eu.poc.taskmanagement.generated.model.AuditTrailEntry> toGeneratedAuditEntries(
            List<eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry> source) {
        return source.stream().map(this::toGenerated).toList();
    }

    public Instant toInstant(OffsetDateTime source) {
        if (source == null) {
            return null;
        }
        return source.toInstant();
    }

    public OffsetDateTime toOffsetDateTime(Instant source) {
        if (source == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(source, ZoneOffset.UTC);
    }
}
