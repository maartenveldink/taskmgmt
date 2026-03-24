export type TaskStatus = 'CREATED' | 'ASSIGNED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED' | 'REJECTED';
export type AssigneeType = 'USER' | 'GROUP';

export interface Task {
  taskId: string;
  title: string;
  description: string;
  assignedGroup: string;
  assignedUser: string | null;
  status: TaskStatus;
  deadline: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEntry {
  id: number;
  taskId: string;
  eventType: string;
  payload: string;
  eventTimestamp: string;
  recordedAt: string;
}

export interface CreateTaskRequest {
  correlationId: string;
  title: string;
  description: string;
  groupName: string | null;
  deadline: string;
}

export interface AssignTaskRequest {
  assigneeName: string;
  assigneeType: AssigneeType;
}

export interface ReassignTaskRequest {
  newAssigneeName: string;
  newAssigneeType: AssigneeType;
}

export interface ReasonRequest {
  reason: string | null;
}
