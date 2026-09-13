export function flashModeLabel(mode: string): string {
  if (mode === 'auto') return '自动';
  if (mode === 'on') return '开启';
  if (mode === 'torch') return '常亮';
  return '关闭';
}

export function nextSupportedFlashMode(current: string, modes: string[]): string {
  const supported: string[] = [];
  for (const mode of modes) {
    if ((mode === 'off' || mode === 'auto' || mode === 'on') && !supported.includes(mode)) supported.push(mode);
  }
  if (!supported.length) return 'off';
  const index = supported.indexOf(current);
  return supported[(index < 0 ? -1 : index) + 1 >= supported.length ? 0 : (index < 0 ? 0 : index + 1)];
}
