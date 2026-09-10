import { UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
export default class TestAbility extends UIAbility {onCreate(want: Want): void {AppStorage.setOrCreate('seedQA',want.parameters?.seedQA===true);}
onWindowStageCreate(stage: window.WindowStage): void {stage.loadContent('testability/pages/Index');}}
