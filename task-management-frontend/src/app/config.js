import {Injectable} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {environment} from '../../../../environments/environment';
import {ApplicationsConfig} from './applications-config';
import {Observable} from 'rxjs';
import {shareReplay} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ApplicationsConfigService {

  readonly applicationsConfig$: Observable<ApplicationsConfig>;

  constructor(http: HttpClient) {
    this.applicationsConfig$ = http.get<ApplicationsConfig>("public/assets/config/application/config.json").pipe(
      shareReplay({refCount: false, bufferSize: 1})
    );
  }
}
