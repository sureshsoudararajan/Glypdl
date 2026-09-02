import { NATIVE_HOST_NAME } from '../../shared/constants';
import { createPingMessage } from '../../shared/protocol';
import { ConnectionStatus, ProtocolMessage, ProtocolResponse } from '../../shared/types';
import { GlypdlConnection } from './GlypdlConnection';

export class NativeMessagingConnection implements GlypdlConnection {
  readonly id = 'native-messaging';
  readonly name = 'Firefox Native Messaging';

  private connected = false;
  private lastStatus: ConnectionStatus = {
    connected: false,
    glypdlRunning: false,
    isFlatpak: false,
    lastChecked: 0
  };

  async connect(): Promise<boolean> {
    try {
      const resp = await this.sendMessage(createPingMessage());
      this.connected = !!resp.success;
      this.lastStatus = {
        connected: this.connected,
        hostVersion: resp.hostVersion,
        glypdlRunning: !!resp.glypdlRunning,
        glypdlVersion: resp.glypdlVersion,
        error: resp.error,
        isFlatpak: false,
        lastChecked: Date.now()
      };
      return this.connected;
    } catch (err: unknown) {
      this.connected = false;
      this.lastStatus = {
        connected: false,
        glypdlRunning: false,
        error: err instanceof Error ? err.message : String(err),
        isFlatpak: false,
        lastChecked: Date.now()
      };
      return false;
    }
  }

  disconnect(): void {
    this.connected = false;
  }

  isConnected(): boolean {
    return this.connected;
  }

  async getStatus(): Promise<ConnectionStatus> {
    if (Date.now() - this.lastStatus.lastChecked > 5000) {
      await this.connect();
    }
    return this.lastStatus;
  }

  async sendMessage(message: ProtocolMessage): Promise<ProtocolResponse> {
    return new Promise((resolve) => {
      // @ts-expect-error browser runtime API
      const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

      if (!browserApi || !browserApi.runtime || !browserApi.runtime.sendNativeMessage) {
        resolve({
          protocolVersion: 1,
          success: false,
          error: 'Native Messaging API is not available in this browser environment.'
        });
        return;
      }

      try {
        browserApi.runtime.sendNativeMessage(
          NATIVE_HOST_NAME,
          message,
          (response: ProtocolResponse) => {
            const err = browserApi.runtime.lastError;
            if (err) {
              resolve({
                protocolVersion: 1,
                success: false,
                connected: false,
                error: err.message || 'Failed to communicate with Native Messaging Host'
              });
              return;
            }
            if (!response) {
              resolve({
                protocolVersion: 1,
                success: false,
                connected: false,
                error: 'No response received from Glypdl Native Messaging Host'
              });
              return;
            }
            resolve(response);
          }
        );
      } catch (exc: unknown) {
        resolve({
          protocolVersion: 1,
          success: false,
          connected: false,
          error: exc instanceof Error ? exc.message : String(exc)
        });
      }
    });
  }
}
