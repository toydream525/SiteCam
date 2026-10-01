import { Fix } from '../../../main/ets/core/Models';
import { addressFromGeoAddresses, isFreshFix, LocationService, LocationServiceDependencies, movedMeters, usableAltitude } from '../../../main/ets/core/LocationService';
import { geoLocationManager } from '@kit.LocationKit';

function requireResult(value: boolean, message: string): void {
  if (!value) throw new Error(message);
}

/** Platform error stand-in: the service classifies failures by the error code. */
class PlatformFailure extends Error {
  code: number = 3301200;
  constructor() { super('timeout'); }
}

function located(latitude: number, longitude: number, altitude: number, timeStamp: number): geoLocationManager.Location {
  return {
    latitude: latitude,
    longitude: longitude,
    altitude: altitude,
    accuracy: 3,
    speed: 0,
    timeStamp: timeStamp,
    direction: 0,
    timeSinceBoot: 0
  };
}

/** Pure service checks for the freshness and reverse-geocoder boundaries. */
export class LocationServiceChecks {
  static check(): void {
    const now = 1_000_000;
    const fresh = new Fix();
    fresh.latitude = 30.5;
    fresh.longitude = 120.25;
    fresh.timestamp = now;
    requireResult(isFreshFix(fresh, now), '当前定位被判为过期');
    requireResult(isFreshFix(fresh, now + 5 * 60 * 1000), '五分钟边界定位被拒绝');
    requireResult(!isFreshFix(fresh, now + 5 * 60 * 1000 + 1), '过期定位仍被接受');
    requireResult(!isFreshFix(fresh, now - 30 * 1000 - 1), '未来定位仍被接受');
    const invalid = new Fix();
    invalid.latitude = 91;
    invalid.longitude = 120;
    invalid.timestamp = now;
    requireResult(!isFreshFix(invalid, now), '越界定位仍被接受');
    requireResult(addressFromGeoAddresses([]) === '', '空地址结果未保持为空');
    const place: geoLocationManager.GeoAddress = { placeName: '  现场地址  ' };
    requireResult(addressFromGeoAddresses([place]) === '现场地址', '地点名未规范化');
    const details: geoLocationManager.GeoAddress = { administrativeArea: '浙江省', locality: '杭州市', roadName: '滨江路' };
    requireResult(addressFromGeoAddresses([details]) === '浙江省杭州市滨江路', '结构化地址未拼接');
    // 海拔可用性：海平面 0 m 必须带垂直精度才算有效读数，否则保留上一版真实海拔
    requireResult(usableAltitude(0, undefined) === null, '海拔 0 且无垂直精度被当作有效读数');
    requireResult(usableAltitude(0, 5) === 0, '有垂直精度的海平面读数被丢弃');
    requireResult(usableAltitude(-400, 3) === -400, '死海负海拔被丢弃');
    requireResult(usableAltitude(88.5, undefined) === 88.5, '有效海拔被丢弃');
    requireResult(usableAltitude(Number.NaN, 3) === null, 'NaN 海拔未被丢弃');
    const pointA = new Fix();
    pointA.latitude = 30;
    pointA.longitude = 120;
    const pointB = new Fix();
    pointB.latitude = 30.0001;
    pointB.longitude = 120;
    requireResult(movedMeters(pointA, pointA) === 0, '同点位移不为 0');
    requireResult(movedMeters(pointA, pointB) > 5 && movedMeters(pointA, pointB) < 20, '位移计算超出预期范围');
  }

  /** Service-level checks use only the injected platform seams and no user data. */
  static async serviceBoundaries(): Promise<void> {
    const location: geoLocationManager.Location = {
      latitude: 30.5,
      longitude: 120.25,
      altitude: 10,
      accuracy: 3,
      speed: 0,
      timeStamp: Date.now(),
      direction: 0,
      timeSinceBoot: 0
    };
    let permission = true;
    let permissionChecks = 0;
    let requests = 0;
    const dependencies: LocationServiceDependencies = {
      hasLocationPermission: (): boolean => {
        permissionChecks++;
        return permission;
      },
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => {
        requests++;
        return location;
      },
      reverseAddress: async (_fix: Fix): Promise<string> => '无缓存已授权地址'
    };
    const service = new LocationService(dependencies);
    requireResult(service.fix === null, '无缓存测试不是从空定位开始');
    requireResult(await service.refreshAddress(), '已授权且无缓存时未请求当前位置');
    requireResult(requests === 1 && permissionChecks >= 3, '无缓存定位请求或提交点权限检查缺失');
    requireResult(service.fix !== null && service.fix.address === '无缓存已授权地址', '无缓存定位成功后地址未提交');

    // 验证再次刷新地址能够重新触发定位并刷新最新海拔高度
    location.altitude = 88.5;
    location.timeStamp = Date.now() + 1000;
    requireResult(await service.refreshAddress(), '再次刷新地址与海拔失败');
    requireResult(requests === 2, '再次刷新地址未发起新定位请求');
    requireResult(service.fix !== null && service.fix.altitude === 88.5, '刷新地址未更新最新海拔高度');

    let disabledRequests = 0;
    const disabledService = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => false,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => {
        disabledRequests++;
        return located(30, 120, 10, Date.now());
      },
      reverseAddress: async (_fix: Fix): Promise<string> => '不得使用的定位地址'
    });
    disabledService.fix = new Fix();
    disabledService.fix.latitude = 30;
    disabledService.fix.longitude = 120;
    disabledService.fix.timestamp = Date.now();
    requireResult(!(await disabledService.refreshAddress()), '系统定位关闭时仍报告定位成功');
    requireResult(disabledService.failureReason === 'location-disabled', '系统定位关闭未单独分类');
    requireResult(disabledRequests === 0 && disabledService.fix === null, '系统定位关闭时使用了缓存定位');

    let reverseStarted: (() => void) | null = null;
    const reverseStartedPromise = new Promise<void>((resolve) => { reverseStarted = resolve; });
    let finishReverse: (value: string) => void = (_value: string): void => {};
    const lateAddress = new Promise<string>((resolve) => { finishReverse = resolve; });
    permission = true;
    permissionChecks = 0;
    const revoked = new LocationService({
      hasLocationPermission: (): boolean => {
        permissionChecks++;
        return permission;
      },
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => location,
      reverseAddress: async (_fix: Fix): Promise<string> => {
        reverseStarted?.();
        return lateAddress;
      }
    });
    const refresh = revoked.refreshAddress();
    await reverseStartedPromise;
    permission = false;
    finishReverse('撤权后的迟到地址');
    requireResult(!(await refresh), '反解中撤权仍被视为成功');
    requireResult(permissionChecks >= 3 && revoked.fix === null, '反解中撤权未在提交点清理定位');

    // 竞态一：逆地理等待期间到达的较新连续定位解，不得被较旧的硬件解覆盖
    const raceBase = Date.now();
    let deliverHardware: (value: geoLocationManager.Location) => void = (_value: geoLocationManager.Location): void => {};
    const hardwarePending = new Promise<geoLocationManager.Location>((resolve) => { deliverHardware = resolve; });
    const race = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: (): Promise<geoLocationManager.Location> => hardwarePending,
      reverseAddress: async (_fix: Fix): Promise<string> => '竞态地址'
    });
    race.listening = true;
    const raceRefresh = race.refreshAddress();
    const newerStamp = raceBase + 5000;
    race.receiveLocation(located(31, 121, 88.5, newerStamp));
    deliverHardware(located(30.5, 120.25, 12, raceBase));
    requireResult(await raceRefresh, '竞态场景刷新失败');
    requireResult(race.fix !== null && race.fix.timestamp === newerStamp, '较旧的硬件解覆盖了较新的连续定位解');
    requireResult(race.fix !== null && race.fix.altitude === 88.5, '较新定位解的海拔被较旧硬件解覆盖');

    // 竞态二：硬件定位失败必须如实上报，不能拿缓存顶替成功
    let hardwareFails = false;
    const hardware = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => {
        if (hardwareFails) throw new PlatformFailure();
        return located(30, 120, 10, Date.now());
      },
      reverseAddress: async (_fix: Fix): Promise<string> => '硬件失败场景地址'
    });
    hardware.listening = true;
    requireResult(await hardware.refreshAddress(), '硬件正常时刷新失败');
    hardwareFails = true;
    requireResult(!(await hardware.refreshAddress()), '硬件超时被误报为刷新成功');
    requireResult(hardware.refreshState === 'failed' && hardware.failureReason === 'location-timeout', '硬件超时状态未如实上报');
    requireResult(hardware.fix !== null && hardware.fix.altitude === 10, '硬件超时后有效定位被清空');

    // 竞态三：两次重试期间连续到达两次广播，未解析到最新解的地址不能绑定到它。
    const burstBase = Date.now();
    let burstStep = 0;
    const burst: LocationService = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => located(30, 120, 5, burstBase),
      reverseAddress: async (_fix: Fix): Promise<string> => {
        burstStep++;
        if (burstStep === 1) {
          burst.receiveLocation(located(30.1, 120.1, 6, burstBase + 1000));
          return '突发地址A';
        }
        if (burstStep === 2) {
          burst.receiveLocation(located(30.2, 120.2, 7, burstBase + 2000));
          return '突发地址B';
        }
        return '突发地址C';
      }
    });
    burst.listening = true;
    requireResult(!(await burst.refreshAddress()), '未解析最新定位地址仍报告刷新成功');
    requireResult(burst.failureReason === 'address-unavailable', '耗尽地址解析重试后未标记地址不可用');
    requireResult(burst.fix !== null && burst.fix.timestamp === burstBase + 2000, '连续广播下未提交最新定位解');
    requireResult(burst.fix !== null && burst.fix.altitude === 7, '连续广播下最新海拔未提交');
    requireResult(burst.fix !== null && burst.fix.address === '', '较早定位的地址被绑定到了最新定位解');

    // A successful address belongs to its original coordinates. Timestamp-only
    // callbacks at the same place inherit it without another geocoder call.
    let continuousCalls = 0;
    const continuous = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => located(30, 120, 10, Date.now()),
      reverseAddress: async (_fix: Fix): Promise<string> => { continuousCalls++; return '锚点地址'; }
    });
    continuous.listening = true;
    requireResult(await continuous.refreshAddress(), '锚点初次反查失败');
    const callsAfterAnchor = continuousCalls;
    continuous.receiveLocation(located(30, 120, 11, Date.now() + 1000));
    requireResult(continuous.fix !== null && continuous.fix.address === '锚点地址', '同点新时间戳未继承已验证地址');
    requireResult(continuousCalls === callsAfterAnchor, '同点广播再次触发反查');

    // During one continuous lookup, same-area broadcasts replace the current
    // Fix object. The result must land on the latest Fix rather than be lost.
    let finishContinuous: (value: string) => void = (_value: string): void => {};
    const pendingContinuous = new Promise<string>((resolve) => { finishContinuous = resolve; });
    const coalesced = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      reverseAddress: async (_fix: Fix): Promise<string> => pendingContinuous
    });
    coalesced.listening = true;
    const coalesceBase = Date.now();
    coalesced.receiveLocation(located(30, 120, 1, coalesceBase));
    coalesced.receiveLocation(located(30.00001, 120, 2, coalesceBase + 1000));
    finishContinuous('合并后的地址');
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    requireResult(coalesced.fix !== null && coalesced.fix.address === '合并后的地址', '同点突发广播导致反查结果丢失');

    // Moving beyond the original anchor clears it. Small movements are always
    // measured from that original point, so they cannot extend its lifetime.
    continuous.receiveLocation(located(30.0003, 120, 12, Date.now() + 2000));
    requireResult(continuous.fix !== null && continuous.fix.address === '', '超过 20 米仍沿用旧地址');

    // stop(true) is the camera-to-watermark handoff: it keeps only a fresh fix
    // and validated anchor. Default stop and refresh cancellation clear state.
    const handoff = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => located(30, 120, 10, Date.now()),
      reverseAddress: async (_fix: Fix): Promise<string> => '交接地址'
    });
    requireResult(await handoff.refreshAddress(), '交接地址反查失败');
    handoff.stop(true);
    handoff.start();
    requireResult(handoff.fix !== null && handoff.fix.address === '交接地址', 'camera→watermark 未保留新鲜地址');
    handoff.stop();
    requireResult(handoff.fix === null, '普通 stop 未清除定位');

    let finishStoppedRefresh: (value: string) => void = (_value: string): void => {};
    const stoppedLookup = new Promise<string>((resolve) => { finishStoppedRefresh = resolve; });
    const stopping = new LocationService({
      hasLocationPermission: (): boolean => true,
      isLocationEnabled: (): boolean => true,
      getCurrentLocation: async (): Promise<geoLocationManager.Location> => located(30, 120, 10, Date.now()),
      reverseAddress: async (_fix: Fix): Promise<string> => stoppedLookup
    });
    const stoppedRefresh = stopping.refreshAddress();
    stopping.stop();
    requireResult(!(await stoppedRefresh) && stopping.refreshState === 'idle' && stopping.failureReason === '', 'stop 后手动刷新状态未回到 idle');
    finishStoppedRefresh('stop 后的迟到地址');
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    requireResult(stopping.fix === null, 'stop 后迟到反查回填了定位');
  }
}
