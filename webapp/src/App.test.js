import {createElement} from 'react';
import {renderToString} from 'react-dom/server';
import {describe, expect, test} from 'vitest';
import App, {toBatteryPercent, toSignalBars, wifiDbmToBars} from './App';

describe('dashboard measurement helpers', () => {
  test('renders the dashboard shell', () => {
    expect(renderToString(createElement(App))).toContain('class="App"');
  });

  test('maps RF strength to signal bars', () => {
    expect(toSignalBars(null)).toBe(0);
    expect(toSignalBars(20)).toBe(1);
    expect(toSignalBars(80)).toBe(4);
  });

  test('maps Wi-Fi RSSI to signal bars', () => {
    expect(wifiDbmToBars(-90)).toBe(0);
    expect(wifiDbmToBars(-70)).toBe(2);
    expect(wifiDbmToBars(-50)).toBe(4);
  });

  test('uses reported battery percentage before estimating voltage', () => {
    expect(toBatteryPercent({battery_percent: 76, battery_vp: 3.5})).toBe(76);
    expect(toBatteryPercent({battery_vp: 3.7})).toBe(50);
  });
});
