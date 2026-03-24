import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AuditEntry,
  AssignTaskRequest,
  CreateTaskRequest,
  ReasonRequest,
  ReassignTaskRequest,
  Task,
  TaskStatus,
} from '../models/task.model';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly base = 'http://localhost:8080/tasks';

  constructor(private http: HttpClient) {}

  // ── Queries ──────────────────────────────────────────────────────────────

  getAllTasks(filters?: { status?: TaskStatus; deadlineBefore?: string; deadlineAfter?: string }): Observable<Task[]> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.deadlineBefore) params = params.set('deadlineBefore', filters.deadlineBefore);
    if (filters?.deadlineAfter) params = params.set('deadlineAfter', filters.deadlineAfter);
    return this.http.get<Task[]>(this.base, { params });
  }

  getTasksByUser(userName: string, filters?: { status?: TaskStatus }): Observable<Task[]> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    return this.http.get<Task[]>(`${this.base}/user/${encodeURIComponent(userName)}`, { params });
  }

  getTasksByGroup(groupName: string, filters?: { status?: TaskStatus }): Observable<Task[]> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    return this.http.get<Task[]>(`${this.base}/group/${encodeURIComponent(groupName)}`, { params });
  }

  getAuditTrail(taskId: string): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.base}/${taskId}/audit`);
  }

  // ── Commands ──────────────────────────────────────────────────────────────

  createTask(req: CreateTaskRequest): Observable<void> {
    return this.http.post<void>(this.base, req);
  }

  assignTask(taskId: string, req: AssignTaskRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/${taskId}/assign`, req);
  }

  reassignTask(taskId: string, req: ReassignTaskRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/${taskId}/reassign`, req);
  }

  startTask(taskId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${taskId}/start`, null);
  }

  completeTask(taskId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${taskId}/complete`, null);
  }

  cancelTask(taskId: string, reason?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${taskId}/cancel`, { reason: reason ?? null } as ReasonRequest);
  }

  rejectTask(taskId: string, reason?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${taskId}/reject`, { reason: reason ?? null } as ReasonRequest);
  }
}
