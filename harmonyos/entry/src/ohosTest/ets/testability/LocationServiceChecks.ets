import { Fix } from '../../../main/ets/core/Models';
import { addressFromGeoAddresses, isFreshFix, LocationService, LocationServiceDependencies } from '../../../main/ets/core/LocationService';
import { geoLocationManager } from '@kit.LocationKit';

function requireResult(value: boolean, message: string): void {
  if (!value) throw new Error(message);
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
  }
}
