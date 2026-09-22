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

    // 竞态三：逆地理期间连续到达两次广播，必须提交最新解而不是误判失败
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
    requireResult(await burst.refreshAddress(), '连续广播下刷新被误判失败');
    requireResult(burst.failureReason === '', '连续广播下刷新留下了失败原因');
    requireResult(burst.fix !== null && burst.fix.timestamp === burstBase + 2000, '连续广播下未提交最新定位解');
    requireResult(burst.fix !== null && burst.fix.altitude === 7, '连续广播下最新海拔未提交');
  }
}
