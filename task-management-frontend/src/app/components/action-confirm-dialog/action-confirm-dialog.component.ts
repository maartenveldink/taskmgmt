import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface ActionConfirmDialogData {
  title: string;
  message: string;
  withReason: boolean;
}

@Component({
  selector: 'app-action-confirm-dialog',
  templateUrl: './action-confirm-dialog.component.html',
  standalone: false,
})
export class ActionConfirmDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<ActionConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ActionConfirmDialogData,
  ) {
    this.form = this.fb.group({ reason: [''] });
  }

  confirm(): void {
    this.dialogRef.close(this.data.withReason ? this.form.value.reason : true);
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
