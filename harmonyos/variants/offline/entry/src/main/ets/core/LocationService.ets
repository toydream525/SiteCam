import { geoLocationManager } from '@kit.LocationKit';
import { abilityAccessCtrl, bundleManager } from '@kit.AbilityKit';
import { Fix, usableAltitude } from './Models';

export type LocationRefreshState = 'idle' | 'refreshing' | 'success' | 'failed';
export type LocationFailureReason = '' | 'permission-denied' | 'location-disabled' | 'location-timeout' | 'address-unavailable';

const MAX_AGE_MS: number = 30 * 1000;
const FUTURE_TOLERANCE_MS: number = 30 * 1000;
const REQUEST_TIMEOUT_MS: number = 30 * 1000;

function freshGnss(fix: Fix | null, now: number = Date.now()): boolean {
  if (!fix || fix.sourceType !== geoLocationManager.LocationSourceType.GNSS) return false;
  if (!Number.isFinite(fix.latitude) || !Number.isFinite(fix.longitude) || !Number.isFinite(fix.timestamp)) return false;
  if (fix.latitude < -90 || fix.latitude > 90 || fix.longitude < -180 || fix.longitude > 180) return false;
  const age = now - fix.timestamp;
  return age >= -FUTURE_TOLERANCE_MS && age <= MAX_AGE_MS;
}

interface PlatformFailure { code?: number; }

class RefreshFailure extends Error {
  reason: LocationFailureReason;
  constructor(reason: LocationFailureReason) {
    super(reason);
    this.reason = reason;
  }
}

function reasonForCode(code: number, fallback: LocationFailureReason): LocationFailureReason {
  if (code === 201 || code === -2) return 'permission-denied';
  if (code === 3301100 || code === -4) return 'location-disabled';
  return fallback;
}

function reasonForError(error: Object, fallback: LocationFailureReason): LocationFailureReason {
  const code = (error as PlatformFailure).code;
  return reasonForCode(code === undefined ? 0 : code, fallback);
}

/** Offline-only location service: it accepts fresh GNSS fixes and never resolves addresses. */
export class LocationService {
  private activeFix: Fix | null = null;
  get fix(): Fix | null {
    if (!freshGnss(this.activeFix)) {
      if (this.activeFix !== null) {
        this.activeFix = null;
        this.refreshState = 'failed';
        this.failureReason = 'location-timeout';
      }
      return null;
    }
    try {
      this.verifyAccess();
    } catch (error) {
      const failure = error as RefreshFailure;
      this.activeFix = null;
      this.refreshState = 'failed';
      this.failureReason = failure.reason || reasonForError(error as Object, 'location-timeout');
      return null;
    }
    return this.activeFix;
  }

  refreshState: LocationRefreshState = 'idle';
  failureReason: LocationFailureReason = '';
  onChange: (() => void) | null = null;
  listening: boolean = false;
  private lifecycleEpoch: number = 0;
  private refreshToken: number = 0;
  private refreshPromise: Promise<boolean> | null = null;

  private notifyChange(): void {
    try { this.onChange?.(); } catch (error) { console.warn('SITECAM_OFFLINE_LOCATION_CHANGE ' + String(error)); }
  }

  private locationEnabled(): boolean {
    try { return geoLocationManager.isLocationEnabled(); }
    catch (error) { throw new RefreshFailure(reasonForError(error as Object, 'location-timeout')); }
  }

  isSystemLocationEnabled(): boolean { return this.locationEnabled(); }

  private hasPrecisePermission(): boolean {
    const info = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
    const token = info.appInfo.accessTokenId;
    const manager = abilityAccessCtrl.createAtManager();
    return manager.checkAccessTokenSync(token, 'ohos.permission.LOCATION') === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
  }

  private verifyAccess(): void {
    if (!this.locationEnabled()) throw new RefreshFailure('location-disabled');
    if (!this.hasPrecisePermission()) throw new RefreshFailure('permission-denied');
  }

  private toGnssFix(value: geoLocationManager.Location): Fix | null {
    if (value.sourceType !== geoLocationManager.LocationSourceType.GNSS) return null;
    if (!Number.isFinite(value.latitude) || !Number.isFinite(value.longitude) || !Number.isFinite(value.timeStamp)) return null;
    const fix = new Fix();
    fix.latitude = value.latitude;
    fix.longitude = value.longitude;
    fix.altitude = usableAltitude(value.altitude, value.altitudeAccuracy);
    fix.accuracy = Number.isFinite(value.accuracy) ? value.accuracy : 0;
    fix.timestamp = value.timeStamp;
    fix.sourceType = value.sourceType;
    return freshGnss(fix) ? fix : null;
  }

  private setFailure(reason: LocationFailureReason, token: number): boolean {
    if (token !== this.refreshToken) return false;
    this.refreshState = 'failed';
    this.failureReason = reason;
    if (reason === 'permission-denied' || reason === 'location-disabled') this.activeFix = null;
    this.notifyChange();
    return false;
  }

  private async performRefresh(token: number, lifecycle: number): Promise<boolean> {
    try {
      this.verifyAccess();
      const value = await geoLocationManager.getCurrentLocation({
        priority: geoLocationManager.LocationRequestPriority.ACCURACY,
        scenario: geoLocationManager.LocationRequestScenario.UNSET,
        maxAccuracy: 0,
        timeoutMs: REQUEST_TIMEOUT_MS
      });
      if (token !== this.refreshToken || lifecycle !== this.lifecycleEpoch || this.refreshState !== 'refreshing') return false;
      this.verifyAccess();
      const candidate = this.toGnssFix(value);
      const received = freshGnss(this.activeFix) ? this.activeFix : null;
      const newest = received && candidate ? (received.timestamp >= candidate.timestamp ? received : candidate) : (received || candidate);
      if (!newest) return this.setFailure('location-timeout', token);
      this.activeFix = newest;
      this.refreshState = 'success';
      this.failureReason = '';
      this.notifyChange();
      return true;
    } catch (error) {
      if (token !== this.refreshToken || lifecycle !== this.lifecycleEpoch || this.refreshState !== 'refreshing') return false;
      const failure = error as RefreshFailure;
      const reason = failure.reason || reasonForError(error as Object, 'location-timeout');
      return this.setFailure(reason, token);
    }
  }

  refreshAddress(): Promise<boolean> {
    if (this.refreshPromise) return this.refreshPromise;
    const token = ++this.refreshToken;
    const lifecycle = this.lifecycleEpoch;
    this.refreshState = 'refreshing';
    this.failureReason = '';
    this.notifyChange();
    const promise = this.performRefresh(token, lifecycle);
    this.refreshPromise = promise;
    promise.then(() => { if (this.refreshPromise === promise) this.refreshPromise = null; }, () => { if (this.refreshPromise === promise) this.refreshPromise = null; });
    return promise;
  }

  private receive = (value: geoLocationManager.Location): void => {
    if (!this.listening) return;
    try { this.verifyAccess(); } catch (error) {
      const failure = error as RefreshFailure;
      this.activeFix = null;
      this.refreshState = 'failed';
      this.failureReason = failure.reason || 'location-timeout';
      this.notifyChange();
      return;
    }
    const fix = this.toGnssFix(value);
    if (!fix) return;
    const previous = this.fix;
    if (previous && fix.timestamp <= previous.timestamp) return;
    this.activeFix = fix;
    if (this.refreshState !== 'refreshing') {
      this.refreshState = 'success';
      this.failureReason = '';
    }
    this.notifyChange();
  };

  private locationError = (error: geoLocationManager.LocationError): void => {
    const reason = reasonForCode(error as number, 'location-timeout');
    if (reason === 'permission-denied' || reason === 'location-disabled') {
      this.activeFix = null;
      this.refreshState = 'failed';
      this.failureReason = reason;
      this.refreshToken++;
      this.notifyChange();
    }
  };

  private locationEnabledChanged = (enabled: boolean): void => {
    if (!enabled) {
      this.activeFix = null;
      this.refreshState = 'failed';
      this.failureReason = 'location-disabled';
      this.refreshToken++;
      this.notifyChange();
    } else if (this.failureReason === 'location-disabled') {
      this.refreshState = 'idle';
      this.failureReason = '';
      this.notifyChange();
    }
  };

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
      this.verifyAccess();
    } catch (error) {
      if (locationListening) { try { geoLocationManager.off('locationChange', this.receive); } catch (ignored) {} }
      if (errorListening) { try { geoLocationManager.off('locationError', this.locationError); } catch (ignored) {} }
      if (enabledListening) { try { geoLocationManager.off('locationEnabledChange', this.locationEnabledChanged); } catch (ignored) {} }
      this.listening = false;
      const failure = error as RefreshFailure;
      this.failureReason = failure.reason || reasonForError(error as Object, 'location-timeout');
      this.refreshState = 'failed';
      this.notifyChange();
    }
  }

  stop(): void {
    this.lifecycleEpoch++;
    this.refreshToken++;
    const hadFix = this.activeFix !== null;
    this.activeFix = null;
    if (this.refreshState === 'refreshing') {
      this.refreshState = 'failed';
      this.failureReason = 'location-timeout';
      this.notifyChange();
    }
    if (hadFix) this.notifyChange();
    if (!this.listening) return;
    try { geoLocationManager.off('locationChange', this.receive); } catch (ignored) {}
    try { geoLocationManager.off('locationError', this.locationError); } catch (ignored) {}
    try { geoLocationManager.off('locationEnabledChange', this.locationEnabledChanged); } catch (ignored) {}
    this.listening = false;
  }
}
