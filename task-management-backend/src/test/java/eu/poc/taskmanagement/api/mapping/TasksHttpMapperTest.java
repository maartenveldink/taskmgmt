package eu.poc.taskmanagement.api.mapping;

import eu.poc.taskmanagement.generated.model.AssigneeType;
import eu.poc.taskmanagement.generated.model.AssignTaskRequest;
import eu.poc.taskmanagement.generated.model.CreateTaskRequest;
import eu.poc.taskmanagement.generated.model.TaskStatus;
import eu.poc.taskmanagement.generated.model.TaskType;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TasksHttpMapperTest {

    private final TasksHttpMapper mapper = new TasksHttpMapper();

    @Test
    void mapsGeneratedCreateRequestToCreateCommand() {
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        CreateTaskRequest request = new CreateTaskRequest()
                .correlationId("corr-1")
                .title("title")
                .description("desc")
                .groupName("group-a")
                .taskType(TaskType.USER_PROVISIONING)
                .expectedExternalUsers(List.of("alice", "bob"))
                .deadline(deadline);

        var mapped = mapper.toCreateTaskCommand(request, "fallback-group");

        assertEquals("corr-1", mapped.taskId());
        assertEquals("title", mapped.title());
        assertEquals("desc", mapped.description());
        assertEquals("group-a", mapped.groupName());
        assertEquals(deadline.toInstant(), mapped.deadline());
        assertEquals(eu.poc.taskmanagement.model.TaskType.USER_PROVISIONING, mapped.taskType());
        assertEquals(List.of("alice", "bob"), mapped.expectedExternalUsers());
    }

    @Test
    void mapsGeneratedAssignRequestToAssignCommand() {
        AssignTaskRequest request = new AssignTaskRequest()
                .assigneeName("alice")
                .assigneeType(AssigneeType.USER);

        var mapped = mapper.toAssignTaskCommand("task-1", request);

        assertEquals("task-1", mapped.taskId());
        assertEquals("alice", mapped.assigneeName());
        assertEquals(eu.poc.taskmanagement.model.command.AssigneeType.USER, mapped.assigneeType());
    }

    @Test
    void mapsProjectionTaskViewToGeneratedTaskView() {
        TaskView source = new TaskView();
        source.taskId = "task-1";
        source.title = "title";
        source.description = "desc";
        source.assignedGroup = "group-a";
        source.assignedUser = "alice";
        source.status = eu.poc.taskmanagement.model.TaskStatus.IN_PROGRESS;
        source.taskType = eu.poc.taskmanagement.model.TaskType.USER_PROVISIONING;
        source.setExpectedExternalUsers(List.of("alice", "bob"));
        source.deadline = Instant.now().plusSeconds(3600);
        source.createdAt = Instant.now().minusSeconds(60);
        source.updatedAt = Instant.now();

        var mapped = mapper.toGenerated(source);

        assertEquals("task-1", mapped.getTaskId());
        assertEquals(TaskStatus.IN_PROGRESS, mapped.getStatus());
        assertEquals(TaskType.USER_PROVISIONING, mapped.getTaskType());
        assertEquals(List.of("alice", "bob"), mapped.getExpectedExternalUsers());
        assertNotNull(mapped.getDeadline());
        assertNotNull(mapped.getCreatedAt());
        assertNotNull(mapped.getUpdatedAt());
    }

    @Test
    void mapsProjectionAuditEntryToGeneratedAuditEntry() {
        AuditTrailEntry source = new AuditTrailEntry();
        source.id = 42L;
        source.taskId = "task-2";
        source.eventType = "TaskCreatedEvent";
        source.payload = "{\"key\":\"value\"}";
        source.eventTimestamp = Instant.now().minusSeconds(10);
        source.recordedAt = Instant.now();

        var mapped = mapper.toGenerated(source);

        assertEquals(42L, mapped.getId());
        assertEquals("task-2", mapped.getTaskId());
        assertEquals("TaskCreatedEvent", mapped.getEventType());
        assertEquals("{\"key\":\"value\"}", mapped.getPayload());
        assertNotNull(mapped.getEventTimestamp());
        assertNotNull(mapped.getRecordedAt());
    }
}
