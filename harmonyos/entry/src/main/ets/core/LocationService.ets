import { geoLocationManager } from '@kit.LocationKit';
import { abilityAccessCtrl, bundleManager } from '@kit.AbilityKit';
import { Fix } from './Models';

export type LocationRefreshState = 'idle' | 'refreshing' | 'success' | 'failed';
export type LocationFailureReason = '' | 'permission-denied' | 'location-disabled' | 'location-timeout' | 'address-unavailable';

/** Optional seams keep the lifecycle checks testable without replacing the platform manager in production. */
export interface LocationServiceDependencies {
  hasLocationPermission?: () => boolean;
  isLocationEnabled?: () => boolean;
  getCurrentLocation?: () => Promise<geoLocationManager.Location>;
  reverseAddress?: (fix: Fix) => Promise<string>;
}

const LOCATION_MAX_AGE_MS: number = 5 * 60 * 1000;
const LOCATION_FUTURE_TOLERANCE_MS: number = 30 * 1000;
const CURRENT_LOCATION_TIMEOUT_MS: number = 10 * 1000;
const ADDRESS_LOOKUP_TIMEOUT_MS: number = 8 * 1000;
/** An explicit refresh must reject a coarse/cached solution instead of reporting it as new. */
const CURRENT_LOCATION_MAX_ACCURACY_M: number = 50;
/** The continuous listener may reuse a 5-minute-old fix; an explicit refresh may not. */
const REFRESH_FIX_MAX_AGE_MS: number = 30 * 1000;
/** Skip the geocoder for a fix that barely moved while an address is already displayed. */
const GEOCODE_MIN_INTERVAL_MS: number = 60 * 1000;
const GEOCODE_MIN_MOVE_M: number = 20;

interface LocationPlatformError {
  code?: number;
  message?: string;
}

class RefreshFailure extends Error {
  reason: LocationFailureReason;
  constructor(reason: LocationFailureReason) {
    super(reason);
    this.reason = reason;
  }
}

/** A location is usable for a capture only while its timestamp is current. */
export function isFreshFix(fix: Fix | null, now: number = Date.now()): boolean {
  if (!fix || !Number.isFinite(now)) return false;
  if (!Number.isFinite(fix.latitude) || !Number.isFinite(fix.longitude)) return false;
  if (fix.latitude < -90 || fix.latitude > 90 || fix.longitude < -180 || fix.longitude > 180) return false;
  if (!Number.isFinite(fix.timestamp)) return false;
  const age = now - fix.timestamp;
  return age >= -LOCATION_FUTURE_TOLERANCE_MS && age <= LOCATION_MAX_AGE_MS;
}

/** Keep the address extraction in one place so empty geocoder rows stay a failure. */
export function addressFromGeoAddresses(rows: Array<geoLocationManager.GeoAddress>): string {
  if (!rows.length) return '';
  const row = rows[0];
  if (row.placeName && row.placeName.trim()) return row.placeName.trim();
  if (row.descriptions) {
    const descriptions: string[] = [];
    for (const value of row.descriptions) {
      if (value && value.trim()) descriptions.push(value.trim());
    }
    const text = descriptions.join('');
    if (text) return text;
  }
  const parts: string[] = [];
  const append = (value: string | undefined): void => {
    if (value && value.trim()) parts.push(value.trim());
  };
  append(row.countryName);
  append(row.administrativeArea);
  append(row.subAdministrativeArea);
  append(row.locality);
  append(row.subLocality);
  append(row.roadName);
  append(row.subRoadName);
  append(row.premises);
  return parts.join('');
}

/**
 * HarmonyOS types altitude as a required number, so "no vertical fix" can
 * arrive as 0. Only trust a 0 reading when the platform also reports vertical
 * accuracy; otherwise the reading is unusable and the caller keeps the
 * previous real altitude instead of writing a fabricated 0.0 m into a
 * permanent site record.
 */
export function usableAltitude(altitude: number, altitudeAccuracy: number | undefined): number | null {
  if (!Number.isFinite(altitude)) return null;
  const vertical = Number.isFinite(altitudeAccuracy) && (altitudeAccuracy as number) > 0;
  if (altitude === 0 && !vertical) return null;
  return altitude;
}

/** Great-circle distance in meters, used to throttle repeated geocoding. */
export function movedMeters(from: Fix, to: Fix): number {
  const earthRadius = 6371000;
  const dLat = (to.latitude - from.latitude) * Math.PI / 180;
  const dLon = (to.longitude - from.longitude) * Math.PI / 180;
  const sinLat = Math.sin(dLat / 2);
  const sinLon = Math.sin(dLon / 2);
  const a = sinLat * sinLat + Math.cos(from.latitude * Math.PI / 180) * Math.cos(to.latitude * Math.PI / 180) * sinLon * sinLon;
  return 2 * earthRadius * Math.asin(Math.min(1, Math.sqrt(a)));
}

export class LocationService {
  fix: Fix | null = null;

  /** UI-readable state for the explicit address refresh action. */
  refreshState: LocationRefreshState = 'idle';
  /** Empty until a failed refresh has a classified reason. */
  failureReason: LocationFailureReason = '';
  /** ArkUI can assign a small invalidation callback without coupling this service to a page. */
  onChange: (() => void) | null = null;

  /** True while the platform listener is attached. ohosTest sets it to drive receiveLocation directly. */
  listening: boolean = false;
  private lifecycleEpoch: number = 0;
  private permissionEpoch: number = 0;
  private refreshToken: number = 0;
  private refreshPromise: Promise<boolean> | null = null;
  private refreshResolver: ((value: boolean) => void) | null = null;
  private lastPlatformFailure: LocationFailureReason = '';
  /** Throttle state for the continuous listener's reverse geocoding. */
  private lastGeocodeFix: Fix | null = null;
  private lastGeocodeAt: number = 0;
  private readonly dependencies: LocationServiceDependencies;

  constructor(dependencies: LocationServiceDependencies = {}) {
    this.dependencies = dependencies;
  }

  private notifyChange(): void {
    try {
      this.onChange?.();
    } catch (e) {
      console.warn('SITECAM_LOCATION_CHANGE ' + String(e));
    }
  }

  private errorCode(error: Object): number {
    const code = (error as LocationPlatformError).code;
    return typeof code === 'number' ? code : 0;
  }

  private reasonForCode(code: number, fallback: LocationFailureReason): LocationFailureReason {
    if (code === 201 || code === -2) return 'permission-denied';
    if (code === 3301100 || code === -4) return 'location-disabled';
    // 3301000 means that the location service is unavailable; it does not
    // mean that the user switched location off.
    if (code === 3301000) return 'location-timeout';
    if (code === 3301200 || code === -5) return 'location-timeout';
    if (code === 3301300) return 'address-unavailable';
    return fallback;
  }

  private reasonForError(error: Object, fallback: LocationFailureReason): LocationFailureReason {
    return this.reasonForCode(this.errorCode(error), fallback);
  }

  private reasonFromFailure(error: Object, fallback: LocationFailureReason): LocationFailureReason {
    const failure = error as RefreshFailure;
    if (failure.reason) return failure.reason;
    return this.reasonForError(error, fallback);
  }

  private locationEnabled(): boolean {
    try {
      return this.dependencies.isLocationEnabled?.() ?? geoLocationManager.isLocationEnabled();
    } catch (e) {
      throw new RefreshFailure(this.reasonForError(e as Object, 'location-disabled'));
    }
  }

  /** Check the current app token without starting another location request. */
  private verifyLocationAccess(): void {
    if (!this.locationEnabled()) throw new RefreshFailure('location-disabled');
    try {
      if (this.dependencies.hasLocationPermission) {
        if (!this.dependencies.hasLocationPermission()) throw new RefreshFailure('permission-denied');
        return;
      }
      const info = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
      const token = info.appInfo.accessTokenId;
      const manager = abilityAccessCtrl.createAtManager();
      const precise = manager.checkAccessTokenSync(token, 'ohos.permission.LOCATION');
      const approximate = manager.checkAccessTokenSync(token, 'ohos.permission.APPROXIMATELY_LOCATION');
      if (precise !== abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED && approximate !== abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
        throw new RefreshFailure('permission-denied');
      }
    } catch (e) {
      const failure = e as RefreshFailure;
      if (failure.reason) throw failure;
      throw new RefreshFailure(this.reasonForError(e as Object, 'permission-denied'));
    }
  }

  private invalidateLocation(reason: LocationFailureReason): void {
    this.permissionEpoch++;
    this.lastPlatformFailure = reason;
    const hadFix = this.fix !== null;
    this.fix = null;
    if (hadFix) this.notifyChange();
    if (this.refreshState === 'refreshing') {
      this.refreshToken++;
      this.refreshState = 'failed';
      this.failureReason = reason;
      this.notifyChange();
      const resolve = this.refreshResolver;
      this.refreshResolver = null;
      resolve?.(false);
      this.refreshPromise = null;
    }
  }

  private locationError = (error: geoLocationManager.LocationError): void => {
    const reason = this.reasonForCode(error as number, 'location-timeout');
    if (reason === 'permission-denied' || reason === 'location-disabled') {
      this.invalidateLocation(reason);
    }
  };

  private locationEnabledChanged = (enabled: boolean): void => {
    if (!enabled) this.invalidateLocation('location-disabled');
  };

  private asFix(value: geoLocationManager.Location): Fix | null {
    if (!Number.isFinite(value.latitude) || !Number.isFinite(value.longitude) || !Number.isFinite(value.timeStamp)) return null;
    const fix = new Fix();
    fix.latitude = value.latitude;
    fix.longitude = value.longitude;
    fix.altitude = usableAltitude(value.altitude, value.altitudeAccuracy);
    fix.accuracy = Number.isFinite(value.accuracy) ? value.accuracy : 0;
    fix.timestamp = value.timeStamp;
    return fix;
  }

  private reverseAddress = async (fix: Fix): Promise<string> => {
    try {
      this.verifyLocationAccess();
      if (this.dependencies.reverseAddress) {
        const address = await this.dependencies.reverseAddress(fix);
        this.verifyLocationAccess();
        if (!address || !address.trim()) throw new RefreshFailure('address-unavailable');
        return address.trim();
      }
      if (!geoLocationManager.isGeocoderAvailable()) throw new RefreshFailure('address-unavailable');
      const request = geoLocationManager.getAddressesFromLocation({
        latitude: fix.latitude,
        longitude: fix.longitude,
        maxItems: 1
      });
      const rows = await new Promise<Array<geoLocationManager.GeoAddress>>((resolve, reject) => {
        let settled = false;
        const timer = setTimeout(() => {
          if (!settled) {
            settled = true;
            reject(new RefreshFailure('address-unavailable'));
          }
        }, ADDRESS_LOOKUP_TIMEOUT_MS);
        request.then((value: Array<geoLocationManager.GeoAddress>) => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve(value);
        }).catch((error: Error) => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          reject(error);
        });
      });
      this.verifyLocationAccess();
      const address = addressFromGeoAddresses(rows);
      if (!address) throw new RefreshFailure('address-unavailable');
      return address;
    } catch (e) {
      const reason = this.reasonFromFailure(e as Object, 'address-unavailable');
      throw new RefreshFailure(reason);
    }
  };

  private currentFix = async (): Promise<Fix> => {
    try {
      this.verifyLocationAccess();
      const value = this.dependencies.getCurrentLocation
        ? await this.dependencies.getCurrentLocation()
        : await geoLocationManager.getCurrentLocation({
            priority: geoLocationManager.LocationRequestPriority.ACCURACY,
            scenario: geoLocationManager.LocationRequestScenario.UNSET,
            maxAccuracy: CURRENT_LOCATION_MAX_ACCURACY_M,
            timeoutMs: CURRENT_LOCATION_TIMEOUT_MS
          });
      const fix = this.asFix(value);
      // The listener tolerates a 5-minute-old fix; an explicit refresh must not,
      // otherwise "refresh" can hand back the exact cached reading it replaced.
      if (!fix || !isFreshFix(fix) || Date.now() - fix.timestamp > REFRESH_FIX_MAX_AGE_MS) throw new RefreshFailure('location-timeout');
      return fix;
    } catch (e) {
      const reason = this.reasonFromFailure(e as Object, 'location-timeout');
      throw new RefreshFailure(reason);
    }
  };

  private canCommit(token: number, lifecycle: number, permission: number): boolean {
    return token === this.refreshToken && lifecycle === this.lifecycleEpoch && permission === this.permissionEpoch && this.refreshState === 'refreshing';
  }

  private staleReason(): LocationFailureReason {
    return this.lastPlatformFailure || 'location-timeout';
  }

  private finishFailure(token: number, reason: LocationFailureReason): boolean {
    if (token !== this.refreshToken) return false;
    this.refreshState = 'failed';
    this.failureReason = reason;
    if (reason === 'permission-denied' || reason === 'location-disabled') this.fix = null;
    this.notifyChange();
    return false;
  }

  private finishSuccess(token: number): boolean {
    if (token !== this.refreshToken) return false;
    this.refreshState = 'success';
    this.failureReason = '';
    this.notifyChange();
    return true;
  }

  private async performRefresh(token: number, lifecycle: number, permission: number): Promise<boolean> {
    let candidate: Fix | null = null;
    try {
      candidate = await this.currentFix();
      if (!this.canCommit(token, lifecycle, permission)) return this.finishFailure(token, this.staleReason());
      if (candidate.altitude === null && this.fix && Number.isFinite(this.fix.altitude)) {
        candidate.altitude = this.fix.altitude;
      }
      if (!candidate.address && this.fix && this.fix.address) {
        candidate.address = this.fix.address;
      }
      // A newer continuous-location fix may have arrived while the hardware
      // query was in flight; never regress to an older reading.
      if (!this.fix || candidate.timestamp > this.fix.timestamp) {
        this.fix = candidate;
      } else {
        candidate = this.fix;
      }
      this.notifyChange();
    } catch (e) {
      if (!this.canCommit(token, lifecycle, permission)) return this.finishFailure(token, this.staleReason());
      // A failed hardware query must not be reported as a successful refresh.
      return this.finishFailure(token, this.reasonFromFailure(e as Object, 'location-timeout'));
    }
    if (!candidate) return this.finishFailure(token, 'location-timeout');

    // A continuous callback may deliver a newer fix while reverse geocoding.
    // Retry that newer fix once so its address cannot be overwritten by an old result.
    let activeCandidate: Fix = candidate;
    let lastAddress: string = '';
    for (let attempt = 0; attempt < 2; attempt++) {
      if (!isFreshFix(activeCandidate)) return this.finishFailure(token, 'location-timeout');
      const address = await this.reverseAddress(activeCandidate);
      lastAddress = address;
      if (!this.canCommit(token, lifecycle, permission)) return this.finishFailure(token, this.staleReason());
      if (this.fix !== activeCandidate) {
        const newer: Fix | null = this.fix;
        if (newer && isFreshFix(newer) && newer.timestamp >= activeCandidate.timestamp) {
          if (newer.altitude === null && activeCandidate.altitude !== null) {
            newer.altitude = activeCandidate.altitude;
          }
          if (!newer.address && activeCandidate.address) {
            newer.address = activeCandidate.address;
          }
          activeCandidate = newer;
          continue;
        }
        return this.finishFailure(token, 'location-timeout');
      }
      if (!isFreshFix(activeCandidate)) return this.finishFailure(token, 'location-timeout');
      activeCandidate.address = address;
      this.notifyChange();
      return this.finishSuccess(token);
    }
    // Both retries were spent on broadcasts that kept arriving. Commit the
    // freshest fix we hold together with the address that was actually
    // resolved, instead of reporting a valid refresh as a failure.
    if (!this.canCommit(token, lifecycle, permission)) return this.finishFailure(token, this.staleReason());
    const newest: Fix = this.fix !== null && isFreshFix(this.fix) && this.fix.timestamp >= activeCandidate.timestamp ? this.fix : activeCandidate;
    if (isFreshFix(newest)) {
      if (lastAddress) newest.address = lastAddress;
      this.fix = newest;
      this.notifyChange();
      return this.finishSuccess(token);
    }
    return this.finishFailure(token, 'address-unavailable');
  }

  /**
   * Force reverse geocoding of the freshest fix, requesting one when needed.
   * Calls made while this operation is in flight share its exact Promise.
   */
  refreshAddress(): Promise<boolean> {
    if (this.refreshPromise) return this.refreshPromise;
    const token = ++this.refreshToken;
    const lifecycle = this.lifecycleEpoch;
    const permission = this.permissionEpoch;
    this.lastPlatformFailure = '';
    this.refreshState = 'refreshing';
    this.failureReason = '';
    this.notifyChange();
    const promise = new Promise<boolean>((resolve) => {
      this.refreshResolver = resolve;
      this.performRefresh(token, lifecycle, permission).then((value: boolean) => {
        resolve(value);
      }).catch((e: Object) => {
        resolve(this.finishFailure(token, this.reasonFromFailure(e, 'address-unavailable')));
      });
    });
    this.refreshPromise = promise;
    promise.then(() => {
      if (this.refreshPromise === promise) {
        this.refreshPromise = null;
        this.refreshResolver = null;
      }
    }, () => {
      if (this.refreshPromise === promise) {
        this.refreshPromise = null;
        this.refreshResolver = null;
      }
    });
    return promise;
  }

  private receive = (value: geoLocationManager.Location): void => { this.receiveLocation(value); };

  /**
   * Continuous-location entry point. Also the seam ohosTest uses to drive
   * broadcast interleavings without replacing the platform manager.
   */
  receiveLocation(value: geoLocationManager.Location): void {
    if (!this.listening) return;
    const fix = this.asFix(value);
    if (!fix || !isFreshFix(fix)) return;
    if (this.fix && fix.timestamp <= this.fix.timestamp) return;
    if (fix.altitude === null && this.fix && Number.isFinite(this.fix.altitude)) {
      fix.altitude = this.fix.altitude;
    }
    if (!fix.address && this.fix && this.fix.address) {
      fix.address = this.fix.address;
    }
    this.fix = fix;
    this.notifyChange();
    if (this.refreshState === 'refreshing') return;
    // A stationary site would otherwise hit the geocoder on every broadcast.
    // Re-resolve as soon as the fix actually moves or the interval elapses.
    if (this.geocodeSkipApplies(fix)) return;
    const lifecycle = this.lifecycleEpoch;
    const token = this.refreshToken;
    const permission = this.permissionEpoch;
    this.lastGeocodeFix = fix;
    this.lastGeocodeAt = Date.now();
    this.reverseAddress(fix).then((address: string) => {
      if (this.listening && this.lifecycleEpoch === lifecycle && this.refreshToken === token && this.permissionEpoch === permission && this.refreshState !== 'refreshing' && this.fix === fix && isFreshFix(fix)) {
        fix.address = address;
        this.notifyChange();
      }
    }).catch(() => {});
  }

  private geocodeSkipApplies(fix: Fix): boolean {
    if (!fix.address || this.lastGeocodeFix === null) return false;
    if (Date.now() - this.lastGeocodeAt > GEOCODE_MIN_INTERVAL_MS) return false;
    return movedMeters(this.lastGeocodeFix, fix) < GEOCODE_MIN_MOVE_M;
  }

  start(): void {
    if (this.listening) return;
    let locationListening = false;
    let errorListening = false;
    let enabledListening = false;
    try {
      geoLocationManager.on('locationChange', {
        priority: geoLocationManager.LocationRequestPriority.FIRST_FIX,
        scenario: geoLocationManager.LocationRequestScenario.UNSET,
        timeInterval: 10,
        distanceInterval: 5,
        maxAccuracy: 0
      }, this.receive);
      locationListening = true;
      geoLocationManager.on('locationError', this.locationError);
      errorListening = true;
      geoLocationManager.on('locationEnabledChange', this.locationEnabledChanged);
      enabledListening = true;
      this.listening = true;
    } catch (e) {
      if (locationListening) {
        try { geoLocationManager.off('locationChange', this.receive); } catch (ignored) {}
      }
      if (errorListening) {
        try { geoLocationManager.off('locationError', this.locationError); } catch (ignored) {}
      }
      if (enabledListening) {
        try { geoLocationManager.off('locationEnabledChange', this.locationEnabledChanged); } catch (ignored) {}
      }
      this.listening = false;
    }
  }

  stop(): void {
    this.lifecycleEpoch++;
    const hadFix = this.fix !== null;
    this.fix = null;
    if (hadFix) this.notifyChange();
    if (this.refreshState === 'refreshing') {
      this.refreshToken++;
      this.refreshState = 'failed';
      this.failureReason = 'location-disabled';
      this.notifyChange();
      const resolve = this.refreshResolver;
      this.refreshResolver = null;
      resolve?.(false);
      this.refreshPromise = null;
    }
    if (!this.listening) return;
    try { geoLocationManager.off('locationChange', this.receive); } catch (e) {}
    try { geoLocationManager.off('locationError', this.locationError); } catch (e) {}
    try { geoLocationManager.off('locationEnabledChange', this.locationEnabledChanged); } catch (e) {}
    this.listening = false;
  }
}
