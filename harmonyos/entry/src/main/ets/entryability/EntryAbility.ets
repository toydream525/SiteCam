import { UIAbility, Configuration, ConfigurationConstant } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { WindowEnvironmentController } from '../core/WindowEnvironment';
export default class EntryAbility extends UIAbility {
  private environment: WindowEnvironmentController = new WindowEnvironmentController();
  onCreate(): void { this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_DARK); }
  onWindowStageDestroy(): void { this.environment.detach(); }
  onWindowStageCreate(stage: window.WindowStage): void { console.info(`SITECAM_FONT_CONFIG fontSizeScale=${this.context.config.fontSizeScale} maxScale=3.2`); AppStorage.setOrCreate('fontSizeScale',this.context.config.fontSizeScale||1);stage.loadContent('pages/Index');window.getLastWindow(this.context).then((win: window.Window)=>{this.environment.attach(win).catch((e: Error)=>console.error('SITECAM_WINDOW '+e.message));win.setWindowBackgroundColor('#121212');return win.setWindowSystemBarProperties({statusBarColor:'#121212',navigationBarColor:'#121212',statusBarContentColor:'#FFFFFF',navigationBarContentColor:'#FFFFFF',isStatusBarLightIcon:false,isNavigationBarLightIcon:false});}).catch((e: Error)=>console.error('SITECAM_BARS '+e.message)); }
  onConfigurationUpdate(config: Configuration): void { console.info(`SITECAM_FONT_CONFIG_UPDATE fontSizeScale=${config.fontSizeScale} maxScale=3.2`);AppStorage.setOrCreate('fontSizeScale',config.fontSizeScale||1);}
  onForeground(): void { AppStorage.setOrCreate('foreground',true); }
  onBackground(): void { AppStorage.setOrCreate('foreground',false); }
}
