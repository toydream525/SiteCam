import { media } from '@kit.MediaKit';
import { audio } from '@kit.AudioKit';
import { common } from '@kit.AbilityKit';

interface SoundPoolLoadWait {
  promise: Promise<void>;
  setExpected: (soundId: number) => void;
  cancel: () => void;
}

/** Plays the local shutter cue without joining the video microphone path. */
export class ShutterSoundService {
  private pool: media.SoundPool|null = null;
  private soundId: number = 0;
  private preparing: Promise<void>|null = null;
  private active: boolean = true;
  private lifecycleToken: number = 0;
  private cancelLoadWait: (() => void)|null = null;
  private playing: number = 0;
  private playWaiters: Array<() => void> = [];
  private streams: number[] = [];
  /** Serializes disposal with any later prepare, including quick background/foreground changes. */
  private resourceBarrier: Promise<void> = Promise.resolve();

  private createLoadWait(pool: media.SoundPool, token: number): SoundPoolLoadWait {
    let expectedId = 0;
    let completedId = 0;
    let finished = false;
    let timeout = -1;
    let cancel: () => void = () => {};
    let resolveWait: () => void = () => {};
    let rejectWait: (error: Error) => void = () => {};
    const finish = (error?: Error): void => {
      if (finished) return;
      finished = true;
      if (timeout >= 0) clearTimeout(timeout);
      try { pool.off('loadComplete'); } catch (e) {}
      try { pool.off('error'); } catch (e) {}
      if (this.cancelLoadWait === cancel) this.cancelLoadWait = null;
      if (error) rejectWait(error); else resolveWait();
    };
    const promise = new Promise<void>((resolve, reject) => {
      resolveWait = resolve;
      rejectWait = reject;
    });
    // The load Promise may settle before the caller can await the event-backed
    // promise. Attach a handler now while preserving the original rejection.
    promise.catch(() => {});
    const onLoaded = (loadedId: number): void => {
      completedId = loadedId;
      if (expectedId > 0 && loadedId === expectedId) finish();
    };
    const onError = (error: Error): void => finish(error);
    cancel = (): void => finish(new Error('快门音加载已取消'));
    this.cancelLoadWait = cancel;
    pool.on('loadComplete', onLoaded);
    pool.on('error', onError);
    if (!this.active || token !== this.lifecycleToken) {
      finish(new Error('快门音生命周期已结束'));
    } else {
      timeout = setTimeout(() => finish(new Error('快门音加载超时')), 2500);
    }
    return {
      promise: promise,
      setExpected: (soundId: number): void => {
        expectedId = soundId;
        if (completedId === soundId) finish();
      },
      cancel: cancel
    };
  }

  private async load(context: common.Context, token: number): Promise<void> {
    const pool = await media.createSoundPool(1, {
      // SoundPool's documented mixing usages are UNKNOWN/MUSIC/MOVIE/
      // AUDIOBOOK. MUSIC keeps the local shutter cue in the media path and
      // avoids treating it as a notification or system alert.
      usage: audio.StreamUsage.STREAM_USAGE_MUSIC,
      rendererFlags: 0
    });
    const rawName = 'camera_shutter.wav';
    let wait: SoundPoolLoadWait|null = null;
    try {
      if (!this.active || token !== this.lifecycleToken) throw new Error('快门音生命周期已结束');
      const raw = await context.resourceManager.getRawFd(rawName);
      try {
        // Register before load: a short raw sound can emit loadComplete before
        // the load Promise has returned its sound ID.
        wait = this.createLoadWait(pool, token);
        const soundId = await pool.load(raw.fd, raw.offset, raw.length);
        if (soundId <= 0) throw new Error('快门音资源加载失败');
        wait.setExpected(soundId);
        await wait.promise;
        wait = null;
        if (!this.active || token !== this.lifecycleToken) throw new Error('快门音生命周期已结束');
        this.pool = pool;
        this.soundId = soundId;
      } finally {
        await context.resourceManager.closeRawFd(rawName);
      }
    } catch (e) {
      wait?.cancel();
      try { await pool.release(); } catch (releaseError) { console.warn('SITECAM_SHUTTER_RELEASE ' + String(releaseError)); }
      throw e as Error;
    }
  }

  private async prepare(context: common.Context, token: number): Promise<void> {
    const barrier = this.resourceBarrier;
    await barrier;
    if (!this.active || token !== this.lifecycleToken) throw new Error('快门音生命周期已结束');
    if (this.pool && this.soundId > 0) return;
    if (this.preparing) return this.preparing;
    const task = this.load(context, token);
    this.preparing = task;
    try {
      await task;
    } finally {
      if (this.preparing === task) this.preparing = null;
    }
  }

  /** Warm the pool while the camera is idle, so the first accepted capture is not silent. */
  async warmUp(context: common.Context): Promise<void> {
    if (!this.active) return;
    const token = this.lifecycleToken;
    try {
      await this.prepare(context, token);
    } catch (e) {
      console.warn('SITECAM_SHUTTER_PRELOAD ' + String(e));
    }
  }

  /** Never rejects into the capture flow: a sound failure must not fail a photo. */
  async play(context: common.Context): Promise<void> {
    if (!this.active) return;
    const token = this.lifecycleToken;
    let counted = false;
    try {
      await this.prepare(context, token);
      if (!this.active || token !== this.lifecycleToken) return;
      this.playing++;
      counted = true;
      const pool = this.pool;
      const soundId = this.soundId;
      if (pool && soundId > 0) {
        const stream = await pool.play(soundId, { loop: 0, leftVolume: 1, rightVolume: 1 });
        if (!this.active || token !== this.lifecycleToken) {
          if (stream > 0) {
            try { await pool.stop(stream); } catch (e) {}
          }
        } else if (stream > 0) {
          this.streams.push(stream);
        }
      }
    } catch (e) {
      console.warn('SITECAM_SHUTTER_SOUND ' + String(e));
    } finally {
      if (counted) {
        this.playing--;
        if (this.playing === 0) {
          const waiters = this.playWaiters.splice(0);
          for (const waiter of waiters) waiter();
        }
      }
    }
  }

  private waitForPlaying(): Promise<void> {
    if (this.playing === 0) return Promise.resolve();
    return new Promise<void>((resolve) => this.playWaiters.push(resolve));
  }

  private async dispose(): Promise<void> {
    const task = this.resourceBarrier.catch(() => {}).then(async() => {
      const pending = this.preparing;
      if (pending) {
        try { await pending; } catch (e) {}
      }
      await this.waitForPlaying();
      const pool = this.pool;
      const soundId = this.soundId;
      this.pool = null;
      this.soundId = 0;
      if (!pool) return;
      for (const stream of this.streams.splice(0)) {
        try { await pool.stop(stream); } catch (e) {}
      }
      try { if (soundId > 0) await pool.unload(soundId); } catch (e) {}
      try { await pool.release(); } catch (e) {}
    });
    this.resourceBarrier = task.catch(() => {});
    await task;
  }

  /** Stop accepting playback while the ability is backgrounded. */
  async suspend(): Promise<void> {
    this.active = false;
    this.lifecycleToken++;
    this.cancelLoadWait?.();
    await this.dispose();
  }

  resume(): void {
    this.active = true;
    this.lifecycleToken++;
  }

  async release(): Promise<void> {
    this.active = false;
    this.lifecycleToken++;
    this.cancelLoadWait?.();
    await this.dispose();
  }
}
