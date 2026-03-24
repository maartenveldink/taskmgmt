import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Task, TaskStatus } from '../../models/task.model';
import { TaskService } from '../../services/task.service';
import { CreateTaskDialogComponent } from '../create-task-dialog/create-task-dialog.component';
import { CreateTaskRequest } from '../../models/task.model';

@Component({
  selector: 'app-task-list',
  templateUrl: './task-list.component.html',
  styleUrls: ['./task-list.component.scss'],
  standalone: false,
})
export class TaskListComponent implements OnInit {
  tasks: Task[] = [];
  loading = false;

  filterForm: FormGroup;
  displayedColumns = ['title', 'status', 'assignedGroup', 'assignedUser', 'deadline', 'actions'];

  statuses: TaskStatus[] = ['CREATED', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'CANCELLED', 'REJECTED'];
  viewModes = [
    { label: 'All Tasks', value: 'all' },
    { label: 'By User', value: 'user' },
    { label: 'By Group', value: 'group' },
  ];

  constructor(
    private fb: FormBuilder,
    private taskService: TaskService,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
  ) {
    this.filterForm = this.fb.group({
      viewMode: ['all'],
      filterName: [''],
      status: [null],
      deadlineBefore: [''],
      deadlineAfter: [''],
    });
  }

  ngOnInit(): void {
    this.loadTasks();
  }

  loadTasks(): void {
    this.loading = true;
    const v = this.filterForm.value;

    const filters = {
      status: v.status || undefined,
      deadlineBefore: v.deadlineBefore ? new Date(v.deadlineBefore).toISOString() : undefined,
      deadlineAfter: v.deadlineAfter ? new Date(v.deadlineAfter).toISOString() : undefined,
    };

    const obs$ =
      v.viewMode === 'user' && v.filterName
        ? this.taskService.getTasksByUser(v.filterName, { status: v.status || undefined })
        : v.viewMode === 'group' && v.filterName
          ? this.taskService.getTasksByGroup(v.filterName, { status: v.status || undefined })
          : this.taskService.getAllTasks(filters);

    obs$.subscribe({
      next: tasks => { this.tasks = tasks; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(CreateTaskDialogComponent, { width: '480px' });
    ref.afterClosed().subscribe((req: CreateTaskRequest | undefined) => {
      if (!req) return;
      this.taskService.createTask(req).subscribe({
        next: () => {
          this.snackBar.open('Task created', 'Close', { duration: 3000 });
          this.loadTasks();
        },
        error: e => {
          const msg = e?.error?.message ?? 'Failed to create task';
          this.snackBar.open(`Error: ${msg}`, 'Close', { duration: 5000, panelClass: 'snack-error' });
        },
      });
    });
  }

  viewDetail(task: Task): void {
    this.router.navigate(['/tasks', task.taskId]);
  }

  statusColor(status: string): string {
    const map: Record<string, string> = {
      CREATED: '',
      ASSIGNED: 'primary',
      IN_PROGRESS: 'accent',
      DONE: 'primary',
      CANCELLED: 'warn',
      REJECTED: 'warn',
    };
    return map[status] ?? '';
  }

  isOverdue(task: Task): boolean {
    return !['DONE', 'CANCELLED', 'REJECTED'].includes(task.status) &&
      new Date(task.deadline) < new Date();
  }
}
