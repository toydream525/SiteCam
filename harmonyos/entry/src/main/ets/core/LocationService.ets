import { geoLocationManager } from '@kit.LocationKit';
import { abilityAccessCtrl, bundleManager } from '@kit.AbilityKit';
import { Fix, isCaptureFreshFix } from './Models';

export type LocationRefreshState = 'idle' | 'refreshing' | 'success' | 'failed';
export type LocationFailureReason = '' | 'permission-denied' | 'location-disabled' | 'location-timeout' | 'address-unavailable';

/** Optional seams keep the lifecycle checks testable without replacing the platform manager in production. */
export interface LocationServiceDependencies {
  hasLocationPermission?: () => boolean;
  isLocationEnabled?: () => boolean;
  getCurrentLocation?: () => Promise<geoLocationManager.Location>;
  reverseAddress?: (fix: Fix) => Promise<string>;
  now?: () => number;
}

const LOCATION_MAX_AGE_MS: number = 5 * 60 * 1000;
const LOCATION_FUTURE_TOLERANCE_MS: number = 30 * 1000;
const CURRENT_LOCATION_TIMEOUT_MS: number = 20 * 1000;
const ADDRESS_LOOKUP_TIMEOUT_MS: number = 8 * 1000;
/** The fresh-timestamp checks reject old cache results while allowing a real coarse fix. */
// 0 leaves the platform free to return a real coarse fix when the user grants
// approximate location; freshness is still enforced by the timestamp checks.
const CURRENT_LOCATION_MAX_ACCURACY_M: number = 0;
/** Skip the geocoder for a fix that barely moved while an address is already displayed. */
const GEOCODE_MIN_INTERVAL_MS: number = 60 * 1000;
const GEOCODE_MIN_MOVE_M: number = 20;
/** Coalescing window for continuous-position broadcasts; the tail is always delivered. */
const LOCATION_BROADCAST_INTERVAL_MS: number = 250;
/** Back-off after a failed continuous subscription so the clock cannot hot-loop. */
const LISTEN_RETRY_BACKOFF_MS: number = 60 * 1000;

interface LocationPlatformError {
  code?: number;
  message?: string;
}

interface AddressAnchor {
  latitude: number;
  longitude: number;
  timestamp: number;
  address: string;
  resolvedAt: number;
}

interface ContinuousGeocode {
  origin: Fix;
  lifecycle: number;
  token: number;
  permission: number;
}

class RefreshFailure extends Error {
  reason: LocationFailureReason;
  constructor(reason: LocationFailureReason) {
    super(reason);
    this.reason = reason;
  }
}

/** Retained location/address provenance expires after five minutes; capture has a shorter boundary. */
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
  /** Reported when the continuous subscription could not be registered. */
  onListenFailed: ((reason: string) => void) | null = null;

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
  private addressAnchor: AddressAnchor | null = null;
  private continuousGeocode: ContinuousGeocode | null = null;
  /** Pending coalesced position notification; -1 means nothing scheduled. */
  private locationNotifyTimer: number = -1;
  /** Earliest time ensureListening may call start() again after a failure. */
  private listenRetryAfter: number = 0;
  private readonly dependencies: LocationServiceDependencies;

  constructor(dependencies: LocationServiceDependencies = {}) {
    this.dependencies = dependencies;
  }

  private now(): number { return this.dependencies.now?.() ?? Date.now(); }
  private retainedFix(fix: Fix | null): boolean { return isFreshFix(fix, this.now()); }
  private captureFreshFix(fix: Fix | null): boolean { return isCaptureFreshFix(fix, this.now()); }

  private notifyChange(): void {
    try {
      this.onChange?.();
    } catch (e) {
      console.warn('SITECAM_LOCATION_CHANGE ' + String(e));
    }
  }

  /**
   * A position-only broadcast is coalesced; the trailing update is never
   * dropped because a later broadcast simply leaves the pending timer in
   * place and the timer reads the newest state when it fires.
   */
  private notifyLocationChange(): void {
    if (this.locationNotifyTimer >= 0) return;
    this.locationNotifyTimer = setTimeout(() => {
      this.locationNotifyTimer = -1;
      this.notifyChange();
    }, LOCATION_BROADCAST_INTERVAL_MS);
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

  isSystemLocationEnabled(): boolean {
    return this.locationEnabled();
  }

  permissionStateChanged(granted: boolean): void {
    if (!granted && (this.fix !== null || this.addressAnchor !== null || this.continuousGeocode !== null || this.refreshState === 'refreshing')) {
      this.invalidateLocation('permission-denied');
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
    const stateChanged = this.refreshState !== 'failed' || this.failureReason !== reason;
    this.fix = null;
    this.addressAnchor = null;
    this.continuousGeocode = null;
    this.lastGeocodeFix = null;
    this.lastGeocodeAt = 0;
    if (this.refreshState === 'refreshing') {
      this.refreshToken++;
      this.refreshState = 'failed';
      this.failureReason = reason;
      this.notifyChange();
      const resolve = this.refreshResolver;
      this.refreshResolver = null;
      resolve?.(false);
      this.refreshPromise = null;
    } else {
      this.refreshState = 'failed';
      this.failureReason = reason;
      if (hadFix || stateChanged) this.notifyChange();
    }
  }

  private locationError = (error: geoLocationManager.LocationError): void => {
    const reason = this.reasonForCode(error as number, 'location-timeout');
    if (reason === 'permission-denied' || reason === 'location-disabled') {
      this.invalidateLocation(reason);
    }
  };

  private locationEnabledChanged = (enabled: boolean): void => {
    if (!enabled) {
      this.invalidateLocation('location-disabled');
      return;
    }
    if (this.refreshState === 'failed' && this.failureReason === 'location-disabled') {
      this.refreshState = 'idle';
      this.failureReason = '';
      this.lastPlatformFailure = '';
      this.notifyChange();
    }
  };

  private asFix(value: geoLocationManager.Location): Fix | null {
    if (!Number.isFinite(value.latitude) || !Number.isFinite(value.longitude) || !Number.isFinite(value.timeStamp)) return null;
    const fix = new Fix();
    fix.latitude = value.latitude;
    fix.longitude = value.longitude;
    fix.altitude = usableAltitude(value.altitude, value.altitudeAccuracy);
    fix.accuracy = Number.isFinite(value.accuracy) ? value.accuracy : 0;
    fix.timestamp = value.timeStamp;
    fix.sourceType = value.sourceType === undefined ? -1 : value.sourceType;
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
      console.info('SITECAM_LOCATION_REVERSE_FAILED code=' + this.errorCode(e as Object) + ' reason=' + reason);
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
      if (!fix || !this.captureFreshFix(fix)) throw new RefreshFailure('location-timeout');
      return fix;
    } catch (e) {
      const reason = this.reasonFromFailure(e as Object, 'location-timeout');
      console.info('SITECAM_LOCATION_CURRENT_FAILED code=' + this.errorCode(e as Object) + ' reason=' + reason);
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
    if (reason === 'permission-denied' || reason === 'location-disabled') {
      this.fix = null;
      this.addressAnchor = null;
      this.continuousGeocode = null;
      this.lastGeocodeFix = null;
      this.lastGeocodeAt = 0;
    }
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
      // A newer continuous-location fix may have arrived while the hardware
      // query was in flight; never regress to an older reading.
      if (!this.fix || candidate.timestamp > this.fix.timestamp) {
        const anchor = this.addressAnchor;
        if (anchor && this.retainedFix(this.fixFromAnchor(anchor)) && movedMeters(this.fixFromAnchor(anchor), candidate) < GEOCODE_MIN_MOVE_M) {
          candidate.address = anchor.address;
        }
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
    for (let attempt = 0; attempt < 2; attempt++) {
      if (!this.captureFreshFix(activeCandidate)) return this.finishFailure(token, 'location-timeout');
      const addressOrigin = activeCandidate;
      const address = await this.reverseAddress(activeCandidate);
      if (!this.canCommit(token, lifecycle, permission)) return this.finishFailure(token, this.staleReason());
      if (this.fix !== activeCandidate && (!this.fix || movedMeters(activeCandidate, this.fix) >= GEOCODE_MIN_MOVE_M)) {
        const newer: Fix | null = this.fix;
        if (newer && this.retainedFix(newer) && newer.timestamp >= activeCandidate.timestamp) {
          activeCandidate = newer;
          continue;
        }
        return this.finishFailure(token, 'location-timeout');
      }
      if (this.fix && this.fix !== activeCandidate && movedMeters(activeCandidate, this.fix) < GEOCODE_MIN_MOVE_M) activeCandidate = this.fix;
      if (!this.captureFreshFix(activeCandidate)) return this.finishFailure(token, 'location-timeout');
      activeCandidate.address = address;
      this.addressAnchor = { latitude: addressOrigin.latitude, longitude: addressOrigin.longitude, timestamp: addressOrigin.timestamp, address, resolvedAt: this.now() };
      if (!this.hasUsableAddress()) return this.finishFailure(token, 'location-timeout');
      return this.finishSuccess(token);
    }
    // Both retries were spent on broadcasts that kept arriving. Commit the
    // freshest fix, but leave its address untouched because the last resolved
    // address belongs to an earlier coordinate.
    if (!this.canCommit(token, lifecycle, permission)) return this.finishFailure(token, this.staleReason());
    const newest: Fix = this.fix !== null && this.retainedFix(this.fix) && this.fix.timestamp >= activeCandidate.timestamp ? this.fix : activeCandidate;
    if (this.retainedFix(newest)) {
      this.fix = newest;
      this.notifyChange();
      return this.finishFailure(token, 'address-unavailable');
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
    try {
      this.verifyLocationAccess();
    } catch (e) {
      this.invalidateLocation(this.reasonFromFailure(e as Object, 'permission-denied'));
      return;
    }
    const fix = this.asFix(value);
    if (!fix || !this.retainedFix(fix)) return;
    if (this.fix && fix.timestamp <= this.fix.timestamp) return;
    const anchor = this.addressAnchor;
    if (anchor && this.retainedFix(this.fixFromAnchor(anchor)) && movedMeters(this.fixFromAnchor(anchor), fix) < GEOCODE_MIN_MOVE_M) {
      fix.address = anchor.address;
      this.fix = fix;
      this.notifyLocationChange();
      if (this.now() - anchor.resolvedAt <= GEOCODE_MIN_INTERVAL_MS) return;
    } else {
      this.addressAnchor = null;
      fix.address = '';
    }
    this.fix = fix;
    this.notifyLocationChange();
    if (this.refreshState === 'refreshing') return;
    if (this.continuousGeocode) return;
    // A stationary site would otherwise hit the geocoder on every broadcast.
    // Re-resolve as soon as the fix actually moves or the interval elapses.
    if (this.geocodeSkipApplies(fix)) return;
    const lifecycle = this.lifecycleEpoch;
    const token = this.refreshToken;
    const permission = this.permissionEpoch;
    this.lastGeocodeFix = fix;
    this.lastGeocodeAt = this.now();
    const request: ContinuousGeocode = { origin: fix, lifecycle, token, permission };
    this.continuousGeocode = request;
    this.reverseAddress(fix).then((address: string) => {
      if (this.continuousGeocode === request) this.continuousGeocode = null;
      const current = this.fix;
      if (this.listening && this.lifecycleEpoch === lifecycle && this.refreshToken === token && this.permissionEpoch === permission && this.refreshState !== 'refreshing' && current && this.retainedFix(current) && this.retainedFix(fix) && movedMeters(fix, current) < GEOCODE_MIN_MOVE_M) {
        this.commitContinuousAddress(current, fix, address);
      } else if (this.listening && this.lifecycleEpoch === lifecycle && this.refreshToken === token && this.permissionEpoch === permission && current && this.retainedFix(current) && this.refreshState !== 'refreshing') {
        this.resolveContinuous(current);
      }
    }).catch((error: Object) => {
      this.continuousGeocodeFailed(error, request, lifecycle, token, permission);
    });
  }

  /**
   * Both continuous reverse-geocode paths (broadcast-driven and retry-driven)
   * report through here so neither can fail silently. Manual refresh state is
   * left untouched: a background failure must not rewrite it.
   */
  private continuousGeocodeFailed(error: Object, request: ContinuousGeocode, lifecycle: number, token: number, permission: number): void {
    const currentRequest = this.continuousGeocode === request;
    if (currentRequest) this.continuousGeocode = null;
    const code = this.errorCode(error);
    const reason = this.reasonFromFailure(error, 'address-unavailable');
    console.info('SITECAM_LOCATION_CONTINUOUS_FAILED code=' + code + ' reason=' + reason);
    if (!currentRequest || !this.listening || this.lifecycleEpoch !== lifecycle || this.refreshToken !== token || this.permissionEpoch !== permission || this.refreshState === 'refreshing') return;
    if (reason === 'permission-denied' || reason === 'location-disabled') {
      this.invalidateLocation(reason);
      return;
    }
    // Keep the fix, but surface why no address accompanies it.
    if (reason === 'address-unavailable' && this.failureReason !== reason) {
      this.failureReason = reason;
      this.notifyChange();
    }
  }

  private commitContinuousAddress(current: Fix, origin: Fix, address: string): void {
    current.address = address;
    this.addressAnchor = { latitude: origin.latitude, longitude: origin.longitude, timestamp: origin.timestamp, address, resolvedAt: this.now() };
    if (this.hasUsableAddress()) {
      this.refreshState = 'success';
      this.failureReason = '';
      this.lastPlatformFailure = '';
    }
    this.notifyChange();
  }

  private fixFromAnchor(anchor: AddressAnchor): Fix {
    const fix = new Fix();
    fix.latitude = anchor.latitude;
    fix.longitude = anchor.longitude;
    fix.timestamp = anchor.timestamp;
    return fix;
  }

  hasUsableAddress(now: number = this.now()): boolean {
    if (!this.fix || !this.addressAnchor || !this.addressAnchor.address.trim()) return false;
    const origin = this.fixFromAnchor(this.addressAnchor);
    return isFreshFix(origin, now) && isCaptureFreshFix(this.fix, now) &&
      movedMeters(origin, this.fix) < GEOCODE_MIN_MOVE_M && this.fix.address === this.addressAnchor.address;
  }

  /** Snapshot input with address provenance checked at the moment it is read. */
  snapshotFixForWatermark(): Fix | null {
    if (!this.fix) return null;
    const safe = JSON.parse(JSON.stringify(this.fix)) as Fix;
    if (!this.hasUsableAddress()) safe.address = '';
    return safe;
  }

  private resolveContinuous(fix: Fix): void {
    if (this.continuousGeocode || this.refreshState === 'refreshing') return;
    if (this.geocodeSkipApplies(fix)) return;
    const lifecycle = this.lifecycleEpoch;
    const token = this.refreshToken;
    const permission = this.permissionEpoch;
    this.lastGeocodeFix = fix;
    this.lastGeocodeAt = this.now();
    const request: ContinuousGeocode = { origin: fix, lifecycle, token, permission };
    this.continuousGeocode = request;
    this.reverseAddress(fix).then((address: string) => {
      if (this.continuousGeocode === request) this.continuousGeocode = null;
      const current = this.fix;
      if (this.listening && this.lifecycleEpoch === lifecycle && this.refreshToken === token && this.permissionEpoch === permission && this.refreshState !== 'refreshing' && current && this.retainedFix(current) && this.retainedFix(fix) && movedMeters(fix, current) < GEOCODE_MIN_MOVE_M) {
        this.commitContinuousAddress(current, fix, address);
      } else if (this.listening && this.lifecycleEpoch === lifecycle && this.refreshToken === token && this.permissionEpoch === permission && current && this.retainedFix(current) && this.refreshState !== 'refreshing') {
        this.resolveContinuous(current);
      }
    }).catch((error: Object) => {
      this.continuousGeocodeFailed(error, request, lifecycle, token, permission);
    });
  }

  private geocodeSkipApplies(fix: Fix): boolean {
    if (this.lastGeocodeFix === null) return false;
    if (this.now() - this.lastGeocodeAt > GEOCODE_MIN_INTERVAL_MS) return false;
    return movedMeters(this.lastGeocodeFix, fix) < GEOCODE_MIN_MOVE_M;
  }

  start(): void {
    if (this.listening) return;
    try {
      this.verifyLocationAccess();
      if (this.fix && !this.retainedFix(this.fix)) {
        this.fix = null;
        this.addressAnchor = null;
      } else if (this.fix && this.addressAnchor && !this.retainedFix(this.fixFromAnchor(this.addressAnchor))) {
        this.addressAnchor = null;
        this.fix.address = '';
      }
    } catch (e) {
      this.invalidateLocation(this.reasonFromFailure(e as Object, 'permission-denied'));
      return;
    }
    let locationListening = false;
    let errorListening = false;
    let enabledListening = false;
    try {
      geoLocationManager.on('locationChange', {
        priority: geoLocationManager.LocationRequestPriority.FIRST_FIX,
        scenario: geoLocationManager.LocationRequestScenario.UNSET,
        timeInterval: 10,
        distanceInterval: 0,
        maxAccuracy: 0
      }, this.receive);
      locationListening = true;
      geoLocationManager.on('locationError', this.locationError);
      errorListening = true;
      geoLocationManager.on('locationEnabledChange', this.locationEnabledChanged);
      enabledListening = true;
      this.listening = true;
      this.listenRetryAfter = 0;
      console.info('SITECAM_LOCATION_LISTENING interval=10 distance=0');
      try { this.locationEnabledChanged(this.locationEnabled()); } catch (e) {}
    } catch (e) {
      const failure = e as LocationPlatformError;
      console.warn('SITECAM_LOCATION_LISTEN_FAILED code=' + this.errorCode(e as Object) + ' message=' + (failure.message ?? String(e)));
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
      try {
        this.onListenFailed?.('listen-failed:' + String(this.errorCode(e as Object)));
      } catch (callbackError) {
        console.warn('SITECAM_LOCATION_LISTEN_CALLBACK ' + String(callbackError));
      }
    }
  }

  /**
   * Retry entry for the page clock. It never throws and never retries more
   * often than the back-off allows, so a rejected subscription still reaches
   * the page instead of leaving the session with one-shot fixes only.
   */
  ensureListening(): void {
    if (this.listening) return;
    if (this.now() < this.listenRetryAfter) return;
    try {
      this.start();
    } catch (e) {
      console.warn('SITECAM_LOCATION_LISTEN_FAILED code=' + this.errorCode(e as Object) + ' message=' + String(e));
    }
    if (!this.listening) this.listenRetryAfter = this.now() + LISTEN_RETRY_BACKOFF_MS;
  }

  stop(preserveFix: boolean = false): void {
    if (this.locationNotifyTimer >= 0) {
      clearTimeout(this.locationNotifyTimer);
      this.locationNotifyTimer = -1;
      this.notifyChange();
    }
    this.lifecycleEpoch++;
    this.refreshToken++;
    this.continuousGeocode = null;
    const preserve = preserveFix && this.fix !== null && this.retainedFix(this.fix);
    const staleAddress = preserve && this.addressAnchor !== null && !this.retainedFix(this.fixFromAnchor(this.addressAnchor));
    const hadFix = !preserve && this.fix !== null;
    if (!preserve) {
      this.fix = null;
      this.addressAnchor = null;
      this.lastGeocodeFix = null;
      this.lastGeocodeAt = 0;
    } else if (staleAddress) {
      this.addressAnchor = null;
      if (this.fix) this.fix.address = '';
    }
    if (hadFix || staleAddress) this.notifyChange();
    if (this.refreshState === 'refreshing') {
      this.refreshState = 'idle';
      this.failureReason = '';
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
