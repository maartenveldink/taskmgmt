import {Injectable} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {environment} from '../../../../environments/environment';
import {ApplicationsConfig} from './applications-config';
import {Observable} from 'rxjs';
import {forkJoin} from 'rxjs';
import {map, shareReplay} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ApplicationsConfigService {

  readonly applicationsConfig$: Observable<ApplicationsConfig>;

  constructor(http: HttpClient) {
    this.applicationsConfig$ = forkJoin([
      http.get<Partial<ApplicationsConfig>>("public/assets/config/application/config.json"),
      http.get<Partial<ApplicationsConfig>>("public/assets/config/application/config-extra.json"),
    ]).pipe(
      map(([config1, config2]) => ({ ...config1, ...config2 } as ApplicationsConfig)),
      shareReplay({ refCount: false, bufferSize: 1 })
    );
  }
}