import { StorageService } from '../shared/storage';
import { ConnectionStatus, ExtensionSettings } from '../shared/types';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

class OptionsController {
  private settings: ExtensionSettings | null = null;

  async init(): Promise<void> {
    this.settings = await StorageService.getSettings();
    this.populateForm();
    this.bindEvents();
    this.runConnectionTest();
  }

  private populateForm(): void {
    if (!this.settings) return;

    const setChecked = (id: string, val: boolean) => {
      const el = document.getElementById(id) as HTMLInputElement | null;
      if (el) el.checked = val;
    };

    const setValue = (id: string, val: string | number) => {
      const el = document.getElementById(id) as HTMLInputElement | HTMLSelectElement | null;
      if (el) el.value = String(val);
    };

    setChecked('autoDetect', this.settings.autoDetect);
    setChecked('detectHtml5Video', this.settings.detectHtml5Video);
    setChecked('detectHtml5Audio', this.settings.detectHtml5Audio);
    setChecked('detectHls', this.settings.detectHls);
    setChecked('detectDash', this.settings.detectDash);
    setChecked('showPlayerButton', this.settings.showPlayerButton);
    setValue('minFileSizeKb', this.settings.minFileSizeKb);
  }

  private bindEvents(): void {
    const bindChange = (id: string, key: keyof ExtensionSettings, isBool = true) => {
      const el = document.getElementById(id) as HTMLInputElement | HTMLSelectElement | null;
      el?.addEventListener('change', async () => {
        const val = isBool ? (el as HTMLInputElement).checked : (el as HTMLSelectElement).value;
        await StorageService.saveSettings({ [key]: val } as any);
      });
    };

    bindChange('autoDetect', 'autoDetect', true);
    bindChange('detectHtml5Video', 'detectHtml5Video', true);
    bindChange('detectHtml5Audio', 'detectHtml5Audio', true);
    bindChange('detectHls', 'detectHls', true);
    bindChange('detectDash', 'detectDash', true);
    bindChange('showPlayerButton', 'showPlayerButton', true);
    bindChange('minFileSizeKb', 'minFileSizeKb', false);

    document.getElementById('test-connection-btn')?.addEventListener('click', () => {
      this.runConnectionTest();
    });
  }

  private runConnectionTest(): void {
    const resultBox = document.getElementById('connection-result');
    if (!resultBox || !browserApi?.runtime) return;

    resultBox.style.display = 'block';
    resultBox.className = 'status-box';
    resultBox.textContent = 'Testing connection with Glypdl Native Host…';

    try {
      browserApi.runtime.sendMessage({ action: 'test_connection' }, (status: ConnectionStatus) => {
        if (status && status.connected) {
          resultBox.className = 'status-box ok';
          const glypdlText = status.glypdlRunning
            ? `Connected to Glypdl Desktop Application (v${status.glypdlVersion || '1.0.0'})`
            : 'Native Host is active (Glypdl desktop app is not currently open and will launch on demand)';
          resultBox.innerHTML = `✓ <b>Success:</b> ${glypdlText}`;
        } else {
          resultBox.className = 'status-box fail';
          resultBox.innerHTML = `
            ❌ <b>Not Connected:</b> ${status?.error || 'Native Messaging Host not registered.'}<br/><br/>
            <b>To resolve:</b><br/>
            1. Open the <b>Glypdl</b> desktop app.<br/>
            2. Go to <b>Preferences (Ctrl+,) &rarr; Extension</b> tab.<br/>
            3. Click <b>[Register Host]</b>.
          `;
        }
      });
    } catch (e) {
      resultBox.className = 'status-box fail';
      resultBox.textContent = `Connection test failed: ${e}`;
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const options = new OptionsController();
  options.init();
});
