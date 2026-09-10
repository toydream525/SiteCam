export const transcode: (source: string, destination: string, rgba: ArrayBuffer, width: number, height: number) => Promise<void>;
export const cancel: () => void;
export const progress: () => number;
