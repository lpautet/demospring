import {createElement} from 'react';
import {renderToString} from 'react-dom/server';
import {describe, expect, test} from 'vitest';
import App, {decodeBase64Url, encodeBase64Url, toBatteryPercent, toSignalBars, wifiDbmToBars} from './App';

describe('dashboard measurement helpers', () => {
  test('renders the passkey sign-in shell before a session is known', () => {
    const html = renderToString(createElement(App));
    expect(html).toContain('class="App auth-page"');
    expect(html).toContain('Checking session');
  });

  test('round-trips WebAuthn binary values as base64url', () => {
    const input = Uint8Array.from([0, 127, 128, 255]).buffer;
    expect(new Uint8Array(decodeBase64Url(encodeBase64Url(input))))
      .toEqual(new Uint8Array(input));
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
