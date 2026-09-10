import { geoLocationManager } from '@kit.LocationKit';
import { Fix } from './Models';
export class LocationService {
  fix: Fix|null=null;
  private listening: boolean=false;
  private receive=(v: geoLocationManager.Location): void=> {const f=new Fix();f.latitude=v.latitude;f.longitude=v.longitude;f.altitude=v.altitude;f.accuracy=v.accuracy;f.timestamp=v.timeStamp;this.fix=f;
    geoLocationManager.getAddressesFromLocation({latitude:f.latitude,longitude:f.longitude,maxItems:1}).then(rows=>{if(this.fix===f&&rows.length)f.address=rows[0].placeName||rows[0].descriptions?.join('')||'';}).catch(()=>{});
  };
  start(): void {if(this.listening)return;try {geoLocationManager.on('locationChange',{priority:geoLocationManager.LocationRequestPriority.FIRST_FIX,scenario:geoLocationManager.LocationRequestScenario.UNSET,timeInterval:10,distanceInterval:5,maxAccuracy:0},this.receive);this.listening=true;}catch(e){this.listening=false;}}
  stop(): void {if(this.listening){try {geoLocationManager.off('locationChange',this.receive);}catch(e){}this.listening=false;}}
}
