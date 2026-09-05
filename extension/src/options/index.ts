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
            ? `Connected to Glypdl Desktop Application (v${status.glypdlVersion || '1.1.0'})`
            : 'Native Host is active (Glypdl desktop app is not currently open and will launch on demand)';
          resultBox.replaceChildren();
          const bold = document.createElement('b');
          bold.textContent = 'Success:';
          resultBox.append('✓ ', bold, ` ${glypdlText}`);
        } else {
          resultBox.className = 'status-box fail';
          resultBox.replaceChildren();

          const errorBold = document.createElement('b');
          errorBold.textContent = 'Not Connected:';
          const errText = status?.error || 'Native Messaging Host not registered.';

          const resolveTitle = document.createElement('b');
          resolveTitle.textContent = 'To resolve:';

          const ol = document.createElement('ol');
          ol.style.margin = '8px 0 0 16px';
          ol.style.padding = '0';

          const li1 = document.createElement('li');
          li1.textContent = 'Open the Glypdl desktop app.';
          const li2 = document.createElement('li');
          li2.textContent = 'Go to Preferences (Ctrl+,) → Extension tab.';
          const li3 = document.createElement('li');
          li3.textContent = 'Click [Register Host].';
          ol.append(li1, li2, li3);

          resultBox.append('❌ ', errorBold, ` ${errText}`, document.createElement('br'), document.createElement('br'), resolveTitle, ol);
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
