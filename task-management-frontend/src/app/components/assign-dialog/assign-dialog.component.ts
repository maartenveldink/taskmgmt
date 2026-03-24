import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AssignTaskRequest, ReassignTaskRequest } from '../../models/task.model';

export interface AssignDialogData {
  mode: 'assign' | 'reassign';
}

@Component({
  selector: 'app-assign-dialog',
  templateUrl: './assign-dialog.component.html',
  standalone: false,
})
export class AssignDialogComponent {
  form: FormGroup;
  assigneeTypes = ['USER', 'GROUP'];

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<AssignDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AssignDialogData,
  ) {
    this.form = this.fb.group({
      assigneeName: ['', Validators.required],
      assigneeType: ['USER', Validators.required],
    });
  }

  get title(): string {
    return this.data.mode === 'assign' ? 'Assign Task' : 'Reassign Task';
  }

  submit(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    if (this.data.mode === 'assign') {
      this.dialogRef.close({ assigneeName: v.assigneeName, assigneeType: v.assigneeType } as AssignTaskRequest);
    } else {
      this.dialogRef.close({ newAssigneeName: v.assigneeName, newAssigneeType: v.assigneeType } as ReassignTaskRequest);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
