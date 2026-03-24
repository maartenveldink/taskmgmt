import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { switchMap } from 'rxjs/operators';
import { AuditEntry, AssignTaskRequest, ReassignTaskRequest, Task } from '../../models/task.model';
import { TaskService } from '../../services/task.service';
import { AssignDialogComponent } from '../assign-dialog/assign-dialog.component';
import { ActionConfirmDialogComponent } from '../action-confirm-dialog/action-confirm-dialog.component';

@Component({
  selector: 'app-task-detail',
  templateUrl: './task-detail.component.html',
  styleUrls: ['./task-detail.component.scss'],
  standalone: false,
})
export class TaskDetailComponent implements OnInit {
  task: Task | null = null;
  auditEntries: AuditEntry[] = [];
  loading = true;

  auditColumns = ['eventTimestamp', 'eventType', 'payload'];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private taskService: TaskService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const taskId = this.route.snapshot.paramMap.get('id')!;
    this.taskService.getAllTasks().subscribe(tasks => {
      this.task = tasks.find(t => t.taskId === taskId) ?? null;
      this.loading = false;
    });
    this.taskService.getAuditTrail(taskId).subscribe(entries => {
      this.auditEntries = entries;
    });
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  openAssign(mode: 'assign' | 'reassign'): void {
    const ref = this.dialog.open(AssignDialogComponent, { data: { mode }, width: '400px' });
    ref.afterClosed().subscribe((result: AssignTaskRequest | ReassignTaskRequest | undefined) => {
      if (!result) return;
      const taskId = this.task!.taskId;
      const obs = mode === 'assign'
        ? this.taskService.assignTask(taskId, result as AssignTaskRequest)
        : this.taskService.reassignTask(taskId, result as ReassignTaskRequest);
      obs.subscribe({ next: () => this.onSuccess(), error: e => this.onError(e) });
    });
  }

  confirmStart(): void {
    this.confirm('Start Task', 'Move this task to IN_PROGRESS?', false).subscribe(ok => {
      if (!ok) return;
      this.taskService.startTask(this.task!.taskId)
        .subscribe({ next: () => this.onSuccess(), error: e => this.onError(e) });
    });
  }

  confirmComplete(): void {
    this.confirm('Complete Task', 'Mark this task as DONE?', false).subscribe(ok => {
      if (!ok) return;
      this.taskService.completeTask(this.task!.taskId)
        .subscribe({ next: () => this.onSuccess(), error: e => this.onError(e) });
    });
  }

  confirmCancel(): void {
    this.confirm('Cancel Task', 'Cancel this task?', true).subscribe(result => {
      if (result === null) return;
      this.taskService.cancelTask(this.task!.taskId, result || undefined)
        .subscribe({ next: () => this.onSuccess(), error: e => this.onError(e) });
    });
  }

  confirmReject(): void {
    this.confirm('Reject Task', 'Reject this task?', true).subscribe(result => {
      if (result === null) return;
      this.taskService.rejectTask(this.task!.taskId, result || undefined)
        .subscribe({ next: () => this.onSuccess(), error: e => this.onError(e) });
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  isTerminal(): boolean {
    return ['DONE', 'CANCELLED', 'REJECTED'].includes(this.task?.status ?? '');
  }

  statusColor(status: string): string {
    const map: Record<string, string> = {
      CREATED: 'default',
      ASSIGNED: 'primary',
      IN_PROGRESS: 'accent',
      DONE: 'primary',
      CANCELLED: 'warn',
      REJECTED: 'warn',
    };
    return map[status] ?? 'default';
  }

  formatPayload(raw: string): string {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  back(): void {
    this.router.navigate(['/tasks']);
  }

  private confirm(title: string, message: string, withReason: boolean) {
    return this.dialog.open(ActionConfirmDialogComponent, {
      data: { title, message, withReason },
      width: '400px',
    }).afterClosed();
  }

  private onSuccess(): void {
    this.snackBar.open('Action completed successfully', 'Close', { duration: 3000 });
    this.load();
  }

  private onError(err: any): void {
    const msg = err?.error?.message ?? err?.message ?? 'An error occurred';
    this.snackBar.open(`Error: ${msg}`, 'Close', { duration: 5000, panelClass: 'snack-error' });
  }
}
