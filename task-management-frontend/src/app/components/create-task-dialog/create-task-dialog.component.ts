import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { CreateTaskRequest } from '../../models/task.model';
import { v4 as uuidv4 } from 'uuid';

@Component({
  selector: 'app-create-task-dialog',
  templateUrl: './create-task-dialog.component.html',
  standalone: false,
})
export class CreateTaskDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<CreateTaskDialogComponent>,
  ) {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 7);

    this.form = this.fb.group({
      title: ['Test Task', Validators.required],
      description: ['This is a test task for demonstration purposes.'],
      groupName: ['default'],
      deadline: [tomorrow.toISOString().substring(0, 16), Validators.required],
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    const req: CreateTaskRequest = {
      correlationId: uuidv4(),
      title: v.title,
      description: v.description,
      groupName: v.groupName || null,
      deadline: new Date(v.deadline).toISOString(),
    };
    this.dialogRef.close(req);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
